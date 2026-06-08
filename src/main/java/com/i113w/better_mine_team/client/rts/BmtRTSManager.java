package com.i113w.better_mine_team.client.rts;

public class BmtRTSManager {
    public enum RTSMode {
        CONTROL,
        RECRUIT
    }

    private static RTSMode currentMode = RTSMode.CONTROL;

    public static void setMode(RTSMode mode) {
        currentMode = mode;
    }

    public static RTSMode getMode() {
        return currentMode;
    }
}