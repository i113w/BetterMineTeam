package com.i113w.better_mine_team.common.rts.ai.goal;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.scores.PlayerTeam;

import java.util.EnumSet;

public class RTSAttackGoal extends Goal {
    private final Mob mob;
    private RTSUnitData data;
    private int targetIdCheckTimer = 0;

    public RTSAttackGoal(Mob mob) {
        this.mob = mob;
        // 不占用 MOVE 标志，让原版攻击目标 Goal 控制移动
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        this.data = RTSUnitData.get(mob);
        boolean shouldActivate = data.getCommand() == CommandType.ATTACK;
        BetterMineTeam.debug("[RTS-ATTACK-GOAL] canUse() for {}: command={}, activate={}",
                mob.getName().getString(), data.getCommand(), shouldActivate);
        return shouldActivate;
    }

    @Override
    public void start() {
        BetterMineTeam.debug("[RTS-ATTACK-GOAL] 🚀 START for {}: targetEntityId={}",
                mob.getName().getString(), data.getTargetEntityId());
    }

    @Override
    public void tick() {
        if (--targetIdCheckTimer > 0) return;
        targetIdCheckTimer = 10;

        // 刷新 data（自动从 NBT 读取最新状态）
        this.data = RTSUnitData.get(mob);

        int cmdTargetId = data.getTargetEntityId();
        Entity cmdTarget = mob.level().getEntity(cmdTargetId);
        boolean cmdTargetValid = isValidTarget(cmdTarget);

        if (cmdTargetValid) {
            if (mob.getTarget() != cmdTarget) {
                mob.setTarget((LivingEntity) cmdTarget);
            }
        } else {
            // 指令目标已死 → 警戒模式，寻找下一个威胁
            PlayerTeam team = TeamManager.getTeam(mob);
            LivingEntity nextThreat = TeamManager.getBestThreat(team, mob);

            if (nextThreat != null) {
                BetterMineTeam.debug("[RTS-AUTO-ENGAGE] {} engaging next threat: {}",
                        mob.getName().getString(), nextThreat.getName().getString());
                if (mob.getTarget() != nextThreat) {
                    mob.setTarget(nextThreat);
                }
            } else {
                BetterMineTeam.debug("[RTS-ATTACK-GOAL] No more targets, stopping.");
                data.stop();
                mob.setTarget(null);
            }
        }
    }

    private boolean isValidTarget(Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive();
    }

    @Override
    public void stop() {
        BetterMineTeam.debug("[RTS-ATTACK-GOAL] ⛔ STOP for {}", mob.getName().getString());
    }
}
