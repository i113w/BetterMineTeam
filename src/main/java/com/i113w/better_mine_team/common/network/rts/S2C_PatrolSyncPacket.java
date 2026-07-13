package com.i113w.better_mine_team.common.network.rts;

import com.i113w.better_mine_team.client.rts.ClientPatrolManager;
import com.i113w.better_mine_team.common.rts.data.PatrolMode;
import com.i113w.better_mine_team.common.rts.data.PatrolTask;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2C_PatrolSyncPacket {
    private final int entityId;
    private final boolean enabled;
    private final String dimensionId;
    private final PatrolMode mode;
    private final BlockPos center;
    private final int radius;
    private final BlockPos minCorner;
    private final BlockPos maxCorner;

    public S2C_PatrolSyncPacket(int entityId, boolean enabled, String dimensionId, PatrolMode mode,
                                BlockPos center, int radius, BlockPos minCorner, BlockPos maxCorner) {
        this.entityId = entityId;
        this.enabled = enabled;
        this.dimensionId = dimensionId;
        this.mode = mode;
        this.center = center;
        this.radius = radius;
        this.minCorner = minCorner;
        this.maxCorner = maxCorner;
    }

    public static S2C_PatrolSyncPacket fromTask(int entityId, PatrolTask task) {
        if (task == null || !task.isEnabled()) return clear(entityId);
        return new S2C_PatrolSyncPacket(entityId, true, task.getDimensionId(), task.getMode(), task.getCenter(),
                task.getRadius(), task.getMinCorner(), task.getMaxCorner());
    }

    public static S2C_PatrolSyncPacket clear(int entityId) {
        return new S2C_PatrolSyncPacket(entityId, false, "", PatrolMode.POINT, BlockPos.ZERO, 0,
                BlockPos.ZERO, BlockPos.ZERO);
    }

    public static void encode(S2C_PatrolSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.enabled);
        buf.writeUtf(msg.dimensionId);
        buf.writeEnum(msg.mode);
        buf.writeBlockPos(msg.center);
        buf.writeVarInt(msg.radius);
        buf.writeBlockPos(msg.minCorner);
        buf.writeBlockPos(msg.maxCorner);
    }

    public static S2C_PatrolSyncPacket decode(FriendlyByteBuf buf) {
        return new S2C_PatrolSyncPacket(buf.readVarInt(), buf.readBoolean(), buf.readUtf(),
                buf.readEnum(PatrolMode.class), buf.readBlockPos(), buf.readVarInt(),
                buf.readBlockPos(), buf.readBlockPos());
    }

    public static void handle(S2C_PatrolSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientPatrolManager.applySync(msg));
        ctx.setPacketHandled(true);
    }

    public int getEntityId() { return entityId; }
    public boolean isEnabled() { return enabled; }
    public String getDimensionId() { return dimensionId; }
    public PatrolMode getMode() { return mode; }
    public BlockPos getCenter() { return center; }
    public int getRadius() { return radius; }
    public BlockPos getMinCorner() { return minCorner; }
    public BlockPos getMaxCorner() { return maxCorner; }
}
