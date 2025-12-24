package com.i113w.better_mine_team;

import com.i113w.better_mine_team.client.ClientSetup;
import com.mojang.logging.LogUtils;
import com.i113w.better_mine_team.common.config.MineTeamConfig;
// import com.xiaohunao.better_mine_team.common.init.MTAttachmentTypes; // 已删除，不再需要导入
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import com.i113w.better_mine_team.common.init.MTNetworkRegister; // 导入你的注册类
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import com.i113w.better_mine_team.common.registry.ModMenuTypes; // 导入我们刚写的注册类
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(BetterMineTeam.MODID)
public class BetterMineTeam {
    public static final String MODID = "better_mine_team";
    public static final Logger LOGGER = LogUtils.getLogger();
    // 检查兼容性模组是否存在
    public static final boolean IS_CONFLUENCE_LOADED = ModList.get().isLoaded("confluence");

    public BetterMineTeam(IEventBus modEventBus, ModContainer modContainer) {
        // 核心修改：删除了附件类型的注册，因为我们改用了原版计分板
        // MTAttachmentTypes.ATTACHMENT_TYPES.register(modEventBus);

        modEventBus.addListener(this::onFMLCommonSetup);

        // 注册配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, MineTeamConfig.CONFIG, "better_mine_team.toml");
        modEventBus.addListener(MTNetworkRegister::registerPayload);
        ModMenuTypes.register(modEventBus);
        modEventBus.addListener(ClientSetup::registerScreens);


    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    @SubscribeEvent
    public void onFMLCommonSetup(FMLCommonSetupEvent event) {
        // 初始化驯服材料配置
        MineTeamConfig.loadTamingMaterials();
    }

}