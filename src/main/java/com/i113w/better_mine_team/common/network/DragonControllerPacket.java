package com.i113w.better_mine_team.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务器：传递末影龙控制输入 (加速/减速)
 */
public class DragonControllerPacket {
    private final boolean isSpaceDown;
    private final boolean isShiftDown;

    public DragonControllerPacket(boolean isSpaceDown, boolean isShiftDown) {
        this.isSpaceDown = isSpaceDown;
        this.isShiftDown = isShiftDown;
    }

    public static void encode(DragonControllerPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isSpaceDown);
        buf.writeBoolean(msg.isShiftDown);
    }

    public static DragonControllerPacket decode(FriendlyByteBuf buf) {
        return new DragonControllerPacket(
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    public static void handle(DragonControllerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // 将输入状态暂存到玩家 NBT
                player.getPersistentData().putBoolean("bmt_dragon_space", msg.isSpaceDown);
                player.getPersistentData().putBoolean("bmt_dragon_shift", msg.isShiftDown);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}