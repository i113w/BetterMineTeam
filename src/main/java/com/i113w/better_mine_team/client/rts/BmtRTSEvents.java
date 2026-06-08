package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.screen.TeamManagementScreen;
import com.i113w.better_mine_team.client.manager.ClientSelectionManager;
import com.i113w.better_mine_team.common.network.data.CommandTarget;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.network.rts.C2S_IssueCommandPayload;
import com.i113w.better_mine_team.common.network.rts.C2S_SelectionSyncPayload;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.camera_lib.api.CameraLibAPI;
import com.i113w.camera_lib.api.event.RTSBoxSelectEvent;
import com.i113w.camera_lib.api.event.RTSRightClickEvent;
import com.i113w.camera_lib.camera.RTSCameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class BmtRTSEvents {

    // ==========================================
    // 1. 库事件监听 (选区与指令)
    // ==========================================

    @SubscribeEvent
    public static void onBoxSelect(RTSBoxSelectEvent event) {
        ClientSelectionManager.clear();
        for (Entity e : event.getCandidates()) {
            ClientSelectionManager.select(e.getId());
        }
        ClientSelectionManager.syncToLib();
        syncSelectionToServer();
    }

    @SubscribeEvent
    public static void onRightClick(RTSRightClickEvent event) {
        if (ClientSelectionManager.getSelectedIds().isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        BmtRTSManager.RTSMode mode = BmtRTSManager.getMode();
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

    // 按键绘制

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (com.i113w.better_mine_team.client.ModKeyMappings.OPEN_TEAM_MENU.consumeClick()) {
            Minecraft.getInstance().setScreen(new TeamManagementScreen());
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        // 只有在 RTS 摄像机激活时才渲染 UI
        if (!RTSCameraController.get().isActive()) return;

        Minecraft mc = Minecraft.getInstance();
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
        int camBtnW = 90;
        int camBtnH = 20;
        int camBtnX = 10;
        int camBtnY = height - camBtnH - 10;
        event.getGuiGraphics().fill(camBtnX, camBtnY, camBtnX + camBtnW, camBtnY + camBtnH, 0x80000000);

        // 判断当前相机是 RTS 还是 FREE 风格
        String styleText = RTSCameraController.get().getCameraStyle().name().equals("RTS") ? "Camera: RTS" : "Camera: Free";
        event.getGuiGraphics().drawCenteredString(mc.font, styleText, camBtnX + camBtnW / 2, camBtnY + 6, 0xFFFFFF);
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
                RTSCameraController.get().toggleRTSMode();
                ClientSelectionManager.clear();
                ClientSelectionManager.syncToLib();
                syncSelectionToServer();
                event.setCanceled(true); // 拦截事件，防止触发框选
                return;
            }

            // 切换相机风格按钮判定
            int camBtnW = 90, camBtnH = 20, camBtnX = 10, camBtnY = height - camBtnH - 10;
            if (mx >= camBtnX && mx <= camBtnX + camBtnW && my >= camBtnY && my <= camBtnY + camBtnH) {
                // 切换摄像机风格
                RTSCameraController.get().toggleCameraStyle();
                event.setCanceled(true); // 拦截事件
                return;
            }
        }
    }
}