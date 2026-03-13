package com.i113w.better_mine_team.common.entity.goal;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.registry.ModTags;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class AggressiveScanGoal extends TargetGoal implements TeamGoal {

    private final PathfinderMob mob;
    private LivingEntity foundTarget = null;

    public AggressiveScanGoal(PathfinderMob mob) {
        super(mob, false);
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        int aggressiveLevel = TeamManager.getAggressiveLevel(mob);
        if (aggressiveLevel == 0) return false;

        // 使用随机节流替代死板的 Ticker，避免由于被打断而导致的死锁，分散服务器性能压力
        int interval = BMTConfig.getAggressiveScanEntityInterval();
        if (mob.getRandom().nextInt(Math.max(1, interval)) != 0) return false;

        PlayerTeam myTeam = TeamManager.getTeam(mob);
        if (myTeam == null) return false;

        // Per-team throttle
        long now = mob.level().getGameTime();
        if (TeamManager.isAggressiveScanOnCooldown(myTeam, now)) return false;

        // Already engaged with a valid enemy — don't interrupt
        LivingEntity current = mob.getTarget();
        if (current != null && current.isAlive() && !TeamManager.isAlly(mob, current)) return false;

        foundTarget = findTarget(aggressiveLevel, myTeam);
        if (foundTarget == null) return false;

        TeamManager.markAggressiveScanDone(myTeam, now);
        BetterMineTeam.debug("AggressiveScanGoal (L{}): {} → {}",
                aggressiveLevel, mob.getName().getString(), foundTarget.getName().getString());
        return true;
    }

    @Override
    public void start() {
        mob.setTarget(foundTarget);
        super.start(); // TargetGoal.start() 会将 mob.getTarget() 存入 this.targetMob
    }

    @Override
    public boolean canContinueToUse() {
        // 必须验证目标有效性并保持 true，否则原版 stop() 会瞬间清空 target！
        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget == null || !currentTarget.isAlive()) return false;
        if (TeamManager.isAlly(mob, currentTarget)) return false;

        // 依赖原版 TargetGoal 的可见性/距离持续性检查
        return super.canContinueToUse();
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public void resetScanTicker() {
        // 随机节流模式下无需手动重置 Ticker，保留空方法兼容其他类的调用
    }

    // ── Target search ────────────────────────────────────────────────────────

    private LivingEntity findTarget(int aggressiveLevel, PlayerTeam myTeam) {
        double radius = BMTConfig.getAggressiveScanRadius();
        AABB box = mob.getBoundingBox().inflate(radius, 8.0, radius);

        List<LivingEntity> candidates = mob.level().getEntitiesOfClass(
                LivingEntity.class, box,
                e -> isValidTarget(e, aggressiveLevel, myTeam)
        );

        return candidates.stream()
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .filter(e -> this.canAttack(e, TargetingConditions.DEFAULT))
                .orElse(null);
    }

    private boolean isValidTarget(LivingEntity candidate, int aggressiveLevel, PlayerTeam myTeam) {
        if (candidate == mob || !candidate.isAlive() || candidate.isSpectator()) return false;
        if (TeamManager.isAlly(mob, candidate)) return false;
        if (candidate instanceof Mob m && m.isNoAi()) return false;
        if (candidate instanceof Player p && (p.isCreative() || p.isSpectator())) return false;

        String typeId = candidate.getType().builtInRegistryHolder().key().location().toString();
        if (BMTConfig.getAggressiveEntityBlacklist().contains(typeId)) return false;

        return aggressiveLevel == 1 ? isLevel1Target(candidate, myTeam) : isLevel2Target(candidate);
    }

    private boolean isLevel1Target(LivingEntity candidate, PlayerTeam myTeam) {
        if (!(candidate instanceof Mob aggressorMob)) return false;
        LivingEntity aggressorTarget = aggressorMob.getTarget();
        if (aggressorTarget == null || !aggressorTarget.isAlive()) return false;
        PlayerTeam victimTeam = TeamManager.getTeam(aggressorTarget);
        return victimTeam != null && victimTeam.getName().equals(myTeam.getName());
    }

    private boolean isLevel2Target(LivingEntity candidate) {
        // 如果该实体存在于 Tag 黑名单中，则忽略
        return !candidate.getType().is(ModTags.Entities.IGNORED_BY_LEVEL2_SCAN);
    }
}