package com.i113w.better_mine_team.common.registry;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.menu.EntityDetailsMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    // [关键修改] 1.20.1 使用 DeferredRegister.create()，不再使用泛型参数
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, BetterMineTeam.MODID);

    // [关键修改] RegistryObject 代替 DeferredHolder
    // IForgeMenuType 代替 IMenuTypeExtension
    public static final RegistryObject<MenuType<EntityDetailsMenu>> ENTITY_DETAILS_MENU =
            MENUS.register("entity_details", () -> IForgeMenuType.create(EntityDetailsMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}