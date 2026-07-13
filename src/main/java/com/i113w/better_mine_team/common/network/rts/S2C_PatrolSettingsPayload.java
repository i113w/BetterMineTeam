package com.i113w.better_mine_team.common.network.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.config.PatrolSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2C_PatrolSettingsPayload(PatrolSettings settings) implements CustomPacketPayload {
    public static final Type<S2C_PatrolSettingsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BetterMineTeam.MODID, "patrol_settings"));

    public static final StreamCodec<ByteBuf, S2C_PatrolSettingsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> encodeSettings(buf, payload.settings()),
            buf -> new S2C_PatrolSettingsPayload(decodeSettings(buf))
    );

    public static S2C_PatrolSettingsPayload current() {
        return new S2C_PatrolSettingsPayload(BMTConfig.getPatrolSettings());
    }

    private static void encodeSettings(ByteBuf buf, PatrolSettings settings) {
        ByteBufCodecs.BOOL.encode(buf, settings.enabled());
        ByteBufCodecs.VAR_INT.encode(buf, settings.pointRadius());
        ByteBufCodecs.VAR_INT.encode(buf, settings.minAreaSize());
        ByteBufCodecs.VAR_INT.encode(buf, settings.maxAreaSize());
        ByteBufCodecs.DOUBLE.encode(buf, settings.maxCommandDistance());
        ByteBufCodecs.DOUBLE.encode(buf, settings.movementSpeed());
        ByteBufCodecs.VAR_INT.encode(buf, settings.waypointSpacing());
        ByteBufCodecs.VAR_INT.encode(buf, settings.minimumPointWaypoints());
        ByteBufCodecs.VAR_INT.encode(buf, settings.maxWaypointCandidates());
        ByteBufCodecs.VAR_INT.encode(buf, settings.safeScanUp());
        ByteBufCodecs.VAR_INT.encode(buf, settings.safeScanDown());
        ByteBufCodecs.VAR_INT.encode(buf, settings.pathRetryLimit());
        ByteBufCodecs.VAR_INT.encode(buf, settings.repathIntervalTicks());
        ByteBufCodecs.DOUBLE.encode(buf, settings.arrivalDistance());
        ByteBufCodecs.VAR_INT.encode(buf, settings.routeRetryDelayTicks());
        ByteBufCodecs.VAR_INT.encode(buf, settings.pathFailureCooldownTicks());
        ByteBufCodecs.VAR_INT.encode(buf, settings.maxResumeDelayTicks());
        ByteBufCodecs.VAR_LONG.encode(buf, settings.revision());
    }

    private static PatrolSettings decodeSettings(ByteBuf buf) {
        return new PatrolSettings(
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.DOUBLE.decode(buf),
                ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_LONG.decode(buf)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void clientHandle(S2C_PatrolSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.i113w.better_mine_team.client.rts.ClientPatrolSettings.apply(payload.settings()));
    }
}
