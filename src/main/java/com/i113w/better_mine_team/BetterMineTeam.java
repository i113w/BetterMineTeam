package com.i113w.better_mine_team;

import com.i113w.better_mine_team.client.ClientSetup;
import com.i113w.better_mine_team.client.ModKeyMappings;
import com.mojang.logging.LogUtils;
import com.i113w.better_mine_team.common.config.BMTConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import net.neoforged.fml.event.config.ModConfigEvent;

import com.i113w.better_mine_team.common.registry.ModMenuTypes;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(BetterMineTeam.MODID)
public class BetterMineTeam {
    public static final String MODID = "better_mine_team";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean IS_CONFLUENCE_LOADED = ModList.get().isLoaded("confluence");

    public BetterMineTeam(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onFMLCommonSetup);

        // 注册配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, BMTConfig.CONFIG, "better_mine_team.toml");
        modEventBus.addListener(MTNetworkRegister::registerPayload);
        ModMenuTypes.register(modEventBus);

        // 将屏幕注册移动到 if (CLIENT) 代码块内部
        // 这样服务器永远不会尝试加载 ClientSetup 类，也就不会触发 Screen 类缺失的错误
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ClientSetup::registerScreens);
            modEventBus.addListener(ModKeyMappings::onRegisterKeyMappings);
        }
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    @SubscribeEvent
    public void onFMLCommonSetup(FMLCommonSetupEvent event) {
    }

    public static void debug(String message, Object... params) {
        if (BMTConfig.isDebugEnabled()) {
            LOGGER.info("[BMT-DEBUG] " + message, params);
        }
    }
    public void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == BMTConfig.CONFIG) {
            BMTConfig.loadTamingMaterials();
        }
    }
    public void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == BMTConfig.CONFIG) {
            LOGGER.info("Config reloaded, refreshing taming materials...");
            BMTConfig.loadTamingMaterials();
        }
    }
}