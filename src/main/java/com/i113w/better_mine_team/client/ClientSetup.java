package com.i113w.better_mine_team.client;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.screen.EntityDetailsScreen;
import com.i113w.better_mine_team.client.manager.ClientSelectionManager;
import com.i113w.better_mine_team.client.rts.BmtRTSDelegate;
import com.i113w.better_mine_team.client.rts.BmtRTSManager;
import com.i113w.better_mine_team.client.rts.ClientPatrolManager;
import com.i113w.better_mine_team.client.rts.ClientPatrolSettings;
import com.i113w.better_mine_team.common.registry.ModMenuTypes;
import com.i113w.camera_lib.api.CameraLibAPI;
import com.i113w.camera_lib.camera.RTSCameraController;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class ClientSetup {

    public static void onClientSetup(FMLClientSetupEvent event) {
        CameraLibAPI.get().setInteractionDelegate(new BmtRTSDelegate());
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ENTITY_DETAILS_MENU.get(), EntityDetailsScreen::new);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSelectionManager.clear();
        ClientSelectionManager.syncToLib();
        RTSCameraController.get().reset();
        BmtRTSManager.reset();
        ClientPatrolManager.clear();
        ClientPatrolSettings.reset();
    }
}
