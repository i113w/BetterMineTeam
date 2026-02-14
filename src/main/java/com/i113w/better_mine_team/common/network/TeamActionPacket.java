package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.team.TeamDataStorage;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class TeamActionPacket {
    private final int action;
    private final String data;
    private final boolean state;

    // 用于识别 Mob UUID 的正则表达式
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

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

                        // 自动队长分配逻辑
                        if (BMTConfig.isAutoGrantCaptainEnabled()) {
                            // 检查该队伍是否已经有队长
                            UUID existingCaptain = storage.getCaptain(newTeam.getName());

                            if (existingCaptain == null) {
                                // 检查队伍中是否还有其他玩家（忽略生物UUID）
                                boolean hasOtherPlayers = false;
                                for (String member : newTeam.getPlayers()) {
                                    if (member.equals(player.getScoreboardName())) continue; // 跳过自己

                                    // 如果成员名不是 UUID 格式，则认为是玩家
                                    if (!UUID_PATTERN.matcher(member).matches()) {
                                        hasOtherPlayers = true;
                                        break;
                                    }
                                }

                                // 如果没有其他玩家，自动成为队长
                                if (!hasOtherPlayers) {
                                    storage.setCaptain(newTeam.getName(), player.getUUID());
                                    player.displayClientMessage(
                                            Component.translatable("better_mine_team.msg.auto_captain_granted").withStyle(ChatFormatting.GOLD),
                                            true
                                    );
                                    BetterMineTeam.debug("Auto-granted captain rights to {} for team {}", player.getName().getString(), newTeam.getName());
                                }
                            }
                        }
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