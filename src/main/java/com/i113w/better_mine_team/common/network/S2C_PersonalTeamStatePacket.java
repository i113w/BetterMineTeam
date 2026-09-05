package com.i113w.better_mine_team.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2C_PersonalTeamStatePacket {
    private final boolean available;
    private final boolean enabled;

    public S2C_PersonalTeamStatePacket(boolean available, boolean enabled) {
        this.available = available;
        this.enabled = enabled;
    }

    public static void encode(S2C_PersonalTeamStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.available);
        buf.writeBoolean(msg.enabled);
    }

    public static S2C_PersonalTeamStatePacket decode(FriendlyByteBuf buf) {
        return new S2C_PersonalTeamStatePacket(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(S2C_PersonalTeamStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ClientHandler.handle(msg, ctx);
        ctx.setPacketHandled(true);
    }

    // [FIX] Client-only logic lives in a static inner class as a lazy-loading firewall:
    // the server no longer references net.minecraft.client.* when loading this class (otherwise
    // Forge's RuntimeDistCleaner throws "Attempted to load class .../LocalPlayer for invalid dist").
    // ClientHandler is only loaded when handle() actually runs on the client.
    private static class ClientHandler {
        static void handle(S2C_PersonalTeamStatePacket msg, NetworkEvent.Context ctx) {
            ctx.enqueueWork(() -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    com.i113w.better_mine_team.client.gui.ClientTeamUiState.setPersonalTeamState(mc.player, msg.available, msg.enabled);
                }
            });
        }
    }
}
