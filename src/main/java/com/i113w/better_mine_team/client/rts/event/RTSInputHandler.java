package com.i113w.better_mine_team.client.rts.event;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.ModKeyMappings;
import com.i113w.better_mine_team.client.gui.screen.TeamManagementScreen;
import com.i113w.better_mine_team.client.rts.RTSCameraManager;
import com.i113w.better_mine_team.client.rts.RTSSelectionManager;
import com.i113w.better_mine_team.client.rts.util.MouseRayCaster;
import com.i113w.better_mine_team.client.rts.util.ScreenProjector;
import com.i113w.better_mine_team.common.network.data.CommandTarget;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.network.rts.C2S_IssueCommandPayload;
import com.i113w.better_mine_team.common.network.rts.C2S_SelectionSyncPayload;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class RTSInputHandler {

    private static final ResourceLocation CURSOR_NORMAL = ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "textures/gui/cursors/cursor_normal.png");
    private static final ResourceLocation CURSOR_ATTACK = ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "textures/gui/cursors/cursor_attack.png");
    private static final ResourceLocation CURSOR_ALLY = ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "textures/gui/cursors/cursor_ally.png");

    // [优化] 性能控制：鼠标悬停检测冷却
    private static int hoverCheckCooldown = 0;
    private static final int HOVER_CHECK_INTERVAL = 3; // 每 3 tick 检测一次

    // [优化] 常量定义
    private static final double EDGE_PITCH_THRESHOLD = 20.0;
    private static final float EDGE_PITCH_SPEED = 2.0f;

    @SubscribeEvent
    public static void onInputUpdate(MovementInputUpdateEvent event) {
        if (RTSCameraManager.get().isActive()) {
            event.getInput().forwardImpulse = 0;
            event.getInput().leftImpulse = 0;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }

    // 强制缩小 FOV 获取等距视觉效果
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (RTSCameraManager.get().isActive() && RTSCameraManager.get().getCameraStyle() == RTSCameraManager.CameraStyle.RTS) {
            // 非常小的 FOV 以产生正交错觉
            event.setFOV(25.0);
        }
    }

    // 屏蔽第一人称玩家手臂的渲染
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (RTSCameraManager.get().isActive()) {
            event.setCanceled(true);
        }
    }

    // 白名单模式：屏蔽几乎所有原版及其他模组注入的杂乱 GUI 层
    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (RTSCameraManager.get().isActive()) {
            ResourceLocation layerName = event.getName();

            // 仅放行：聊天框、F3调试界面、TAB玩家列表
            if (!VanillaGuiLayers.CHAT.equals(layerName) &&
                    !VanillaGuiLayers.DEBUG_OVERLAY.equals(layerName) &&
                    !VanillaGuiLayers.TAB_LIST.equals(layerName) &&
                    !VanillaGuiLayers.OVERLAY_MESSAGE.equals(layerName) &&
                    !VanillaGuiLayers.TITLE.equals(layerName) &&
                    !VanillaGuiLayers.SUBTITLE_OVERLAY.equals(layerName)) {

                event.setCanceled(true);
            }
        }
    }

    // --- 3. 核心：RTS 相机与交互逻辑 ---
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        RTSCameraManager cameraManager = RTSCameraManager.get();
        if (!cameraManager.isActive()) return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }

        cameraManager.tick(mc.getTimer().getGameTimeDeltaPartialTick(false));

        float moveX = 0;
        float moveZ = 0;
        float moveY = 0;

        if (mc.options.keyUp.isDown()) moveZ += 1;
        if (mc.options.keyDown.isDown()) moveZ -= 1;
        if (mc.options.keyLeft.isDown()) moveX += 1;
        if (mc.options.keyRight.isDown()) moveX -= 1;

        if (mc.options.keyJump.isDown()) moveY += 1;
        if (mc.options.keyShift.isDown()) moveY -= 1;

        // 旋转逻辑
        boolean isRotateKeyDown = ModKeyMappings.RTS_CAMERA_ROTATE.isDown();
        float rotateYaw = 0;

        // 处理 RTS 相机下的阶跃式偏航角控制
        if (isRotateKeyDown) {
            // 使用鼠标 Delta 进行旋转
            double centerX = mc.getWindow().getScreenWidth() / 2.0;
            double deltaX = mc.mouseHandler.xpos() - centerX;

            if (cameraManager.getCameraStyle() == RTSCameraManager.CameraStyle.RTS) {
                // 更大的触发阈值，确保玩家是有意滑动的
                if (Math.abs(deltaX) > 40.0) {
                    float step = deltaX > 0 ? 90f : -90f;
                    cameraManager.snapYaw(step);
                    GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), centerX, mc.getWindow().getScreenHeight() / 2.0);
                }
            } else {
                if (Math.abs(deltaX) > 5.0) {
                    rotateYaw = (float) (deltaX * 0.05);
                    GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), centerX, mc.getWindow().getScreenHeight() / 2.0);
                }
            }
        } else {
            handleEdgePitch(mc, cameraManager);
        }

        if (moveX != 0 || moveZ != 0 || moveY != 0 || rotateYaw != 0) {
            cameraManager.handleInput(moveX, moveZ, rotateYaw, 0, moveY);
        }

        if (RTSSelectionManager.get().isDragging()) {
            double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            RTSSelectionManager.get().updateDrag((float) mx, (float) my);
        }

        if (RTSSelectionManager.get().isAttackDragging()) {
            double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            RTSSelectionManager.get().updateAttackDrag((float) mx, (float) my);
        }

        // [优化] 降频检测鼠标悬停实体
        if (++hoverCheckCooldown >= HOVER_CHECK_INTERVAL) {
            hoverCheckCooldown = 0;
            updateHoveredEntity(mc);
        }
    }

    private static void handleEdgePitch(Minecraft mc, RTSCameraManager manager) {
        double y = mc.mouseHandler.ypos();
        double height = mc.getWindow().getHeight();

        if (y < EDGE_PITCH_THRESHOLD) {
            manager.adjustPitch(-EDGE_PITCH_SPEED);
        } else if (y > height - EDGE_PITCH_THRESHOLD) {
            manager.adjustPitch(EDGE_PITCH_SPEED);
        }
    }

    private static void updateHoveredEntity(Minecraft mc) {
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();
        // 由于相机在高空，需加长射线
        HitResult hit = MouseRayCaster.pickFromMouse(mouseX, mouseY, 1024.0);

        if (hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hit;
            RTSSelectionManager.get().setHoveredEntity(entityHit.getEntity());
        } else {
            RTSSelectionManager.get().setHoveredEntity(null);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (RTSCameraManager.get().isActive()) {
            double scrollDelta = event.getScrollDeltaY();
            RTSCameraManager.get().handleZoom((float) scrollDelta);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!RTSCameraManager.get().isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        int btnW = 80;
        int btnH = 20;
        int btnX = width / 2 - btnW / 2;
        int btnY = 10;
        event.getGuiGraphics().fill(btnX, btnY, btnX + btnW, btnY + btnH, 0x80000000);
        event.getGuiGraphics().drawCenteredString(mc.font, "Exit RTS [ESC]", width / 2, btnY + 6, 0xFFFFFF);

        // 左下角相机模式切换按钮
        int camBtnW = 90;
        int camBtnH = 20;
        int camBtnX = 10;
        int camBtnY = height - camBtnH - 10;
        event.getGuiGraphics().fill(camBtnX, camBtnY, camBtnX + camBtnW, camBtnY + camBtnH, 0x80000000);
        String styleText = RTSCameraManager.get().getCameraStyle() == RTSCameraManager.CameraStyle.RTS ? "Camera: RTS" : "Camera: Free";
        event.getGuiGraphics().drawCenteredString(mc.font, styleText, camBtnX + camBtnW / 2, camBtnY + 6, 0xFFFFFF);

        // 鼠标渲染逻辑
        double guiMouseX = mc.mouseHandler.xpos() * width / mc.getWindow().getScreenWidth();
        double guiMouseY = mc.mouseHandler.ypos() * height / mc.getWindow().getScreenHeight();

        int hoveredId = RTSSelectionManager.get().getHoveredEntityId();
        Entity hoveredEntity = null;
        if (hoveredId != -1 && mc.level != null) {
            hoveredEntity = mc.level.getEntity(hoveredId);
        }

        ResourceLocation cursorTexture = CURSOR_NORMAL;
        if (hoveredEntity != null) {
            boolean isAlly = TeamManager.isAlly(mc.player, hoveredEntity instanceof LivingEntity l ? l : null);
            cursorTexture = isAlly ? CURSOR_ALLY : CURSOR_ATTACK;
        }

        RenderSystem.enableBlend();
        event.getGuiGraphics().blit(cursorTexture, (int) guiMouseX, (int) guiMouseY, 0, 0, 16, 16, 16, 16);
        RenderSystem.disableBlend();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (ModKeyMappings.OPEN_TEAM_MENU.consumeClick()) {
            Minecraft.getInstance().setScreen(new TeamManagementScreen());
            return;
        }

        if (RTSCameraManager.get().isActive()) {
            if (event.getKey() == GLFW.GLFW_KEY_ESCAPE && event.getAction() == GLFW.GLFW_PRESS) {
                RTSCameraManager.get().toggleRTSMode();
                RTSSelectionManager.get().clearSelection();
                syncSelectionToServer();
                Minecraft.getInstance().setScreen(null);
            }
        }
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (!RTSCameraManager.get().isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        RTSSelectionManager manager = RTSSelectionManager.get();

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS) {
            double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();

            // 顶部的退出按钮判定
            int btnW = 80, btnH = 20, btnX = width / 2 - btnW / 2, btnY = 10;
            if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
                RTSCameraManager.get().toggleRTSMode();
                manager.clearSelection();
                syncSelectionToServer();
                event.setCanceled(true);
                return;
            }

            // 切换相机风格按钮的判定
            int camBtnW = 90, camBtnH = 20, camBtnX = 10, camBtnY = height - camBtnH - 10;
            if (mx >= camBtnX && mx <= camBtnX + camBtnW && my >= camBtnY && my <= camBtnY + camBtnH) {
                RTSCameraManager.get().toggleCameraStyle();
                event.setCanceled(true);
                return;
            }
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
                double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
                manager.startDrag((float) mx, (float) my);
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                if (manager.isDragging()) {
                    performBoxSelection();
                    manager.endDrag();
                    syncSelectionToServer();
                }
            }
            event.setCanceled(true);
        }
        else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
                double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
                manager.startAttackDrag((float) mx, (float) my);
                event.setCanceled(true);
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                if (manager.isAttackDragging()) {
                    performBoxAttack();
                    manager.endAttackDrag();
                }
                event.setCanceled(true);
            }
        }
    }

    private static void performBoxSelection() {
        RTSSelectionManager manager = RTSSelectionManager.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.cameraEntity == null) return;

        var rect = manager.getSelectionRect();
        Set<Integer> newSelection = new HashSet<>();
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        final double MAX_VERTICAL_RANGE = 250.0; // [加宽] 应对抬升后的摄像机
        final double MAX_HORIZONTAL_RANGE = 256.0;

        if (rect.width() < 2 && rect.height() < 2) {
            // [加长] 单点拾取射线
            HitResult hit = MouseRayCaster.pickFromMouse(mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), 1024.0);
            if (hit.getType() == HitResult.Type.ENTITY) {
                Entity target = ((EntityHitResult) hit).getEntity();
                if (isSelectableEntity(target, camPos, MAX_VERTICAL_RANGE, MAX_HORIZONTAL_RANGE)) {
                    newSelection.add(target.getId());
                }
            }
        }
        else {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!isSelectableEntity(entity, camPos, MAX_VERTICAL_RANGE, MAX_HORIZONTAL_RANGE)) continue;
                if (ScreenProjector.isAABBInScreenRect(
                        entity.getBoundingBox(),
                        rect,
                        manager.getViewMatrix(),
                        manager.getProjectionMatrix(),
                        camPos
                )) {
                    newSelection.add(entity.getId());
                }
            }
        }
        manager.setSelected(newSelection);
    }

    private static void performBoxAttack() {
        RTSSelectionManager manager = RTSSelectionManager.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (RTSCameraManager.get().getMode() == RTSCameraManager.RTSMode.RECRUIT) {
            performRightClickCommand();
            return;
        }
        var rect = manager.getAttackRect();

        if (rect.width() < 2 && rect.height() < 2) {
            performRightClickCommand();
            return;
        }

        List<Integer> targetIds = new ArrayList<>();
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity == mc.player) continue;

            if (TeamManager.isAlly(mc.player, (LivingEntity) entity)) continue;

            if (ScreenProjector.isAABBInScreenRect(
                    entity.getBoundingBox(),
                    rect,
                    manager.getViewMatrix(),
                    manager.getProjectionMatrix(),
                    camPos
            )) {
                targetIds.add(entity.getId());
            }
        }

        if (targetIds.isEmpty()) return;

        int primaryId = targetIds.get(0);
        targetIds.remove(0);

        CommandTarget target = new CommandTarget(Vec3.ZERO, primaryId, BlockPos.ZERO);
        int revision = manager.getRevision();

        PacketDistributor.sendToServer(new C2S_IssueCommandPayload(CommandType.ATTACK, target, targetIds, revision));
    }

    private static void performRightClickCommand() {
        if (RTSSelectionManager.get().getSelectedIds().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();

        // [新增] 检查当前模式
        RTSCameraManager.RTSMode mode = RTSCameraManager.get().getMode();
        if (mode == RTSCameraManager.RTSMode.RECRUIT) {
            // --- 征召模式逻辑 ---
            int revision = RTSSelectionManager.get().getRevision();
            // 发送 RECRUIT 指令，目标可以为空，因为我们只关心选中的单位
            // 将 CommandTarget 设为 EMPTY
            PacketDistributor.sendToServer(new C2S_IssueCommandPayload(
                    CommandType.RECRUIT,
                    CommandTarget.EMPTY,
                    java.util.Collections.emptyList(),
                    revision
            ));

            // 可以在这里清空选择，或者等服务端反馈
            RTSSelectionManager.get().clearSelection();
            return;
        }

        // [加长] 右键点阵投射射线到 1024
        HitResult hit = MouseRayCaster.pickFromMouse(mouseX, mouseY, 1024.0);
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

        int revision = RTSSelectionManager.get().getRevision();
        PacketDistributor.sendToServer(new C2S_IssueCommandPayload(type, target, java.util.Collections.emptyList(), revision));
    }

    private static void syncSelectionToServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        var selectedIds = RTSSelectionManager.get().getSelectedIds();
        List<Integer> validIds = new ArrayList<>();
        for (int id : selectedIds) {
            Entity entity = mc.level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                validIds.add(id);
            }
        }

        int revision = RTSSelectionManager.get().getRevision();
        PacketDistributor.sendToServer(new C2S_SelectionSyncPayload(validIds, revision));
    }

    private static boolean isSelectableEntity(Entity entity, Vec3 camPos, double maxVerticalRange, double maxHorizontalRange) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        if (entity == Minecraft.getInstance().player) return false;
        if (!(entity instanceof net.minecraft.world.entity.PathfinderMob)) return false;

        double verticalDist = Math.abs(entity.getY() - camPos.y);
        if (verticalDist > maxVerticalRange) return false;

        double dx = entity.getX() - camPos.x;
        double dz = entity.getZ() - camPos.z;
        double horizontalDistSqr = dx * dx + dz * dz;
        if (horizontalDistSqr > maxHorizontalRange * maxHorizontalRange) return false;

        if (entity.getY() < -64 || entity.getY() > 320) return false;

        return true;
    }
}