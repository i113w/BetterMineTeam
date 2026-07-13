package com.i113w.better_mine_team.common.network.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.rts.data.PatrolMode;
import com.i113w.better_mine_team.common.rts.data.PatrolTask;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2C_PatrolSyncPayload(
        int entityId,
        boolean enabled,
        String dimensionId,
        PatrolMode mode,
        BlockPos center,
        int radius,
        BlockPos minCorner,
        BlockPos maxCorner
) implements CustomPacketPayload {
    public static final Type<S2C_PatrolSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BetterMineTeam.MODID, "rts_patrol_sync"));

    public static final StreamCodec<ByteBuf, S2C_PatrolSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.entityId());
                ByteBufCodecs.BOOL.encode(buf, payload.enabled());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                PatrolMode.STREAM_CODEC.encode(buf, payload.mode());
                BlockPos.STREAM_CODEC.encode(buf, payload.center());
                ByteBufCodecs.VAR_INT.encode(buf, payload.radius());
                BlockPos.STREAM_CODEC.encode(buf, payload.minCorner());
                BlockPos.STREAM_CODEC.encode(buf, payload.maxCorner());
            },
            buf -> new S2C_PatrolSyncPayload(
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), PatrolMode.STREAM_CODEC.decode(buf),
                    BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    BlockPos.STREAM_CODEC.decode(buf), BlockPos.STREAM_CODEC.decode(buf)
            )
    );

    public static S2C_PatrolSyncPayload fromTask(int entityId, PatrolTask task) {
        if (task == null || !task.isEnabled()) return clear(entityId);
        return new S2C_PatrolSyncPayload(
                entityId, true, task.getDimensionId(), task.getMode(), task.getCenter(),
                task.getRadius(), task.getMinCorner(), task.getMaxCorner()
        );
    }

    public static S2C_PatrolSyncPayload clear(int entityId) {
        return new S2C_PatrolSyncPayload(
                entityId, false, "", PatrolMode.POINT, BlockPos.ZERO, 0, BlockPos.ZERO, BlockPos.ZERO
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void clientHandle(S2C_PatrolSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.i113w.better_mine_team.client.rts.ClientPatrolManager.applySync(payload));
    }
}
