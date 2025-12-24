package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
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
import org.jetbrains.annotations.NotNull; // 1. 导入注解

public record TeamActionPayload(int action, String data, boolean state) implements CustomPacketPayload {

    public static final Type<TeamActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "team_action"));

    public static final StreamCodec<ByteBuf, TeamActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TeamActionPayload::action,
            ByteBufCodecs.STRING_UTF8, TeamActionPayload::data,
            ByteBufCodecs.BOOL, TeamActionPayload::state,
            TeamActionPayload::new
    );

    @Override
    @NotNull // 2. 修复：添加非空注解
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void serverHandle(final TeamActionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // 3. 修复：防止 player.getServer() 返回 null 导致空指针
            MinecraftServer server = player.getServer();
            if (server == null) return;

            Scoreboard scoreboard = server.getScoreboard();

            // Action 0: 切换队伍
            if (payload.action == 0) {
                // 4. 修复：移除冗余变量 colorName，直接使用 payload.data
                DyeColor color = DyeColor.byName(payload.data, null);

                if (color != null) {
                    String teamName = TeamManager.getTeamName(color);
                    PlayerTeam team = scoreboard.getPlayerTeam(teamName);

                    if (team != null) {
                        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
                    }
                }
            }
            // Action 1: 切换 PvP 状态
            else if (payload.action == 1) {
                PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
                // 兼容性修改：只要有队伍就能改，不再强制检查前缀
                if (team != null) {
                    team.setAllowFriendlyFire(payload.state);
                }
            }
        });
    }
}