package com.i113w.better_mine_team.common.network.rts;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：同步选区实体 ID 列表
 */
public class C2S_SelectionSyncPacket {

    private final List<Integer> entityIds;
    private final int revision;

    public C2S_SelectionSyncPacket(List<Integer> entityIds, int revision) {
        this.entityIds = entityIds;
        this.revision = revision;
    }

    public static void encode(C2S_SelectionSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityIds.size());
        for (int id : msg.entityIds) {
            buf.writeVarInt(id);
        }
        buf.writeVarInt(msg.revision);
    }

    public static C2S_SelectionSyncPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Integer> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readVarInt());
        }
        int revision = buf.readVarInt();
        return new C2S_SelectionSyncPacket(ids, revision);
    }

    public List<Integer> getEntityIds() { return entityIds; }
    public int getRevision() { return revision; }
}
