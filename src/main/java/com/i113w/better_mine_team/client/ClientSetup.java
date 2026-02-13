package com.i113w.better_mine_team.client;

import com.i113w.better_mine_team.client.gui.screen.EntityDetailsScreen;
import com.i113w.better_mine_team.client.manager.ClientSelectionManager;
import com.i113w.better_mine_team.client.renderer.RTSCameraRenderer;
import com.i113w.better_mine_team.client.rts.RTSCameraManager;
import com.i113w.better_mine_team.client.rts.RTSSelectionManager;
import com.i113w.better_mine_team.client.rts.util.RenderMatrixStorage;
import com.i113w.better_mine_team.common.registry.ModEntities;
import com.i113w.better_mine_team.common.registry.ModMenuTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class ClientSetup {

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ENTITY_DETAILS_MENU.get(), EntityDetailsScreen::new);
    }

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.RTS_CAMERA.get(), RTSCameraRenderer::new);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        // 1. 清理基础选择管理器
        ClientSelectionManager.clear();

        // 2. [修复] 强制退出 RTS 模式并重置相机状态
        RTSCameraManager.get().reset();

        // 3. [修复] 清理 RTS 选区和拖拽状态
        RTSSelectionManager.get().reset();

        // 4. [修复] 清理渲染矩阵缓存
        RenderMatrixStorage.clear();
    }
}