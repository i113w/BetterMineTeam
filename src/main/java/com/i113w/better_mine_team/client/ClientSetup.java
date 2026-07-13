package com.i113w.better_mine_team.client;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.screen.EntityDetailsScreen;
import com.i113w.better_mine_team.client.gui.screen.TeamManagementScreen;
import com.i113w.better_mine_team.client.manager.ClientSelectionManager;
import com.i113w.better_mine_team.client.rts.BMTInteractionDelegate;
import com.i113w.better_mine_team.client.rts.ClientRTSStateManager;
import com.i113w.better_mine_team.client.rts.ClientPatrolManager;
import com.i113w.better_mine_team.client.rts.ClientPatrolSettings;
import com.i113w.better_mine_team.common.registry.ModMenuTypes;
import com.i113w.camera_lib.api.CameraLibAPI;
import com.i113w.camera_lib.camera.RTSCameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.ENTITY_DETAILS_MENU.get(), EntityDetailsScreen::new);
            // 注册给 i113w_camera_lib
            CameraLibAPI.get().setInteractionDelegate(new BMTInteractionDelegate());
        });
    }

}

// 客户端 Forge 总线事件
@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSelectionManager.clear();
        ClientRTSStateManager.get().reset(); // 仅重置我们自己维护的状态即可
        ClientPatrolManager.clear();
        ClientPatrolSettings.reset();
        // 库会自动清理内部相机和选择状态
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        while (ModKeyMappings.OPEN_TEAM_MENU.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (RTSCameraController.get().isActive()) continue;

            if (mc.player != null && mc.level != null && mc.screen == null) {
                mc.setScreen(new TeamManagementScreen());
            }
        }
    }
}
