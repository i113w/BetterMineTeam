package com.i113w.better_mine_team.client;

import com.i113w.better_mine_team.client.gui.screen.EntityDetailsScreen;
import com.i113w.better_mine_team.client.manager.ClientSelectionManager;
import com.i113w.better_mine_team.client.renderer.RTSCameraRenderer;
import com.i113w.better_mine_team.common.registry.ModEntities;
import com.i113w.better_mine_team.common.registry.ModMenuTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public class ClientSetup {

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ENTITY_DETAILS_MENU.get(), EntityDetailsScreen::new);
    }

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.RTS_CAMERA.get(), RTSCameraRenderer::new);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSelectionManager.clear();
    }
}