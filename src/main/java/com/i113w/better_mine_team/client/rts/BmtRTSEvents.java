package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.ModKeyMappings;
import com.i113w.better_mine_team.client.gui.screen.TeamManagementScreen;
import com.i113w.better_mine_team.client.manager.ClientSelectionManager;
import com.i113w.better_mine_team.common.config.BMTClientConfig;
import com.i113w.better_mine_team.common.config.PatrolSettings;
import com.i113w.better_mine_team.common.network.data.CommandTarget;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.network.data.PatrolAction;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class BmtRTSEvents {
    private static final int CAMERA_STYLE_BUTTON_WIDTH = 128;
    private static final int CAMERA_STYLE_BUTTON_HEIGHT = 20;
    private static final int RTS_HUD_BUTTON_BG = 0x80000000;
    private static final int RTS_HUD_TEXT = 0xFFFFFFFF;

    private static BlockPos patrolDragStart;
    private static BlockPos patrolDragCurrent;
    private static boolean patrolRightDragging;

    @SubscribeEvent
    public static void onBoxSelect(RTSBoxSelectEvent event) {
        if (BmtRTSManager.getMode() == BmtRTSManager.RTSMode.PATROL) {
            handlePatrolSelection(event);
            return;
        }
        ClientSelectionManager.clear();
        for (Entity entity : event.getCandidates()) ClientSelectionManager.select(entity.getId());
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
            ClientPacketDistributor.sendToServer(new C2S_IssueCommandPayload(
                    CommandType.RECRUIT, CommandTarget.EMPTY, Collections.emptyList(),
                    ClientSelectionManager.getRevision()));
            ClientSelectionManager.clear();
            ClientSelectionManager.syncToLib();
            return;
        }

        if (event.isDrag()) {
            List<Integer> targetIds = new ArrayList<>();
            for (Entity target : event.getDragTargets()) {
                if (target instanceof LivingEntity living && target.isAlive() && target != mc.player
                        && !TeamManager.isAlly(mc.player, living)) {
                    targetIds.add(target.getId());
                }
            }
            if (targetIds.isEmpty()) return;
            int primaryId = targetIds.remove(0);
            CommandTarget target = new CommandTarget(Vec3.ZERO, primaryId, BlockPos.ZERO);
            ClientPacketDistributor.sendToServer(new C2S_IssueCommandPayload(
                    CommandType.ATTACK, target, targetIds, ClientSelectionManager.getRevision()));
            return;
        }

        HitResult hit = event.getSingleHitResult();
        if (hit == null) return;
        CommandType type;
        CommandTarget target;
        if (hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            type = TeamManager.isAlly(mc.player, entity instanceof LivingEntity living ? living : null)
                    ? CommandType.MOVE : CommandType.ATTACK;
            target = new CommandTarget(entity.position(), entity.getId(), entity.blockPosition());
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            type = CommandType.MOVE;
            target = new CommandTarget(hit.getLocation(), -1, pos);
        } else {
            return;
        }
        ClientPacketDistributor.sendToServer(new C2S_IssueCommandPayload(
                type, target, Collections.emptyList(), ClientSelectionManager.getRevision()));
    }

    private static void handlePatrolSelection(RTSBoxSelectEvent event) {
        if (!ClientPatrolSettings.get().enabled()) return;
        List<Entity> candidates = event.getCandidates().stream()
                .filter(BmtRTSEvents::isPatrolSelectable)
                .toList();

        if (Minecraft.getInstance().options.keyShift.isDown() && candidates.size() == 1) {
            int id = candidates.getFirst().getId();
            if (ClientSelectionManager.getSelectedIds().contains(id)) ClientSelectionManager.deselect(id);
            else ClientSelectionManager.select(id);
        } else {
            ClientSelectionManager.clear();
            for (Entity entity : candidates) ClientSelectionManager.select(entity.getId());
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
                if (end != null) patrolDragCurrent = end;
                if (patrolRightDragging && patrolDragStart != null && patrolDragCurrent != null
                        && !patrolDragStart.equals(patrolDragCurrent)
                        && !ClientSelectionManager.getSelectedIds().isEmpty()) {
                    sendPatrolArea(new ArrayList<>(ClientSelectionManager.getSelectedIds()),
                            patrolDragStart, patrolDragCurrent);
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
        List<Integer> cancelIds = selected.stream()
                .filter(id -> ClientPatrolManager.get(id).map(task -> task.contains(target)).orElse(false))
                .toList();
        if (!cancelIds.isEmpty()) sendPatrolCancel(cancelIds);
        else sendPatrolPoint(selected, target);
    }

    public static void syncSelectionToServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Set<Integer> selectedIds = ClientSelectionManager.getSelectedIds();
        List<Integer> validIds = new ArrayList<>();
        for (int id : selectedIds) {
            Entity entity = mc.level.getEntity(id);
            if (entity != null && entity.isAlive()) validIds.add(id);
        }
        ClientPacketDistributor.sendToServer(
                new C2S_SelectionSyncPayload(validIds, ClientSelectionManager.getRevision()));
    }

    private static boolean isPatrolSelectable(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientPatrolSettings.get().enabled() || mc.player == null) return false;
        if (!(entity instanceof PathfinderMob mob) || !mob.isAlive() || entity == mc.player) return false;
        if (TeamManager.isAlly(mc.player, mob)) return true;
        return entity instanceof TamableAnimal tamable && tamable.isOwnedBy(mc.player);
    }

    private static void sendPatrolPoint(List<Integer> entityIds, BlockPos target) {
        Minecraft mc = Minecraft.getInstance();
        PatrolSettings settings = ClientPatrolSettings.get();
        if (mc.level == null || mc.player == null || !settings.enabled()) return;
        if (!isWithinPatrolCommandDistance(mc.player.position(), settings, target)) {
            mc.player.sendOverlayMessage(Component.translatable("better_mine_team.msg.patrol_too_far")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        ClientPacketDistributor.sendToServer(new C2S_PatrolCommandPayload(
                PatrolAction.ASSIGN_POINT, entityIds, mc.level.dimension().identifier().toString(),
                target, settings.pointRadius(), target, target));
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
            mc.player.sendOverlayMessage(Component.translatable(
                    "better_mine_team.msg.patrol_invalid_area",
                    PatrolTargeting.areaWidth(min, max), PatrolTargeting.areaDepth(min, max),
                    settings.minAreaSize(), settings.maxAreaSize()).withStyle(ChatFormatting.RED));
            return;
        }

        BlockPos minMaxCorner = new BlockPos(min.getX(), centerY, max.getZ());
        BlockPos maxMinCorner = new BlockPos(max.getX(), centerY, min.getZ());
        if (!isWithinPatrolCommandDistance(mc.player.position(), settings,
                min, max, minMaxCorner, maxMinCorner)) {
            mc.player.sendOverlayMessage(Component.translatable("better_mine_team.msg.patrol_too_far")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        BlockPos center = new BlockPos((min.getX() + max.getX()) / 2, centerY,
                (min.getZ() + max.getZ()) / 2);
        ClientPacketDistributor.sendToServer(new C2S_PatrolCommandPayload(
                PatrolAction.ASSIGN_AREA, entityIds, mc.level.dimension().identifier().toString(),
                center, 0, min, max));
    }

    private static void sendPatrolCancel(List<Integer> entityIds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ClientPacketDistributor.sendToServer(new C2S_PatrolCommandPayload(
                PatrolAction.CANCEL, entityIds, mc.level.dimension().identifier().toString(),
                BlockPos.ZERO, 0, BlockPos.ZERO, BlockPos.ZERO));
    }

    private static BlockPos getStandPos(BlockHitResult hit) {
        return hit.getBlockPos().relative(hit.getDirection());
    }

    private static BlockPos getCurrentPatrolGroundPos() {
        Minecraft mc = Minecraft.getInstance();
        PatrolSettings settings = ClientPatrolSettings.get();
        HitResult hit = MouseRayCaster.pickFromMouse(
                mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), settings.maxCommandDistance());
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return getStandPos(blockHit);
        }
        return null;
    }

    private static boolean isWithinPatrolCommandDistance(Vec3 origin, PatrolSettings settings,
                                                          BlockPos... positions) {
        for (BlockPos pos : positions) {
            if (origin.distanceToSqr(Vec3.atCenterOf(pos)) > settings.maxCommandDistanceSqr()) return false;
        }
        return true;
    }

    public static void resetPatrolDragState() {
        patrolDragStart = null;
        patrolDragCurrent = null;
        patrolRightDragging = false;
    }

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
        if (!RTSCameraController.get().isActive() || mc.options.hideGui) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int btnW = 80;
        int btnH = 20;
        int btnX = width / 2 - btnW / 2;
        int btnY = 10;
        event.getGuiGraphics().fill(btnX, btnY, btnX + btnW, btnY + btnH, RTS_HUD_BUTTON_BG);
        event.getGuiGraphics().centeredText(mc.font, "Exit RTS [ESC]", width / 2, btnY + 6, RTS_HUD_TEXT);

        int camBtnX = 10;
        int camBtnY = height - CAMERA_STYLE_BUTTON_HEIGHT - 10;
        event.getGuiGraphics().fill(camBtnX, camBtnY,
                camBtnX + CAMERA_STYLE_BUTTON_WIDTH, camBtnY + CAMERA_STYLE_BUTTON_HEIGHT, RTS_HUD_BUTTON_BG);
        event.getGuiGraphics().centeredText(mc.font, getCameraStyleText(),
                camBtnX + CAMERA_STYLE_BUTTON_WIDTH / 2, camBtnY + 6, RTS_HUD_TEXT);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!RTSCameraController.get().isActive()
                || BmtRTSManager.getMode() != BmtRTSManager.RTSMode.PATROL
                || !ClientPatrolSettings.get().enabled()) {
            resetPatrolDragState();
            return;
        }
        if (!patrolRightDragging) return;
        BlockPos current = getCurrentPatrolGroundPos();
        if (current != null) patrolDragCurrent = current;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (!RTSCameraController.get().isActive()
                || BmtRTSManager.getMode() != BmtRTSManager.RTSMode.PATROL
                || !ClientPatrolSettings.get().enabled() || mc.options.hideGui || mc.level == null) return;

        PatrolSettings settings = ClientPatrolSettings.get();
        BMTClientConfig.PatrolVisualSettings visual = BMTClientConfig.getPatrolVisualSettings();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getLevelRenderState().cameraRenderState.pos;
        VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderTypes.lines());
        String dimension = mc.level.dimension().identifier().toString();

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

        if (visual.showPatrolDragPreview() && patrolRightDragging
                && patrolDragStart != null && patrolDragCurrent != null) {
            BlockPos min = PatrolTargeting.normalizedMin(patrolDragStart, patrolDragCurrent);
            BlockPos max = PatrolTargeting.normalizedMax(patrolDragStart, patrolDragCurrent);
            int y = patrolDragStart.getY();
            BlockPos flatMin = new BlockPos(min.getX(), y, min.getZ());
            BlockPos flatMax = new BlockPos(max.getX(), y, max.getZ());
            BlockPos minMaxCorner = new BlockPos(flatMin.getX(), y, flatMax.getZ());
            BlockPos maxMinCorner = new BlockPos(flatMax.getX(), y, flatMin.getZ());
            boolean valid = PatrolTargeting.isAreaSizeValid(flatMin, flatMax, settings)
                    && mc.player != null && isWithinPatrolCommandDistance(
                            mc.player.position(), settings, flatMin, flatMax, minMaxCorner, maxMinCorner);
            renderGroundBox(poseStack, lines, cameraPos, flatMin, flatMax,
                    valid ? visual.validPreviewColor() : visual.invalidPreviewColor(), visual);
        }
    }

    private static void renderPatrolMarker(PoseStack poseStack, VertexConsumer lines, Vec3 cameraPos,
                                           LivingEntity entity, BMTClientConfig.PatrolVisualSettings visual) {
        double half = Math.max(visual.markerMinHalfSize(),
                entity.getBbWidth() * visual.markerWidthMultiplier());
        double y = entity.getBoundingBox().maxY + visual.markerVerticalOffset();
        renderLineBox(poseStack, lines, cameraPos,
                new AABB(entity.getX() - half, y, entity.getZ() - half,
                        entity.getX() + half, y + visual.markerHeight(), entity.getZ() + half),
                visual.patrolMarkerColor());
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
        renderGroundBox(poseStack, lines, cameraPos,
                new BlockPos(center.getX() - radius, center.getY(), center.getZ() - radius),
                new BlockPos(center.getX() + radius, center.getY(), center.getZ() + radius),
                color, visual);
    }

    private static void renderGroundBox(PoseStack poseStack, VertexConsumer lines, Vec3 cameraPos,
                                        BlockPos min, BlockPos max, BMTClientConfig.PatrolColor color,
                                        BMTClientConfig.PatrolVisualSettings visual) {
        double y = min.getY() + visual.groundBoxVerticalOffset();
        renderLineBox(poseStack, lines, cameraPos,
                new AABB(min.getX(), y, min.getZ(),
                        max.getX() + 1.0D, y + visual.groundBoxHeight(), max.getZ() + 1.0D), color);
    }

    private static void renderLineBox(PoseStack poseStack, VertexConsumer lines, Vec3 cameraPos,
                                      AABB box, BMTClientConfig.PatrolColor color) {
        int argb = ((int) (color.alpha() * 255.0F) << 24)
                | ((int) (color.red() * 255.0F) << 16)
                | ((int) (color.green() * 255.0F) << 8)
                | (int) (color.blue() * 255.0F);
        poseStack.pushPose();
        ShapeRenderer.renderShape(poseStack, lines,
                Shapes.create(box.move(-cameraPos.x, -cameraPos.y, -cameraPos.z)),
                0.0D, 0.0D, 0.0D, argb, 1.0F);
        poseStack.popPose();
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
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS) {
            Minecraft mc = Minecraft.getInstance();
            double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();

            int btnW = 80;
            int btnH = 20;
            int btnX = width / 2 - btnW / 2;
            int btnY = 10;
            if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
                BmtRTSManager.exitCamera();
                ClientSelectionManager.clear();
                ClientSelectionManager.syncToLib();
                syncSelectionToServer();
                event.setCanceled(true);
                return;
            }

            int camBtnX = 10;
            int camBtnY = height - CAMERA_STYLE_BUTTON_HEIGHT - 10;
            if (mx >= camBtnX && mx <= camBtnX + CAMERA_STYLE_BUTTON_WIDTH
                    && my >= camBtnY && my <= camBtnY + CAMERA_STYLE_BUTTON_HEIGHT) {
                BmtRTSManager.cycleCameraStyle();
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPatrolMouseClick(InputEvent.MouseButton.Pre event) {
        if (!RTSCameraController.get().isActive()
                || BmtRTSManager.getMode() != BmtRTSManager.RTSMode.PATROL
                || !ClientPatrolSettings.get().enabled()
                || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        if (event.getAction() == GLFW.GLFW_PRESS) {
            patrolDragStart = getCurrentPatrolGroundPos();
            patrolDragCurrent = patrolDragStart;
            patrolRightDragging = patrolDragStart != null;
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            resetPatrolDragState();
        }
    }
}
