package com.i113w.better_mine_team.common.network.handler;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.network.rts.C2S_IssueCommandPayload;
import com.i113w.better_mine_team.common.network.rts.C2S_SelectionSyncPayload;
import com.i113w.better_mine_team.common.network.rts.S2C_CommandAckPayload;
import com.i113w.better_mine_team.common.registry.ModAttachments;
import com.i113w.better_mine_team.common.rts.ai.RTSUnitAIController;
import com.i113w.better_mine_team.common.rts.data.RTSPlayerData;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ServerPacketHandler {

    // 最大控制距离 (格)
    private static final double MAX_CONTROL_DISTANCE_SQR = 256.0 * 256.0;

    // 处理选区同步
    public static void handleSelectionSync(final C2S_SelectionSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RTSPlayerData data = player.getData(ModAttachments.PLAYER_DATA);

                BetterMineTeam.debug("[RTS-SERVER] 📥 Selection sync received from {}: {} entities (revision: {})",
                        player.getName().getString(),
                        payload.entityIds().size(),
                        payload.revision());

                data.updateSelection(payload.entityIds(), payload.revision());
            }
        });
    }

    // 处理指令发布
    public static void handleIssueCommand(final C2S_IssueCommandPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // [优化] 使用 Debug 日志防止刷屏
            if (BMTConfig.isDebugEnabled()) {
                BetterMineTeam.debug("========================================");
                BetterMineTeam.debug("[RTS-SERVER] 📥 Command received");
                BetterMineTeam.debug("[RTS-SERVER] Player: {}", player.getName().getString());
                BetterMineTeam.debug("[RTS-SERVER] Command Type: {}", payload.commandType());
                BetterMineTeam.debug("[RTS-SERVER] Target: {}", payload.target());
            }

            RTSPlayerData playerData = player.getData(ModAttachments.PLAYER_DATA);
            Set<Integer> selectedIds = playerData.getSelection();

            if (selectedIds.isEmpty()) {
                BetterMineTeam.debug("[RTS-SERVER] ⚠️ No entities in selection! Check if C2S_SelectionSync was received.");
                sendAck(player, false, 0, Component.translatable("better_mine_team.msg.cmd_no_units").withStyle(ChatFormatting.RED));
                return;
            }

            Level level = player.level();
            List<Mob> validUnits = new ArrayList<>();
            PlayerTeam playerTeam = TeamManager.getTeam(player);

            // [优化] 移除大量 INFO 日志，改为 Debug
            BetterMineTeam.debug("[RTS-SERVER] Validating {} selected entities...", selectedIds.size());

            for (int id : selectedIds) {
                Entity entity = level.getEntity(id);

                if (entity == null) {
                    continue;
                }

                if (!(entity instanceof Mob mob)) {
                    continue;
                }

                if (!mob.isAlive()) {
                    continue;
                }

                // [修复] 严格的权限校验
                if (isValidController(player, playerTeam, mob, payload.commandType())) {
                    validUnits.add(mob);
                } else {
                    BetterMineTeam.debug("[RTS-SERVER] ❌ Permission denied or invalid state for entity {}", id);
                }
            }

            int successCount = validUnits.size();
            BetterMineTeam.debug("[RTS-SERVER] Valid units count: {}/{}", successCount, selectedIds.size());

            if (successCount == 0) {
                sendAck(player, false, 0, Component.translatable("better_mine_team.msg.cmd_no_units").withStyle(ChatFormatting.RED));
                return;
            }

            // 执行指令
            switch (payload.commandType()) {
                case MOVE -> executeMoveCommand(validUnits, payload.target().pos());
                case ATTACK -> executeAttackCommand(validUnits, level, payload.target().targetEntityId(), payload.secondaryTargetIds());
                case STOP -> executeStopCommand(validUnits);
                case RECRUIT -> executeRecruitCommand(player, validUnits);
            }

            BetterMineTeam.debug("[RTS-SERVER] ✅ Command executed successfully");

            sendAck(player, true, successCount, Component.translatable("better_mine_team.msg.cmd_ack", successCount).withStyle(ChatFormatting.GREEN));
        });
    }

    private static void executeAttackCommand(List<Mob> units, Level level, int primaryTargetId, List<Integer> secondaryTargetIds) {
        // 1. 收集所有目标 (主 + 副)
        List<Entity> allTargets = new ArrayList<>();
        Entity primaryTarget = level.getEntity(primaryTargetId);
        if (primaryTarget != null) allTargets.add(primaryTarget);

        for (int id : secondaryTargetIds) {
            Entity e = level.getEntity(id);
            if (e != null && e != primaryTarget) allTargets.add(e);
        }

        if (allTargets.isEmpty()) return;

        BetterMineTeam.debug("[RTS-ATTACK-CMD] Units: {}, Targets: {}", units.size(), allTargets.size());

        // 2. 处理团队混战逻辑 (Team Aggression)
        for (Mob unit : units) {
            PlayerTeam unitTeam = TeamManager.getTeam(unit);
            if (unitTeam == null) continue;

            for (Entity target : allTargets) {
                if (!(target instanceof LivingEntity livingTarget)) continue;
                if (TeamManager.isAlly(unit, livingTarget)) continue; // 不打自己人

                PlayerTeam targetTeam = TeamManager.getTeam(target);

                if (targetTeam != null) {
                    TeamManager.scanAndAddThreats(unitTeam, targetTeam, livingTarget);
                    // [可选] 双向宣战
                    TeamManager.scanAndAddThreats(targetTeam, unitTeam, unit);
                } else {
                    TeamManager.addThreat(unitTeam, livingTarget);
                }
            }
        }

        // 3. 分配攻击目标
        // [修复] 增加类型检查，确保攻击目标是 LivingEntity
        if (primaryTarget instanceof LivingEntity livingPrimary) {
            for (Mob unit : units) {
                if (unit == livingPrimary) continue;
                if (TeamManager.isAlly(unit, livingPrimary)) continue;

                RTSUnitAIController.setAttackTarget(unit, livingPrimary);
            }
        } else if (!allTargets.isEmpty()) {
            // 如果主目标无效，寻找第一个有效的 LivingEntity
            LivingEntity fallbackTarget = null;
            for (Entity e : allTargets) {
                if (e instanceof LivingEntity le && le.isAlive()) {
                    fallbackTarget = le;
                    break;
                }
            }

            if (fallbackTarget != null) {
                for (Mob unit : units) {
                    if (!TeamManager.isAlly(unit, fallbackTarget)) {
                        RTSUnitAIController.setAttackTarget(unit, fallbackTarget);
                    }
                }
            }
        }
    }

    // [修复] 增强的权限校验逻辑
    private static boolean isValidController(ServerPlayer player, PlayerTeam playerTeam, Mob mob, com.i113w.better_mine_team.common.network.data.CommandType commandType) {
        // 1. 维度检查
        if (player.level() != mob.level()) return false;

        // 2. 区块加载检查 (防止操作卸载区块实体)
        if (!mob.level().isLoaded(mob.blockPosition())) return false;

        // 3. 距离检查 (防作弊/防误操作)
        if (player.distanceToSqr(mob) > MAX_CONTROL_DISTANCE_SQR) return false;

        if (commandType == com.i113w.better_mine_team.common.network.data.CommandType.RECRUIT) {
            if (com.i113w.better_mine_team.common.team.TeamPermissions.hasOverridePermission(player)) {
                // 允许操作无队伍生物
                return TeamManager.getTeam(mob) == null;
            }
            return false;
        }

        // 4. 所有权/队伍检查
        boolean hasPermission = false;
        PlayerTeam mobTeam = TeamManager.getTeam(mob);
        if (playerTeam != null && mobTeam != null && playerTeam.getName().equals(mobTeam.getName())) {
            hasPermission = true;
        } else if (mob instanceof net.minecraft.world.entity.TamableAnimal tamable) {
            if (tamable.isOwnedBy(player)) {
                hasPermission = true;
            }
        }
        return hasPermission;
    }

    private static void executeMoveCommand(List<Mob> units, Vec3 centerTarget) {
        int count = units.size();
        BetterMineTeam.debug("[RTS-MOVE-CMD] Executing for {} units to {}", count, centerTarget);

        if (count == 0) return;

        if (count == 1) {
            RTSUnitAIController.setMoveTarget(units.get(0), centerTarget);
            return;
        }

        // 简单的网格阵型
        int cols = (int) Math.ceil(Math.sqrt(count));
        double spacing = 2.0;

        for (int i = 0; i < count; i++) {
            Mob unit = units.get(i);
            int row = i / cols;
            int col = i % cols;

            double offsetX = (col - (cols - 1) / 2.0) * spacing;
            double offsetZ = (row - (cols - 1) / 2.0) * spacing;

            Vec3 unitTarget = centerTarget.add(offsetX, 0, offsetZ);
            RTSUnitAIController.setMoveTarget(unit, unitTarget);
        }
    }

    private static void executeStopCommand(List<Mob> units) {
        BetterMineTeam.debug("[RTS-STOP-CMD] Executing for {} units", units.size());
        for (Mob unit : units) {
            RTSUnitAIController.stop(unit);
        }
    }
    private static void executeRecruitCommand(ServerPlayer player, List<Mob> units) {
        // 1. 二次权限检查 (以防万一)
        if (!com.i113w.better_mine_team.common.team.TeamPermissions.hasOverridePermission(player)) {
            player.displayClientMessage(Component.translatable("better_mine_team.msg.permission_denied").withStyle(ChatFormatting.RED), true);
            return;
        }

        PlayerTeam playerTeam = TeamManager.getTeam(player);
        if (playerTeam == null) {
            player.displayClientMessage(Component.translatable("message.better_mine_team.error.no_team_specified", player.getName()).withStyle(ChatFormatting.RED), true);
            return;
        }

        int successCount = 0;
        net.minecraft.world.scores.Scoreboard scoreboard = player.getScoreboard();

        for (Mob mob : units) {
            // 2. 检查生物是否已有队伍
            PlayerTeam mobTeam = TeamManager.getTeam(mob);
            if (mobTeam != null) {
                // 如果已经有队伍，跳过 (或者如果你想允许抢人，可以去掉这个检查)
                // 提示：如果要允许抢人，请确保处理好原队伍的仇恨移除
                continue;
            }

            // 3. 核心入队逻辑 (参考 MobTeamEventSubscriber)
            scoreboard.addPlayerToTeam(mob.getStringUUID(), playerTeam);

            // 4. 设置属性
            var followAttribute = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            if (followAttribute != null) {
                double newRange = BMTConfig.getGuardFollowRange();
                if (followAttribute.getBaseValue() < newRange) {
                    followAttribute.setBaseValue(newRange);
                }
            }

            mob.setHealth(mob.getMaxHealth());
            mob.getPersistentData().putBoolean("bmt_follow_enabled", false);
            mob.setPersistenceRequired(); // 防止刷没

            // 5. 添加 AI 目标
            mob.targetSelector.addGoal(1, new com.i113w.better_mine_team.common.entity.goal.TeamHurtByTargetGoal(mob));
            mob.goalSelector.addGoal(2, new com.i113w.better_mine_team.common.entity.goal.TeamFollowCaptainGoal(mob,
                    BMTConfig.getGuardFollowSpeed(),
                    BMTConfig.getGuardFollowStartDist(),
                    BMTConfig.getGuardFollowStopDist()));

            // 6. 发光特效
            mob.setGlowingTag(true);

            // 7. 特效反馈 (可选：播放声音或粒子)
            // level.broadcastEntityEvent(mob, (byte) ...);

            successCount++;
        }

        if (successCount > 0) {
            player.displayClientMessage(Component.translatable("better_mine_team.msg.recruit_success", successCount).withStyle(ChatFormatting.GREEN), true);
        } else {
            player.displayClientMessage(Component.translatable("better_mine_team.msg.recruit_fail_no_target").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    private static void sendAck(ServerPlayer player, boolean success, int count, Component msg) {
        PacketDistributor.sendToPlayer(player, new S2C_CommandAckPayload(success, count, msg));
    }
}