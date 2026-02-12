package com.i113w.better_mine_team.common.network.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// [修复] 这里的 ByteBuf 类型必须是 RegistryFriendlyByteBuf，因为 ComponentSerialization 需要它
public record S2C_CommandAckPayload(boolean success, int count, Component message) implements CustomPacketPayload {

    public static final Type<S2C_CommandAckPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "rts_cmd_ack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2C_CommandAckPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, S2C_CommandAckPayload::success,
            ByteBufCodecs.VAR_INT, S2C_CommandAckPayload::count,
            ComponentSerialization.STREAM_CODEC, S2C_CommandAckPayload::message,
            S2C_CommandAckPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clientHandle(final S2C_CommandAckPayload payload, final net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(payload.message(), true);
            }
        });
    }
}