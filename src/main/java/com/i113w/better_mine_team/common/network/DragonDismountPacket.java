package com.i113w.better_mine_team.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务器：请求下马末影龙
 */
public class DragonDismountPacket {

    // 空包，无需额外数据
    public DragonDismountPacket() {}

    public static void encode(DragonDismountPacket msg, FriendlyByteBuf buf) {
        // 空包，无需编码
    }

    public static DragonDismountPacket decode(FriendlyByteBuf buf) {
        return new DragonDismountPacket();
    }

    public static void handle(DragonDismountPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // 只有骑着末影龙时才允许通过此包下马
                if (player.getVehicle() instanceof EnderDragon) {
                    player.stopRiding();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}