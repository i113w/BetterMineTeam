package com.i113w.better_mine_team.common.network.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record C2S_SelectionSyncPayload(List<Integer> entityIds, int revision) implements CustomPacketPayload {

    public static final Type<C2S_SelectionSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "rts_select_sync"));

    public static final StreamCodec<ByteBuf, C2S_SelectionSyncPayload> STREAM_CODEC = StreamCodec.composite(
            // 实体ID列表 (VarInt压缩)
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
            C2S_SelectionSyncPayload::entityIds,
            // 版本号
            ByteBufCodecs.VAR_INT,
            C2S_SelectionSyncPayload::revision,
            C2S_SelectionSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}