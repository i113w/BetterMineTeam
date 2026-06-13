package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.client.gui.ClientTeamUiState;
import net.minecraft.client.Minecraft;
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
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ClientTeamUiState.setPersonalTeamState(mc.player, msg.available, msg.enabled);
            }
        });
        ctx.setPacketHandled(true);
    }
}
