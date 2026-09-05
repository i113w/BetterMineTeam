package com.i113w.better_mine_team.common.network;

import com.i113w.better_mine_team.BetterMineTeam;
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
        ClientHandler.handle(msg, ctx);
        ctx.setPacketHandled(true);
    }

    // [修复] 客户端专属逻辑放入静态内部类作为"懒加载防火墙"：
    // 服务端加载本类时不再直接引用 net.minecraft.client.*（否则 Forge 的 RuntimeDistCleaner 会在
    // 服务端抛 "Attempted to load class .../LocalPlayer for invalid dist DEDICATED_SERVER"）。
    // 只有客户端真正执行 handle 时，ClientHandler 才会被加载。
    private static class ClientHandler {
        static void handle(S2C_SyncTeamLordPacket msg, NetworkEvent.Context ctx) {
            ctx.enqueueWork(() -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.getPersistentData().putBoolean("bmt_lord_of_teams", msg.hasPermission);
                    BetterMineTeam.debug("Client received TeamsLord permission: {}", msg.hasPermission);
                }
            });
        }
    }

    public boolean hasPermission() { return hasPermission; }
}
