package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.registry.ModAttachments;
import com.i113w.better_mine_team.common.rts.ai.goal.PatrolGoal;
import com.i113w.better_mine_team.common.rts.ai.goal.PatrolCombatLeashGoal;
import com.i113w.better_mine_team.common.rts.ai.goal.RTSAttackGoal;
import com.i113w.better_mine_team.common.rts.ai.goal.RTSMoveGoal;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import net.minecraft.world.entity.PathfinderMob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = BetterMineTeam.MODID)
public class RTSEntityHandler {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // ✅ 严格的服务端检查
        if (event.getLevel().isClientSide()) {
            return;
        }

        // ✅ 只处理 PathfinderMob（有寻路能力的生物）
        if (!(event.getEntity() instanceof PathfinderMob mob)) {
            return;
        }

        // ✅ 强制创建 RTSUnitData（触发 lazy initialization）
        RTSUnitData data = mob.getData(ModAttachments.UNIT_DATA);

        boolean hasMove = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapper -> wrapper.getGoal() instanceof RTSMoveGoal);
        if (!hasMove) {
            mob.goalSelector.addGoal(0, new RTSMoveGoal(mob, BMTConfig.getRtsMovementSpeed()));
        }

        boolean hasAttack = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapper -> wrapper.getGoal() instanceof RTSAttackGoal);
        if (!hasAttack) {
            mob.goalSelector.addGoal(1, new RTSAttackGoal(mob));
        }

        boolean hasPatrol = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapper -> wrapper.getGoal() instanceof PatrolGoal);
        if (!hasPatrol) {
            mob.goalSelector.addGoal(1, new PatrolGoal(mob));
        }

        boolean hasPatrolLeash = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapper -> wrapper.getGoal() instanceof PatrolCombatLeashGoal);
        if (!hasPatrolLeash) {
            mob.goalSelector.addGoal(0, new PatrolCombatLeashGoal(mob));
        }


        BetterMineTeam.debug("[RTS-HANDLER] ✅ RTS Goals added to: {} (UUID: {}, Total goals: {})",
                mob.getName().getString(),
                mob.getUUID(),
                mob.goalSelector.getAvailableGoals().size());
    }
}
