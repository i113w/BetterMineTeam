package com.i113w.better_mine_team.common.team;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamManager {

    public static final String TEAM_PREFIX = BetterMineTeam.MODID + "_";
    private static final int MAX_THREATS_PER_TEAM = 20;

    // 威胁记录：实体 + 时间戳
    public record ThreatEntry(LivingEntity entity, long timestamp) {}

    // 核心数据存储
    // Key: TeamName, Value: 仇恨列表
    // 使用 ConcurrentHashMap 保证 Map 结构的线程安全
    // 列表内容的安全性通过 Copy-On-Write 策略在 doAddThreat 中保证
    private static final Map<String, List<ThreatEntry>> teamThreats = new ConcurrentHashMap<>();

    // [新增] 扫描冷却记录，防止团战时 TPS 暴跌
    // Key: TeamName, Value: 上次全图扫描的 GameTime
    private static final Map<String, Long> teamScanCooldowns = new ConcurrentHashMap<>();

    /**
     * 服务器启动时初始化队伍
     */
    public static void initTeams(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        for (DyeColor color : DyeColor.values()) {
            String teamName = getTeamName(color);
            PlayerTeam playerTeam = scoreboard.getPlayerTeam(teamName);
            if (playerTeam == null) {
                playerTeam = scoreboard.addPlayerTeam(teamName);
            }
            playerTeam.setDisplayName(net.minecraft.network.chat.Component.translatable("color.minecraft." + color.getName()));
            ChatFormatting formatting = dyeToChatFormatting(color);
            if (formatting != null) {
                playerTeam.setColor(formatting);
            }
            // 默认设置：禁止友伤，允许看到隐身队友
            if (playerTeam.getCollisionRule() == net.minecraft.world.scores.Team.CollisionRule.ALWAYS) {
                playerTeam.setAllowFriendlyFire(false);
                playerTeam.setSeeFriendlyInvisibles(true);
            }
        }
    }

    /**
     * [新增] 清理所有静态数据
     * 必须在 ServerStoppingEvent 中调用，防止内存泄漏和跨存档数据污染
     */
    public static void clearAllData() {
        teamThreats.clear();
        teamScanCooldowns.clear();
        BetterMineTeam.debug("TeamManager data cleared.");
    }

    /**
     * 添加单一仇恨目标
     */
    public static void addThreat(PlayerTeam team, LivingEntity target) {
        if (team == null || target == null || !target.isAlive()) return;
        doAddThreat(team.getName(), target);
    }

    /**
     * [优化] 扫描并添加仇恨 (战争迷雾/连锁仇恨)
     * 包含冷却机制，防止高频调用导致卡顿。
     */
    public static void scanAndAddThreats(PlayerTeam myTeam, PlayerTeam enemyTeam, LivingEntity center) {
        if (myTeam == null || enemyTeam == null || center == null) return;

        long currentTime = center.level().getGameTime();
        String teamName = myTeam.getName();

        // 1. 检查扫描冷却 (3秒 = 60 ticks)
        // 如果冷却未到，只添加中心目标，跳过昂贵的 AABB 扫描
        long lastScan = teamScanCooldowns.getOrDefault(teamName, 0L);
        if (currentTime - lastScan < 60L) {
            doAddThreat(teamName, center);
            return;
        }

        // 2. 更新冷却时间
        teamScanCooldowns.put(teamName, currentTime);

        // 3. 执行全范围扫描
        double range = BMTConfig.getWarPropagationRange();
        AABB area = center.getBoundingBox().inflate(range, 32.0D, range);

        List<LivingEntity> enemies = center.level().getEntitiesOfClass(LivingEntity.class, area, e -> {
            if (!e.isAlive()) return false;
            PlayerTeam t = getTeam(e);
            return t != null && t.getName().equals(enemyTeam.getName());
        });

        if (!enemies.isEmpty()) {
            BetterMineTeam.debug("WAR: Team {} scanned around {}: Found {} enemies of Team {}.",
                    myTeam.getName(), center.getName().getString(), enemies.size(), enemyTeam.getName());
        }

        // 4. 批量添加仇恨
        for (LivingEntity enemy : enemies) {
            doAddThreat(teamName, enemy);
        }
    }

    /**
     * [新增] 重置扫描冷却
     * 建议在击杀事件(onLivingDeath)中调用，确保击杀后能立即索敌远处的敌人
     */
    public static void resetScanCooldown(String teamName) {
        teamScanCooldowns.remove(teamName);
    }

    /**
     * [核心修复] 线程安全的添加仇恨逻辑
     * 使用 Copy-On-Write 策略：每次修改都创建新列表，避免读取端发生 CME 异常。
     */
    private static void doAddThreat(String teamName, LivingEntity target) {
        teamThreats.compute(teamName, (key, oldList) -> {
            // 1. 创建新列表 (复制旧数据或新建)
            List<ThreatEntry> newList = (oldList == null) ? new ArrayList<>() : new ArrayList<>(oldList);
            long currentTime = target.level().getGameTime();

            // 2. 移除旧的同名目标 (更新时间戳)
            newList.removeIf(e -> e.entity() == target);

            // 3. 添加新目标
            newList.add(new ThreatEntry(target, currentTime));

            // 4. 限制列表大小 (LRU: 移除最早的)
            if (newList.size() > MAX_THREATS_PER_TEAM) {
                newList.sort(Comparator.comparingLong(ThreatEntry::timestamp));
                while (newList.size() > MAX_THREATS_PER_TEAM) {
                    newList.remove(0); // 移除时间戳最小(最早)的
                }
            }

            // 5. 返回新列表，原子替换 Map 中的 Value
            return newList;
        });

        BetterMineTeam.debug("Threat update for Team {}: {}", teamName, target.getName().getString());
    }

    /**
     * [核心修复] 获取最佳仇恨目标
     * 由于 doAddThreat 使用了写时复制，这里获取到的 List 是不可变快照，
     * 即使其他线程正在写入，这里也不会报错，无需加锁。
     */
    @Nullable
    public static LivingEntity getBestThreat(PlayerTeam team, LivingEntity seeker) {
        if (team == null || seeker == null) return null;

        List<ThreatEntry> threats = teamThreats.get(team.getName());
        if (threats == null || threats.isEmpty()) return null;

        LivingEntity bestTarget = null;
        double minDistanceSqr = Double.MAX_VALUE;

        long currentTime = seeker.level().getGameTime();
        long memoryDuration = BMTConfig.getTeamHateMemoryDuration();

        for (ThreatEntry entry : threats) {
            LivingEntity target = entry.entity();

            // 基础有效性检查
            if (target == null || !target.isAlive() || target.isRemoved()) {
                continue;
            }

            // [核心修复 1] 绝对禁止攻击自己 (防止 DistSqr: 0.0 刷屏)
            if (target == seeker) {
                continue;
            }

            // [核心修复 2] 禁止攻击队友 (防止友伤导致的内讧死循环)
            // 这一步检查比 AI Goal 里的检查更早，能显著提升性能
            if (isAlly(target, seeker)) {
                continue;
            }

            // 超时检查
            if (currentTime - entry.timestamp() > memoryDuration) {
                continue;
            }

            // 跨世界检查
            if (target.level() != seeker.level()) {
                continue;
            }

            double distSqr = seeker.distanceToSqr(target);
            if (distSqr < minDistanceSqr) {
                minDistanceSqr = distSqr;
                bestTarget = target;
            }
        }

        return bestTarget;
    }

    /**
     * 当目标彻底死亡时，从所有队伍的仇恨列表中移除
     */
    public static void onTargetDeath(LivingEntity deadEntity) {
        // 遍历所有队伍，移除该实体
        teamThreats.forEach((teamName, list) -> {
            // 使用 compute 确保线程安全地更新列表
            teamThreats.computeIfPresent(teamName, (k, oldList) -> {
                // 如果列表中包含该实体，则创建新列表并移除
                // 这里做一个简单检查避免无意义的复制
                boolean contains = oldList.stream().anyMatch(e -> e.entity() == deadEntity);
                if (!contains) return oldList;

                List<ThreatEntry> newList = new ArrayList<>(oldList);
                newList.removeIf(e -> e.entity() == deadEntity);
                return newList;
            });
        });
    }

    public static ChatFormatting dyeToChatFormatting(DyeColor color) {
        return switch (color) {
            case WHITE -> ChatFormatting.WHITE;
            case ORANGE -> ChatFormatting.GOLD;
            case MAGENTA -> ChatFormatting.LIGHT_PURPLE;
            case LIGHT_BLUE -> ChatFormatting.AQUA;
            case YELLOW -> ChatFormatting.YELLOW;
            case LIME -> ChatFormatting.GREEN;
            case PINK -> ChatFormatting.LIGHT_PURPLE;
            case GRAY -> ChatFormatting.DARK_GRAY;
            case LIGHT_GRAY -> ChatFormatting.GRAY;
            case CYAN -> ChatFormatting.DARK_AQUA;
            case PURPLE -> ChatFormatting.DARK_PURPLE;
            case BLUE -> ChatFormatting.BLUE;
            case BROWN -> ChatFormatting.GOLD;
            case GREEN -> ChatFormatting.DARK_GREEN;
            case RED -> ChatFormatting.RED;
            case BLACK -> ChatFormatting.BLACK;
        };
    }

    public static String getTeamName(DyeColor color) {
        return TEAM_PREFIX + color.getName();
    }

    @Nullable
    public static PlayerTeam getTeam(Entity entity) {
        if (entity == null) return null;
        return entity.getTeam() instanceof PlayerTeam pt ? pt : null;
    }

    public static boolean isAlly(LivingEntity a, LivingEntity b) {
        if (a == null || b == null) return false;
        if (a.is(b)) return true;
        PlayerTeam teamA = getTeam(a);
        PlayerTeam teamB = getTeam(b);
        if (teamA == null || teamB == null) return false;
        return teamA.getName().equals(teamB.getName()) || teamA.isAlliedTo(teamB);
    }

    /**
     * 定期清理过期数据
     * 注意：这里也需要使用原子操作更新 Map
     */
    public static void cleanupExpiredHateData(MinecraftServer server) {
        long currentTime = server.overworld().getGameTime();
        long memoryDuration = BMTConfig.getTeamHateMemoryDuration();

        // 迭代 KeySet 避免直接操作 EntrySet 可能带来的并发问题
        for (String teamName : teamThreats.keySet()) {
            teamThreats.computeIfPresent(teamName, (key, oldList) -> {
                // 检查是否有过期的
                boolean hasExpired = oldList.stream().anyMatch(e ->
                        e.entity() == null || !e.entity().isAlive() || e.entity().isRemoved() ||
                                (currentTime - e.timestamp() > memoryDuration));

                if (!hasExpired) return oldList;

                // 创建新列表并清理
                List<ThreatEntry> newList = new ArrayList<>(oldList);
                newList.removeIf(e ->
                        e.entity() == null || !e.entity().isAlive() || e.entity().isRemoved() ||
                                (currentTime - e.timestamp() > memoryDuration));

                // 如果清理后为空，可以考虑返回 null (从 map 中移除)，或者返回空列表
                // 为了避免 null 处理复杂，返回空列表
                return newList;
            });
        }
    }
}