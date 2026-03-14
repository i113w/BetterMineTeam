package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.menu.EntityDetailsMenu;
import com.i113w.better_mine_team.common.team.TeamDataStorage;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.better_mine_team.common.team.TeamPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/**
 * 双向包：处理队伍管理的各种操作
 * 客户端 -> 服务器：操作请求 (传送、踢人、改名等)
 * 服务器 -> 客户端：状态同步 (跟随状态)
 */
public class TeamManagementPacket {
    // 动作常量
    public static final int ACTION_TELEPORT = 1;
    public static final int ACTION_TOGGLE_FOLLOW = 2;
    public static final int ACTION_KICK = 3;
    public static final int ACTION_RENAME = 4;
    public static final int ACTION_SET_CAPTAIN = 5;
    public static final int ACTION_OPEN_INVENTORY = 6;
    public static final int ACTION_SYNC_FOLLOW_STATE = 7;
    public static final int ACTION_SET_AGGRESSIVE_LEVEL = 8;
    public static final int ACTION_GET_AGGRESSIVE_LEVEL = 9;

    private final int actionType;
    private final int targetEntityId;
    private final String extraData;

    public TeamManagementPacket(int actionType, int targetEntityId, String extraData) {
        this.actionType = actionType;
        this.targetEntityId = targetEntityId;
        this.extraData = extraData;
    }

    public static void encode(TeamManagementPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.actionType);
        buf.writeVarInt(msg.targetEntityId);
        buf.writeUtf(msg.extraData);
    }

    public static TeamManagementPacket decode(FriendlyByteBuf buf) {
        return new TeamManagementPacket(buf.readVarInt(), buf.readVarInt(), buf.readUtf());
    }

    public static void handle(TeamManagementPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            // 判断包的方向
            if (context.getDirection().getReceptionSide().isServer()) {
                // 服务端处理 (来自客户端的请求)
                handleServerSide(msg, context);
            } else {
                // 客户端处理 (来自服务端的同步)
                ClientHandler.handleClientSide(msg);
            }
        });

        context.setPacketHandled(true);
    }

    // ==================== 服务端逻辑 ====================

    private static void handleServerSide(TeamManagementPacket msg, NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) return;

        ServerLevel level = player.serverLevel();
        Entity target = level.getEntity(msg.targetEntityId);

        if (target == null || target.isRemoved()) return;

        PlayerTeam playerTeam = TeamManager.getTeam(player);
        PlayerTeam targetTeam = TeamManager.getTeam(target);

        if (playerTeam == null || targetTeam == null || !playerTeam.getName().equals(targetTeam.getName())) {
            return;
        }

        boolean isCaptain = TeamDataStorage.get(level).isCaptain(player);
        boolean hasAdmin = TeamPermissions.hasOverridePermission(player);

        try {
            switch (msg.actionType) {
                case ACTION_TELEPORT -> {
                    if (!isCaptain) { sendPermissionError(player); return; }
                    if (target.isAlive()) target.teleportTo(player.getX(), player.getY(), player.getZ());
                }

                case ACTION_TOGGLE_FOLLOW -> {
                    if (!isCaptain) { sendPermissionError(player); return; }
                    if (target instanceof Mob mob && mob.isAlive()) {
                        boolean current = mob.getPersistentData().getBoolean("bmt_follow_enabled");
                        boolean newState = !current;
                        mob.getPersistentData().putBoolean("bmt_follow_enabled", newState);

                        // 如果关闭了跟随，打断当前生物正在进行的原版寻路系统
                        if (!newState) {
                            mob.getNavigation().stop();
                        }

                        TeamManagementPacket syncPacket = new TeamManagementPacket(ACTION_SYNC_FOLLOW_STATE, mob.getId(), String.valueOf(newState));
                        com.i113w.better_mine_team.common.init.MTNetworkRegister.CHANNEL.send(
                                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), syncPacket
                        );

                        player.displayClientMessage(Component.translatable(newState ? "better_mine_team.msg.follow_enabled" : "better_mine_team.msg.follow_disabled"), true);
                    }
                }

                case ACTION_KICK -> {
                    if (!isCaptain) { sendPermissionError(player); return; }
                    if (target == player) return;
                    if (target instanceof LivingEntity living) {
                        Scoreboard scoreboard = level.getScoreboard();
                        scoreboard.removePlayerFromTeam(living.getStringUUID(), targetTeam);
                        living.setGlowingTag(false);
                        living.getPersistentData().remove("bmt_follow_enabled");
                    }
                }

                case ACTION_RENAME -> {
                    if (!isCaptain) { sendPermissionError(player); return; }
                    String rawName = msg.extraData;
                    if (rawName.length() > 32) rawName = rawName.substring(0, 32);
                    String safeName = ChatFormatting.stripFormatting(rawName);
                    target.setCustomName(Component.literal(safeName));
                }

                case ACTION_SET_CAPTAIN -> {
                    if (!isCaptain) { sendPermissionError(player); return; }
                    if (target instanceof ServerPlayer targetPlayer) {
                        TeamDataStorage.get(level).setCaptain(playerTeam.getName(), targetPlayer.getUUID());
                        player.displayClientMessage(Component.translatable("better_mine_team.msg.captain_transferred", targetPlayer.getName()), true);
                    }
                }

                case ACTION_OPEN_INVENTORY -> {
                    if (target instanceof ServerPlayer targetPlayer) {
                        boolean isSelf = player.getUUID().equals(targetPlayer.getUUID());
                        if (isSelf || hasAdmin) {
                            NetworkHooks.openScreen(
                                    player,
                                    new SimpleMenuProvider(
                                            (id, inventory, p) -> new EntityDetailsMenu(id, inventory, targetPlayer),
                                            targetPlayer.getDisplayName()
                                    ),
                                    buf -> {
                                        buf.writeInt(targetPlayer.getId());
                                        // 写入玩家当前的 Follow 状态
                                        buf.writeBoolean(targetPlayer.getPersistentData().getBoolean("bmt_follow_enabled"));
                                    }
                            );
                        } else {
                            player.displayClientMessage(Component.translatable("better_mine_team.message.permission_lord_required").withStyle(ChatFormatting.RED), true);
                        }
                    } else if (target instanceof LivingEntity livingTarget && livingTarget.isAlive()) {
                        // 面板黑名单拦截逻辑
                        if (!hasAdmin && BMTConfig.isEntityDetailsScreenBlacklisted(livingTarget.getType())) {
                            player.displayClientMessage(Component.translatable("better_mine_team.msg.details_blacklisted").withStyle(ChatFormatting.RED), true);
                            return;
                        }

                        int aggrLevel = TeamManager.getAggressiveLevel(livingTarget);
                        com.i113w.better_mine_team.common.init.MTNetworkRegister.CHANNEL.send(
                                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                                new TeamManagementPacket(ACTION_GET_AGGRESSIVE_LEVEL, livingTarget.getId(), String.valueOf(aggrLevel))
                        );

                        NetworkHooks.openScreen(
                                player,
                                new SimpleMenuProvider(
                                        (id, inventory, p) -> new EntityDetailsMenu(id, inventory, livingTarget),
                                        livingTarget.getDisplayName()
                                ),
                                buf -> {
                                    buf.writeInt(livingTarget.getId());
                                    // 写入生物当前的 Follow 状态
                                    buf.writeBoolean(livingTarget.getPersistentData().getBoolean("bmt_follow_enabled"));
                                }
                        );
                    }
                }
                case ACTION_SET_AGGRESSIVE_LEVEL -> {
                    if (!isCaptain) { sendPermissionError(player); return; }
                    if (!(target instanceof LivingEntity livingTarget)) return;

                    int newLevel = Mth.clamp(Integer.parseInt(msg.extraData), 0, 2);
                    int oldLevel = TeamManager.getAggressiveLevel(livingTarget);
                    TeamManager.setAggressiveLevel(livingTarget, newLevel);

                    // 如果玩家将模式设为了 0（Passive），强制清空它现在的攻击目标，打断攻击行为
                    if (newLevel == 0 && oldLevel > 0 && livingTarget instanceof Mob mobTarget) {
                        mobTarget.setTarget(null);
                    }

                    BetterMineTeam.debug("SET_AGGRESSIVE_LEVEL: {} set level {} on {}.",
                            player.getName().getString(), newLevel, livingTarget.getName().getString());

                    // 回传更新后的等级
                    com.i113w.better_mine_team.common.init.MTNetworkRegister.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            new TeamManagementPacket(ACTION_GET_AGGRESSIVE_LEVEL, livingTarget.getId(), String.valueOf(newLevel))
                    );
                }
                case ACTION_GET_AGGRESSIVE_LEVEL -> {
                    if (!(target instanceof LivingEntity livingTarget)) return;
                    int currentLevel = TeamManager.getAggressiveLevel(livingTarget);

                    com.i113w.better_mine_team.common.init.MTNetworkRegister.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            new TeamManagementPacket(ACTION_GET_AGGRESSIVE_LEVEL, livingTarget.getId(), String.valueOf(currentLevel))
                    );
                }
            }
        } catch (Exception e) {
            BetterMineTeam.LOGGER.error("Error handling team action {}", msg.actionType, e);
        }
    }

    private static void sendPermissionError(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("better_mine_team.msg.permission_denied").withStyle(ChatFormatting.RED), true);
    }

    private static class ClientHandler {
        static void handleClientSide(TeamManagementPacket msg) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.world.entity.player.Player player = mc.player;
            if (player == null || player.level() == null) return;

            if (msg.actionType == ACTION_SYNC_FOLLOW_STATE) {
                Entity entity = player.level().getEntity(msg.targetEntityId);
                if (entity != null) {
                    boolean newState = Boolean.parseBoolean(msg.extraData);
                    entity.getPersistentData().putBoolean("bmt_follow_enabled", newState);
                }
            }
            else if (msg.actionType == ACTION_GET_AGGRESSIVE_LEVEL) {
                // 通知当前打开的屏幕更新 UI
                int level2 = Mth.clamp(Integer.parseInt(msg.extraData), 0, 2);
                if (mc.screen instanceof com.i113w.better_mine_team.client.gui.screen.EntityDetailsScreen screen) {
                    screen.setAggressiveLevel(level2);
                }
            }
        }
    }
}