package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.common.team.TeamDataStorage;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TeamActionPacket {
    private final int action;
    private final String data;
    private final boolean state;

    public TeamActionPacket(int action, String data, boolean state) {
        this.action = action;
        this.data = data;
        this.state = state;
    }

    public static void encode(TeamActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeUtf(msg.data);
        buf.writeBoolean(msg.state);
    }

    public static TeamActionPacket decode(FriendlyByteBuf buf) {
        return new TeamActionPacket(
                buf.readVarInt(),
                buf.readUtf(),
                buf.readBoolean()
        );
    }

    public static void handle(TeamActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            MinecraftServer server = player.getServer();
            if (server == null) return;

            Scoreboard scoreboard = server.getScoreboard();

            // Action 0: 切换队伍
            if (msg.action == 0) {
                DyeColor color = DyeColor.byName(msg.data, null);

                if (color != null) {
                    String newTeamName = TeamManager.getTeamName(color);
                    PlayerTeam newTeam = scoreboard.getPlayerTeam(newTeamName);

                    if (newTeam != null) {
                        // 检查玩家是否是当前队伍的队长，如果是，则清除
                        PlayerTeam currentTeam = TeamManager.getTeam(player);
                        TeamDataStorage storage = TeamDataStorage.get(player.serverLevel());

                        if (currentTeam != null && storage.isCaptain(player)) {
                            storage.removeCaptain(currentTeam.getName());
                        }

                        // 加入新队伍
                        scoreboard.addPlayerToTeam(player.getScoreboardName(), newTeam);
                    }
                }
            }
            // Action 1: 切换 PvP 状态
            else if (msg.action == 1) {
                PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
                if (team != null) {
                    team.setAllowFriendlyFire(msg.state);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}