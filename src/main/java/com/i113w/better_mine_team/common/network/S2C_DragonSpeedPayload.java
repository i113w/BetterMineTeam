package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.DragonClientHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2C_DragonSpeedPayload(float speed) implements CustomPacketPayload {

    public static final Type<S2C_DragonSpeedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BetterMineTeam.MODID, "dragon_speed"));

    public static final StreamCodec<ByteBuf, S2C_DragonSpeedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT,
                    S2C_DragonSpeedPayload::speed,
                    S2C_DragonSpeedPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clientHandle(final S2C_DragonSpeedPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> DragonClientHandler.setSyncedDragonSpeed(payload.speed()));
    }
}