package com.i113w.better_mine_team.client.gui;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.team.TeamRender;
import com.i113w.better_mine_team.common.config.BMTConfig;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// [关键修改] ScreenEvent 属于 Forge 总线
@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GuiEventHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (BetterMineTeam.IS_CONFLUENCE_LOADED) return;
        if (!BMTConfig.isShowInventoryTeamButtons()) return;

        if (event.getScreen() instanceof InventoryScreen || event.getScreen() instanceof CreativeModeInventoryScreen) {
            TeamRender.attachTo(event);
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (BetterMineTeam.IS_CONFLUENCE_LOADED) return;

        if (event.getScreen() instanceof InventoryScreen || event.getScreen() instanceof CreativeModeInventoryScreen) {
            TeamRender.onRender(event);
        }
    }
}