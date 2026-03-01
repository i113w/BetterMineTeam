package com.i113w.better_mine_team;

import com.i113w.better_mine_team.client.ClientSetup;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.registry.ModEntities;
import com.i113w.better_mine_team.common.registry.ModMenuTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        // 配置加载/重载监听
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);
        // RTS
        ModEntities.register(modEventBus);
        modEventBus.addListener(ClientSetup::registerEntityRenderers);

        // 注册 /reload 监听器（Forge 总线）
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<BMTConfig.BlacklistSnapshot>() {

            @Override
            protected BMTConfig.BlacklistSnapshot prepare(
                    ResourceManager manager, ProfilerFiller profiler) {
                Path configPath = FMLPaths.CONFIGDIR.get().resolve("better_mine_team.toml");

                Set<EntityType<?>> teamMemberSet = new HashSet<>();
                Set<EntityType<?>> entityDetailsSet = new HashSet<>();

                try (CommentedFileConfig fileConfig = CommentedFileConfig.of(configPath)) {
                    fileConfig.load();

                    List<String> teamMemberRaw = fileConfig.getOrElse(
                            List.of("gui", "teamMemberListBlacklist"), List.of());
                    List<String> entityDetailsRaw = fileConfig.getOrElse(
                            List.of("gui", "entityDetailsScreenBlacklist"), List.of());

                    for (String id : teamMemberRaw) {
                        ResourceLocation rl = ResourceLocation.tryParse(id);
                        if (rl != null) {
                            BuiltInRegistries.ENTITY_TYPE.getOptional(rl)
                                    .ifPresent(teamMemberSet::add);
                        }
                    }
                    for (String id : entityDetailsRaw) {
                        ResourceLocation rl = ResourceLocation.tryParse(id);
                        if (rl != null) {
                            BuiltInRegistries.ENTITY_TYPE.getOptional(rl)
                                    .ifPresent(entityDetailsSet::add);
                        }
                    }

                    LOGGER.info("[BMT] /reload: have loaded teamMember={} counts, entityDetails={} counts",
                            teamMemberSet.size(), entityDetailsSet.size());

                } catch (Exception e) {
                    LOGGER.warn("[BMT] failed to load config file in reloading, jump this turn of refreshing: {}", e.getMessage());
                    // 返回 null 让 apply() 跳过写入，保留现有缓存
                    return null;
                }

                return new BMTConfig.BlacklistSnapshot(teamMemberSet, entityDetailsSet);
            }

            @Override
            protected void apply(BMTConfig.BlacklistSnapshot snapshot,
                                 ResourceManager manager, ProfilerFiller profiler) {
                // 在主线程原子替换缓存引用
                if (snapshot == null) {
                    LOGGER.warn("[BMT] /reload 配置读取失败，缓存保持不变");
                    return;
                }
                BMTConfig.applyBlacklistSnapshot(snapshot);
                LOGGER.info("[BMT] Config blacklists refreshed via /reload");
            }
        });
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