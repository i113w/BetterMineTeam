package com.i113w.better_mine_team.client.rts;

import com.i113w.camera_lib.camera.RTSCameraController;

public class ClientRTSStateManager {
    public enum RTSMode {
        CONTROL,
        PATROL,
        RECRUIT
    }

    private static final ClientRTSStateManager INSTANCE = new ClientRTSStateManager();
    private static final RTSCameraController.CameraStyle DEFAULT_CAMERA_STYLE =
            RTSCameraController.CameraStyle.ORTHOGRAPHIC;

    private RTSMode currentMode = RTSMode.CONTROL;
    private RTSCameraController.CameraStyle lastCameraStyle = DEFAULT_CAMERA_STYLE;
    private int selectionRevision = 0;

    private ClientRTSStateManager() {}

    public static ClientRTSStateManager get() {
        return INSTANCE;
    }

    public RTSMode getMode() {
        return currentMode;
    }

    public void setMode(RTSMode mode) {
        if (currentMode == RTSMode.PATROL && mode != RTSMode.PATROL) {
            RTSLibEventHandler.resetPatrolDragState();
        }
        this.currentMode = mode;
    }

    public int getNextRevision() {
        return ++selectionRevision;
    }

    public int getRevision() {
        return selectionRevision;
    }

    public void enterCameraWithLastStyle() {
        RTSCameraController controller = RTSCameraController.get();
        if (!controller.isActive()) {
            controller.enterMode(lastCameraStyle);
        }
    }

    public void exitCamera() {
        RTSLibEventHandler.resetPatrolDragState();
        RTSCameraController controller = RTSCameraController.get();
        if (controller.isActive()) {
            lastCameraStyle = controller.getCameraStyle();
            controller.exitMode();
        }
    }

    public void cycleCameraStyle() {
        RTSCameraController controller = RTSCameraController.get();
        if (controller.isActive()) {
            controller.toggleCameraStyle();
            lastCameraStyle = controller.getCameraStyle();
        }
    }

    public void reset() {
        RTSLibEventHandler.resetPatrolDragState();
        this.currentMode = RTSMode.CONTROL;
        this.lastCameraStyle = DEFAULT_CAMERA_STYLE;
        this.selectionRevision = 0;
    }
}
