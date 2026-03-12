package com.i113w.better_mine_team.common.rts.ai.goal;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.entity.goal.TeamGoal;
import com.i113w.better_mine_team.common.registry.ModAttachments;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.scores.PlayerTeam;

import java.util.EnumSet;

public class RTSAttackGoal extends Goal implements TeamGoal {
    private final Mob mob;
    private RTSUnitData data;
    private int targetIdCheckTimer = 0;

    public RTSAttackGoal(Mob mob) {
        this.mob = mob;
        // ✅ 关键：不占用 MOVE 标志
        // 让原版的 MeleeAttackGoal/RangedAttackGoal 控制移动和攻击
        this.setFlags(EnumSet.noneOf(Flag.class));
    }


    @Override
    public boolean canUse() {
        // ✅ 直接用 getData
        this.data = mob.getData(ModAttachments.UNIT_DATA);

        boolean shouldActivate = data.getCommand() == CommandType.ATTACK;

        BetterMineTeam.debug("[RTS-ATTACK-GOAL] canUse() for {}: command={}, shouldActivate={}",
                mob.getName().getString(),
                data.getCommand(),
                shouldActivate);

        return shouldActivate;
    }

    @Override
    public void start() {
        BetterMineTeam.debug("[RTS-ATTACK-GOAL] 🚀 START for {}: targetEntityId={}",
                mob.getName().getString(),
                data.getTargetEntityId());
    }

    @Override
    public void tick() {
        if (--targetIdCheckTimer > 0) return;
        targetIdCheckTimer = 10; // 每 0.5 秒检查一次

        // 1. 检查当前 RTS 指令的目标
        int cmdTargetId = data.getTargetEntityId();
        Entity cmdTarget = mob.level().getEntity(cmdTargetId);

        boolean cmdTargetValid = isValidTarget(cmdTarget);

        if (cmdTargetValid) {
            // 如果指令目标还活着，死咬不放
            if (mob.getTarget() != cmdTarget) {
                mob.setTarget((LivingEntity) cmdTarget);
            }
        }
        else {
            // 2. [核心新增] 警戒模式 (Aggressive Stance)
            // 指令目标已死，但处于 ATTACK 模式 -> 寻找 TeamManager 中的下一个威胁
            PlayerTeam team = TeamManager.getTeam(mob);
            LivingEntity nextThreat = TeamManager.getBestThreat(team, mob);

            if (nextThreat != null) {
                // 找到了新敌人，自动攻击
                if (mob.getTarget() != nextThreat) {
                    BetterMineTeam.debug("[RTS-AUTO-ENGAGE] {} engaging next threat: {}",
                            mob.getName().getString(), nextThreat.getName().getString());
                    mob.setTarget(nextThreat);
                }
            } else {
                // 周围没敌人了，停止指令
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