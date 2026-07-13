package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.common.network.rts.S2C_PatrolSyncPayload;
import com.i113w.better_mine_team.common.rts.data.PatrolMode;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ClientPatrolManager {
    private static final Map<Integer, ClientPatrolTask> TASKS = new HashMap<>();

    private ClientPatrolManager() {}

    public static void applySync(S2C_PatrolSyncPayload payload) {
        if (!payload.enabled()) {
            TASKS.remove(payload.entityId());
            return;
        }
        TASKS.put(payload.entityId(), new ClientPatrolTask(
                payload.entityId(), payload.dimensionId(), payload.mode(), payload.center(),
                payload.radius(), payload.minCorner(), payload.maxCorner()
        ));
    }

    public static Optional<ClientPatrolTask> get(int entityId) {
        return Optional.ofNullable(TASKS.get(entityId));
    }

    public static Collection<ClientPatrolTask> allTasks() { return TASKS.values(); }
    public static void clear() { TASKS.clear(); }

    public record ClientPatrolTask(
            int entityId,
            String dimensionId,
            PatrolMode mode,
            BlockPos center,
            int radius,
            BlockPos minCorner,
            BlockPos maxCorner
    ) {
        public boolean contains(BlockPos pos) {
            if (mode == PatrolMode.AREA) {
                return pos.getX() >= minCorner.getX() && pos.getX() <= maxCorner.getX()
                        && pos.getZ() >= minCorner.getZ() && pos.getZ() <= maxCorner.getZ();
            }
            int dx = pos.getX() - center.getX();
            int dz = pos.getZ() - center.getZ();
            int r = Math.max(1, radius);
            return dx * dx + dz * dz <= r * r;
        }
    }
}
