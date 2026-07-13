package com.i113w.better_mine_team.common.network.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.network.data.PatrolAction;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record C2S_PatrolCommandPayload(
        PatrolAction action,
        List<Integer> entityIds,
        String dimensionId,
        BlockPos center,
        int radius,
        BlockPos minCorner,
        BlockPos maxCorner
) implements CustomPacketPayload {

    public static final Type<C2S_PatrolCommandPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "rts_patrol_cmd"));

    public static final StreamCodec<ByteBuf, C2S_PatrolCommandPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                PatrolAction.STREAM_CODEC.encode(buf, payload.action());
                ByteBufCodecs.VAR_INT.encode(buf, payload.entityIds().size());
                for (int id : payload.entityIds()) {
                    ByteBufCodecs.VAR_INT.encode(buf, id);
                }
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                BlockPos.STREAM_CODEC.encode(buf, payload.center());
                ByteBufCodecs.VAR_INT.encode(buf, payload.radius());
                BlockPos.STREAM_CODEC.encode(buf, payload.minCorner());
                BlockPos.STREAM_CODEC.encode(buf, payload.maxCorner());
            },
            buf -> {
                PatrolAction action = PatrolAction.STREAM_CODEC.decode(buf);
                int size = ByteBufCodecs.VAR_INT.decode(buf);
                java.util.List<Integer> entityIds = new java.util.ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entityIds.add(ByteBufCodecs.VAR_INT.decode(buf));
                }
                String dimensionId = ByteBufCodecs.STRING_UTF8.decode(buf);
                BlockPos center = BlockPos.STREAM_CODEC.decode(buf);
                int radius = ByteBufCodecs.VAR_INT.decode(buf);
                BlockPos minCorner = BlockPos.STREAM_CODEC.decode(buf);
                BlockPos maxCorner = BlockPos.STREAM_CODEC.decode(buf);
                return new C2S_PatrolCommandPayload(action, entityIds, dimensionId, center, radius, minCorner, maxCorner);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
