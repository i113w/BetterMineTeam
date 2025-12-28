package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DragonDismountPayload() implements CustomPacketPayload {

    public static final Type<DragonDismountPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "dragon_dismount"));

    // 空包不需要编解码逻辑，直接返回单例或新实例
    public static final StreamCodec<ByteBuf, DragonDismountPayload> STREAM_CODEC = StreamCodec.unit(new DragonDismountPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void serverHandle(final DragonDismountPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // 只有骑着末影龙时才允许通过此包下马
                if (player.getVehicle() instanceof EnderDragon) {
                    player.stopRiding();
                }
            }
        });
    }
}