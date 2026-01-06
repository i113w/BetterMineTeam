package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

// [注意] 顶部绝对不能 Import 任何 net.minecraft.client 包下的类！
// 不要 Import TeamManagementScreen！

public record OpenTeamGuiPayload() implements CustomPacketPayload {

    public static final Type<OpenTeamGuiPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "open_team_gui"));

    public static final StreamCodec<ByteBuf, OpenTeamGuiPayload> STREAM_CODEC = StreamCodec.unit(new OpenTeamGuiPayload());

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clientHandle(final OpenTeamGuiPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // 将具体逻辑委托给内部类
            // 服务器加载 OpenTeamGuiPayload 时，不会去动 ClientHandler
            // 只有客户端真正执行这行代码时，ClientHandler 才会被加载，进而加载 Screen
            ClientHandler.openScreen();
        });
    }

    // 静态内部类：利用 JVM 的懒加载特性作为防火墙
    private static class ClientHandler {
        static void openScreen() {
            // 在这里引用客户端类是安全的
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    new com.i113w.better_mine_team.client.gui.screen.TeamManagementScreen()
            );
        }
    }
}