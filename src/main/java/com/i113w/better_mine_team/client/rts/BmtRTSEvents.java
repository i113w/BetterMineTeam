package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.ModKeyMappings;
import com.i113w.better_mine_team.client.gui.screen.TeamManagementScreen;
import com.i113w.better_mine_team.client.manager.ClientSelectionManager;
import com.i113w.better_mine_team.common.config.BMTClientConfig;
import com.i113w.better_mine_team.common.config.PatrolSettings;
import com.i113w.better_mine_team.common.network.data.PatrolAction;
import com.i113w.better_mine_team.common.network.data.CommandTarget;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.network.rts.C2S_IssueCommandPayload;
import com.i113w.better_mine_team.common.network.rts.C2S_PatrolCommandPayload;
import com.i113w.better_mine_team.common.network.rts.C2S_SelectionSyncPayload;
import com.i113w.better_mine_team.common.rts.ai.PatrolTargeting;
import com.i113w.better_mine_team.common.rts.data.PatrolMode;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.camera_lib.api.CameraLibAPI;
import com.i113w.camera_lib.api.event.RTSBoxSelectEvent;
import com.i113w.camera_lib.api.event.RTSRightClickEvent;
import com.i113w.camera_lib.camera.RTSCameraController;
import com.i113w.camera_lib.math.MouseRayCaster;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class BmtRTSEvents {

    private static final int CAMERA_STYLE_BUTTON_WIDTH = 128;
    private static final int CAMERA_STYLE_BUTTON_HEIGHT = 20;
    private static BlockPos patrolDragStart = null;
    private static BlockPos patrolDragCurrent = null;
    private static boolean patrolRightDragging = false;

    // ==========================================
    // 1. 库事件监听 (选区与指令)
    // ==========================================

    @SubscribeEvent
    public static void onBoxSelect(RTSBoxSelectEvent event) {
        if (BmtRTSManager.getMode() == BmtRTSManager.RTSMode.PATROL) {
            handlePatrolSelection(event);
            return;
        }

        ClientSelectionManager.clear();
        for (Entity e : event.getCandidates()) {
            ClientSelectionManager.select(e.getId());
        }
        ClientSelectionManager.syncToLib();
        syncSelectionToServer();
    }

    @SubscribeEvent
    public static void onRightClick(RTSRightClickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        BmtRTSManager.RTSMode mode = BmtRTSManager.getMode();
        if (mode == BmtRTSManager.RTSMode.PATROL) {
            handlePatrolRightClick(event);
            return;
        }

        if (ClientSelectionManager.getSelectedIds().isEmpty()) return;

        if (mode == BmtRTSManager.RTSMode.RECRUIT) {
            int revision = ClientSelectionManager.getRevision();
            PacketDistributor.sendToServer(new C2S_IssueCommandPayload(
                    CommandType.RECRUIT,
                    CommandTarget.EMPTY,
                    Collections.emptyList(),
                    revision
            ));
            ClientSelectionManager.clear();
            ClientSelectionManager.syncToLib();
            return;
        }

        if (event.isDrag()) {
            List<Integer> targetIds = new ArrayList<>();
            for (Entity target : event.getDragTargets()) {
                if (target instanceof LivingEntity living && target.isAlive() && target != mc.player) {
                    if (!TeamManager.isAlly(mc.player, living)) {
                        targetIds.add(target.getId());
                    }
                }
            }

            if (targetIds.isEmpty()) return;

            int primaryId = targetIds.get(0);
            targetIds.remove(0);
            CommandTarget target = new CommandTarget(Vec3.ZERO, primaryId, BlockPos.ZERO);
            int revision = ClientSelectionManager.getRevision();
            PacketDistributor.sendToServer(new C2S_IssueCommandPayload(CommandType.ATTACK, target, targetIds, revision));

        } else {
            HitResult hit = event.getSingleHitResult();
            if (hit == null) return;

            CommandType type = CommandType.MOVE;
            CommandTarget target = CommandTarget.EMPTY;

            if (hit.getType() == HitResult.Type.ENTITY) {
                Entity entity = ((EntityHitResult) hit).getEntity();
                if (!TeamManager.isAlly(mc.player, entity instanceof LivingEntity l ? l : null)) {
                    type = CommandType.ATTACK;
                } else {
                    type = CommandType.MOVE;
                }
                target = new CommandTarget(entity.position(), entity.getId(), entity.blockPosition());
            } else if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                type = CommandType.MOVE;
                target = new CommandTarget(hit.getLocation(), -1, pos);
            } else {
                return;
            }

            int revision = ClientSelectionManager.getRevision();
            PacketDistributor.sendToServer(new C2S_IssueCommandPayload(type, target, Collections.emptyList(), revision));
        }
    }

    private static void handlePatrolSelection(RTSBoxSelectEvent event) {
        if (!ClientPatrolSettings.get().enabled()) return;
        List<Entity> candidates = event.getCandidates().stream()
                .filter(BmtRTSEvents::isPatrolSelectable)
                .toList();

        if (Screen.hasShiftDown() && candidates.size() == 1) {
            int id = candidates.get(0).getId();
            if (ClientSelectionManager.getSelectedIds().contains(id)) {
                ClientSelectionManager.deselect(id);
            } else {
                ClientSelectionManager.select(id);
            }
        } else {
            ClientSelectionManager.clear();
            for (Entity entity : candidates) {
                ClientSelectionManager.select(entity.getId());
            }
        }

        ClientSelectionManager.syncToLib();
        syncSelectionToServer();
    }

    private static void handlePatrolRightClick(RTSRightClickEvent event) {
        if (!ClientPatrolSettings.get().enabled()) {
            resetPatrolDragState();
            return;
        }
        if (event.isDrag()) {
            try {
                BlockPos end = getCurrentPatrolGroundPos();
                if (end != null) {
                    patrolDragCurrent = end;
                }

                if (patrolRightDragging
                        && patrolDragStart != null
                        && patrolDragCurrent != null
                        && !patrolDragStart.equals(patrolDragCurrent)
                        && !ClientSelectionManager.getSelectedIds().isEmpty()) {
                    sendPatrolArea(
                            new ArrayList<>(ClientSelectionManager.getSelectedIds()),
                            patrolDragStart,
                            patrolDragCurrent
                    );
                }
            } finally {
                resetPatrolDragState();
            }
            return;
        }

        resetPatrolDragState();
        if (ClientSelectionManager.getSelectedIds().isEmpty()) return;

        HitResult hit = event.getSingleHitResult();
        if (!(hit instanceof BlockHitResult blockHit)) return;

        BlockPos target = getStandPos(blockHit);
        List<Integer> selected = new ArrayList<>(ClientSelectionManager.getSelectedIds());
        if (selected.isEmpty()) return;

        List<Integer> cancelIds = selected.stream()
                .filter(id -> ClientPatrolManager.get(id).map(task -> task.contains(target)).orElse(false))
                .toList();

        if (!cancelIds.isEmpty()) {
            sendPatrolCancel(cancelIds);
            return;
        }

        sendPatrolPoint(selected, target);
    }

    public static void syncSelectionToServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Set<Integer> selectedIds = ClientSelectionManager.getSelectedIds();
        List<Integer> validIds = new ArrayList<>();
        for (int id : selectedIds) {
            Entity entity = mc.level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                validIds.add(id);
            }
        }

        int revision = ClientSelectionManager.getRevision();
        PacketDistributor.sendToServer(new C2S_SelectionSyncPayload(validIds, revision));
    }

    private static boolean isPatrolSelectable(Entity entity) {
        if (!ClientPatrolSettings.get().enabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (!(entity instanceof PathfinderMob mob) || !mob.isAlive()) return false;
        if (entity == mc.player) return false;
        if (TeamManager.isAlly(mc.player, mob)) return true;
        return entity instanceof TamableAnimal tamable && tamable.isOwnedBy(mc.player);
    }

    private static void sendPatrolPoint(List<Integer> entityIds, BlockPos target) {
        Minecraft mc = Minecraft.getInstance();
        PatrolSettings settings = ClientPatrolSettings.get();
        if (mc.level == null || mc.player == null || !settings.enabled()) return;
        if (!isWithinPatrolCommandDistance(mc.player.position(), settings, target)) {
            mc.player.displayClientMessage(
                    Component.translatable("better_mine_team.msg.patrol_too_far").withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }
        PacketDistributor.sendToServer(new C2S_PatrolCommandPayload(
                PatrolAction.ASSIGN_POINT,
                entityIds,
                mc.level.dimension().location().toString(),
                target,
                settings.pointRadius(),
                target,
                target
        ));
    }

    private static void sendPatrolArea(List<Integer> entityIds, BlockPos start, BlockPos end) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        PatrolSettings settings = ClientPatrolSettings.get();
        if (!settings.enabled()) return;

        BlockPos rawMin = PatrolTargeting.normalizedMin(start, end);
        BlockPos rawMax = PatrolTargeting.normalizedMax(start, end);
        int centerY = start.getY();
        BlockPos min = new BlockPos(rawMin.getX(), centerY, rawMin.getZ());
        BlockPos max = new BlockPos(rawMax.getX(), centerY, rawMax.getZ());

        if (!PatrolTargeting.isAreaSizeValid(min, max, settings)) {
            mc.player.displayClientMessage(
                    Component.translatable(
                            "better_mine_team.msg.patrol_invalid_area",
                            PatrolTargeting.areaWidth(min, max),
                            PatrolTargeting.areaDepth(min, max),
                            settings.minAreaSize(),
                            settings.maxAreaSize()
                    ).withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        BlockPos minMaxCorner = new BlockPos(min.getX(), centerY, max.getZ());
        BlockPos maxMinCorner = new BlockPos(max.getX(), centerY, min.getZ());
        if (!isWithinPatrolCommandDistance(
                mc.player.position(), settings, min, max, minMaxCorner, maxMinCorner)) {
            mc.player.displayClientMessage(
                    Component.translatable("better_mine_team.msg.patrol_too_far").withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        BlockPos center = new BlockPos(
                (min.getX() + max.getX()) / 2,
                centerY,
                (min.getZ() + max.getZ()) / 2
        );
        PacketDistributor.sendToServer(new C2S_PatrolCommandPayload(
                PatrolAction.ASSIGN_AREA,
                entityIds,
                mc.level.dimension().location().toString(),
                center,
                0,
                min,
                max
        ));
    }

    private static void sendPatrolCancel(List<Integer> entityIds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        PacketDistributor.sendToServer(new C2S_PatrolCommandPayload(
                PatrolAction.CANCEL,
                entityIds,
                mc.level.dimension().location().toString(),
                BlockPos.ZERO,
                0,
                BlockPos.ZERO,
                BlockPos.ZERO
        ));
    }

    private static BlockPos getStandPos(BlockHitResult hit) {
        return hit.getBlockPos().relative(hit.getDirection());
    }

    private static BlockPos getCurrentPatrolGroundPos() {
        Minecraft mc = Minecraft.getInstance();
        PatrolSettings settings = ClientPatrolSettings.get();
        HitResult hit = MouseRayCaster.pickFromMouse(
                mc.mouseHandler.xpos(),
                mc.mouseHandler.ypos(),
                settings.maxCommandDistance()
        );
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return getStandPos(blockHit);
        }
        return null;
    }

    private static boolean isWithinPatrolCommandDistance(Vec3 origin, PatrolSettings settings,
                                                          BlockPos... positions) {
        double maxDistanceSqr = settings.maxCommandDistanceSqr();
        for (BlockPos pos : positions) {
            if (origin.distanceToSqr(Vec3.atCenterOf(pos)) > maxDistanceSqr) return false;
        }
        return true;
    }

    public static void resetPatrolDragState() {
        patrolDragStart = null;
        patrolDragCurrent = null;
        patrolRightDragging = false;
    }

    // 按键绘制

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        while (ModKeyMappings.OPEN_TEAM_MENU.consumeClick()) {
            if (RTSCameraController.get().isActive()) continue;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null && mc.screen == null) {
                mc.setScreen(new TeamManagementScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // 只有在 RTS 摄像机激活时才渲染 UI
        if (!RTSCameraController.get().isActive() || mc.options.hideGui) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // 顶部退出按钮
        int btnW = 80;
        int btnH = 20;
        int btnX = width / 2 - btnW / 2;
        int btnY = 10;
        event.getGuiGraphics().fill(btnX, btnY, btnX + btnW, btnY + btnH, 0x80000000);
        event.getGuiGraphics().drawCenteredString(mc.font, "Exit RTS [ESC]", width / 2, btnY + 6, 0xFFFFFF);

        // 左下角相机模式切换按钮
        int camBtnW = CAMERA_STYLE_BUTTON_WIDTH;
        int camBtnH = CAMERA_STYLE_BUTTON_HEIGHT;
        int camBtnX = 10;
        int camBtnY = height - camBtnH - 10;
        event.getGuiGraphics().fill(camBtnX, camBtnY, camBtnX + camBtnW, camBtnY + camBtnH, 0x80000000);

        String styleText = getCameraStyleText();
        event.getGuiGraphics().drawCenteredString(mc.font, styleText, camBtnX + camBtnW / 2, camBtnY + 6, 0xFFFFFF);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!RTSCameraController.get().isActive()
                || BmtRTSManager.getMode() != BmtRTSManager.RTSMode.PATROL
                || !ClientPatrolSettings.get().enabled()) {
            resetPatrolDragState();
            return;
        }
        if (!patrolRightDragging) {
            return;
        }
        BlockPos current = getCurrentPatrolGroundPos();
        if (current != null) {
            patrolDragCurrent = current;
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!RTSCameraController.get().isActive()
                || BmtRTSManager.getMode() != BmtRTSManager.RTSMode.PATROL
                || !ClientPatrolSettings.get().enabled()
                || mc.options.hideGui
                || mc.level == null
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        PatrolSettings settings = ClientPatrolSettings.get();
        BMTClientConfig.PatrolVisualSettings visual = BMTClientConfig.getPatrolVisualSettings();
        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());

        String dimension = mc.level.dimension().location().toString();
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
            for (int selectedId : ClientSelectionManager.getSelectedIds()) {
                ClientPatrolManager.get(selectedId)
                        .filter(task -> dimension.equals(task.dimensionId()))
                        .ifPresent(task -> renderPatrolGroundBox(
                                poseStack, lines, cameraPos, task, visual.assignedBoundsColor(), visual));
            }
        }

        if (visual.showPatrolDragPreview()
                && patrolRightDragging && patrolDragStart != null && patrolDragCurrent != null) {
            BlockPos min = PatrolTargeting.normalizedMin(patrolDragStart, patrolDragCurrent);
            BlockPos max = PatrolTargeting.normalizedMax(patrolDragStart, patrolDragCurrent);
            int y = patrolDragStart.getY();
            BlockPos flatMin = new BlockPos(min.getX(), y, min.getZ());
            BlockPos flatMax = new BlockPos(max.getX(), y, max.getZ());
            BlockPos minMaxCorner = new BlockPos(flatMin.getX(), y, flatMax.getZ());
            BlockPos maxMinCorner = new BlockPos(flatMax.getX(), y, flatMin.getZ());
            boolean valid = PatrolTargeting.isAreaSizeValid(flatMin, flatMax, settings)
                    && mc.player != null
                    && isWithinPatrolCommandDistance(
                    mc.player.position(), settings, flatMin, flatMax, minMaxCorner, maxMinCorner);
            renderGroundBox(
                    poseStack,
                    lines,
                    cameraPos,
                    flatMin,
                    flatMax,
                    valid ? visual.validPreviewColor() : visual.invalidPreviewColor(),
                    visual
            );
        }

        buffer.endBatch(RenderType.lines());
    }

    private static void renderPatrolMarker(PoseStack poseStack, VertexConsumer lines, Vec3 cameraPos,
                                           LivingEntity entity, BMTClientConfig.PatrolVisualSettings visual) {
        double half = Math.max(visual.markerMinHalfSize(),
                entity.getBbWidth() * visual.markerWidthMultiplier());
        double y = entity.getBoundingBox().maxY + visual.markerVerticalOffset();
        AABB box = new AABB(
                entity.getX() - half, y, entity.getZ() - half,
                entity.getX() + half, y + visual.markerHeight(), entity.getZ() + half
        );
        renderLineBox(poseStack, lines, cameraPos, box, visual.patrolMarkerColor());
    }

    private static void renderPatrolGroundBox(PoseStack poseStack, VertexConsumer lines, Vec3 cameraPos,
                                              ClientPatrolManager.ClientPatrolTask task,
                                              BMTClientConfig.PatrolColor color,
                                              BMTClientConfig.PatrolVisualSettings visual) {
        if (task.mode() == PatrolMode.AREA) {
            renderGroundBox(poseStack, lines, cameraPos, task.minCorner(), task.maxCorner(), color, visual);
            return;
        }

        int radius = Math.max(1, task.radius());
        BlockPos center = task.center();
        BlockPos min = new BlockPos(center.getX() - radius, center.getY(), center.getZ() - radius);
        BlockPos max = new BlockPos(center.getX() + radius, center.getY(), center.getZ() + radius);
        renderGroundBox(poseStack, lines, cameraPos, min, max, color, visual);
    }

    private static void renderGroundBox(PoseStack poseStack, VertexConsumer lines, Vec3 cameraPos,
                                        BlockPos min, BlockPos max,
                                        BMTClientConfig.PatrolColor color,
                                        BMTClientConfig.PatrolVisualSettings visual) {
        double y = min.getY() + visual.groundBoxVerticalOffset();
        AABB box = new AABB(
                min.getX(), y, min.getZ(),
                max.getX() + 1.0D, y + visual.groundBoxHeight(), max.getZ() + 1.0D
        );
        renderLineBox(poseStack, lines, cameraPos, box, color);
    }

    private static void renderLineBox(PoseStack poseStack, VertexConsumer lines, Vec3 cameraPos,
                                      AABB box, BMTClientConfig.PatrolColor color) {
        LevelRenderer.renderLineBox(poseStack, lines,
                box.move(-cameraPos.x, -cameraPos.y, -cameraPos.z),
                color.red(), color.green(), color.blue(), color.alpha());
    }

    private static String getCameraStyleText() {
        return switch (RTSCameraController.get().getCameraStyle()) {
            case RTS -> "Camera: RTS";
            case FREE -> "Camera: Free";
            case ORTHOGRAPHIC -> "Camera: Orthographic";
        };
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (!RTSCameraController.get().isActive()) return;

        // 拦截左键点击按下的瞬间，用于判断是否点到了我们绘制的 UI 按钮
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS) {
            Minecraft mc = Minecraft.getInstance();
            double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();

            // 顶部退出按钮判定
            int btnW = 80, btnH = 20, btnX = width / 2 - btnW / 2, btnY = 10;
            if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
                // 退出摄像机
                BmtRTSManager.exitCamera();
                ClientSelectionManager.clear();
                ClientSelectionManager.syncToLib();
                syncSelectionToServer();
                event.setCanceled(true); // 拦截事件，防止触发框选
                return;
            }

            // 切换相机风格按钮判定
            int camBtnW = CAMERA_STYLE_BUTTON_WIDTH, camBtnH = CAMERA_STYLE_BUTTON_HEIGHT, camBtnX = 10, camBtnY = height - camBtnH - 10;
            if (mx >= camBtnX && mx <= camBtnX + camBtnW && my >= camBtnY && my <= camBtnY + camBtnH) {
                // 切换摄像机风格
                BmtRTSManager.cycleCameraStyle();
                event.setCanceled(true); // 拦截事件
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPatrolMouseClick(InputEvent.MouseButton.Pre event) {
        if (!RTSCameraController.get().isActive()
                || BmtRTSManager.getMode() != BmtRTSManager.RTSMode.PATROL
                || !ClientPatrolSettings.get().enabled()
                || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }

        if (event.getAction() == GLFW.GLFW_PRESS) {
            patrolDragStart = getCurrentPatrolGroundPos();
            patrolDragCurrent = patrolDragStart;
            patrolRightDragging = patrolDragStart != null;
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            // Camera lib posts RTSRightClickEvent and ends its own drag before this LOWEST listener.
            resetPatrolDragState();
        }
    }
}
