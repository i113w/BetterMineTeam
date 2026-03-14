package com.i113w.better_mine_team.common.entity.goal;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;

import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class TeamHurtByTargetGoal extends TargetGoal implements TeamGoal {

    private int checkTicker   = 0;
    private int debugCooldown = 0;

    // ── Attack Commitment ──
    private WeakReference<LivingEntity> committedTarget = new WeakReference<>(null);
    private int commitmentTicks = 0;

    public TeamHurtByTargetGoal(Mob mob) {
        super(mob, false);
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (debugCooldown > 0) debugCooldown--;

        PlayerTeam myTeam = TeamManager.getTeam(this.mob);
        if (myTeam == null) return false;

        // 获取最佳威胁
        LivingEntity bestThreat = TeamManager.getBestThreat(myTeam, this.mob);

        if (bestThreat == null) return false;

        if (this.mob.getTarget() == bestThreat) return false;

        if (!this.canAttack(bestThreat, TargetingConditions.DEFAULT)) return false;
        if (TeamManager.isAlly(this.mob, bestThreat)) return false;

        // 必须赋值给父类的 targetMob，否则 super.start() 会清空目标
        this.targetMob = bestThreat;
        return true;
    }

    @Override
    public void start() {
        PlayerTeam enemyTeam = TeamManager.getTeam(this.targetMob);
        LivingEntity finalTarget = this.targetMob;

        if (enemyTeam != null) {
            double scanRange = BMTConfig.getTacticalSwitchRange();
            AABB searchBox = this.mob.getBoundingBox().inflate(scanRange, 8.0D, scanRange);

            List<LivingEntity> closeEnemies = this.mob.level().getEntitiesOfClass(LivingEntity.class, searchBox, entity -> {
                if (entity == this.mob || !entity.isAlive()) return false;
                PlayerTeam otherTeam = TeamManager.getTeam(entity);
                return otherTeam != null && otherTeam.getName().equals(enemyTeam.getName());
            });

            if (!closeEnemies.isEmpty()) {
                closeEnemies.sort(Comparator.comparingDouble(this.mob::distanceToSqr));
                LivingEntity closestEnemy = closeEnemies.get(0);
                if (canAttack(closestEnemy, TargetingConditions.DEFAULT)) {
                    finalTarget = closestEnemy;
                }
            }
        }

        // 同样更新 targetMob
        this.targetMob = finalTarget;
        this.mob.setTarget(this.targetMob);
        establishCommitment(this.targetMob);

        super.start(); // 此时 super.start() 会正确读取 targetMob 并锁定
    }

    @Override
    public void stop() {
        super.stop();
        resetCommitment();
    }

    @Override
    public void tick() {
        super.tick();

        // 承诺倒计时
        if (commitmentTicks > 0) {
            commitmentTicks--;
            if (isCommitmentBroken()) {
                BetterMineTeam.debug("Commitment broken for {}, resetting.", this.mob.getName().getString());
                resetCommitment();
            }
        }

        if (++checkTicker < 10) return;
        checkTicker = 0;

        PlayerTeam myTeam = TeamManager.getTeam(this.mob);
        if (myTeam == null) return;

        LivingEntity bestNow = TeamManager.getBestThreat(myTeam, this.mob);
        LivingEntity current = this.mob.getTarget();

        if (bestNow == null || bestNow == current) return;
        if (!canAttack(bestNow, TargetingConditions.DEFAULT)) return;

        double distToCurrent = (current == null) ? Double.MAX_VALUE : this.mob.distanceToSqr(current);
        double distToNew = this.mob.distanceToSqr(bestNow);

        int hardThreshold = BMTConfig.getAttackCommitmentSoftTicks() - BMTConfig.getAttackCommitmentHardTicks();

        if (commitmentTicks > hardThreshold) return;

        if (commitmentTicks > 0) {
            double switchRatio = BMTConfig.getAttackCommitmentSwitchRatio();
            if (distToNew >= distToCurrent * switchRatio) return;
        } else {
            if (distToNew >= distToCurrent - 16.0D) return;
        }

        // 切换目标时，不仅要 setTarget，还要同步给父类的 targetMob
        this.targetMob = bestNow;
        this.mob.setTarget(this.targetMob);
        establishCommitment(this.targetMob);
    }

    // 辅助方法
    private void establishCommitment(LivingEntity target) {
        this.committedTarget = new WeakReference<>(target);
        this.commitmentTicks = BMTConfig.getAttackCommitmentSoftTicks();
    }

    private void resetCommitment() {
        this.committedTarget = new WeakReference<>(null);
        this.commitmentTicks = 0;
    }

    private boolean isCommitmentBroken() {
        LivingEntity target = committedTarget.get();
        if (target == null || !target.isAlive() || target.isRemoved()) return true;
        if (target.level() != this.mob.level()) return true;

        double followRange = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (this.mob.distanceToSqr(target) > followRange * followRange * 4.0) return true;

        LivingEntity lastAttacker = this.mob.getLastHurtByMob();
        if (lastAttacker != null && lastAttacker != target && lastAttacker.isAlive() && !TeamManager.isAlly(this.mob, lastAttacker)) {
            int ticksSinceHurt = this.mob.tickCount - this.mob.getLastHurtByMobTimestamp();
            if (ticksSinceHurt < 20) return true;
        }

        return false;
    }
}