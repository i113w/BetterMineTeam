package com.i113w.better_mine_team.client.rts;

import com.i113w.camera_lib.camera.RTSCameraController;

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

    public static void enterCameraWithLastStyle() {
        RTSCameraController controller = RTSCameraController.get();
        if (!controller.isActive()) {
            controller.enterMode(controller.getCameraStyle());
        }
    }

    public static void exitCamera() {
        RTSCameraController controller = RTSCameraController.get();
        if (controller.isActive()) {
            controller.exitMode();
        }
    }

    public static void cycleCameraStyle() {
        RTSCameraController controller = RTSCameraController.get();
        if (controller.isActive()) {
            controller.toggleCameraStyle();
        }
    }
}