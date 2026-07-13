package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.common.config.PatrolSettings;

public final class ClientPatrolSettings {
    private static volatile PatrolSettings settings = PatrolSettings.DEFAULT;

    private ClientPatrolSettings() {}

    public static PatrolSettings get() { return settings; }

    public static void apply(PatrolSettings syncedSettings) {
        settings = syncedSettings == null ? PatrolSettings.DEFAULT : syncedSettings;
        if (!settings.enabled() && BmtRTSManager.getMode() == BmtRTSManager.RTSMode.PATROL) {
            BmtRTSManager.setMode(BmtRTSManager.RTSMode.CONTROL);
        }
    }

    public static void reset() { settings = PatrolSettings.DEFAULT; }
}
