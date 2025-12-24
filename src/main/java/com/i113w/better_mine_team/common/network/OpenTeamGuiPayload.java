package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.screen.TeamManagementScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

// 这是一个空包，不需要传输数据，只需要触发“打开界面”这个动作
public record OpenTeamGuiPayload() implements CustomPacketPayload {

    public static final Type<OpenTeamGuiPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "open_team_gui"));

    // 空的编解码器
    public static final StreamCodec<ByteBuf, OpenTeamGuiPayload> STREAM_CODEC = StreamCodec.unit(new OpenTeamGuiPayload());

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 客户端收到包后的处理逻辑
    public static void clientHandle(final OpenTeamGuiPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // 打开我们即将编写的界面
            // 只有客户端才有 Minecraft.getInstance()，所以这行代码在服务端跑会崩，但这里是 clientHandle 安全。
            Minecraft.getInstance().setScreen(new TeamManagementScreen());
        });
    }
}