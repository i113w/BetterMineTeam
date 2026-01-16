package com.i113w.better_mine_team.common.init;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class MTNetworkRegister {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BetterMineTeam.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        BetterMineTeam.LOGGER.info("Registering network packets...");

        // 1. TeamActionPacket (C -> S)
        CHANNEL.registerMessage(
                packetId++,
                TeamActionPacket.class,
                TeamActionPacket::encode,
                TeamActionPacket::decode,
                TeamActionPacket::handle
        );

        // 2. OpenTeamGuiPacket (S -> C)
        CHANNEL.registerMessage(
                packetId++,
                OpenTeamGuiPacket.class,
                OpenTeamGuiPacket::encode,
                OpenTeamGuiPacket::decode,
                OpenTeamGuiPacket::handle
        );

        // 3. TeamManagementPacket (Bidirectional)
        CHANNEL.registerMessage(
                packetId++,
                TeamManagementPacket.class,
                TeamManagementPacket::encode,
                TeamManagementPacket::decode,
                TeamManagementPacket::handle
        );

        // 4. DragonControllerPacket (C -> S)
        CHANNEL.registerMessage(
                packetId++,
                DragonControllerPacket.class,
                DragonControllerPacket::encode,
                DragonControllerPacket::decode,
                DragonControllerPacket::handle
        );

        // 5. DragonDismountPacket (C -> S)
        CHANNEL.registerMessage(
                packetId++,
                DragonDismountPacket.class,
                DragonDismountPacket::encode,
                DragonDismountPacket::decode,
                DragonDismountPacket::handle
        );

        BetterMineTeam.LOGGER.info("Network packets registered successfully!");
    }
}