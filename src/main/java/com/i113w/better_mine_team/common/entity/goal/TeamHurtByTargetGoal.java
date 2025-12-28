package com.i113w.better_mine_team.common.entity.goal;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class TeamHurtByTargetGoal extends TargetGoal {

    private LivingEntity targetToAttack;
    private int checkTicker = 0;

    public TeamHurtByTargetGoal(Mob mob) {
        super(mob, false);
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        PlayerTeam myTeam = TeamManager.getTeam(this.mob);
        if (myTeam == null) return false;

        // 获取最佳威胁
        LivingEntity bestThreat = TeamManager.getBestThreat(myTeam, this.mob);

        if (bestThreat == null) return false;

        // 如果我们已经在攻击这个目标，不需要重新 start
        if (this.mob.getTarget() == bestThreat) {
            return false;
        }

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
        // === 战术切换逻辑：尝试寻找比 targetToAttack 更近的敌人 ===
        // 这里的逻辑是为了应对"TeamManager 返回的是列表中最近的，但可能还没把身边最近的敌人加进去"的情况
        // 虽然 scanAndAddThreats 会加全家，但为了保险起见，这里再做一次局部优化

        PlayerTeam enemyTeam = TeamManager.getTeam(this.targetToAttack);
        LivingEntity finalTarget = this.targetToAttack;

        if (enemyTeam != null) {
            double scanRange = BMTConfig.getTacticalSwitchRange();
            AABB searchBox = this.mob.getBoundingBox().inflate(scanRange, 8.0D, scanRange);

            List<LivingEntity> closeEnemies = this.mob.level().getEntitiesOfClass(LivingEntity.class, searchBox, entity -> {
                if (entity == this.mob) return false;
                if (!entity.isAlive()) return false;
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
        super.start();
    }

    @Override
    public void tick() {
        super.tick();

        if (++checkTicker >= 10) {
            checkTicker = 0;
            PlayerTeam myTeam = TeamManager.getTeam(this.mob);
            if (myTeam != null) {
                LivingEntity bestNow = TeamManager.getBestThreat(myTeam, this.mob);
                LivingEntity current = this.mob.getTarget();

                if (bestNow != null && bestNow != current) {
                    if (canAttack(bestNow, TargetingConditions.DEFAULT)) {
                        double distCurrent = (current == null) ? Double.MAX_VALUE : this.mob.distanceToSqr(current);
                        double distNew = this.mob.distanceToSqr(bestNow);

                        if (distNew < distCurrent - 16.0D) {
                            this.mob.setTarget(bestNow);
                        }
                    }
                }
            }
        }
    }
}