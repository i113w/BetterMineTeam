package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.rts.ai.goal.PatrolGoal;
import com.i113w.better_mine_team.common.rts.ai.goal.PatrolCombatLeashGoal;
import com.i113w.better_mine_team.common.rts.ai.goal.RTSAttackGoal;
import com.i113w.better_mine_team.common.rts.ai.goal.RTSMoveGoal;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID)
public class RTSEntityHandler {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // 服务端检查
        if (event.getLevel().isClientSide()) return;

        // 只处理有寻路能力的生物
        if (!(event.getEntity() instanceof PathfinderMob mob)) return;

        // 防止重复添加 RTS Goals
        // 触发 lazy init（确保 NBT key 存在）
        RTSUnitData.get(mob);

        boolean hasMove = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal() instanceof RTSMoveGoal);
        if (!hasMove) mob.goalSelector.addGoal(0, new RTSMoveGoal(mob, BMTConfig.getRtsMovementSpeed()));

        boolean hasAttack = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal() instanceof RTSAttackGoal);
        if (!hasAttack) mob.goalSelector.addGoal(1, new RTSAttackGoal(mob));

        boolean hasPatrol = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal() instanceof PatrolGoal);
        if (!hasPatrol) mob.goalSelector.addGoal(1, new PatrolGoal(mob));

        boolean hasPatrolLeash = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal() instanceof PatrolCombatLeashGoal);
        if (!hasPatrolLeash) mob.goalSelector.addGoal(0, new PatrolCombatLeashGoal(mob));

        BetterMineTeam.debug("[RTS-HANDLER] RTS Goals added to: {} (Total goals: {})",
                mob.getName().getString(),
                mob.goalSelector.getAvailableGoals().size());
    }
}
