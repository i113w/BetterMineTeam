package com.i113w.better_mine_team.client.rts.util;

import com.i113w.better_mine_team.BetterMineTeam;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class RenderMatrixStorage {
    // 使用 JOML 的 Matrix4f
    public static final Matrix4f capturedModelViewMatrix = new Matrix4f();
    public static final Matrix4f capturedProjectionMatrix = new Matrix4f();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // 选择一个合适的阶段，AFTER_ENTITIES 通常是安全的，因为此时相机已经完全设置好
        // 且所有的变换都已经应用
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            capturedModelViewMatrix.set(event.getModelViewMatrix());
            capturedProjectionMatrix.set(event.getProjectionMatrix());
        }
    }
}