package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.registry.ModAttachments;
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

        // ✅ 检查是否已经有 RTS Goals（防止重复添加）
        boolean alreadyHasRTSGoals = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapper -> wrapper.getGoal() instanceof RTSMoveGoal
                        || wrapper.getGoal() instanceof RTSAttackGoal);

        if (alreadyHasRTSGoals) {
            BetterMineTeam.debug("[RTS-HANDLER] Entity {} already has RTS goals, skipping",
                    mob.getName().getString());
            return;
        }

        // ✅ 强制创建 RTSUnitData（触发 lazy initialization）
        RTSUnitData data = mob.getData(ModAttachments.UNIT_DATA);

        // ✅ 添加 RTS Goals
        mob.goalSelector.addGoal(0, new RTSMoveGoal(mob, BMTConfig.getRtsMovementSpeed()));
        mob.goalSelector.addGoal(1, new RTSAttackGoal(mob));


        BetterMineTeam.debug("[RTS-HANDLER] ✅ RTS Goals added to: {} (UUID: {}, Total goals: {})",
                mob.getName().getString(),
                mob.getUUID(),
                mob.goalSelector.getAvailableGoals().size());
    }
}