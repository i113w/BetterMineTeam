package com.i113w.better_mine_team.client.rts;

import com.i113w.camera_lib.camera.RTSCameraController;

public class BmtRTSManager {
    public enum RTSMode {
        CONTROL,
        RECRUIT
    }

    private static final RTSCameraController.CameraStyle DEFAULT_CAMERA_STYLE =
            RTSCameraController.CameraStyle.ORTHOGRAPHIC;

    private static RTSMode currentMode = RTSMode.CONTROL;
    private static RTSCameraController.CameraStyle lastCameraStyle = DEFAULT_CAMERA_STYLE;

    public static void setMode(RTSMode mode) {
        currentMode = mode;
    }

    public static RTSMode getMode() {
        return currentMode;
    }

    public static void enterCameraWithLastStyle() {
        RTSCameraController controller = RTSCameraController.get();
        if (!controller.isActive()) {
            controller.enterMode(lastCameraStyle);
        }
    }

    public static void exitCamera() {
        RTSCameraController controller = RTSCameraController.get();
        if (controller.isActive()) {
            lastCameraStyle = controller.getCameraStyle();
            controller.exitMode();
        }
    }

    public static void cycleCameraStyle() {
        RTSCameraController controller = RTSCameraController.get();
        if (controller.isActive()) {
            controller.toggleCameraStyle();
            lastCameraStyle = controller.getCameraStyle();
        }
    }

    public static void reset() {
        currentMode = RTSMode.CONTROL;
        lastCameraStyle = DEFAULT_CAMERA_STYLE;
    }
}
