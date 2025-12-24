package com.i113w.better_mine_team.common.registry;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.menu.EntityDetailsMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, BetterMineTeam.MODID);

    // 注册实体详情菜单
    public static final DeferredHolder<MenuType<?>, MenuType<EntityDetailsMenu>> ENTITY_DETAILS_MENU =
            MENUS.register("entity_details", () -> IMenuTypeExtension.create(EntityDetailsMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}