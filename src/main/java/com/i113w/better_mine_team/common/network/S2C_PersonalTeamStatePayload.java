package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.ClientTeamUiState;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2C_PersonalTeamStatePayload(boolean available, boolean enabled) implements CustomPacketPayload {

    public static final Type<S2C_PersonalTeamStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "personal_team_state"));

    public static final StreamCodec<ByteBuf, S2C_PersonalTeamStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, S2C_PersonalTeamStatePayload::available,
            ByteBufCodecs.BOOL, S2C_PersonalTeamStatePayload::enabled,
            S2C_PersonalTeamStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clientHandle(final S2C_PersonalTeamStatePayload payload, final IPayloadContext context) {
        ClientHandler.handle(payload, context);
    }

    // [修复] 客户端专属逻辑放入静态内部类作为"懒加载防火墙"：
    // 服务端加载本 payload 类时不再直接引用 net.minecraft.client.*（否则 NeoForge 的
    // RuntimeDistCleaner 会在服务端抛 "Attempted to load class .../LocalPlayer for invalid dist"）。
    // 只有客户端真正执行 clientHandle 时，ClientHandler 才会被加载。
    private static final class ClientHandler {
        static void handle(final S2C_PersonalTeamStatePayload payload, final IPayloadContext context) {
            context.enqueueWork(() -> {
                if (Minecraft.getInstance().player != null) {
                    ClientTeamUiState.setPersonalTeamState(Minecraft.getInstance().player, payload.available(), payload.enabled());
                }
            });
        }
    }
}