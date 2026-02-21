package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：同步 TeamsLord 权限状态
 */
public class S2C_SyncTeamLordPacket {

    private final boolean hasPermission;

    public S2C_SyncTeamLordPacket(boolean hasPermission) {
        this.hasPermission = hasPermission;
    }

    public static void encode(S2C_SyncTeamLordPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.hasPermission);
    }

    public static S2C_SyncTeamLordPacket decode(FriendlyByteBuf buf) {
        return new S2C_SyncTeamLordPacket(buf.readBoolean());
    }

    public static void handle(S2C_SyncTeamLordPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.getPersistentData().putBoolean("bmt_lord_of_teams", msg.hasPermission);
                BetterMineTeam.debug("Client received TeamsLord permission: {}", msg.hasPermission);
            }
        });
        ctx.setPacketHandled(true);
    }

    public boolean hasPermission() { return hasPermission; }
}
