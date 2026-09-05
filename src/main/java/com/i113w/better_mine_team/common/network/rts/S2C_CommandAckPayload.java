package com.i113w.better_mine_team.common.network.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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

    public static void clientHandle(final S2C_CommandAckPayload payload, final IPayloadContext context) {
        ClientHandler.handle(payload, context);
    }

    // [修复] 客户端专属处理放在静态内部类中作为"防火墙"，避免服务端加载本类时连带加载 net.minecraft.client.*
    // （否则 dist-cleaner 会抛 "Attempted to load class .../LocalPlayer for invalid dist DEDICATED_SERVER"）。
    private static final class ClientHandler {
        static void handle(final S2C_CommandAckPayload payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(payload.message(), true);
                }
            });
        }
    }
}