package com.i113w.better_mine_team.client;

import com.i113w.better_mine_team.client.gui.screen.EntityDetailsScreen;
import com.i113w.better_mine_team.client.manager.ClientSelectionManager;
import com.i113w.better_mine_team.common.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
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
}

// 客户端 Forge 总线事件
@Mod.EventBusSubscriber(modid = com.i113w.better_mine_team.BetterMineTeam.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ClientSelectionManager.clear();
    }
}