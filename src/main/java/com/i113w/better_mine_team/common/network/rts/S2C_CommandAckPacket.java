package com.i113w.better_mine_team.common.network.rts;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：指令执行确认/结果反馈
 */
public class S2C_CommandAckPacket {

    private final boolean success;
    private final int count;
    private final Component message;

    public S2C_CommandAckPacket(boolean success, int count, Component message) {
        this.success = success;
        this.count = count;
        this.message = message;
    }

    public static void encode(S2C_CommandAckPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.success);
        buf.writeVarInt(msg.count);
        buf.writeComponent(msg.message);
    }

    public static S2C_CommandAckPacket decode(FriendlyByteBuf buf) {
        boolean success = buf.readBoolean();
        int count = buf.readVarInt();
        Component message = buf.readComponent();
        return new S2C_CommandAckPacket(success, count, message);
    }

    public static void handle(S2C_CommandAckPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(msg.message, true);
            }
        });
        ctx.setPacketHandled(true);
    }

    public boolean isSuccess() { return success; }
    public int getCount() { return count; }
    public Component getMessage() { return message; }
}
