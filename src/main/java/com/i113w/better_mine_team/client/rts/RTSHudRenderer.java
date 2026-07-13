package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTClientConfig;
import com.i113w.better_mine_team.common.config.PatrolSettings;
import com.i113w.camera_lib.api.CameraLibAPI;
import com.i113w.camera_lib.camera.RTSCameraController;
import com.i113w.better_mine_team.common.rts.ai.PatrolTargeting;
import com.i113w.better_mine_team.common.rts.data.PatrolMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RTSHudRenderer {

    private static final int CAMERA_STYLE_BUTTON_WIDTH = 128;
    private static final int CAMERA_STYLE_BUTTON_HEIGHT = 20;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!RTSCameraController.get().isActive()
                || ClientRTSStateManager.get().getMode() != ClientRTSStateManager.RTSMode.PATROL
                || mc.options.hideGui || mc.level == null
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        String dimension = mc.level.dimension().location().toString();
        BMTClientConfig.PatrolVisualSettings visual = BMTClientConfig.getPatrolVisualSettings();
        PatrolSettings rules = ClientPatrolSettings.get();

        if (visual.showPatrolMarkers()) {
            for (ClientPatrolManager.ClientPatrolTask task : ClientPatrolManager.allTasks()) {
                if (!dimension.equals(task.dimensionId())) continue;
                Entity entity = mc.level.getEntity(task.entityId());
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    renderPatrolMarker(poseStack, lines, cameraPos, living, visual);
                }
            }
        }

        if (visual.showSelectedPatrolBounds()) {
            for (int selectedId : CameraLibAPI.get().getSelectedEntities()) {
                ClientPatrolManager.get(selectedId)
                        .filter(task -> dimension.equals(task.dimensionId()))
                        .ifPresent(task -> renderPatrolGroundBox(poseStack, lines, cameraPos, task,
                                visual.assignedBoundsColor(), visual));
            }
        }

        BlockPos dragStart = RTSLibEventHandler.getPatrolDragStart();
        BlockPos dragCurrent = RTSLibEventHandler.getPatrolDragCurrent();
        if (visual.showPatrolDragPreview() && RTSLibEventHandler.isPatrolRightDragging()
                && dragStart != null && dragCurrent != null) {
            BlockPos min = PatrolTargeting.normalizedMin(dragStart, dragCurrent);
            BlockPos max = PatrolTargeting.normalizedMax(dragStart, dragCurrent);
            int y = dragStart.getY();
            BlockPos flatMin = new BlockPos(min.getX(), y, min.getZ());
            BlockPos flatMax = new BlockPos(max.getX(), y, max.getZ());
            BlockPos minMax = new BlockPos(flatMin.getX(), y, flatMax.getZ());
            BlockPos maxMin = new BlockPos(flatMax.getX(), y, flatMin.getZ());
            boolean valid = PatrolTargeting.isAreaSizeValid(flatMin, flatMax, rules)
                    && mc.player != null
                    && isWithinPatrolCommandDistance(mc.player.position(), rules,
                    flatMin, flatMax, minMax, maxMin);
            renderGroundBox(poseStack, lines, cameraPos, flatMin, flatMax,
                    valid ? visual.validPreviewColor() : visual.invalidPreviewColor(), visual);
        }

        buffer.endBatch(RenderType.lines());
    }

    private static void renderPatrolMarker(PoseStack poseStack, VertexConsumer lines, Vec3 cameraPos,
                                           LivingEntity entity, BMTClientConfig.PatrolVisualSettings visual) {
        double half = Math.max(visual.markerMinHalfSize(), entity.getBbWidth() * visual.markerWidthMultiplier());
        double y = entity.getBoundingBox().maxY + visual.markerVerticalOffset();
        BMTClientConfig.PatrolColor color = visual.patrolMarkerColor();
        renderLineBox(poseStack, lines, cameraPos, new AABB(entity.getX() - half, y, entity.getZ() - half,
                entity.getX() + half, y + visual.markerHeight(), entity.getZ() + half),
                color.red(), color.green(), color.blue(), color.alpha());
    }

    private static void renderPatrolGroundBox(PoseStack poseStack, VertexConsumer lines, Vec3 cameraPos,
                                              ClientPatrolManager.ClientPatrolTask task,
                                              BMTClientConfig.PatrolColor color,
                                              BMTClientConfig.PatrolVisualSettings visual) {
        if (task.mode() == PatrolMode.AREA) {
            renderGroundBox(poseStack, lines, cameraPos, task.minCorner(), task.maxCorner(), color, visual);
        } else {
            int radius = Math.max(1, task.radius());
            BlockPos center = task.center();
            renderGroundBox(poseStack, lines, cameraPos,
                    new BlockPos(center.getX() - radius, center.getY(), center.getZ() - radius),
                    new BlockPos(center.getX() + radius, center.getY(), center.getZ() + radius), color, visual);
        }
    }

    private static void renderGroundBox(PoseStack poseStack, VertexConsumer lines, Vec3 cameraPos,
                                        BlockPos min, BlockPos max, BMTClientConfig.PatrolColor color,
                                        BMTClientConfig.PatrolVisualSettings visual) {
        double y = min.getY() + visual.groundBoxVerticalOffset();
        renderLineBox(poseStack, lines, cameraPos,
                new AABB(min.getX(), y, min.getZ(), max.getX() + 1.0D,
                        y + visual.groundBoxHeight(), max.getZ() + 1.0D),
                color.red(), color.green(), color.blue(), color.alpha());
    }

    private static void renderLineBox(PoseStack poseStack, VertexConsumer lines, Vec3 cameraPos,
                                      AABB box, float r, float g, float b, float a) {
        LevelRenderer.renderLineBox(poseStack, lines, box.move(-cameraPos.x, -cameraPos.y, -cameraPos.z),
                r, g, b, a);
    }

    private static boolean isWithinPatrolCommandDistance(Vec3 origin, PatrolSettings settings,
                                                          BlockPos... positions) {
        for (BlockPos pos : positions) {
            if (origin.distanceToSqr(Vec3.atCenterOf(pos)) > settings.maxCommandDistanceSqr()) return false;
        }
        return true;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!RTSCameraController.get().isActive() || mc.options.hideGui) return;

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
