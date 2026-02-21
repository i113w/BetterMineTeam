package com.i113w.better_mine_team.client;

import com.i113w.better_mine_team.client.gui.screen.EntityDetailsScreen;
import com.i113w.better_mine_team.client.gui.screen.TeamManagementScreen;
import com.i113w.better_mine_team.client.manager.ClientSelectionManager;
import com.i113w.better_mine_team.client.renderer.RTSCameraRenderer;
import com.i113w.better_mine_team.client.rts.RTSCameraManager;
import com.i113w.better_mine_team.client.rts.RTSSelectionManager;
import com.i113w.better_mine_team.client.rts.util.RenderMatrixStorage;
import com.i113w.better_mine_team.common.registry.ModEntities;
import com.i113w.better_mine_team.common.registry.ModMenuTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

// [1.20.1 修改] RegisterMenuScreensEvent 不存在，使用 FMLClientSetupEvent
@Mod.EventBusSubscriber(modid = com.i113w.better_mine_team.BetterMineTeam.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // [1.20.1 API] 使用 MenuScreens.register
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.ENTITY_DETAILS_MENU.get(), EntityDetailsScreen::new);
        });
    }
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.RTS_CAMERA.get(), RTSCameraRenderer::new);
    }

}

// 客户端 Forge 总线事件
@Mod.EventBusSubscriber(modid = com.i113w.better_mine_team.BetterMineTeam.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSelectionManager.clear();
        RTSCameraManager.get().reset();        // 强制退出 RTS 模式
        RTSSelectionManager.get().reset();     // 清理选区
        RenderMatrixStorage.clear();           // 清理矩阵缓存
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        while (ModKeyMappings.OPEN_TEAM_MENU.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null && mc.screen == null) {
                mc.setScreen(new TeamManagementScreen());
            }
        }
    }
}