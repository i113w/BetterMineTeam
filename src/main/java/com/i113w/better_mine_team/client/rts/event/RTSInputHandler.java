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
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class RTSInputHandler {

    // 资源路径
    private static final ResourceLocation CURSOR_NORMAL = ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "textures/gui/cursors/cursor_normal.png");
    private static final ResourceLocation CURSOR_ATTACK = ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "textures/gui/cursors/cursor_attack.png");
    private static final ResourceLocation CURSOR_ALLY = ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "textures/gui/cursors/cursor_ally.png");

    // --- 1. 核心：屏蔽玩家本体输入 ---
    @SubscribeEvent
    public static void onInputUpdate(MovementInputUpdateEvent event) {
        if (RTSCameraManager.get().isActive()) {
            event.getInput().forwardImpulse = 0;
            event.getInput().leftImpulse = 0;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }

    // --- 2. 隐藏原版十字准星 ---
    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (RTSCameraManager.get().isActive()) {
            if (VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
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

        // 强制显示鼠标
        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }

        // 更新平滑相机
        cameraManager.tick(mc.getTimer().getGameTimeDeltaPartialTick(false));

        // 处理相机移动 (WASD + Space/Shift)
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

        if (isRotateKeyDown) {
            // 使用鼠标 Delta 进行旋转
            double centerX = mc.getWindow().getScreenWidth() / 2.0;
            double deltaX = mc.mouseHandler.xpos() - centerX;

            if (Math.abs(deltaX) > 5.0) {
                rotateYaw = (float) (deltaX * 0.05);
                GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), centerX, mc.getWindow().getScreenHeight() / 2.0);
            }
        } else {
            // [新增] 边缘俯仰角 (Edge Pitch)
            // 仅在未按旋转键时生效，防止冲突
            handleEdgePitch(mc, cameraManager);
        }

        if (moveX != 0 || moveZ != 0 || moveY != 0 || rotateYaw != 0) {
            cameraManager.handleInput(moveX, moveZ, rotateYaw, 0, moveY);
        }

        // 更新左键拖拽 (选择)
        if (RTSSelectionManager.get().isDragging()) {
            double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            RTSSelectionManager.get().updateDrag((float) mx, (float) my);
        }

        // [新增] 更新右键拖拽 (攻击)
        if (RTSSelectionManager.get().isAttackDragging()) {
            double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            RTSSelectionManager.get().updateAttackDrag((float) mx, (float) my);
        }

        // [修复] 更新鼠标悬停实体 (使用物理坐标)
        updateHoveredEntity(mc);
    }

    // [新增] 边缘俯仰角逻辑
    private static void handleEdgePitch(Minecraft mc, RTSCameraManager manager) {
        double y = mc.mouseHandler.ypos();
        double height = mc.getWindow().getHeight();
        double edgeThreshold = 20.0; // 边缘阈值
        float pitchSpeed = 2.0f;

        // y=0 是顶部, y=height 是底部
        if (y < edgeThreshold) {
            manager.adjustPitch(-pitchSpeed); // 向上看
        } else if (y > height - edgeThreshold) {
            manager.adjustPitch(pitchSpeed);  // 向下看
        }
    }

    private static void updateHoveredEntity(Minecraft mc) {
        // [修复] 直接使用物理坐标
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();

        HitResult hit = MouseRayCaster.pickFromMouse(mouseX, mouseY, 512.0);

        if (hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hit;
            RTSSelectionManager.get().setHoveredEntity(entityHit.getEntity());
        } else {
            RTSSelectionManager.get().setHoveredEntity(null);
        }
    }

    // --- 4. UI 绘制 (光标 & 退出按钮) ---
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!RTSCameraManager.get().isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // 4.1 绘制 "Exit RTS" 按钮
        int btnW = 80;
        int btnH = 20;
        int btnX = width / 2 - btnW / 2;
        int btnY = 10;
        event.getGuiGraphics().fill(btnX, btnY, btnX + btnW, btnY + btnH, 0x80000000);
        event.getGuiGraphics().drawCenteredString(mc.font, "Exit RTS [ESC]", width / 2, btnY + 6, 0xFFFFFF);

        // 4.2 绘制动态鼠标光标
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
        // 16x16 图标，光标左上角对齐鼠标热点
        event.getGuiGraphics().blit(cursorTexture, (int) guiMouseX, (int) guiMouseY, 0, 0, 16, 16, 16, 16);
        RenderSystem.disableBlend();
    }

    // --- 5. 鼠标与按键事件 ---

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

        // 退出按钮检测
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS) {
            double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

            int width = mc.getWindow().getGuiScaledWidth();
            int btnW = 80;
            int btnH = 20;
            int btnX = width / 2 - btnW / 2;
            int btnY = 10;

            if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
                RTSCameraManager.get().toggleRTSMode();
                manager.clearSelection();
                syncSelectionToServer();
                event.setCanceled(true);
                return;
            }
        }

        // 5.1 左键：选择逻辑
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
        // 5.2 右键：命令逻辑
        else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                // [修复] 拦截 Press 防止鼠标归位
                double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
                double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
                // [新增] 开始攻击框选
                manager.startAttackDrag((float) mx, (float) my);
                event.setCanceled(true);
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                // 结束框选，判断是单击还是框选
                if (manager.isAttackDragging()) {
                    performBoxAttack();
                    manager.endAttackDrag();
                }
                event.setCanceled(true);
            }
        }
    }

    // --- 6. 逻辑实现方法 ---

    // 6.1 框选选择 (Left Click)
    private static void performBoxSelection() {
        RTSSelectionManager manager = RTSSelectionManager.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.cameraEntity == null) return;

        var rect = manager.getSelectionRect();
        Set<Integer> newSelection = new HashSet<>();
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        final double MAX_VERTICAL_RANGE = 50.0;
        final double MAX_HORIZONTAL_RANGE = 128.0;

        // 单击选择
        if (rect.width() < 2 && rect.height() < 2) {
            // 使用物理坐标进行精准射线检测
            HitResult hit = MouseRayCaster.pickFromMouse(mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), 256.0);
            if (hit.getType() == HitResult.Type.ENTITY) {
                Entity target = ((EntityHitResult) hit).getEntity();
                if (isSelectableEntity(target, camPos, MAX_VERTICAL_RANGE, MAX_HORIZONTAL_RANGE)) {
                    newSelection.add(target.getId());
                }
            }
        }
        // 框选
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

    // 6.2 框选攻击 (Right Click)
    private static void performBoxAttack() {
        RTSSelectionManager manager = RTSSelectionManager.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        var rect = manager.getAttackRect();

        // 判定为单击
        if (rect.width() < 2 && rect.height() < 2) {
            performRightClickCommand();
            return;
        }

        // 判定为框选攻击
        List<Integer> targetIds = new ArrayList<>();
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity == mc.player) continue;

            // 过滤掉自己的单位 (可选：让服务端去过滤更好，这里只做粗筛)
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

        // 如果框里没有敌人，视为无效操作或移动到中心
        if (targetIds.isEmpty()) {
            // 这里简单处理：如果没有敌人，就不发指令
            // 或者你可以计算框中心点执行移动指令
            return;
        }

        // 取第一个为主目标，其余为副目标
        int primaryId = targetIds.get(0);
        targetIds.remove(0);

        CommandTarget target = new CommandTarget(Vec3.ZERO, primaryId, BlockPos.ZERO);
        int revision = manager.getRevision();

        PacketDistributor.sendToServer(new C2S_IssueCommandPayload(CommandType.ATTACK, target, targetIds, revision));
    }

    // 6.3 单击命令 (Right Click Single)
    private static void performRightClickCommand() {
        if (RTSSelectionManager.get().getSelectedIds().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();

        // [修复] 使用物理坐标
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();

        HitResult hit = MouseRayCaster.pickFromMouse(mouseX, mouseY, 256.0);

        CommandType type = CommandType.MOVE;
        CommandTarget target = CommandTarget.EMPTY;

        if (hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            if (!TeamManager.isAlly(mc.player, entity instanceof LivingEntity l ? l : null)) {
                type = CommandType.ATTACK;
            } else {
                type = CommandType.MOVE; // 对盟友右键暂时也是移动/跟随
            }
            target = new CommandTarget(entity.position(), entity.getId(), entity.blockPosition());
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            // 简单的交互判定，这里统一视为移动
            type = CommandType.MOVE;
            target = new CommandTarget(hit.getLocation(), -1, pos);
        } else {
            return;
        }

        int revision = RTSSelectionManager.get().getRevision();
        // 发送空列表作为副目标
        PacketDistributor.sendToServer(new C2S_IssueCommandPayload(type, target, Collections.emptyList(), revision));
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