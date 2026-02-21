package com.i113w.better_mine_team.client.rts.util;

import com.i113w.better_mine_team.BetterMineTeam;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 每帧从渲染事件中直接捕获原版计算好的 View / Projection 矩阵。
 */
@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class RenderMatrixStorage {

    /** View 矩阵：世界坐标 → 相机坐标（仅旋转，平移由 relPos 处理） */
    public static final Matrix4f capturedModelViewMatrix  = new Matrix4f();

    /** Projection 矩阵：相机坐标 → 裁剪坐标 */
    public static final Matrix4f capturedProjectionMatrix = new Matrix4f();

    private static boolean everCaptured = false;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // 在 AFTER_ENTITIES 阶段：相机已就位，时序与原版实体渲染对齐
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        // ── View 矩阵 ─────────────────────────────────────────────────
        // PoseStack 此时的顶层 pose 就是准确的世界空间到相机空间的 View 矩阵
        capturedModelViewMatrix.set(event.getPoseStack().last().pose());

        // ── Projection 矩阵 ──────────────────────────────────────────
        // 直接从事件中获取原版已经算好的（包含 FOV、宽高比、动态视觉效果）的透视矩阵
        capturedProjectionMatrix.set(event.getProjectionMatrix());

        // ── 首次捕获日志 ─────────────────────────────────────────────────
        if (!everCaptured) {
            everCaptured = true;
            BetterMineTeam.debug("[RTS-MATRIX] First capture using Event Matrices");
            BetterMineTeam.debug("[RTS-MATRIX] View Matrix Captured: m00={}", capturedModelViewMatrix.m00());
            BetterMineTeam.debug("[RTS-MATRIX] Projection Matrix Captured: m00={}", capturedProjectionMatrix.m00());
        }
    }

    public static void clear() {
        capturedModelViewMatrix.identity();
        capturedProjectionMatrix.identity();
        everCaptured = false;
        BetterMineTeam.debug("[RTS-MATRIX] Cleared");
    }
}

