package com.i113w.better_mine_team.client;

import com.i113w.better_mine_team.client.gui.screen.EntityDetailsScreen;
import com.i113w.better_mine_team.common.registry.ModMenuTypes;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// 移除注解，变成一个普通的工具类
public class ClientSetup {

    // 保持方法为 static
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ENTITY_DETAILS_MENU.get(), EntityDetailsScreen::new);
    }
}