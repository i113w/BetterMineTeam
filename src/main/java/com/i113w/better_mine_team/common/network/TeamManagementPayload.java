package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.menu.EntityDetailsMenu;
import com.i113w.better_mine_team.common.team.TeamDataStorage;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.better_mine_team.common.team.TeamPermissions;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record TeamManagementPayload(int actionType, int targetEntityId, String extraData) implements CustomPacketPayload {

    public static final int ACTION_TELEPORT             = 1;
    public static final int ACTION_TOGGLE_FOLLOW        = 2;
    public static final int ACTION_KICK                 = 3;
    public static final int ACTION_RENAME               = 4;
    public static final int ACTION_SET_CAPTAIN          = 5;
    public static final int ACTION_OPEN_INVENTORY       = 6;
    public static final int ACTION_SYNC_FOLLOW_STATE    = 7;
    public static final int ACTION_SET_AGGRESSIVE_LEVEL = 8;

    public static final int ACTION_GET_AGGRESSIVE_LEVEL = 9;

    public static final Type<TeamManagementPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "team_manage"));

    public static final StreamCodec<ByteBuf, TeamManagementPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TeamManagementPayload::actionType,
            ByteBufCodecs.VAR_INT, TeamManagementPayload::targetEntityId,
            ByteBufCodecs.STRING_UTF8, TeamManagementPayload::extraData,
            TeamManagementPayload::new
    );

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final TeamManagementPayload payload, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> ClientHandler.handle(payload, context));
        } else {
            serverHandle(payload, context);
        }
    }

    // ── Server handler ───────────────────────────────────────────────────────

    private static void serverHandle(final TeamManagementPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            Entity target = level.getEntity(payload.targetEntityId);

            if (target == null || target.isRemoved()) return;

            PlayerTeam playerTeam = TeamManager.getTeam(player);
            PlayerTeam targetTeam = TeamManager.getTeam(target);

            if (playerTeam == null || targetTeam == null || !playerTeam.getName().equals(targetTeam.getName())) {
                return;
            }

            boolean isCaptain = TeamDataStorage.get(level).isCaptain(player);

            try {
                switch (payload.actionType) {
                    case ACTION_TELEPORT -> {
                        if (!isCaptain) { sendPermissionError(player); return; }
                        // [修复] 增加类型检查，确保是 LivingEntity 且存活
                        if (target instanceof LivingEntity living && living.isAlive()) {
                            target.teleportTo(player.getX(), player.getY(), player.getZ());
                        }
                    }
                    case ACTION_TOGGLE_FOLLOW -> {
                        if (!isCaptain) { sendPermissionError(player); return; }
                        if (target instanceof Mob mob && mob.isAlive()) {
                            boolean current  = mob.getPersistentData().getBoolean("bmt_follow_enabled");
                            boolean newState = !current;
                            mob.getPersistentData().putBoolean("bmt_follow_enabled", newState);

                            PacketDistributor.sendToPlayersTrackingEntityAndSelf(mob,
                                    new TeamManagementPayload(ACTION_SYNC_FOLLOW_STATE, mob.getId(), String.valueOf(newState)));

                            player.displayClientMessage(Component.translatable(
                                    newState ? "better_mine_team.msg.follow_enabled"
                                            : "better_mine_team.msg.follow_disabled"), true);
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
                        String rawName = payload.extraData;
                        if (rawName.length() > 32) rawName = rawName.substring(0, 32);
                        String safeName = net.minecraft.ChatFormatting.stripFormatting(rawName);
                        target.setCustomName(Component.literal(safeName));
                    }
                    case ACTION_SET_CAPTAIN -> {
                        if (!isCaptain) { sendPermissionError(player); return; }
                        if (target instanceof ServerPlayer targetPlayer) {
                            TeamDataStorage.get(level).setCaptain(playerTeam.getName(), targetPlayer.getUUID());
                            player.displayClientMessage(
                                    Component.translatable("better_mine_team.msg.captain_transferred",
                                            targetPlayer.getName()), true);
                        }
                    }
                    case ACTION_OPEN_INVENTORY -> {
                        if (target instanceof ServerPlayer targetPlayer) {
                            boolean isSelf   = player.getUUID().equals(targetPlayer.getUUID());
                            boolean hasAdmin = TeamPermissions.hasOverridePermission(player);
                            if (isSelf || hasAdmin) {
                                player.openMenu(
                                        new SimpleMenuProvider(
                                                (id, inv, p) -> new EntityDetailsMenu(id, inv, targetPlayer),
                                                targetPlayer.getDisplayName()),
                                        buf -> buf.writeInt(targetPlayer.getId()));
                            } else {
                                player.displayClientMessage(
                                        Component.translatable("better_mine_team.message.permission_lord_required")
                                                .withStyle(ChatFormatting.RED), true);
                            }
                        } else if (target instanceof LivingEntity livingTarget && livingTarget.isAlive()) {

                            // 检查实体详情面板黑名单
                            boolean hasAdmin = TeamPermissions.hasOverridePermission(player);
                            if (!hasAdmin && BMTConfig.isEntityDetailsScreenBlacklisted(livingTarget.getType())) {
                                player.displayClientMessage(
                                        Component.translatable("better_mine_team.msg.details_blacklisted")
                                                .withStyle(ChatFormatting.RED), true);
                                return;
                            }

                            boolean followState = livingTarget.getPersistentData().getBoolean("bmt_follow_enabled");
                            PacketDistributor.sendToPlayer(player,
                                    new TeamManagementPayload(ACTION_SYNC_FOLLOW_STATE, livingTarget.getId(),
                                            String.valueOf(followState)));

                            // 同步 Aggressive 等级给即将打开屏幕的客户端
                            int aggrLevel = TeamManager.getAggressiveLevel(livingTarget);
                            PacketDistributor.sendToPlayer(player,
                                    new TeamManagementPayload(ACTION_GET_AGGRESSIVE_LEVEL, livingTarget.getId(),
                                            String.valueOf(aggrLevel)));

                            player.openMenu(
                                    new SimpleMenuProvider(
                                            (id, inv, p) -> new EntityDetailsMenu(id, inv, livingTarget),
                                            livingTarget.getDisplayName()),
                                    buf -> buf.writeInt(livingTarget.getId()));
                        }
                    }

                    // ── Aggressive level ───────────────────────────────────────────────
                    case ACTION_SET_AGGRESSIVE_LEVEL -> {
                        if (!isCaptain) { sendPermissionError(player); return; }
                        if (!(target instanceof LivingEntity livingTarget)) return;

                        int newLevel = Mth.clamp(Integer.parseInt(payload.extraData), 0, 2);
                        TeamManager.setAggressiveLevel(livingTarget, newLevel);

                        BetterMineTeam.debug("SET_AGGRESSIVE_LEVEL: {} set level {} on {}.",
                                player.getName().getString(), newLevel, livingTarget.getName().getString());

                        // 回传更新后的等级给请求方（其他玩家下次打开 Screen 时自行请求）
                        PacketDistributor.sendToPlayer(player,
                                new TeamManagementPayload(ACTION_GET_AGGRESSIVE_LEVEL,
                                        livingTarget.getId(), String.valueOf(newLevel)));
                    }
                    case ACTION_GET_AGGRESSIVE_LEVEL -> {
                        if (!(target instanceof LivingEntity livingTarget)) return;
                        int currentLevel = TeamManager.getAggressiveLevel(livingTarget);

                        PacketDistributor.sendToPlayer(player,
                                new TeamManagementPayload(ACTION_GET_AGGRESSIVE_LEVEL,
                                        livingTarget.getId(), String.valueOf(currentLevel)));
                    }
                }
            } catch (Exception e) {
                BetterMineTeam.LOGGER.error("Error handling team action {}", payload.actionType, e);
            }
        });
    }

    // ── Client handler ───────────────────────────────────────────────────────

    private static class ClientHandler {
        static void handle(TeamManagementPayload payload, IPayloadContext context) {
            net.minecraft.world.entity.player.Player player = context.player();
            if (player == null) return;
            Level level = player.level();
            if (level == null) return;

            switch (payload.actionType) {
                case ACTION_SYNC_FOLLOW_STATE -> {
                    Entity entity = level.getEntity(payload.targetEntityId);
                    if (entity != null) {
                        boolean newState = Boolean.parseBoolean(payload.extraData);
                        entity.getPersistentData().putBoolean("bmt_follow_enabled", newState);
                        // EntityDetailsScreen.containerTick() 每 tick 读取此值并自动刷新图标
                    }
                }
                case ACTION_GET_AGGRESSIVE_LEVEL -> {
                    // 服务端回传当前等级，通知 EntityDetailsScreen 更新按钮高亮
                    int level2 = Mth.clamp(Integer.parseInt(payload.extraData), 0, 2);
                    if (Minecraft.getInstance().screen instanceof com.i113w.better_mine_team.client.gui.screen.EntityDetailsScreen screen) {
                        screen.setAggressiveLevel(level2);
                    }
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void sendPermissionError(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("better_mine_team.msg.permission_denied")
                        .withStyle(ChatFormatting.RED), true);
    }
}