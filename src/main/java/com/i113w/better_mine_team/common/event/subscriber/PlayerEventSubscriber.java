package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.network.S2C_PersonalTeamStatePacket;
import com.i113w.better_mine_team.common.network.S2C_SyncTeamLordPacket;
import com.i113w.better_mine_team.common.network.rts.S2C_PatrolSettingsPacket;
import com.i113w.better_mine_team.common.rts.data.RTSPlayerData;
import com.i113w.better_mine_team.common.team.TeamDataStorage;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.better_mine_team.common.team.TeamPermissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID)
public class PlayerEventSubscriber {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            boolean hasPerm = TeamPermissions.hasOverridePermission(player);
            MTNetworkRegister.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2C_SyncTeamLordPacket(hasPerm)
            );
            MTNetworkRegister.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    S2C_PatrolSettingsPacket.current()
            );
            BetterMineTeam.debug("Synced TeamsLord permission to {}: {}", player.getName().getString(), hasPerm);
            handlePersonalTeamLogin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 清理临时会话数据，防止跨会话影响
            RTSPlayerData.clear(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            boolean hasPerm = TeamPermissions.hasOverridePermission(player);
            MTNetworkRegister.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2C_SyncTeamLordPacket(hasPerm)
            );
            syncPersonalTeamState(player);
        }
    }

    private static void handlePersonalTeamLogin(ServerPlayer player) {
        if (BMTConfig.isPersonalTeamsEnabled()) {
            TeamDataStorage storage = TeamDataStorage.get(player.serverLevel());
            if (storage.getPersonalTeamPreference(player)) {
                TeamManager.joinPersonalTeam(player, false);
            }
        }
        syncPersonalTeamState(player);
    }

    public static void syncPersonalTeamState(ServerPlayer player) {
        TeamDataStorage storage = TeamDataStorage.get(player.serverLevel());
        boolean available = BMTConfig.isPersonalTeamsEnabled();
        boolean enabled = available && storage.getPersonalTeamPreference(player);
        MTNetworkRegister.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2C_PersonalTeamStatePacket(available, enabled)
        );
    }
}
