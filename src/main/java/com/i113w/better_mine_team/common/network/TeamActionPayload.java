package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.team.TeamDataStorage;
import com.i113w.better_mine_team.common.team.TeamManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record TeamActionPayload(int action, String data, boolean state) implements CustomPacketPayload {

    public static final Type<TeamActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "team_action"));

    public static final StreamCodec<ByteBuf, TeamActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TeamActionPayload::action,
            ByteBufCodecs.STRING_UTF8, TeamActionPayload::data,
            ByteBufCodecs.BOOL, TeamActionPayload::state,
            TeamActionPayload::new
    );

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void serverHandle(final TeamActionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;

            Scoreboard scoreboard = server.getScoreboard();

            // Action 0: 切换队伍
            if (payload.action == 0) {
                DyeColor color = DyeColor.byName(payload.data, null);

                if (color != null) {
                    String newTeamName = TeamManager.getTeamName(color);
                    PlayerTeam newTeam = scoreboard.getPlayerTeam(newTeamName);

                    if (newTeam != null) {
                        // [新增] 检查玩家是否是当前队伍的队长，如果是，则清除
                        PlayerTeam currentTeam = TeamManager.getTeam(player);
                        TeamDataStorage storage = TeamDataStorage.get(player.serverLevel());

                        if (currentTeam != null && storage.isCaptain(player)) {
                            storage.removeCaptain(currentTeam.getName());
                            // 可以选择给玩家发个消息提示已卸任，或者静默处理
                        }

                        // 加入新队伍
                        scoreboard.addPlayerToTeam(player.getScoreboardName(), newTeam);
                    }
                }
            }
            // Action 1: 切换 PvP 状态
            else if (payload.action == 1) {
                PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
                if (team != null) {
                    team.setAllowFriendlyFire(payload.state);
                }
            }
        });
    }
}