package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.network.rts.S2C_PatrolSyncPayload;
import com.i113w.better_mine_team.common.registry.ModAttachments;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = BetterMineTeam.MODID)
public class PatrolSyncEventSubscriber {
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity target = event.getTarget();
        if (!(target instanceof Mob mob)) return;
        RTSUnitData data = mob.getData(ModAttachments.UNIT_DATA);
        if (data.getPatrolTask().isEnabled()) {
            PacketDistributor.sendToPlayer(player,
                    S2C_PatrolSyncPayload.fromTask(mob.getId(), data.getPatrolTask()));
        }
    }

    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PacketDistributor.sendToPlayer(player, S2C_PatrolSyncPayload.clear(event.getTarget().getId()));
    }
}
