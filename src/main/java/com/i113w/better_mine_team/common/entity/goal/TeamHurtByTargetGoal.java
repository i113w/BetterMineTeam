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

/**
 * Replaces all vanilla target-selection goals for team mobs.
 *
 * <p>Implements {@link TeamGoal} so that {@link GoalSanitizer} never removes
 * this goal when processing a mob that joins a team.</p>
 *
 * <h3>Attack Commitment system</h3>
 * Prevents target thrashing when two enemies are equidistant.  Uses a two-phase window:
 * <ol>
 *   <li><b>Hard lock</b> (configurable, default 20 t): refuses any target switch.</li>
 *   <li><b>Soft commitment</b> (remainder up to configurable total, default 60 t):
 *       allows switching only when the new target is significantly closer
 *       (see {@code attackCommitmentSwitchRatio}).</li>
 * </ol>
 * Commitment is broken early by: target death/removal, cross-dimension teleport,
 * target escaping follow-range, or a third-party attacker hitting the mob recently.
 */
public class TeamHurtByTargetGoal extends TargetGoal implements TeamGoal {

    private LivingEntity targetToAttack;
    private int checkTicker   = 0;
    private int debugCooldown = 0;

    // ── Attack Commitment ────────────────────────────────────────────────────
    private WeakReference<LivingEntity> committedTarget = new WeakReference<>(null);
    private int commitmentTicks = 0;
    // ─────────────────────────────────────────────────────────────────────────

    public TeamHurtByTargetGoal(Mob mob) {
        super(mob, false);
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    // -------------------------------------------------------
    // 核心流程
    // -------------------------------------------------------

    @Override
    public boolean canUse() {
        if (debugCooldown > 0) debugCooldown--;

        PlayerTeam myTeam = TeamManager.getTeam(this.mob);
        if (myTeam == null) return false;

        LivingEntity bestThreat = TeamManager.getBestThreat(myTeam, this.mob);
        if (bestThreat == null) return false;

        // 已经在攻击最优目标，不需要重新 start
        if (this.mob.getTarget() == bestThreat) return false;

        BetterMineTeam.debug("Mob {} (Team {}) detected threat: {}",
                this.mob.getName().getString(), myTeam.getName(), bestThreat.getName().getString());

        if (!this.canAttack(bestThreat, TargetingConditions.DEFAULT)) {
            BetterMineTeam.debug("Goal Failed: canAttack returned false. DistSqr: {}", this.mob.distanceToSqr(bestThreat));
            return false;
        }

        if (TeamManager.isAlly(this.mob, bestThreat)) {
            BetterMineTeam.debug("Goal Failed: Target is ally.");
            return false;
        }

        this.targetToAttack = bestThreat;
        BetterMineTeam.debug("Goal Activated! Target set to: {}", this.targetToAttack.getName().getString());
        return true;
    }

    @Override
    public void start() {
        // 战术切换：在近距离范围内寻找更近的同队敌人
        PlayerTeam enemyTeam = TeamManager.getTeam(this.targetToAttack);
        LivingEntity finalTarget = this.targetToAttack;

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
                    BetterMineTeam.debug("Smart Switch: Switched target from {} to closer enemy {}",
                            this.targetToAttack.getName().getString(), closestEnemy.getName().getString());
                }
            }
        }

        this.targetToAttack = finalTarget;
        this.mob.setTarget(this.targetToAttack);
        establishCommitment(this.targetToAttack);
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        resetCommitment();
    }

    @Override
    public void tick() {
        super.tick();

        // --- 承诺倒计时 & 破坏检测 ---
        if (commitmentTicks > 0) {
            commitmentTicks--;
            if (isCommitmentBroken()) {
                BetterMineTeam.debug("Commitment broken for {}, resetting.", this.mob.getName().getString());
                resetCommitment();
                // 不 return，允许本 tick 立即重新评估目标
            }
        }

        // --- 每 10 tick 的目标切换评估 ---
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

        // 硬锁阶段阈值：commitmentTicks 高于此值时，拒绝一切切换
        int hardThreshold = BMTConfig.getAttackCommitmentSoftTicks() - BMTConfig.getAttackCommitmentHardTicks();

        if (commitmentTicks > hardThreshold) {
            // === 硬锁阶段：拒绝 ===
            BetterMineTeam.debug("Hard lock: blocking switch for {} ({} ticks left)",
                    this.mob.getName().getString(), commitmentTicks);
            return;
        }

        if (commitmentTicks > 0) {
            // === Soft-commitment phase: switch only when new target has significant distance advantage ===
            double switchRatio = BMTConfig.getAttackCommitmentSwitchRatio();
            if (distToNew >= distToCurrent * switchRatio) return;
            BetterMineTeam.debug("Soft commitment overridden for {} (new target is sufficiently closer)",
                    this.mob.getName().getString());
        } else {
            // === Free-switch phase: original 16-unit linear threshold ===
            if (distToNew >= distToCurrent - 16.0D) return;
        }

        // 切换目标并重新建立承诺
        BetterMineTeam.debug("Target switched: {} → {} (phase: {})",
                current != null ? current.getName().getString() : "null",
                bestNow.getName().getString(),
                commitmentTicks > 0 ? "soft" : "free");
        this.mob.setTarget(bestNow);
        establishCommitment(bestNow);
    }

    // -------------------------------------------------------
    // 承诺辅助方法
    // -------------------------------------------------------

    /**
     * 建立对指定目标的攻击承诺。
     * 承诺时长由 Config 中的 attackCommitmentSoftTicks 控制。
     */
    private void establishCommitment(LivingEntity target) {
        this.committedTarget = new WeakReference<>(target);
        this.commitmentTicks = BMTConfig.getAttackCommitmentSoftTicks();
        BetterMineTeam.debug("Commitment established: {} → {} ({} ticks)",
                this.mob.getName().getString(), target.getName().getString(), this.commitmentTicks);
    }

    /** 清除承诺状态，恢复自由切换。 */
    private void resetCommitment() {
        this.committedTarget = new WeakReference<>(null);
        this.commitmentTicks = 0;
    }

    /**
     * 检测当前承诺是否应该被打破（懒惰求值，仅在承诺期内每 tick 调用一次）。
     *
     * 破坏条件（由轻到重排列，短路求值）：
     * 1. 目标引用已被 GC（实体卸载）
     * 2. 目标死亡或被标记移除
     * 3. 目标跨越维度
     * 4. 目标逃离过远（4 倍 followRange 平方）
     * 5. 近期被第三方敌人攻击（20 tick 内）
     */
    private boolean isCommitmentBroken() {
        LivingEntity target = committedTarget.get();

        // 条件 1：WeakReference 已被 GC
        if (target == null) return true;

        // 条件 2：目标死亡或已移除
        if (!target.isAlive() || target.isRemoved()) return true;

        // 条件 3：目标跨维度（O(1) 引用比较）
        if (target.level() != this.mob.level()) return true;

        // 条件 4：目标超出 followRange 的 2 倍（注意 distanceToSqr 返回平方值）
        double followRange = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (this.mob.distanceToSqr(target) > followRange * followRange * 4.0) return true;

        // 条件 5：近期被第三方敌人攻击（攻击者不是当前承诺目标）
        LivingEntity lastAttacker = this.mob.getLastHurtByMob();
        if (lastAttacker != null
                && lastAttacker != target
                && lastAttacker.isAlive()
                && !TeamManager.isAlly(this.mob, lastAttacker)) {
            // 使用 tickCount 确认攻击是否发生在近 20 tick 内（非陈旧记录）
            int ticksSinceHurt = this.mob.tickCount - this.mob.getLastHurtByMobTimestamp();
            if (ticksSinceHurt < 20) {
                BetterMineTeam.debug("Commitment broken by new attacker: {} ({}t ago)",
                        lastAttacker.getName().getString(), ticksSinceHurt);
                return true;
            }
        }

        return false;
    }
}