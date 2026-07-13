package com.i113w.better_mine_team.common.network.rts;

import com.i113w.better_mine_team.client.rts.ClientPatrolSettings;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.config.PatrolSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2C_PatrolSettingsPacket {
    private final PatrolSettings settings;

    public S2C_PatrolSettingsPacket(PatrolSettings settings) {
        this.settings = settings;
    }

    public static S2C_PatrolSettingsPacket current() {
        return new S2C_PatrolSettingsPacket(BMTConfig.getPatrolSettings());
    }

    public static void encode(S2C_PatrolSettingsPacket packet, FriendlyByteBuf buf) {
        PatrolSettings s = packet.settings;
        buf.writeBoolean(s.enabled());
        buf.writeVarInt(s.pointRadius());
        buf.writeVarInt(s.minAreaSize());
        buf.writeVarInt(s.maxAreaSize());
        buf.writeDouble(s.maxCommandDistance());
        buf.writeDouble(s.movementSpeed());
        buf.writeVarInt(s.waypointSpacing());
        buf.writeVarInt(s.minimumPointWaypoints());
        buf.writeVarInt(s.maxWaypointCandidates());
        buf.writeVarInt(s.safeScanUp());
        buf.writeVarInt(s.safeScanDown());
        buf.writeVarInt(s.pathRetryLimit());
        buf.writeVarInt(s.repathIntervalTicks());
        buf.writeDouble(s.arrivalDistance());
        buf.writeVarInt(s.routeRetryDelayTicks());
        buf.writeVarInt(s.pathFailureCooldownTicks());
        buf.writeVarInt(s.maxResumeDelayTicks());
        buf.writeVarLong(s.revision());
    }

    public static S2C_PatrolSettingsPacket decode(FriendlyByteBuf buf) {
        return new S2C_PatrolSettingsPacket(new PatrolSettings(
                buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarLong()
        ));
    }

    public static void handle(S2C_PatrolSettingsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientPatrolSettings.apply(packet.settings));
        context.setPacketHandled(true);
    }
}
