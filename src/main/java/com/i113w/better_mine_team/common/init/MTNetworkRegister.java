package com.i113w.better_mine_team.common.init;

import com.i113w.better_mine_team.common.network.*;
import com.i113w.better_mine_team.common.network.handler.ServerPacketHandler;
import com.i113w.better_mine_team.common.network.rts.C2S_IssueCommandPayload;
import com.i113w.better_mine_team.common.network.rts.C2S_SelectionSyncPayload;
import com.i113w.better_mine_team.common.network.rts.S2C_CommandAckPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class MTNetworkRegister {
    public static final String VERSION = "1.0.0";

    public static void registerPayload(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(VERSION);

        // --- 旧有的包 ---
        registrar.playToServer(
                TeamActionPayload.TYPE,
                TeamActionPayload.STREAM_CODEC,
                TeamActionPayload::serverHandle
        );

        registrar.playToClient(
                OpenTeamGuiPayload.TYPE,
                OpenTeamGuiPayload.STREAM_CODEC,
                OpenTeamGuiPayload::clientHandle
        );

        registrar.playBidirectional(
                TeamManagementPayload.TYPE,
                TeamManagementPayload.STREAM_CODEC,
                TeamManagementPayload::handle
        );

        registrar.playToServer(
                DragonControllerPayload.TYPE,
                DragonControllerPayload.STREAM_CODEC,
                DragonControllerPayload::serverHandle
        );

        registrar.playToServer(
                DragonDismountPayload.TYPE,
                DragonDismountPayload.STREAM_CODEC,
                DragonDismountPayload::serverHandle
        );

        // --- [新增] RTS 模块网络包 ---

        // 1. 客户端 -> 服务端：选区同步
        registrar.playToServer(
                C2S_SelectionSyncPayload.TYPE,
                C2S_SelectionSyncPayload.STREAM_CODEC,
                ServerPacketHandler::handleSelectionSync
        );

        // 2. 客户端 -> 服务端：发布指令
        registrar.playToServer(
                C2S_IssueCommandPayload.TYPE,
                C2S_IssueCommandPayload.STREAM_CODEC,
                ServerPacketHandler::handleIssueCommand
        );

        // 3. 服务端 -> 客户端：指令反馈 (注意使用 RegistryFriendlyByteBuf 兼容的 Codec)
        registrar.playToClient(
                S2C_CommandAckPayload.TYPE,
                S2C_CommandAckPayload.STREAM_CODEC,
                S2C_CommandAckPayload::clientHandle
        );

        registrar.playToClient(
                S2C_SyncTeamLordPayload.TYPE,
                S2C_SyncTeamLordPayload.STREAM_CODEC,
                S2C_SyncTeamLordPayload::clientHandle
        );
    }
}