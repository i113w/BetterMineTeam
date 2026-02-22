package com.i113w.better_mine_team.common.config;

import com.i113w.better_mine_team.BetterMineTeam;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BMTConfig {
    public static final ModConfigSpec CONFIG;

    // 通用
    private static final ModConfigSpec.BooleanValue enableTeamFocusFire;
    private static final ModConfigSpec.IntValue teamHateMemoryDuration;
    private static final ModConfigSpec.BooleanValue enableDebugLogging;
    private static final ModConfigSpec.DoubleValue remoteInventoryRange;
    private static final ModConfigSpec.BooleanValue enableMobTaming;
    private static final ModConfigSpec.BooleanValue showInventoryTeamButtons;
    private static final ModConfigSpec.BooleanValue autoAssignCaptain;
    private static final ModConfigSpec.BooleanValue enableTeammateCarry;
    private static final ModConfigSpec.BooleanValue enableSummonAutoJoin;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> summonAutoJoinBlacklist;
    private static final Set<EntityType<?>> summonBlacklistCache = new HashSet<>();

    // AI 参数
    private static final ModConfigSpec.BooleanValue defaultFollowEnabled;
    private static final ModConfigSpec.BooleanValue enableAutoTeleport;
    private static final ModConfigSpec.DoubleValue autoTeleportDistance;

    private static final ModConfigSpec.DoubleValue guardFollowRange;
    private static final ModConfigSpec.DoubleValue warPropagationRange;
    private static final ModConfigSpec.DoubleValue tacticalSwitchRange;
    private static final ModConfigSpec.DoubleValue guardFollowSpeed;
    private static final ModConfigSpec.DoubleValue guardFollowStartDist;
    private static final ModConfigSpec.DoubleValue guardFollowStopDist;

    // Dragon & Taming
    private static final ModConfigSpec.BooleanValue enableDragonTaming;
    private static final ModConfigSpec.BooleanValue enableDragonRiding;
    private static final ModConfigSpec.DoubleValue dragonBaseSpeed;
    private static final ModConfigSpec.DoubleValue dragonAcceleration;
    private static final ModConfigSpec.DoubleValue dragonDeceleration;
    private static final ModConfigSpec.DoubleValue dragonRotationSpeed;
    private static final ModConfigSpec.DoubleValue dragonPitchSpeed;

    private static final ModConfigSpec.ConfigValue<String> defaultTamingMaterial;
    private static final ModConfigSpec.ConfigValue<String> dragonTamingMaterial;

    private static final ModConfigSpec.ConfigValue<List<? extends String>> tamingMaterials;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> blacklistedEntities;

    private static final com.google.common.collect.BiMap<EntityType<?>, Ingredient> tamingMaterialMap = com.google.common.collect.HashBiMap.create();
    private static Ingredient cachedDefaultIngredient = Ingredient.of(Items.GOLDEN_APPLE);
    private static Ingredient cachedDragonIngredient = Ingredient.of(Items.GOLDEN_APPLE);
    private static final Set<EntityType<?>> blacklistedCache = new HashSet<>();
    private static final ModConfigSpec.DoubleValue dragonMaxPitch;

    private static final ModConfigSpec.IntValue followPathFailThreshold;
    private static final ModConfigSpec.DoubleValue rtsMovementSpeed;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

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

        enableTeammateCarry = builder
                .comment("Allow players to pick up (Carry On) team members, even if they are hostile mobs or blacklisted.")
                .comment("Requires 'Carry On' mod to be installed.")
                .define("enableTeammateCarry", true);

        builder.pop();

        builder.push("team_logic");
        autoAssignCaptain = builder
                .comment("Automatically assign captain permission when a player joins a team that has no other players.")
                .comment("This ignores non-player entities (mobs) in the team.")
                .define("autoAssignCaptain", true);
        builder.pop();

        builder.push("ai");
        defaultFollowEnabled = builder
                .comment("Whether entities should have 'Follow' enabled by default when joining a team.")
                .define("defaultFollowEnabled", true);

        enableAutoTeleport = builder
                .comment("Whether team entities following the captain should automatically teleport to them when too far.")
                .define("enableAutoTeleport", true);

        autoTeleportDistance = builder
                .comment("The distance at which a following entity will teleport to the captain.")
                .defineInRange("autoTeleportDistance", 24.0, 5.0, 128.0);

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

        rtsMovementSpeed = builder
                .comment("Movement speed multiplier for RTS-controlled units.")
                .comment("This affects both move and attack commands to ensure consistency.")
                .defineInRange("rtsMovementSpeed", 1.0, 0.5, 2.0);

        enableSummonAutoJoin = builder
                .comment("Automatically add summoned entities (e.g., Vexes, Wolves) to the owner's team.")
                .define("enableSummonAutoJoin", true);

        summonAutoJoinBlacklist = builder
                .comment("List of entity IDs that should NOT auto-join the owner's team when summoned.")
                .define("summonAutoJoinBlacklist", List.of("minecraft:wither_skull"), o -> true);
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
                .comment("Example: 'minecraft:golden_apple' or '#minecraft:flowers'")
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
                .comment("Example 1: ['minecraft:cow-{\"item\": \"minecraft:wheat\"}']")
                .comment("Example 2: ['minecraft:sheep-{\"tag\": \"minecraft:wool\"}']")
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
        // 加载默认材料
        loadDefaultMaterial();
        // 加载龙材料
        loadDragonMaterial();

        blacklistedCache.clear();
        for (String id : blacklistedEntities.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) BuiltInRegistries.ENTITY_TYPE.getOptional(rl).ifPresent(blacklistedCache::add);
        }

        tamingMaterialMap.clear();
        for (String entry : tamingMaterials.get()) {
            try {
                String[] split = entry.split("-", 2);
                if (split.length != 2) continue;
                ResourceLocation entityId = ResourceLocation.tryParse(split[0]);
                if (entityId != null) {
                    BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).ifPresent(entityType -> {
                        try {
                            JsonElement jsonElement = JsonParser.parseString(split[1]);
                            Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, jsonElement)
                                    .result().ifPresent(ingredient -> tamingMaterialMap.put(entityType, ingredient));
                        } catch (Exception ignored) {}
                    });
                }
            } catch (Exception ignored) {}
        }

        summonBlacklistCache.clear();
        for (String id : summonAutoJoinBlacklist.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) BuiltInRegistries.ENTITY_TYPE.getOptional(rl).ifPresent(summonBlacklistCache::add);
        }
    }

    private static void loadDefaultMaterial() { cachedDefaultIngredient = parseIngredientString(defaultTamingMaterial.get(), Items.GOLDEN_APPLE); }
    private static void loadDragonMaterial() { cachedDragonIngredient = parseIngredientString(dragonTamingMaterial.get(), Items.GOLDEN_APPLE); }

    private static Ingredient parseIngredientString(String str, net.minecraft.world.item.Item fallback) {
        try {
            if (str.startsWith("#")) {
                var result = Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive("{\"tag\": \"" + str.substring(1) + "\"}"));
                return result.result().orElse(Ingredient.of(fallback));
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
    public static double getRtsMovementSpeed() { return rtsMovementSpeed.get(); }
    public static boolean isAutoAssignCaptainEnabled() { return autoAssignCaptain.get(); }

    public static boolean isTeammateCarryEnabled() { return enableTeammateCarry.get(); }

    public static boolean isSummonAutoJoinEnabled() { return enableSummonAutoJoin.get(); }
    public static boolean isSummonBlacklisted(EntityType<?> type) { return summonBlacklistCache.contains(type); }

    public static boolean isDefaultFollowEnabled() { return defaultFollowEnabled.get(); }
    public static boolean isAutoTeleportEnabled() { return enableAutoTeleport.get(); }
    public static double getAutoTeleportDistanceSqr() { return autoTeleportDistance.get() * autoTeleportDistance.get(); }

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
    public static double getRemoteInventoryRangeSqr() { return remoteInventoryRange.get() * remoteInventoryRange.get(); }
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