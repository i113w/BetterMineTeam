package com.i113w.better_mine_team.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务器 -> 客户端：打开队伍管理界面
 * 注意：顶部绝对不能 Import 任何 net.minecraft.client 包下的类！
 */
public class OpenTeamGuiPacket {

    // 空包，无需额外数据
    public OpenTeamGuiPacket() {}

    public static void encode(OpenTeamGuiPacket msg, FriendlyByteBuf buf) {
        // 空包，无需编码
    }

    public static OpenTeamGuiPacket decode(FriendlyByteBuf buf) {
        return new OpenTeamGuiPacket();
    }

    public static void handle(OpenTeamGuiPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 委托给内部类处理，利用 JVM 懒加载保护服务端
            ClientHandler.openScreen();
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * 静态内部类：作为防火墙隔离客户端代码
     * 服务器加载 OpenTeamGuiPacket 时不会触发 ClientHandler 加载
     * 只有客户端真正执行 openScreen() 时才会加载 Screen 类
     */
    private static class ClientHandler {
        static void openScreen() {
            // 在这里引用客户端类是安全的
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    new com.i113w.better_mine_team.client.gui.screen.TeamManagementScreen()
            );
        }
    }
}