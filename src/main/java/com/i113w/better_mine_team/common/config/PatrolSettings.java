package com.i113w.better_mine_team.common.config;

/** Immutable, normalized server-authoritative Patrol gameplay settings. */
public record PatrolSettings(
        boolean enabled,
        int pointRadius,
        int minAreaSize,
        int maxAreaSize,
        double maxCommandDistance,
        double movementSpeed,
        int waypointSpacing,
        int minimumPointWaypoints,
        int maxWaypointCandidates,
        int safeScanUp,
        int safeScanDown,
        int pathRetryLimit,
        int repathIntervalTicks,
        double arrivalDistance,
        int routeRetryDelayTicks,
        int pathFailureCooldownTicks,
        int maxResumeDelayTicks,
        double combatLeashMinPadding,
        double combatLeashScale,
        int combatLeashCheckIntervalTicks,
        long revision
) {
    public static final PatrolSettings DEFAULT = new PatrolSettings(
            true, 10, 4, 32, 1024.0D, 1.0D, 3, 8, 64,
            4, 8, 3, 20, 1.0D, 60, 40, 20, 4.0D, 0.5D, 5, 0L
    );

    public double maxCommandDistanceSqr() {
        return maxCommandDistance * maxCommandDistance;
    }

    public double arrivalDistanceSqr() {
        return arrivalDistance * arrivalDistance;
    }
}
