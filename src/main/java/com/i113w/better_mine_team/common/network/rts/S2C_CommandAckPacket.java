package com.i113w.better_mine_team.common.network.rts;

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
        ClientHandler.handle(msg, ctx);
        ctx.setPacketHandled(true);
    }

    // [修复] 客户端专属逻辑放入静态内部类作为"懒加载防火墙"：
    // 服务端加载本类时不再直接引用 net.minecraft.client.*（否则 Forge 的 RuntimeDistCleaner 会在
    // 服务端抛 "Attempted to load class .../LocalPlayer for invalid dist DEDICATED_SERVER"）。
    // 只有客户端真正执行 handle 时，ClientHandler 才会被加载。
    private static class ClientHandler {
        static void handle(S2C_CommandAckPacket msg, NetworkEvent.Context ctx) {
            ctx.enqueueWork(() -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(msg.message, true);
                }
            });
        }
    }

    public boolean isSuccess() { return success; }
    public int getCount() { return count; }
    public Component getMessage() { return message; }
}
