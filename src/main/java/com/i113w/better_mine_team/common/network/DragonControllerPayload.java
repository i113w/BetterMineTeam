package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DragonControllerPayload(boolean isSpaceDown, boolean isShiftDown) implements CustomPacketPayload {

    public static final Type<DragonControllerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "dragon_ctrl"));

    public static final StreamCodec<ByteBuf, DragonControllerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DragonControllerPayload::isSpaceDown,
            ByteBufCodecs.BOOL, DragonControllerPayload::isShiftDown,
            DragonControllerPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void serverHandle(final DragonControllerPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // 将输入状态暂存到玩家 NBT (或者你可以用 Capability，但 NBT 对这种临时状态最简单)
                player.getPersistentData().putBoolean("bmt_dragon_space", payload.isSpaceDown);
                player.getPersistentData().putBoolean("bmt_dragon_shift", payload.isShiftDown);
            }
        });
    }
}