package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2C_SyncTeamLordPayload(boolean hasPermission) implements CustomPacketPayload {

    public static final Type<S2C_SyncTeamLordPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "sync_team_lord"));

    public static final StreamCodec<ByteBuf, S2C_SyncTeamLordPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, S2C_SyncTeamLordPayload::hasPermission,
            S2C_SyncTeamLordPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clientHandle(final S2C_SyncTeamLordPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // 客户端处理：接收包并更新本地玩家的 NBT
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.getPersistentData().putBoolean("bmt_lord_of_teams", payload.hasPermission());
                BetterMineTeam.debug("Client received TeamsLord permission: " + payload.hasPermission());
            }
        });
    }
}