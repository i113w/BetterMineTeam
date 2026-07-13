package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.network.rts.S2C_PatrolSyncPacket;
import com.i113w.better_mine_team.common.rts.data.PatrolTask;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID)
public class PatrolSyncEventSubscriber {
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Mob mob)) return;
        PatrolTask task = RTSUnitData.get(mob).getPatrolTask();
        if (task.isEnabled()) {
            MTNetworkRegister.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    S2C_PatrolSyncPacket.fromTask(mob.getId(), task));
        }
    }

    @SubscribeEvent
    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MTNetworkRegister.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                S2C_PatrolSyncPacket.clear(event.getTarget().getId()));
    }
}
