package com.i113w.better_mine_team.client.rts.event;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.rts.RTSCameraManager;
import com.i113w.better_mine_team.client.rts.RTSSelectionManager;
import com.i113w.better_mine_team.client.rts.util.RenderMatrixStorage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class RTSRenderHandler {

    // ===== 3D 渲染：选中高亮框 =====
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!RTSCameraManager.get().isActive()) return;

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            // 矩阵更新由 RenderMatrixStorage 在 AFTER_ENTITIES 阶段完成
            // 此处直接将最新缓存同步给 SelectionManager（供 ScreenProjector 使用）
            RTSSelectionManager.get().updateMatrices(
                    RenderMatrixStorage.capturedModelViewMatrix,
                    RenderMatrixStorage.capturedProjectionMatrix
            );

            renderSelectedOutlines(event);
        }
    }

    // ===== 2D 渲染：拖拽选框 =====
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!RTSCameraManager.get().isActive()) return;
        RTSSelectionManager manager = RTSSelectionManager.get();

        // 框选拖拽 → 绿色
        if (manager.isDragging()) {
            var rect = manager.getSelectionRect();
            event.getGuiGraphics().fill(
                    (int) rect.x(), (int) rect.y(),
                    (int)(rect.x() + rect.width()), (int)(rect.y() + rect.height()),
                    0x8000FF00
            );
            event.getGuiGraphics().renderOutline(
                    (int) rect.x(), (int) rect.y(),
                    (int) rect.width(), (int) rect.height(),
                    0xFF00FF00
            );
        }

        // 攻击框选 → 红色
        if (manager.isAttackDragging()) {
            var rect = manager.getAttackRect();
            event.getGuiGraphics().fill(
                    (int) rect.x(), (int) rect.y(),
                    (int)(rect.x() + rect.width()), (int)(rect.y() + rect.height()),
                    0x80FF0000
            );
            event.getGuiGraphics().renderOutline(
                    (int) rect.x(), (int) rect.y(),
                    (int) rect.width(), (int) rect.height(),
                    0xFFFF0000
            );
        }
    }

    // ─── 私有方法 ─────────────────────────────────────────────────────────────

    private static void renderSelectedOutlines(RenderLevelStageEvent event) {
        RTSSelectionManager manager = RTSSelectionManager.get();
        var selectedIds = manager.getSelectedIds();
        int hoveredId = manager.getHoveredEntityId();

        if (selectedIds.isEmpty() && hoveredId == -1) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        // 已选中 → 白色
        for (Integer id : selectedIds) {
            Entity entity = mc.level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                renderEntityOutline(poseStack, buffer, entity, camPos, 1f, 1f, 1f, 1f);
            }
        }

        // 悬停（未选中）→ 黄色
        if (hoveredId != -1 && !selectedIds.contains(hoveredId)) {
            Entity entity = mc.level.getEntity(hoveredId);
            if (entity != null && entity.isAlive()) {
                renderEntityOutline(poseStack, buffer, entity, camPos, 1f, 1f, 0f, 1f);
            }
        }
    }

    private static void renderEntityOutline(PoseStack poseStack, VertexConsumer buffer,
                                            Entity entity, Vec3 camPos,
                                            float r, float g, float b, float a) {
        poseStack.pushPose();
        poseStack.translate(
                entity.getX() - camPos.x,
                entity.getY() - camPos.y,
                entity.getZ() - camPos.z
        );

        AABB localAABB = entity.getBoundingBox()
                .move(-entity.getX(), -entity.getY(), -entity.getZ())
                .inflate(0.05);

        LevelRenderer.renderLineBox(poseStack, buffer, localAABB, r, g, b, a);
        poseStack.popPose();
    }
}
