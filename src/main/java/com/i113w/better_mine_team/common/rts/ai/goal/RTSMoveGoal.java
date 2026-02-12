package com.i113w.better_mine_team.common.rts.ai.goal;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.registry.ModAttachments;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import java.util.EnumSet;

public class RTSMoveGoal extends Goal {
    private final PathfinderMob mob;
    private final double speedModifier;
    private RTSUnitData data;
    private int forcePathTimer = 0;

    // 缓存目标点
    private double targetX, targetY, targetZ;

    private boolean arrivedSuccessfully = false;

    public RTSMoveGoal(PathfinderMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // ✅ 核心修复：直接用 getData，不检查 hasData
        this.data = mob.getData(ModAttachments.UNIT_DATA);

        // 只有当前指令是 MOVE 时才激活
        if (data.getCommand() != CommandType.MOVE) return false;

        Vec3 target = data.getTargetPos();

        // 如果已经到达目标点，停止指令
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

        // ✅ Goal 统一控制 Navigation（不在 Controller 里调用）
        boolean success = mob.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, this.speedModifier);
        BetterMineTeam.LOGGER.info("🚀 RTSMoveGoal START: {} moving to ({}, {}, {})",
                mob.getName().getString(), targetX, targetY, targetZ);
        // Debug 日志（可选，调试时启用）
        if (!success) {
            BetterMineTeam.debug("RTSMoveGoal: Navigation failed for {} to {}",
                    mob.getName().getString(), target);
        }
        BetterMineTeam.LOGGER.info("Navigation success: {}", success);
    }

    @Override
    public void tick() {
        // 强制寻路刷新机制（每 20 tick）
        if (++forcePathTimer > 20) {
            forcePathTimer = 0;
            if (mob.distanceToSqr(targetX, targetY, targetZ) > getCompletionDistSqr()) {
                mob.getNavigation().moveTo(targetX, targetY, targetZ, speedModifier);
            }
        }

        // 检查是否到达
        if (mob.distanceToSqr(targetX, targetY, targetZ) <= getCompletionDistSqr()) {
            this.arrivedSuccessfully = true;
            // 这一步会让 canContinueToUse 返回 false，从而触发 stop()
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();

        // [核心修复] 智能衔接：移动结束后，如果有敌人，立即切换到 ATTACK 模式
        if (this.arrivedSuccessfully || mob.distanceToSqr(targetX, targetY, targetZ) <= getCompletionDistSqr()) {

            PlayerTeam myTeam = TeamManager.getTeam(mob);
            // 1. 尝试在 TeamManager 仇恨列表中寻找最近的敌人
            LivingEntity potentialTarget = TeamManager.getBestThreat(myTeam, mob);

            if (potentialTarget != null) {
                BetterMineTeam.debug("[RTS-MOVE-GOAL] Arrived. Found threat {}, switching to ATTACK.",
                        potentialTarget.getName().getString());

                // 自动切换为攻击指令 (实现 Attack Move)
                data.setAttackCommand(potentialTarget.getId());
                mob.setTarget(potentialTarget);
            } else {
                // 没有敌人，才进入发呆(STOP)状态
                data.stop();
            }
        } else {
            // 如果是被打断（比如被攻击击退导致 Goal 终止），也暂定为 STOP，
            // 这样 TeamHurtByTargetGoal 有机会接管
            data.stop();
        }
    }

    private double getCompletionDistSqr() {
        double w = mob.getBbWidth();
        return Math.max(2.0, w * w * 2.0);
    }
}