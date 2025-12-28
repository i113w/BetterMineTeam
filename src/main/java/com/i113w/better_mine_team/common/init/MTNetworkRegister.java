package com.i113w.better_mine_team.common.init;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.network.*;
import net.neoforged.bus.api.SubscribeEvent; // 这个注解可以保留，也可以去掉，手动注册时不需要它
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// 移除 @EventBusSubscriber 注解
public class MTNetworkRegister {
    public static final String VERSION = "1.0.0";

    // 移除 @SubscribeEvent (手动注册时不需要这个注解，虽然留着也没事，但为了干净建议删掉)
    public static void registerPayload(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(VERSION);

        // 1. TeamActionPayload (C -> S)
        registrar.playToServer(
                TeamActionPayload.TYPE,
                TeamActionPayload.STREAM_CODEC,
                TeamActionPayload::serverHandle
        );

        // 2. OpenTeamGuiPayload (S -> C)
        registrar.playToClient(
                OpenTeamGuiPayload.TYPE,
                OpenTeamGuiPayload.STREAM_CODEC,
                OpenTeamGuiPayload::clientHandle
        );

        // 3. TeamManagementPayload (Bidirectional)
        // [修复] 使用单个 handle 方法作为入口，内部根据 PacketFlow 分流
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
    }

}