package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.team.TeamDataStorage;
import com.i113w.better_mine_team.common.team.TeamManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

import java.util.regex.Pattern;

public record TeamActionPayload(int action, String data, boolean state) implements CustomPacketPayload {

    public static final Type<TeamActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "team_action"));

    // 用于识别 UUID 字符串的正则 (用于区分生物和玩家)
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

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
                        PlayerTeam currentTeam = TeamManager.getTeam(player);
                        TeamDataStorage storage = TeamDataStorage.get(player.serverLevel());

                        // 如果玩家是旧队伍的队长，先移除其权限
                        if (currentTeam != null && storage.isCaptain(player)) {
                            storage.removeCaptain(currentTeam.getName());
                            // 可选：通知玩家已卸任
                        }

                        // 加入新队伍
                        scoreboard.addPlayerToTeam(player.getScoreboardName(), newTeam);

                        // 智能授予队长逻辑
                        if (BMTConfig.isAutoAssignCaptainEnabled()) {
                            // 检查新队伍里有没有"其他玩家"
                            // 我们过滤掉：a. 自己 b. UUID格式的成员(通常是生物)
                            boolean hasOtherPlayers = newTeam.getPlayers().stream().anyMatch(memberName -> {
                                if (memberName.equals(player.getScoreboardName())) return false; // 排除自己
                                // 如果不是 UUID 格式，我们认为它是玩家名
                                return !UUID_PATTERN.matcher(memberName).matches();
                            });

                            // 如果没有其他玩家，直接上位
                            if (!hasOtherPlayers) {
                                storage.setCaptain(newTeamName, player.getUUID());
                                player.displayClientMessage(
                                        Component.translatable("better_mine_team.msg.auto_captain_granted", newTeam.getDisplayName())
                                                .withStyle(ChatFormatting.GOLD),
                                        true
                                );
                                BetterMineTeam.debug("Auto-assigned captain to {} for team {}", player.getName().getString(), newTeamName);
                            }
                        }
                    }
                }
            }
            // Action 1: 切换 PvP 状态
            else if (payload.action == 1) {
                PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
                if (team != null) {
                    // [安全检查] 只有队长才能切换 PvP
                    // 虽然 UI 做了限制，但服务端最好也防一下发包
                    if (TeamDataStorage.get(player.serverLevel()).isCaptain(player)) {
                        team.setAllowFriendlyFire(payload.state);
                    }
                }
            }
        });
    }
}