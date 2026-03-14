package com.i113w.better_mine_team.common.rts.ai.goal;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.entity.goal.TeamGoal;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import java.util.EnumSet;

public class RTSMoveGoal extends Goal implements TeamGoal {
    private final PathfinderMob mob;
    private final double speedModifier;
    private RTSUnitData data;
    private int forcePathTimer = 0;

    private double targetX, targetY, targetZ;
    private boolean arrivedSuccessfully = false;

    public RTSMoveGoal(PathfinderMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.data = RTSUnitData.get(mob);
        if (data.getCommand() != CommandType.MOVE) return false;

        Vec3 target = data.getTargetPos();
        if (mob.position().distanceToSqr(target) < getCompletionDistSqr()) {
            data.stop();
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return data.getCommand() == CommandType.MOVE
                && !mob.getNavigation().isDone()
                && mob.distanceToSqr(this.targetX, this.targetY, this.targetZ) > getCompletionDistSqr();
    }

    @Override
    public void start() {
        Vec3 target = data.getTargetPos();
        this.targetX = target.x;
        this.targetY = target.y;
        this.targetZ = target.z;
        this.forcePathTimer = 0;
        this.arrivedSuccessfully = false;

        boolean success = mob.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, this.speedModifier);
        BetterMineTeam.LOGGER.info("🚀 RTSMoveGoal START: {} moving to ({}, {}, {}), navSuccess={}",
                mob.getName().getString(), targetX, targetY, targetZ, success);
    }

    @Override
    public void tick() {
        if (++forcePathTimer > 20) {
            forcePathTimer = 0;
            if (mob.distanceToSqr(targetX, targetY, targetZ) > getCompletionDistSqr()) {
                mob.getNavigation().moveTo(targetX, targetY, targetZ, speedModifier);
            }
        }

        if (mob.distanceToSqr(targetX, targetY, targetZ) <= getCompletionDistSqr()) {
            this.arrivedSuccessfully = true;
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();

        // 智能衔接：移动结束后，如果有敌人，立即切换到 ATTACK 模式
        if (this.arrivedSuccessfully || mob.distanceToSqr(targetX, targetY, targetZ) <= getCompletionDistSqr()) {

            PlayerTeam myTeam = TeamManager.getTeam(mob);
            LivingEntity potentialTarget = TeamManager.getBestThreat(myTeam, mob);

            if (potentialTarget != null) {
                BetterMineTeam.debug("[RTS-MOVE-GOAL] Arrived. Found threat {}, switching to ATTACK.",
                        potentialTarget.getName().getString());

                // 自动切换为攻击指令 (实现 Attack Move)
                data.setAttackCommand(potentialTarget.getId());
                mob.setTarget(potentialTarget);
            } else {
                data.stop();
            }
        } else {
            // 被打断（例如击退）→ 交给 TeamHurtByTargetGoal 接管
            RTSUnitData.get(mob).stop();
        }
    }

    private double getCompletionDistSqr() {
        double w = mob.getBbWidth();
        return Math.max(2.0, w * w * 2.0);
    }
}
