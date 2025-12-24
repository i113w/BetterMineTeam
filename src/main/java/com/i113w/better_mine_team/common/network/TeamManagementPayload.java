package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.team.TeamDataStorage;
import com.i113w.better_mine_team.common.team.TeamManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand; // 修复点 1: 导入 InteractionHand
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import com.i113w.better_mine_team.common.menu.EntityDetailsMenu; // 导入新 Menu
import net.minecraft.network.FriendlyByteBuf;
import com.i113w.better_mine_team.common.team.TeamPermissions;

public record TeamManagementPayload(int actionType, int targetEntityId, String extraData) implements CustomPacketPayload {

    // Action Types
    public static final int ACTION_TELEPORT = 1;
    public static final int ACTION_TOGGLE_FOLLOW = 2;
    public static final int ACTION_KICK = 3;
    public static final int ACTION_RENAME = 4;
    public static final int ACTION_SET_CAPTAIN = 5;
    public static final int ACTION_OPEN_INVENTORY = 6;

    public static final Type<TeamManagementPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "team_manage"));

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

    public static void serverHandle(final TeamManagementPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            Entity target = level.getEntity(payload.targetEntityId);

            if (target == null) return;

            // --- 权限与安全性检查 ---

            // 1. 检查是否同队
            PlayerTeam playerTeam = TeamManager.getTeam(player);
            PlayerTeam targetTeam = TeamManager.getTeam(target);

            // 如果目标没有队伍，或者两人队伍不同，拒绝操作 (除了任命队长可能需要特殊逻辑，这里暂定必须同队)
            if (playerTeam == null || targetTeam == null || !playerTeam.getName().equals(targetTeam.getName())) {
                // 允许特例：如果目标没队，可能是招募逻辑？但这里是管理界面，假设都在队里
                return;
            }

            // 2. 检查操作者是否为队长
            boolean isCaptain = TeamDataStorage.get(level).isCaptain(player);

            switch (payload.actionType) {
                case ACTION_TELEPORT -> {
                    // 权限：仅队长
                    if (!isCaptain) {
                        sendPermissionError(player);
                        return;
                    }
                    // 逻辑：传送到队长身边
                    target.teleportTo(player.getX(), player.getY(), player.getZ());
                }

                case ACTION_TOGGLE_FOLLOW -> {
                    // 权限：仅队长
                    if (!isCaptain) {
                        sendPermissionError(player);
                        return;
                    }
                    if (target instanceof Mob mob) {
                        boolean current = mob.getPersistentData().getBoolean("bmt_follow_enabled");
                        mob.getPersistentData().putBoolean("bmt_follow_enabled", !current);
                        player.displayClientMessage(Component.literal("Follow Mode: " + (!current)), true);
                    }
                }

                case ACTION_KICK -> {
                    // 权限：仅队长
                    if (!isCaptain) {
                        sendPermissionError(player);
                        return;
                    }
                    // 不能踢自己
                    if (target == player) return;

                    if (target instanceof LivingEntity living) {
                        Scoreboard scoreboard = level.getScoreboard();
                        scoreboard.removePlayerFromTeam(living.getStringUUID(), targetTeam);
                        living.setGlowingTag(false);
                        // 可选：清除跟随状态
                        living.getPersistentData().remove("bmt_follow_enabled");
                    }
                }

                case ACTION_RENAME -> {
                    // 权限：建议仅队长，或者所有人？这里设为仅队长
                    if (!isCaptain) {
                        sendPermissionError(player);
                        return;
                    }
                    target.setCustomName(Component.literal(payload.extraData));
                }

                case ACTION_SET_CAPTAIN -> {
                    // 权限：仅现任队长可以禅让 (或者 OP，但这里只处理普通逻辑)
                    if (!isCaptain) {
                        sendPermissionError(player);
                        return;
                    }
                    if (target instanceof ServerPlayer targetPlayer) {
                        // Map 结构保证了 Key(队伍名) 唯一，put 会自动覆盖旧队长，不会出现双队长
                        TeamDataStorage.get(level).setCaptain(playerTeam.getName(), targetPlayer.getUUID());
                        player.sendSystemMessage(Component.literal("Captain transferred to " + targetPlayer.getName().getString()));
                    }
                }

                case ACTION_OPEN_INVENTORY -> {
                    // 1. 目标是玩家
                    if (target instanceof ServerPlayer targetPlayer) {
                        // 检查权限：必须是自己，或者拥有 "Lord of the Teams" 权限
                        boolean isSelf = player.getUUID().equals(targetPlayer.getUUID());
                        boolean hasAdmin = TeamPermissions.hasOverridePermission(player);

                        if (isSelf || hasAdmin) {
                            // 打开详情界面 (复用 EntityDetailsMenu)
                            player.openMenu(new SimpleMenuProvider(
                                    (id, inventory, p) -> new EntityDetailsMenu(id, inventory, targetPlayer),
                                    targetPlayer.getDisplayName()
                            ), (buffer) -> {
                                buffer.writeInt(targetPlayer.getId());
                            });
                        } else {
                            player.displayClientMessage(
                                    Component.translatable("better_mine_team.message.permission_lord_required")
                                            .withStyle(ChatFormatting.RED),
                                    true
                            );
                        }
                    }
                    // 2. 目标是生物 (保持原有逻辑，通常队友都能看，或者你可以加 isCaptain 限制)
                    else if (target instanceof LivingEntity livingTarget) {
                        player.openMenu(new SimpleMenuProvider(
                                (id, inventory, p) -> new EntityDetailsMenu(id, inventory, livingTarget),
                                livingTarget.getDisplayName()
                        ), (buffer) -> {
                            buffer.writeInt(livingTarget.getId());
                        });
                    }
                }
            }
        });
    }

    private static void sendPermissionError(ServerPlayer player) {
        player.displayClientMessage(Component.literal("§cPermission Denied: You are not the Captain!"), true);
    }
}