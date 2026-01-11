package com.i113w.better_mine_team.client.gui;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.team.TeamRender;
import com.i113w.better_mine_team.common.config.BMTConfig;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class GuiEventHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (BetterMineTeam.IS_CONFLUENCE_LOADED) return;
        if (!BMTConfig.isShowInventoryTeamButtons()) return;
        // 仅在原版生存背包或创造背包初始化完成后，挂载我们的组件
        if (event.getScreen() instanceof InventoryScreen || event.getScreen() instanceof CreativeModeInventoryScreen) {
            // [修复] 现在 TeamRender 有了这个静态方法
            TeamRender.attachTo(event);
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (BetterMineTeam.IS_CONFLUENCE_LOADED) return;

        if (event.getScreen() instanceof InventoryScreen || event.getScreen() instanceof CreativeModeInventoryScreen) {
            // [修复] 使用 onRender 进行每帧逻辑 (tick)
            // 注意：原来的 renderOverlay 改名为 onRender 了，
            // 且不需要传 GuiGraphics，因为按钮已经在 Init 阶段注册给 Screen，Screen 会自动渲染它们。
            // 这里的 onRender 主要是为了更新按钮状态 (tick)。
            TeamRender.onRender(event);
        }
    }
}