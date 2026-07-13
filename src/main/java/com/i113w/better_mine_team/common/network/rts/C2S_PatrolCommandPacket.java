package com.i113w.better_mine_team.common.network.rts;

import com.i113w.better_mine_team.common.network.data.PatrolAction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public class C2S_PatrolCommandPacket {
    private static final int MAX_ENTITY_IDS = 1024;

    private final PatrolAction action;
    private final List<Integer> entityIds;
    private final String dimensionId;
    private final BlockPos center;
    private final int radius;
    private final BlockPos minCorner;
    private final BlockPos maxCorner;

    public C2S_PatrolCommandPacket(PatrolAction action, List<Integer> entityIds, String dimensionId,
                                   BlockPos center, int radius, BlockPos minCorner, BlockPos maxCorner) {
        this.action = action;
        this.entityIds = List.copyOf(entityIds);
        this.dimensionId = dimensionId;
        this.center = center;
        this.radius = radius;
        this.minCorner = minCorner;
        this.maxCorner = maxCorner;
    }

    public static void encode(C2S_PatrolCommandPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeVarInt(msg.entityIds.size());
        msg.entityIds.forEach(buf::writeVarInt);
        buf.writeUtf(msg.dimensionId);
        buf.writeBlockPos(msg.center);
        buf.writeVarInt(msg.radius);
        buf.writeBlockPos(msg.minCorner);
        buf.writeBlockPos(msg.maxCorner);
    }

    public static C2S_PatrolCommandPacket decode(FriendlyByteBuf buf) {
        PatrolAction action = buf.readEnum(PatrolAction.class);
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTITY_IDS) {
            throw new IllegalArgumentException("Invalid patrol entity count: " + size);
        }
        List<Integer> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) ids.add(buf.readVarInt());
        return new C2S_PatrolCommandPacket(action, ids, buf.readUtf(), buf.readBlockPos(), buf.readVarInt(),
                buf.readBlockPos(), buf.readBlockPos());
    }

    public PatrolAction getAction() { return action; }
    public List<Integer> getEntityIds() { return entityIds; }
    public String getDimensionId() { return dimensionId; }
    public BlockPos getCenter() { return center; }
    public int getRadius() { return radius; }
    public BlockPos getMinCorner() { return minCorner; }
    public BlockPos getMaxCorner() { return maxCorner; }
}
