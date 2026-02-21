package com.i113w.better_mine_team.client.rts.event;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.ModKeyMappings;
import com.i113w.better_mine_team.client.gui.screen.TeamManagementScreen;
import com.i113w.better_mine_team.client.rts.RTSCameraManager;
import com.i113w.better_mine_team.client.rts.RTSSelectionManager;
import com.i113w.better_mine_team.client.rts.util.MouseRayCaster;
import com.i113w.better_mine_team.client.rts.util.ScreenProjector;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.network.data.CommandTarget;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.network.rts.C2S_IssueCommandPacket;
import com.i113w.better_mine_team.common.network.rts.C2S_SelectionSyncPacket;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.*;

@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class RTSInputHandler {

    private static final ResourceLocation CURSOR_NORMAL = new ResourceLocation(BetterMineTeam.MODID, "textures/gui/cursors/cursor_normal.png");
    private static final ResourceLocation CURSOR_ATTACK = new ResourceLocation(BetterMineTeam.MODID, "textures/gui/cursors/cursor_attack.png");
    private static final ResourceLocation CURSOR_ALLY   = new ResourceLocation(BetterMineTeam.MODID, "textures/gui/cursors/cursor_ally.png");

    private static int hoverCheckCooldown = 0;
    private static final int HOVER_CHECK_INTERVAL = 3;
    private static final double EDGE_PITCH_THRESHOLD = 20.0;
    private static final float EDGE_PITCH_SPEED = 2.0f;

    // ─── 1. 禁用玩家移动输入 ──────────────────────────────────────────────────
    @SubscribeEvent
    public static void onInputUpdate(MovementInputUpdateEvent event) {
        if (RTSCameraManager.get().isActive()) {
            event.getInput().forwardImpulse = 0;
            event.getInput().leftImpulse = 0;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }

    // ─── 2. 隐藏原版十字准星 ──────────────────────────────────────────────────
    // NamedGuiOverlay 与 VanillaGuiOverlay enum 类型不同，无法直接 ==。
    // 改为通过 overlay 的 ResourceLocation id 的 path 段匹配。
    // Forge 1.20.1 中准星 overlay 的 id path 为 "crosshair"。
    @SubscribeEvent
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (RTSCameraManager.get().isActive()) {
            if ("crosshair".equals(event.getOverlay().id().getPath())) {
                event.setCanceled(true);
            }
        }
    }

    // ─── 3. RTS 主循环 Tick ───────────────────────────────────────────────────
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        RTSCameraManager cameraManager = RTSCameraManager.get();
        if (!cameraManager.isActive()) return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }

        cameraManager.tick(0f);

        float moveX = 0, moveZ = 0, moveY = 0;
        if (mc.options.keyUp.isDown())    moveZ += 1;
        if (mc.options.keyDown.isDown())  moveZ -= 1;
        if (mc.options.keyLeft.isDown())  moveX += 1;
        if (mc.options.keyRight.isDown()) moveX -= 1;
        if (mc.options.keyJump.isDown())  moveY += 1;
        if (mc.options.keyShift.isDown()) moveY -= 1;

        boolean isRotateKeyDown = ModKeyMappings.RTS_CAMERA_ROTATE.isDown();
        float rotateYaw = 0;

        if (isRotateKeyDown) {
            double centerX = mc.getWindow().getScreenWidth() / 2.0;
            double deltaX  = mc.mouseHandler.xpos() - centerX;
            if (Math.abs(deltaX) > 5.0) {
                rotateYaw = (float)(deltaX * 0.05);
                GLFW.glfwSetCursorPos(
                        mc.getWindow().getWindow(),
                        centerX,
                        mc.getWindow().getScreenHeight() / 2.0
                );
            }
        } else {
            handleEdgePitch(mc, cameraManager);
        }

        if (moveX != 0 || moveZ != 0 || moveY != 0 || rotateYaw != 0) {
            cameraManager.handleInput(moveX, moveZ, rotateYaw, 0, moveY);
        }

        RTSSelectionManager selMgr = RTSSelectionManager.get();
        if (selMgr.isDragging()) {
            double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()  / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            selMgr.updateDrag((float) mx, (float) my);
        }
        if (selMgr.isAttackDragging()) {
            double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()  / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            selMgr.updateAttackDrag((float) mx, (float) my);
        }

        if (++hoverCheckCooldown >= HOVER_CHECK_INTERVAL) {
            hoverCheckCooldown = 0;
            updateHoveredEntity(mc);
        }
    }

    // ─── 4. 渲染自定义光标与 Exit 按钮 ───────────────────────────────────────
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!RTSCameraManager.get().isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        int width  = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // Exit 按钮背景 + 文字
        int btnW = 80, btnH = 20;
        int btnX = width / 2 - btnW / 2, btnY = 10;
        event.getGuiGraphics().fill(btnX, btnY, btnX + btnW, btnY + btnH, 0x80000000);
        event.getGuiGraphics().drawCenteredString(mc.font, "Exit RTS [ESC]", width / 2, btnY + 6, 0xFFFFFF);

        double guiMouseX = mc.mouseHandler.xpos() * width  / mc.getWindow().getScreenWidth();
        double guiMouseY = mc.mouseHandler.ypos() * height / mc.getWindow().getScreenHeight();

        // 动态光标
        int hoveredId = RTSSelectionManager.get().getHoveredEntityId();
        Entity hoveredEntity = (hoveredId != -1 && mc.level != null)
                ? mc.level.getEntity(hoveredId) : null;

        ResourceLocation cursorTex = CURSOR_NORMAL;
        if (hoveredEntity instanceof LivingEntity hoveredLiving) {
            cursorTex = TeamManager.isAlly(mc.player, hoveredLiving) ? CURSOR_ALLY : CURSOR_ATTACK;
        }

        RenderSystem.enableBlend();
        event.getGuiGraphics().blit(cursorTex, (int) guiMouseX, (int) guiMouseY, 0, 0, 16, 16, 16, 16);
        RenderSystem.disableBlend();
    }

    // ─── 5. 键盘输入 ─────────────────────────────────────────────────────────
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

    // ─── 6. 鼠标按键 ─────────────────────────────────────────────────────────
    // Forge 1.20.1: 必须用 Pre，Post 不可取消，调用 setCanceled() 会崩溃
    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (!RTSCameraManager.get().isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        RTSSelectionManager manager = RTSSelectionManager.get();

        // 检查是否点击了 Exit 按钮
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS) {
            double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()  / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            int width = mc.getWindow().getGuiScaledWidth();
            int btnW = 80, btnH = 20;
            int btnX = width / 2 - btnW / 2, btnY = 10;
            if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
                RTSCameraManager.get().toggleRTSMode();
                manager.clearSelection();
                syncSelectionToServer();
                event.setCanceled(true);
                return;
            }
        }

        // 左键：框选
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()  / mc.getWindow().getScreenWidth();
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

            // 右键：攻击框选 / 指令
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()  / mc.getWindow().getScreenWidth();
                double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
                manager.startAttackDrag((float) mx, (float) my);
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                if (manager.isAttackDragging()) {
                    performBoxAttack();
                    manager.endAttackDrag();
                }
            }
            event.setCanceled(true);
        }
    }

    // ─── 私有辅助方法 ─────────────────────────────────────────────────────────

    private static void handleEdgePitch(Minecraft mc, RTSCameraManager manager) {
        double y = mc.mouseHandler.ypos();
        double height = mc.getWindow().getHeight();
        if (y < EDGE_PITCH_THRESHOLD)            manager.adjustPitch(-EDGE_PITCH_SPEED);
        else if (y > height - EDGE_PITCH_THRESHOLD) manager.adjustPitch(EDGE_PITCH_SPEED);
    }

    private static void updateHoveredEntity(Minecraft mc) {
        HitResult hit = MouseRayCaster.pickFromMouse(mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), 512.0);
        if (hit.getType() == HitResult.Type.ENTITY) {
            RTSSelectionManager.get().setHoveredEntity(((EntityHitResult) hit).getEntity());
        } else {
            RTSSelectionManager.get().setHoveredEntity(null);
        }
    }

    private static void performBoxSelection() {
        RTSSelectionManager manager = RTSSelectionManager.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.cameraEntity == null) return;

        var rect = manager.getSelectionRect();
        Set<Integer> newSelection = new HashSet<>();
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        final double MAX_VERT = 50.0, MAX_HORIZ = 128.0;

        if (rect.width() < 2 && rect.height() < 2) {
            // 点选
            HitResult hit = MouseRayCaster.pickFromMouse(mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), 256.0);
            if (hit.getType() == HitResult.Type.ENTITY) {
                Entity target = ((EntityHitResult) hit).getEntity();
                if (isSelectableEntity(target, camPos, MAX_VERT, MAX_HORIZ)) {
                    newSelection.add(target.getId());
                }
            }
        } else {
            // 框选
            boolean mvIsIdentity = isMatrixIdentity(manager.getViewMatrix());
            boolean projIsIdentity = isMatrixIdentity(manager.getProjectionMatrix());
            BetterMineTeam.debug("[RTS-SEL] Box select: rect=({},{})→({},{}) | camPos=({},{},{}) | viewIsIdentity={} projIsIdentity={}",
                    (int)rect.x(), (int)rect.y(),
                    (int)(rect.x()+rect.width()), (int)(rect.y()+rect.height()),
                    fmt(camPos.x), fmt(camPos.y), fmt(camPos.z),
                    mvIsIdentity, projIsIdentity);
            int candidateCount = 0;
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!isSelectableEntity(entity, camPos, MAX_VERT, MAX_HORIZ)) continue;
                candidateCount++;
                boolean hit = ScreenProjector.isAABBInScreenRect(
                        entity.getBoundingBox(), rect,
                        manager.getViewMatrix(), manager.getProjectionMatrix(), camPos);
                if (hit) {
                    BetterMineTeam.debug("[RTS-SEL] Selected: {} (id={}) world=({},{},{})",
                            entity.getName().getString(), entity.getId(),
                            fmt(entity.getX()), fmt(entity.getY()), fmt(entity.getZ()));
                    newSelection.add(entity.getId());
                }
            }
            BetterMineTeam.debug("[RTS-SEL] Candidates={} Selected={}", candidateCount, newSelection.size());
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
                    entity.getBoundingBox(), rect,
                    manager.getViewMatrix(), manager.getProjectionMatrix(), camPos)) {
                targetIds.add(entity.getId());
            }
        }

        if (targetIds.isEmpty()) return;

        int primaryId = targetIds.remove(0);
        MTNetworkRegister.CHANNEL.sendToServer(new C2S_IssueCommandPacket(
                CommandType.ATTACK,
                new CommandTarget(Vec3.ZERO, primaryId, BlockPos.ZERO),
                targetIds,
                manager.getRevision()
        ));
    }

    private static void performRightClickCommand() {
        RTSSelectionManager manager = RTSSelectionManager.get();
        if (manager.getSelectedIds().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();

        // Recruit 模式
        if (RTSCameraManager.get().getMode() == RTSCameraManager.RTSMode.RECRUIT) {
            MTNetworkRegister.CHANNEL.sendToServer(new C2S_IssueCommandPacket(
                    CommandType.RECRUIT, CommandTarget.EMPTY, Collections.emptyList(), manager.getRevision()
            ));
            manager.clearSelection();
            return;
        }

        // 指挥模式
        HitResult hit = MouseRayCaster.pickFromMouse(mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), 256.0);
        if (hit.getType() == HitResult.Type.MISS) return;

        CommandType type;
        CommandTarget target;

        if (hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            type = TeamManager.isAlly(mc.player, entity instanceof LivingEntity l ? l : null)
                    ? CommandType.MOVE : CommandType.ATTACK;
            target = new CommandTarget(entity.position(), entity.getId(), entity.blockPosition());
        } else {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            type = CommandType.MOVE;
            target = new CommandTarget(hit.getLocation(), -1, pos);
        }

        MTNetworkRegister.CHANNEL.sendToServer(new C2S_IssueCommandPacket(
                type, target, Collections.emptyList(), manager.getRevision()
        ));
    }

    private static void syncSelectionToServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<Integer> validIds = new ArrayList<>();
        for (int id : RTSSelectionManager.get().getSelectedIds()) {
            Entity entity = mc.level.getEntity(id);
            if (entity != null && entity.isAlive()) validIds.add(id);
        }

        MTNetworkRegister.CHANNEL.sendToServer(
                new C2S_SelectionSyncPacket(validIds, RTSSelectionManager.get().getRevision())
        );
    }

    private static boolean isSelectableEntity(Entity entity, Vec3 camPos,
                                              double maxVert, double maxHoriz) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        if (entity == Minecraft.getInstance().player) return false;
        if (!(entity instanceof net.minecraft.world.entity.PathfinderMob)) return false;
        if (Math.abs(entity.getY() - camPos.y) > maxVert) return false;
        double dx = entity.getX() - camPos.x, dz = entity.getZ() - camPos.z;
        if (dx * dx + dz * dz > maxHoriz * maxHoriz) return false;
        return entity.getY() >= -64 && entity.getY() <= 320;
    }

    private static String fmt(double v) { return String.format("%.2f", v); }

    /** 判断矩阵是否为单位矩阵（全零或 identity => 未捕获）*/
    private static boolean isMatrixIdentity(org.joml.Matrix4f m) {
        return Math.abs(m.m00() - 1) < 0.001f && Math.abs(m.m11() - 1) < 0.001f
                && Math.abs(m.m22() - 1) < 0.001f && Math.abs(m.m33() - 1) < 0.001f
                && Math.abs(m.m01()) < 0.001f && Math.abs(m.m10()) < 0.001f;
    }
}