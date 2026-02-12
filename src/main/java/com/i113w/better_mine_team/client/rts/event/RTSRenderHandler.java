package com.i113w.better_mine_team.client.rts.event;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.rts.RTSSelectionManager;
import com.i113w.better_mine_team.client.rts.RTSCameraManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class RTSRenderHandler {

    // 1. 3D 渲染：捕获矩阵 & 绘制选中高亮
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!RTSCameraManager.get().isActive()) return;
        RTSSelectionManager manager = RTSSelectionManager.get();

        // [修复] 使用 1.21.1 正确的枚举名称: AFTER_TRANSLUCENT_BLOCKS
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            // 更新矩阵缓存供 InputHandler 使用
            manager.updateMatrices(
                    event.getModelViewMatrix(),
                    event.getProjectionMatrix()
            );

            renderSelectedOutlines(event);
        }
    }

    // 2. 2D 渲染：绘制拖拽选框
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!RTSCameraManager.get().isActive()) return;
        RTSSelectionManager manager = RTSSelectionManager.get();

        if (manager.isDragging()) {
            var rect = manager.getSelectionRect();

            // 绘制半透明绿色填充 (ARGB)
            event.getGuiGraphics().fill(
                    (int)rect.x(), (int)rect.y(),
                    (int)(rect.x() + rect.width()), (int)(rect.y() + rect.height()),
                    0x8000FF00
            );

            // 绘制不透明绿色边框
            event.getGuiGraphics().renderOutline(
                    (int)rect.x(), (int)rect.y(),
                    (int)rect.width(), (int)rect.height(),
                    0xFF00FF00
            );
        }
        if (manager.isAttackDragging()) {
            var rect = manager.getAttackRect();

            // 半透明红色填充
            event.getGuiGraphics().fill(
                    (int)rect.x(), (int)rect.y(),
                    (int)(rect.x() + rect.width()), (int)(rect.y() + rect.height()),
                    0x80FF0000 // A=128, R=255, G=0, B=0
            );

            // 不透明红色边框
            event.getGuiGraphics().renderOutline(
                    (int)rect.x(), (int)rect.y(),
                    (int)rect.width(), (int)rect.height(),
                    0xFFFF0000
            );
        }
    }

    private static void renderSelectedOutlines(RenderLevelStageEvent event) {
        RTSSelectionManager manager = RTSSelectionManager.get(); // [修改] 使用新类名
        var selectedIds = manager.getSelectedIds();
        int hoveredId = manager.getHoveredEntityId(); // [新增]

        if (selectedIds.isEmpty() && hoveredId == -1) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        // 1. 渲染已选中的 (保持原样，通常是绿色或模组主题色)
        for (Integer id : selectedIds) {
            Entity entity = mc.level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                renderEntityOutline(poseStack, buffer, entity, camPos, 1.0f, 1.0f, 1.0f, 1.0f); // 白色
            }
        }

        // 2. [新增] 渲染悬停的高亮 (使用不同颜色，例如黄色或半透明白，且不与已选中的重复)
        if (hoveredId != -1 && !selectedIds.contains(hoveredId)) {
            Entity entity = mc.level.getEntity(hoveredId);
            if (entity != null && entity.isAlive()) {
                // 渲染亮黄色高亮 (R=1, G=1, B=0)
                renderEntityOutline(poseStack, buffer, entity, camPos, 1.0f, 1.0f, 0.0f, 1.0f);
            }
        }
    }

    // [新增] 提取渲染逻辑为辅助方法，避免代码重复
    private static void renderEntityOutline(PoseStack poseStack, VertexConsumer buffer, Entity entity, Vec3 camPos, float r, float g, float b, float a) {
        poseStack.pushPose();
        double dx = entity.getX() - camPos.x;
        double dy = entity.getY() - camPos.y;
        double dz = entity.getZ() - camPos.z;
        poseStack.translate(dx, dy, dz);

        // 获取本地 AABB
        AABB localAABB = entity.getBoundingBox().move(-entity.getX(), -entity.getY(), -entity.getZ());

        // 稍微放大一点点，防止与实体模型重叠闪烁 (Z-fighting)
        localAABB = localAABB.inflate(0.05);

        LevelRenderer.renderLineBox(poseStack, buffer, localAABB, r, g, b, a);
        poseStack.popPose();
    }
}