package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.network.S2C_SyncTeamLordPayload;
import com.i113w.better_mine_team.common.team.TeamPermissions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = BetterMineTeam.MODID)
public class PlayerEventSubscriber {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 检查服务端 NBT
            boolean hasPerm = TeamPermissions.hasOverridePermission(player);
            // 发送包同步给客户端
            PacketDistributor.sendToPlayer(player, new S2C_SyncTeamLordPayload(hasPerm));
            BetterMineTeam.debug("Synced TeamsLord permission to {}: {}", player.getName().getString(), hasPerm);
        }
    }

    // 可选：跨维度传送时可能也需要同步
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            boolean hasPerm = TeamPermissions.hasOverridePermission(player);
            PacketDistributor.sendToPlayer(player, new S2C_SyncTeamLordPayload(hasPerm));
        }
    }
}