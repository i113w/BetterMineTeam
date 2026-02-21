package com.i113w.better_mine_team.common.init;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.network.*;
import com.i113w.better_mine_team.common.network.handler.ServerPacketHandler;
import com.i113w.better_mine_team.common.network.rts.C2S_IssueCommandPacket;
import com.i113w.better_mine_team.common.network.rts.C2S_SelectionSyncPacket;
import com.i113w.better_mine_team.common.network.rts.S2C_CommandAckPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class MTNetworkRegister {

    private static final String PROTOCOL_VERSION = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BetterMineTeam.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;

        // ─── 原有 5 个包 ───────────────────────────────────────

        // ID 0: C→S 队伍动作（换队、切换PVP）
        CHANNEL.registerMessage(id++,
                TeamActionPacket.class,
                TeamActionPacket::encode,
                TeamActionPacket::decode,
                TeamActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // ID 1: 双向 队伍管理（传送/跟随/踢出/重命名/设队长/打开背包/同步跟随状态）
        CHANNEL.registerMessage(id++,
                TeamManagementPacket.class,
                TeamManagementPacket::encode,
                TeamManagementPacket::decode,
                TeamManagementPacket::handle
        );

        // ID 2: S→C 打开队伍 GUI
        CHANNEL.registerMessage(id++,
                OpenTeamGuiPacket.class,
                OpenTeamGuiPacket::encode,
                OpenTeamGuiPacket::decode,
                OpenTeamGuiPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // ID 3: C→S 龙控制输入
        CHANNEL.registerMessage(id++,
                DragonControllerPacket.class,
                DragonControllerPacket::encode,
                DragonControllerPacket::decode,
                DragonControllerPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // ID 4: C→S 龙下坐请求
        CHANNEL.registerMessage(id++,
                DragonDismountPacket.class,
                DragonDismountPacket::encode,
                DragonDismountPacket::decode,
                DragonDismountPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // ─── 新增 4 个 RTS 包 ──────────────────────────────────

        // ID 5: C→S RTS 选区同步
        CHANNEL.registerMessage(id++,
                C2S_SelectionSyncPacket.class,
                C2S_SelectionSyncPacket::encode,
                C2S_SelectionSyncPacket::decode,
                ServerPacketHandler::handleSelectionSync,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // ID 6: C→S RTS 发布指令
        CHANNEL.registerMessage(id++,
                C2S_IssueCommandPacket.class,
                C2S_IssueCommandPacket::encode,
                C2S_IssueCommandPacket::decode,
                ServerPacketHandler::handleIssueCommand,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // ID 7: S→C RTS 指令执行确认
        CHANNEL.registerMessage(id++,
                S2C_CommandAckPacket.class,
                S2C_CommandAckPacket::encode,
                S2C_CommandAckPacket::decode,
                S2C_CommandAckPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // ID 8: S→C TeamsLord 权限同步
        CHANNEL.registerMessage(id++,
                S2C_SyncTeamLordPacket.class,
                S2C_SyncTeamLordPacket::encode,
                S2C_SyncTeamLordPacket::decode,
                S2C_SyncTeamLordPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}