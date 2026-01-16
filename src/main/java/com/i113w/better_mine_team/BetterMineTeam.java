package com.i113w.better_mine_team;

import com.i113w.better_mine_team.client.ClientSetup;
import com.i113w.better_mine_team.client.ModKeyMappings;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.registry.ModMenuTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(BetterMineTeam.MODID)
public class BetterMineTeam {
    public static final String MODID = "better_mine_team";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean IS_CONFLUENCE_LOADED = ModList.get().isLoaded("confluence");

    public BetterMineTeam() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册通用设置事件
        modEventBus.addListener(this::onFMLCommonSetup);

        // 注册配置文件 (Forge 1.20.1 使用 ModLoadingContext)
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BMTConfig.CONFIG, "better_mine_team.toml");

        // 注册菜单类型
        ModMenuTypes.register(modEventBus);

        // 客户端专用注册
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ModKeyMappings::onRegisterKeyMappings);
        }

        // 配置加载/重载监听
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);
    }

    private void onFMLCommonSetup(FMLCommonSetupEvent event) {
        // 在 FMLCommonSetupEvent 中注册网络包（必须在此时机）
        event.enqueueWork(() -> {
            MTNetworkRegister.register();
            LOGGER.info("Better Mine Team Common Setup Complete!");
        });
    }

    public static ResourceLocation asResource(String path) {
        return new ResourceLocation(MODID, path);
    }

    // [1.20.1 兼容] ResourceLocation 工厂方法保持不变
    // 1.20.1 使用构造函数: new ResourceLocation(namespace, path)
    // 1.21.1 使用静态方法: new ResourceLocation(namespace, path)

    public static void debug(String message, Object... params) {
        if (BMTConfig.isDebugEnabled()) {
            LOGGER.info("[BMT-DEBUG] " + message, params);
        }
    }

    private void onConfigLoad(net.minecraftforge.fml.event.config.ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == BMTConfig.CONFIG) {
            BMTConfig.loadTamingMaterials();
        }
    }

    private void onConfigReload(net.minecraftforge.fml.event.config.ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == BMTConfig.CONFIG) {
            LOGGER.info("Config reloaded, refreshing taming materials...");
            BMTConfig.loadTamingMaterials();
        }
    }
}