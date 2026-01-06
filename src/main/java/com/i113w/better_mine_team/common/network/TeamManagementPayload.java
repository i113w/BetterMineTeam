package com.i113w.better_mine_team.common.network;

// ... 保留原有的 Common Imports (World, Entity, Player 等) ...
import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.menu.EntityDetailsMenu;
import com.i113w.better_mine_team.common.team.TeamDataStorage;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.better_mine_team.common.team.TeamPermissions;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

// [注意] 检查这里，绝对不能有 net.minecraft.client.* 的导入！
// 如果有，请删除。

public record TeamManagementPayload(int actionType, int targetEntityId, String extraData) implements CustomPacketPayload {
    // ... 常量定义保持不变 ...
    public static final int ACTION_TELEPORT = 1;
    public static final int ACTION_TOGGLE_FOLLOW = 2;
    public static final int ACTION_KICK = 3;
    public static final int ACTION_RENAME = 4;
    public static final int ACTION_SET_CAPTAIN = 5;
    public static final int ACTION_OPEN_INVENTORY = 6;
    public static final int ACTION_SYNC_FOLLOW_STATE = 7;

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

    public static void handle(final TeamManagementPayload payload, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            clientHandle(payload, context);
        } else {
            serverHandle(payload, context);
        }
    }

    private static void serverHandle(final TeamManagementPayload payload, final IPayloadContext context) {
        // ... 服务端逻辑保持不变 (完全安全，因为这里用的都是 ServerPlayer / ServerLevel) ...
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
                        if (target.isAlive()) target.teleportTo(player.getX(), player.getY(), player.getZ());
                    }
                    case ACTION_TOGGLE_FOLLOW -> {
                        if (!isCaptain) { sendPermissionError(player); return; }
                        if (target instanceof Mob mob && mob.isAlive()) {
                            boolean current = mob.getPersistentData().getBoolean("bmt_follow_enabled");
                            boolean newState = !current;
                            mob.getPersistentData().putBoolean("bmt_follow_enabled", newState);

                            // 同步状态回客户端
                            TeamManagementPayload syncPacket = new TeamManagementPayload(
                                    ACTION_SYNC_FOLLOW_STATE,
                                    mob.getId(),
                                    String.valueOf(newState)
                            );
                            PacketDistributor.sendToPlayer(player, syncPacket);

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
                        target.setCustomName(Component.literal(payload.extraData));
                        String rawName = payload.extraData;
                        // [新增] 输入验证
                        if (rawName.length() > 32) rawName = rawName.substring(0, 32);
                        // 简单的过滤，防止 JSON 注入或其他显示问题
                        String safeName = net.minecraft.ChatFormatting.stripFormatting(rawName);

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
                            boolean hasAdmin = TeamPermissions.hasOverridePermission(player);
                            if (isSelf || hasAdmin) {
                                player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new EntityDetailsMenu(id, inventory, targetPlayer), targetPlayer.getDisplayName()), (buffer) -> buffer.writeInt(targetPlayer.getId()));
                            } else {
                                player.displayClientMessage(Component.translatable("better_mine_team.message.permission_lord_required").withStyle(ChatFormatting.RED), true);
                            }
                        } else if (target instanceof LivingEntity livingTarget && livingTarget.isAlive()) {
                            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new EntityDetailsMenu(id, inventory, livingTarget), livingTarget.getDisplayName()), (buffer) -> buffer.writeInt(livingTarget.getId()));
                        }
                    }
                }
            } catch (Exception e) {
                BetterMineTeam.LOGGER.error("Error handling team action {}", payload.actionType, e);
            }
        });
    }

    // 客户端处理逻辑
    private static void clientHandle(final TeamManagementPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientHandler.handle(payload, context);
        });
    }

    // 内部类隔离
    private static class ClientHandler {
        static void handle(TeamManagementPayload payload, IPayloadContext context) {
            net.minecraft.world.entity.player.Player player = context.player();
            if (player == null) return;
            Level level = player.level();
            if (level == null) return;

            if (payload.actionType == ACTION_SYNC_FOLLOW_STATE) {
                Entity entity = level.getEntity(payload.targetEntityId);
                if (entity != null) {
                    boolean newState = Boolean.parseBoolean(payload.extraData);
                    entity.getPersistentData().putBoolean("bmt_follow_enabled", newState);
                }
            }
        }
    }

    // ... 辅助方法 ...
    private static void sendPermissionError(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("better_mine_team.msg.permission_denied").withStyle(ChatFormatting.RED), true);
    }
}