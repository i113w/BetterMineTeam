package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
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
        boolean alreadyHasRTSGoals = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal() instanceof RTSMoveGoal || w.getGoal() instanceof RTSAttackGoal);

        if (alreadyHasRTSGoals) {
            BetterMineTeam.debug("[RTS-HANDLER] Entity {} already has RTS goals, skipping",
                    mob.getName().getString());
            return;
        }

        // 触发 lazy init（确保 NBT key 存在）
        RTSUnitData.get(mob);

        // 添加 RTS Goals
        mob.goalSelector.addGoal(0, new RTSMoveGoal(mob, BMTConfig.getRtsMovementSpeed()));
        mob.goalSelector.addGoal(1, new RTSAttackGoal(mob));

        BetterMineTeam.debug("[RTS-HANDLER] RTS Goals added to: {} (Total goals: {})",
                mob.getName().getString(),
                mob.goalSelector.getAvailableGoals().size());
    }
}
