package com.i113w.better_mine_team.common.config;

import com.i113w.better_mine_team.BetterMineTeam;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.IConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BMTConfig {
    public static final IConfigSpec CONFIG;

    // 通用
    private static final ForgeConfigSpec.BooleanValue enableTeamFocusFire;
    private static final ForgeConfigSpec.IntValue teamHateMemoryDuration;
    private static final ForgeConfigSpec.BooleanValue enableDebugLogging;
    private static final ForgeConfigSpec.DoubleValue remoteInventoryRange;
    private static final ForgeConfigSpec.BooleanValue enableMobTaming;
    private static final ForgeConfigSpec.BooleanValue showInventoryTeamButtons;

    private static final ForgeConfigSpec.BooleanValue autoGrantCaptainOnJoin;

    // AI 参数
    private static final ForgeConfigSpec.DoubleValue guardFollowRange;
    private static final ForgeConfigSpec.DoubleValue warPropagationRange;
    private static final ForgeConfigSpec.DoubleValue tacticalSwitchRange;
    private static final ForgeConfigSpec.DoubleValue guardFollowSpeed;
    private static final ForgeConfigSpec.DoubleValue guardFollowStartDist;
    private static final ForgeConfigSpec.DoubleValue guardFollowStopDist;

    // Dragon & Taming
    private static final ForgeConfigSpec.BooleanValue enableDragonTaming;
    private static final ForgeConfigSpec.BooleanValue enableDragonRiding;
    private static final ForgeConfigSpec.DoubleValue dragonBaseSpeed;
    private static final ForgeConfigSpec.DoubleValue dragonAcceleration;
    private static final ForgeConfigSpec.DoubleValue dragonDeceleration;
    private static final ForgeConfigSpec.DoubleValue dragonRotationSpeed;
    private static final ForgeConfigSpec.DoubleValue dragonPitchSpeed;
    private static final ForgeConfigSpec.ConfigValue<String> defaultTamingMaterial;
    private static final ForgeConfigSpec.ConfigValue<String> dragonTamingMaterial;

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> tamingMaterials;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> blacklistedEntities;

    private static final com.google.common.collect.BiMap<EntityType<?>, Ingredient> tamingMaterialMap = com.google.common.collect.HashBiMap.create();
    private static Ingredient cachedDefaultIngredient = Ingredient.of(Items.GOLDEN_APPLE);
    private static Ingredient cachedDragonIngredient = Ingredient.of(Items.GOLDEN_APPLE);
    private static final Set<EntityType<?>> blacklistedCache = new HashSet<>();
    private static final ForgeConfigSpec.DoubleValue dragonMaxPitch;

    private static final ForgeConfigSpec.IntValue followPathFailThreshold;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("general");
        enableTeamFocusFire = builder
                .comment("If true, when a team member is attacked, all nearby members will target the attacker.")
                .define("enableTeamFocusFire", true);

        teamHateMemoryDuration = builder
                .comment("How long (in ticks) a team remembers a target. Default: 2400 (2 mins).")
                .defineInRange("teamHateMemoryDuration", 2400, 100, 72000);

        remoteInventoryRange = builder
                .comment("The maximum distance (in blocks) allowed to interact with the entity inventory GUI remotely.")
                .defineInRange("remoteInventoryRange", 64.0, 5.0, 4096.0);

        enableDebugLogging = builder
                .comment("Enable console debug logging for team logic.")
                .define("enableDebugLogging", false);

        showInventoryTeamButtons = builder
                .comment("Whether to show the Team/PvP buttons in the inventory screen.")
                .define("showInventoryTeamButtons", true);

        autoGrantCaptainOnJoin = builder
                .comment("If true, when a player joins a team that has no other PLAYERS, they will automatically become the captain.")
                .define("autoGrantCaptainOnJoin", true);
        builder.pop();

        builder.push("ai");
        guardFollowRange = builder
                .comment("The detection range (attributes.follow_range) set for entities when they join a team.")
                .defineInRange("guardFollowRange", 256.0, 16.0, 1024.0);

        warPropagationRange = builder
                .comment("Range to scan for other enemies when a team member kills a target (War Mode).")
                .defineInRange("warPropagationRange", 64.0, 16.0, 512.0);

        tacticalSwitchRange = builder
                .comment("Range to scan for closer enemies when engaging a distant target.")
                .defineInRange("tacticalSwitchRange", 16.0, 4.0, 64.0);

        guardFollowSpeed = builder
                .comment("Speed modifier when following the captain.")
                .defineInRange("guardFollowSpeed", 1.2, 0.5, 3.0);

        guardFollowStartDist = builder
                .comment("Distance at which the entity starts following the captain.")
                .defineInRange("guardFollowStartDist", 10.0, 5.0, 64.0);

        guardFollowStopDist = builder
                .comment("Distance at which the entity stops following the captain.")
                .defineInRange("guardFollowStopDist", 2.0, 1.0, 16.0);

        followPathFailThreshold = builder
                .comment("How many failed pathfinding attempts before using direct movement.")
                .comment("Lower values make mobs 'stuck' less often but might cause clipping through walls.")
                .defineInRange("followPathFailThreshold", 5, 1, 20);
        builder.pop();

        builder.push("dragon");
        enableDragonTaming = builder.comment("Enable taming the Ender Dragon.").define("enableDragonTaming", true);
        enableDragonRiding = builder.comment("Enable riding the Ender Dragon after taming.").define("enableDragonRiding", true);
        dragonBaseSpeed = builder.comment("Base flight speed of the dragon.").defineInRange("dragonBaseSpeed", 1.5, 0.1, 5.0);
        dragonAcceleration = builder.comment("Acceleration rate per tick.").defineInRange("dragonAcceleration", 0.005, 0.001, 0.1);
        dragonDeceleration = builder.comment("Deceleration rate per tick.").defineInRange("dragonDeceleration", 0.01, 0.001, 0.1);
        dragonRotationSpeed = builder.comment("Turning speed.").defineInRange("dragonRotationSpeed", 2.5, 0.5, 10.0);
        dragonPitchSpeed = builder.comment("Pitch (up/down) speed.").defineInRange("dragonPitchSpeed", 2.0, 0.5, 10.0);
        dragonMaxPitch = builder
                .comment("The maximum pitch (up/down angle) in degrees for the dragon.")
                .comment("Set lower to prevent player clipping visually during steep dives.")
                .defineInRange("dragonMaxPitch", 35.0, 10.0, 90.0);
        builder.pop();

        builder.push("taming");
        enableMobTaming = builder
                .comment("Whether to allow players to tame mobs using items.")
                .comment("Set to false to disable taming functionality entirely (existing pets remain).")
                .define("enableMobTaming", true);
        defaultTamingMaterial = builder
                .comment("Default item/tag used to tame mobs if not specified in the list below.")
                .comment("Example: 'minecraft:wheat'")
                .define("defaultTamingMaterial", "minecraft:golden_apple");

        dragonTamingMaterial = builder
                .comment("Specific item/tag used to tame the Ender Dragon.")
                .define("dragonTamingMaterial", "minecraft:golden_apple");

        blacklistedEntities = builder
                .comment("List of entity IDs that cannot be tamed.")
                .define("blacklistedEntities", List.of(""), o -> true);

        tamingMaterials = builder
                .comment("List of specific materials for specific entities.")
                .comment("Format: 'entity_id-ingredient_json'")
                .comment("Example : ['minecraft:cow-{\"item\": \"minecraft:wheat\"}']")
                .define("tamingMaterials", List.of(), value -> {
                    if (value instanceof List<?> list) {
                        return list.stream().allMatch(o -> o instanceof String str && str.contains("-{"));
                    }
                    return false;
                });

        builder.pop();

        CONFIG = builder.build();
    }

    public static void loadTamingMaterials() {
        loadDefaultMaterial();
        loadDragonMaterial();

        blacklistedCache.clear();
        for (String id : blacklistedEntities.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                BuiltInRegistries.ENTITY_TYPE.getOptional(rl).ifPresent(blacklistedCache::add);
            }
        }

        tamingMaterialMap.clear();
        List<? extends String> materials = tamingMaterials.get();

        for (String entry : materials) {
            try {
                String[] split = entry.split("-", 2);
                if (split.length != 2) {
                    BetterMineTeam.LOGGER.warn("Invalid format for taming material: '{}'. Use 'entity_id-json_ingredient'", entry);
                    continue;
                }

                ResourceLocation entityId = ResourceLocation.tryParse(split[0]);
                String jsonString = split[1];

                if (entityId != null) {
                    BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).ifPresentOrElse(entityType -> {
                        try {
                            JsonElement jsonElement = JsonParser.parseString(jsonString);
                            Ingredient ingredient = Ingredient.fromJson(jsonElement);
                            tamingMaterialMap.put(entityType, ingredient);
                        } catch (Exception e) {
                            BetterMineTeam.LOGGER.error("JSON syntax error for {}: {}", entityId, e.getMessage());
                        }
                    }, () -> BetterMineTeam.LOGGER.warn("Entity type not found: {}", entityId));
                }
            }
            catch (Exception ignored) {}
        }
    }

    private static void loadDefaultMaterial() {
        cachedDefaultIngredient = parseIngredientString(defaultTamingMaterial.get(), Items.GOLDEN_APPLE);
    }

    private static void loadDragonMaterial() {
        cachedDragonIngredient = parseIngredientString(dragonTamingMaterial.get(), Items.GOLDEN_APPLE);
    }

    private static Ingredient parseIngredientString(String str, net.minecraft.world.item.Item fallback) {
        try {
            if (str.startsWith("#")) {
                BetterMineTeam.LOGGER.warn("Tag-based ingredients not fully supported in this version: {}", str);
                return Ingredient.of(fallback);
            } else {
                ResourceLocation rl = ResourceLocation.tryParse(str);
                if (rl != null) {
                    var item = BuiltInRegistries.ITEM.getOptional(rl);
                    if (item.isPresent()) return Ingredient.of(item.get());
                }
            }
        } catch (Exception ignored) {}
        return Ingredient.of(fallback);
    }

    // Getters
    public static boolean isTeamFocusFireEnabled() { return enableTeamFocusFire.get(); }
    public static int getTeamHateMemoryDuration() { return teamHateMemoryDuration.get(); }
    public static boolean isDebugEnabled() { return enableDebugLogging.get(); }
    public static boolean isAutoGrantCaptainEnabled() { return autoGrantCaptainOnJoin.get(); }

    public static double getGuardFollowRange() { return guardFollowRange.get(); }
    public static double getWarPropagationRange() { return warPropagationRange.get(); }
    public static double getTacticalSwitchRange() { return tacticalSwitchRange.get(); }
    public static double getGuardFollowSpeed() { return guardFollowSpeed.get(); }
    public static float getGuardFollowStartDist() { return guardFollowStartDist.get().floatValue(); }
    public static float getGuardFollowStopDist() { return guardFollowStopDist.get().floatValue(); }

    public static boolean isDragonTamingEnabled() { return enableDragonTaming.get(); }
    public static boolean isDragonRidingEnabled() { return enableDragonRiding.get(); }
    public static double getDragonBaseSpeed() { return dragonBaseSpeed.get(); }
    public static float getDragonAcceleration() { return dragonAcceleration.get().floatValue(); }
    public static float getDragonDeceleration() { return dragonDeceleration.get().floatValue(); }
    public static float getDragonRotationSpeed() { return dragonRotationSpeed.get().floatValue(); }
    public static float getDragonPitchSpeed() { return dragonPitchSpeed.get().floatValue(); }
    public static double getRemoteInventoryRangeSqr() {
        double range = remoteInventoryRange.get();
        return range * range;
    }
    public static com.google.common.collect.BiMap<EntityType<?>, Ingredient> getTamingMaterialMap() { return tamingMaterialMap; }

    public static Ingredient getTamingMaterial(EntityType<?> entityType) {
        if (blacklistedCache.contains(entityType)) return Ingredient.EMPTY;
        if (entityType == EntityType.ENDER_DRAGON) return cachedDragonIngredient;
        return tamingMaterialMap.getOrDefault(entityType, cachedDefaultIngredient);
    }
    public static float getDragonMaxPitch() { return dragonMaxPitch.get().floatValue(); }
    public static int getFollowPathFailThreshold() { return followPathFailThreshold.get(); }
    public static boolean isMobTamingEnabled() { return enableMobTaming.get(); }
    public static boolean isShowInventoryTeamButtons() { return showInventoryTeamButtons.get(); }
}