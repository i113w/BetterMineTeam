package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.camera_lib.api.CameraLibAPI;
import com.i113w.camera_lib.camera.RTSCameraController;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RTSHudRenderer {

    private static final int CAMERA_STYLE_BUTTON_WIDTH = 128;
    private static final int CAMERA_STYLE_BUTTON_HEIGHT = 20;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!RTSCameraController.get().isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // 1. 绘制 Exit 按钮
        int exitBtnW = 80;
        int exitBtnH = 20;
        int exitBtnX = width / 2 - exitBtnW / 2;
        int exitBtnY = 10;
        event.getGuiGraphics().fill(exitBtnX, exitBtnY, exitBtnX + exitBtnW, exitBtnY + exitBtnH, 0x80000000);
        event.getGuiGraphics().drawCenteredString(mc.font, "Exit RTS [ESC]", width / 2, exitBtnY + 6, 0xFFFFFF);

        // 2. 动态获取当前相机风格并绘制 Camera 切换按钮
        String cameraText = getCameraStyleText();

        int camBtnW = CAMERA_STYLE_BUTTON_WIDTH;
        int camBtnH = CAMERA_STYLE_BUTTON_HEIGHT;
        int camBtnX = 10;
        int camBtnY = height - camBtnH - 10;
        event.getGuiGraphics().fill(camBtnX, camBtnY, camBtnX + camBtnW, camBtnY + camBtnH, 0x80000000);
        event.getGuiGraphics().drawCenteredString(mc.font, cameraText, camBtnX + camBtnW / 2, camBtnY + 6, 0xFFFFFF);

        // 3. 绘制当前的招募/指挥模式指示器
        // ClientRTSStateManager.RTSMode mode = ClientRTSStateManager.get().getMode();
        // String modeText = mode == ClientRTSStateManager.RTSMode.CONTROL ? "§aControl Mode" : "§eRecruit Mode";
        // event.getGuiGraphics().drawString(mc.font, modeText, 10, height - 20 - camBtnH - 5, 0xFFFFFF);
    }

    private static String getCameraStyleText() {
        return switch (RTSCameraController.get().getCameraStyle()) {
            case RTS -> "Camera: RTS";
            case FREE -> "Camera: Free";
            case ORTHOGRAPHIC -> "Camera: Orthographic";
        };
    }

    // 使用 HIGHEST 优先级，确保在 i113w_camera_lib 的鼠标拖拽判定之前先检测 UI 按钮
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (!RTSCameraController.get().isActive()) return;

        Minecraft mc = Minecraft.getInstance();

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS) {
            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();

            // 转换真实鼠标坐标到 GUI 缩放坐标
            double mx = mc.mouseHandler.xpos() * width / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * height / mc.getWindow().getScreenHeight();

            // 实时计算按钮坐标，避免渲染前点击导致坐标为0
            int exitBtnW = 80;
            int exitBtnH = 20;
            int exitBtnX = width / 2 - exitBtnW / 2;
            int exitBtnY = 10;

            int camBtnW = CAMERA_STYLE_BUTTON_WIDTH;
            int camBtnH = CAMERA_STYLE_BUTTON_HEIGHT;
            int camBtnX = 10;
            int camBtnY = height - camBtnH - 10;

            // 检测点击 Exit 按钮
            if (mx >= exitBtnX && mx <= exitBtnX + exitBtnW && my >= exitBtnY && my <= exitBtnY + exitBtnH) {
                ClientRTSStateManager.get().exitCamera();
                ClientRTSStateManager.get().reset();
                CameraLibAPI.get().clearSelection();
                event.setCanceled(true); // 阻断事件传递给 Lib，防止触发拖拽
            }
            // 检测点击 Camera 按钮
            else if (mx >= camBtnX && mx <= camBtnX + camBtnW && my >= camBtnY && my <= camBtnY + camBtnH) {
                ClientRTSStateManager.get().cycleCameraStyle();
                event.setCanceled(true); // 阻断事件传递给 Lib
            }
        }
    }
}
