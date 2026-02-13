package com.i113w.better_mine_team.client.rts.util;

import com.i113w.better_mine_team.BetterMineTeam;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class RenderMatrixStorage {
    public static final Matrix4f capturedModelViewMatrix = new Matrix4f();
    public static final Matrix4f capturedProjectionMatrix = new Matrix4f();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            capturedModelViewMatrix.set(event.getModelViewMatrix());
            capturedProjectionMatrix.set(event.getProjectionMatrix());
        }
    }

    /**
     * [新增] 清理矩阵缓存
     */
    public static void clear() {
        capturedModelViewMatrix.identity();
        capturedProjectionMatrix.identity();
    }
}