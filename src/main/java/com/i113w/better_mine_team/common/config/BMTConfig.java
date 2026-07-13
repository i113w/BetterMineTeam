package com.i113w.better_mine_team.common.config;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.entity.goal.GoalSanitizer;
import com.i113w.better_mine_team.common.registry.ModTags;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class BMTConfig {
    public static final ModConfigSpec CONFIG;
    private static final Pattern RESOURCE_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");

    public enum TamingListMode {
        BLACKLIST,
        WHITELIST
    }

    // 通用
    private static final ModConfigSpec.BooleanValue enableTeamFocusFire;
    private static final ModConfigSpec.IntValue teamHateMemoryDuration;
    private static final ModConfigSpec.BooleanValue enableDebugLogging;
    private static final ModConfigSpec.DoubleValue remoteInventoryRange;
    private static final ModConfigSpec.BooleanValue enableMobTaming;
    private static final ModConfigSpec.BooleanValue showInventoryTeamButtons;
    private static final ModConfigSpec.BooleanValue enableRTSMode;
    private static final ModConfigSpec.BooleanValue enableTeammateCarry;
    private static final ModConfigSpec.BooleanValue enableSummonAutoJoin;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> summonAutoJoinBlacklist;
    private static final Set<EntityType<?>> summonBlacklistCache = new HashSet<>();

    // GUI 黑名单
    private static final ModConfigSpec.ConfigValue<List<? extends String>> teamMemberListBlacklist;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> entityDetailsScreenBlacklist;
    private static final Set<EntityType<?>> teamMemberListBlacklistCache = new HashSet<>();
    private static final Set<EntityType<?>> entityDetailsScreenBlacklistCache = new HashSet<>();

    // 队伍逻辑与 AI 参数
    private static final ModConfigSpec.BooleanValue autoAssignCaptain;
    private static final ModConfigSpec.BooleanValue enablePersonalTeams;
    private static final ModConfigSpec.BooleanValue autoJoinPersonalTeamOnLogin;
    private static final ModConfigSpec.IntValue personalTeamCleanupDelay;
    private static final ModConfigSpec.BooleanValue aggressiveGoalRemovalEnabled;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> protectedGoalClasses;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> blacklistedGoalClasses;
    private static final ModConfigSpec.BooleanValue blockUnauthorizedAttacks;

    private static final ModConfigSpec.BooleanValue defaultFollowEnabled;
    private static final ModConfigSpec.BooleanValue defaultGlowEnabled;
    private static final ModConfigSpec.BooleanValue enableAutoTeleport;
    private static final ModConfigSpec.DoubleValue autoTeleportDistance;
    private static final ModConfigSpec.IntValue attackCommitmentHardTicks;
    private static final ModConfigSpec.IntValue attackCommitmentSoftTicks;
    private static final ModConfigSpec.DoubleValue attackCommitmentSwitchRatio;
    private static final ModConfigSpec.IntValue defaultAggressiveLevel;
    private static final ModConfigSpec.IntValue aggressiveScanEntityInterval;
    private static final ModConfigSpec.IntValue aggressiveScanTeamCooldown;
    private static final ModConfigSpec.DoubleValue aggressiveScanRadius;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> aggressiveEntityBlacklist;

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

    private static final ModConfigSpec.ConfigValue<TamingListMode> tamingListMode;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> tamingMaterials;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> blacklistedEntities;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> whitelistedEntities;

    private static final com.google.common.collect.BiMap<EntityType<?>, Ingredient> tamingMaterialMap = com.google.common.collect.HashBiMap.create();
    private static Ingredient cachedDefaultIngredient = Ingredient.of(Items.GOLDEN_APPLE);
    private static Ingredient cachedDragonIngredient = Ingredient.of(Items.GOLDEN_APPLE);
    private static final Set<EntityType<?>> blacklistedCache = new HashSet<>();
    private static final Set<String> blacklistedNamespaces = new HashSet<>();
    private static final Set<TagKey<EntityType<?>>> blacklistedTags = new HashSet<>();
    private static final Set<EntityType<?>> whitelistedCache = new HashSet<>();
    private static final Set<String> whitelistedNamespaces = new HashSet<>();
    private static final Set<TagKey<EntityType<?>>> whitelistedTags = new HashSet<>();
    private static final ModConfigSpec.DoubleValue dragonMaxPitch;

    private static final ModConfigSpec.IntValue followPathFailThreshold;
    private static final ModConfigSpec.DoubleValue rtsMovementSpeed;

    // Patrol
    private static final ModConfigSpec.BooleanValue patrolEnabled;
    private static final ModConfigSpec.IntValue patrolPointRadius;
    private static final ModConfigSpec.IntValue patrolMinAreaSize;
    private static final ModConfigSpec.IntValue patrolMaxAreaSize;
    private static final ModConfigSpec.DoubleValue patrolMaxCommandDistance;
    private static final ModConfigSpec.DoubleValue patrolMovementSpeed;
    private static final ModConfigSpec.IntValue patrolWaypointSpacing;
    private static final ModConfigSpec.IntValue patrolMinimumPointWaypoints;
    private static final ModConfigSpec.IntValue patrolMaxWaypointCandidates;
    private static final ModConfigSpec.IntValue patrolSafeScanUp;
    private static final ModConfigSpec.IntValue patrolSafeScanDown;
    private static final ModConfigSpec.IntValue patrolPathRetryLimit;
    private static final ModConfigSpec.IntValue patrolRepathIntervalTicks;
    private static final ModConfigSpec.DoubleValue patrolArrivalDistance;
    private static final ModConfigSpec.IntValue patrolRouteRetryDelayTicks;
    private static final ModConfigSpec.IntValue patrolPathFailureCooldownTicks;
    private static final ModConfigSpec.IntValue patrolMaxResumeDelayTicks;
    private static volatile PatrolSettings patrolSettings = PatrolSettings.DEFAULT;
    private static long patrolSettingsRevision;

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

        enableRTSMode = builder
                .comment("Enable RTS Mode and its UI buttons in the Team Management screen.")
                .define("enableRTSMode", true);

        enableTeammateCarry = builder
                .comment("Allow players to pick up (Carry On) team members, even if they are hostile mobs or blacklisted.")
                .comment("Requires 'Carry On' mod to be installed.")
                .define("enableTeammateCarry", true);
        builder.pop();

        // GUI 黑名单控制区域
        builder.push("gui");
        teamMemberListBlacklist = builder
                .comment("List of entity IDs that will be completely hidden from the Team Management GUI list.")
                .comment("Hidden entities can still be selected and commanded via RTS mode.")
                .comment("Example:[\"minecraft:wolf\", \"minecraft:iron_golem\"]")
                .defineListAllowEmpty("teamMemberListBlacklist", List.of(), o -> o instanceof String);

        entityDetailsScreenBlacklist = builder
                .comment("List of entity IDs whose inventory/details screen cannot be opened by normal players.")
                .comment("TeamsLord admins can bypass this restriction.")
                .comment("Example:[\"minecraft:zombie\", \"minecraft:skeleton\"]")
                .defineListAllowEmpty("entityDetailsScreenBlacklist", List.of(), o -> o instanceof String);
        builder.pop();

        builder.push("team_logic");
        autoAssignCaptain = builder
                .comment("Automatically assign captain permission when a player joins a team that has no other players.")
                .comment("This ignores non-player entities (mobs) in the team.")
                .define("autoAssignCaptain", true);

        enablePersonalTeams = builder
                .comment("Enable per-player personal teams managed by Better Mine Team.")
                .comment("When disabled, the inventory personal-team button is hidden and personal-team requests are rejected.")
                .define("enablePersonalTeams", true);

        autoJoinPersonalTeamOnLogin = builder
                .comment("Default personal-team preference for players who have never changed the inventory personal-team button.")
                .comment("If true, such players are automatically moved into their own personal team when joining the world.")
                .define("autoJoinPersonalTeamOnLogin", false);

        personalTeamCleanupDelay = builder
                .comment("How long an empty Better Mine Team personal team waits before being deleted, in ticks.")
                .comment("Only teams explicitly created and marked by this mod are eligible. Default: 12000 ticks (10 minutes).")
                .defineInRange("personalTeamCleanupDelay", 12000, 20, 72000);

        aggressiveGoalRemovalEnabled = builder
                .comment("When enabled, aggressive AI Goals (target selection and melee/ranged attack goals)")
                .comment("will be removed from a mob's goalSelector and targetSelector when it joins a team.")
                .comment("Basic locomotion goals (wandering, floating, door-opening, etc.) are always preserved.")
                .comment("Goals listed in 'protectedGoalClasses' are also never removed regardless of this setting.")
                .comment("Default: true")
                .define("aggressiveGoalRemovalEnabled", true);

        protectedGoalClasses = builder
                .comment("Fully-qualified class names of Goal subclasses that must NEVER be removed during")
                .comment("aggressive-goal sanitization, even if they would otherwise be considered aggressive.")
                .comment("Example: [\"com.example.mymod.ai.MySpecialGoal\"]")
                .defineListAllowEmpty("protectedGoalClasses", List.of(), o -> o instanceof String);

        blacklistedGoalClasses = builder
                .comment("Fully-qualified class names of Goal subclasses that must ALWAYS be removed during")
                .comment("sanitization, regardless of whether they are TargetGoals or in the whitelist.")
                .defineListAllowEmpty("blacklistedGoalClasses", List.of(), o -> o instanceof String);

        blockUnauthorizedAttacks = builder
                .comment("If true, blocks attacks from team members that are initiated by vanilla AI instead of TeamGoals.")
                .define("blockUnauthorizedAttacks", true);
        builder.pop();

        builder.push("ai");
        defaultFollowEnabled = builder
                .comment("Whether entities should have 'Follow' enabled by default when joining a team.")
                .define("defaultFollowEnabled", false);

        defaultGlowEnabled = builder
                .comment("Whether non-player entities should glow by default when joining a team.")
                .define("defaultGlowEnabled", true);

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

        attackCommitmentHardTicks = builder
                .comment("Duration (in ticks) of the HARD commitment phase after a mob locks onto a target.")
                .comment("During this window the mob will absolutely refuse to switch targets, even to a closer one.")
                .comment("Set to 0 to disable hard locking entirely.")
                .comment("Default: 20 ticks (1 second)")
                .defineInRange("attackCommitmentHardTicks", 20, 0, 200);

        attackCommitmentSoftTicks = builder
                .comment("Total duration (in ticks) of the full commitment window (hard phase + soft phase combined).")
                .comment("After the hard phase ends the mob enters a soft phase: it will only switch if the new target")
                .comment("is significantly closer (see attackCommitmentSwitchRatio).")
                .comment("Must be >= attackCommitmentHardTicks. Set to 0 to disable commitment entirely.")
                .comment("Default: 60 ticks (3 seconds)")
                .defineInRange("attackCommitmentSoftTicks", 60, 0, 400);

        attackCommitmentSwitchRatio = builder
                .comment("During the soft commitment phase, a new target must be within this fraction of the current")
                .comment("target's distance (squared) before the mob will switch.")
                .comment("Example: 0.5 means the new target must be at most 50% as far away as the current target.")
                .comment("Lower values make mobs harder to divert. Range: 0.1 (very sticky) to 1.0 (easy to divert).")
                .comment("Default: 0.5")
                .defineInRange("attackCommitmentSwitchRatio", 0.5, 0.1, 1.0);

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

        defaultAggressiveLevel = builder
                .comment("Default Aggressive level assigned to mobs when they join a team.")
                .comment("0 = Passive (current behavior, no proactive attacks)")
                .comment("1 = Guard  (attacks entities that are currently targeting a team member)")
                .comment("2 = Aggressive (attacks all non-team PathfinderMobs in range)")
                .defineInRange("defaultAggressiveLevel", 0, 0, 2);

        aggressiveScanEntityInterval = builder
                .comment("How often (in ticks) each individual mob runs its aggressive scan.")
                .comment("Lower = more responsive but higher CPU. Default: 20 ticks (1 second).")
                .defineInRange("aggressiveScanEntityInterval", 20, 5, 200);

        aggressiveScanTeamCooldown = builder
                .comment("Minimum ticks between full AABB scans for the same team.")
                .comment("When multiple mobs of the same team would scan simultaneously,")
                .comment("only the first one executes the query; others skip until cooldown expires.")
                .comment("Default: 10 ticks (0.5 seconds).")
                .defineInRange("aggressiveScanTeamCooldown", 10, 1, 100);

        aggressiveScanRadius = builder
                .comment("Radius (in blocks) of the AABB search used for Level 1 and Level 2 aggressive scans.")
                .comment("Default: 16.0 blocks.")
                .defineInRange("aggressiveScanRadius", 16.0, 4.0, 64.0);

        aggressiveEntityBlacklist = builder
                .comment("Entity type resource locations to exclude from Level 2 aggressive targeting.")
                .comment("These entities will never be attacked by Level 2 mobs, regardless of team.")
                .comment("Example: [\"minecraft:villager\", \"minecraft:snow_golem\"]")
                .defineListAllowEmpty("aggressiveEntityBlacklist",
                        List.of("minecraft:pig"),
                        o -> o instanceof String);
        builder.pop();

        builder.push("patrol");
        patrolEnabled = builder
                .comment("Enable Patrol mode, its Team Management button, commands, and AI tasks.")
                .define("enabled", true);
        patrolPointRadius = builder
                .comment("Radius in blocks used by point Patrol assignments.")
                .defineInRange("pointRadius", 10, 1, 128);
        patrolMinAreaSize = builder
                .comment("Minimum width and depth, in blocks, for an area Patrol assignment.")
                .defineInRange("minAreaSize", 4, 1, 128);
        patrolMaxAreaSize = builder
                .comment("Maximum width and depth, in blocks, for an area Patrol assignment.")
                .defineInRange("maxAreaSize", 32, 1, 128);
        patrolMaxCommandDistance = builder
                .comment("Maximum distance in blocks from a player to a Patrol target or area corner.")
                .defineInRange("maxCommandDistance", 1024.0D, 16.0D, 4096.0D);
        patrolMovementSpeed = builder
                .comment("Movement speed multiplier used by patrolling mobs.")
                .defineInRange("movementSpeed", 1.0D, 0.1D, 3.0D);
        patrolWaypointSpacing = builder
                .comment("Spacing in blocks between generated perimeter waypoints.")
                .defineInRange("waypointSpacing", 3, 1, 32);
        patrolMinimumPointWaypoints = builder
                .comment("Minimum waypoint count for circular point Patrol routes.")
                .defineInRange("minimumPointWaypoints", 8, 4, 64);
        patrolMaxWaypointCandidates = builder
                .comment("Maximum perimeter candidates tested when looking for a reachable waypoint.")
                .defineInRange("maxWaypointCandidates", 64, 1, 128);
        patrolSafeScanUp = builder
                .comment("Blocks scanned upward when looking for a safe Patrol standing position.")
                .defineInRange("safeScanUp", 4, 0, 32);
        patrolSafeScanDown = builder
                .comment("Blocks scanned downward when looking for a safe Patrol standing position.")
                .defineInRange("safeScanDown", 8, 0, 64);
        patrolPathRetryLimit = builder
                .comment("Failed path attempts allowed before applying the Patrol failure cooldown.")
                .defineInRange("pathRetryLimit", 3, 0, 20);
        patrolRepathIntervalTicks = builder
                .comment("Ticks between Patrol path recalculations.")
                .defineInRange("repathIntervalTicks", 20, 1, 200);
        patrolArrivalDistance = builder
                .comment("Distance in blocks at which a Patrol waypoint is considered reached.")
                .defineInRange("arrivalDistance", 1.0D, 0.1D, 8.0D);
        patrolRouteRetryDelayTicks = builder
                .comment("Delay before retrying when no reachable Patrol route is available.")
                .defineInRange("routeRetryDelayTicks", 60, 1, 1200);
        patrolPathFailureCooldownTicks = builder
                .comment("Cooldown after Patrol path failures exceed pathRetryLimit.")
                .defineInRange("pathFailureCooldownTicks", 40, 1, 1200);
        patrolMaxResumeDelayTicks = builder
                .comment("Maximum remaining Patrol wait retained after combat or another interruption.")
                .defineInRange("maxResumeDelayTicks", 20, 0, 200);
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

        tamingListMode = builder
                .comment("BLACKLIST: every entity can be tamed unless denied by blacklist rules.")
                .comment("WHITELIST: only entities allowed by whitelist rules can be tamed.")
                .comment("Blacklist rules always win over whitelist rules.")
                .defineEnum("tamingListMode", TamingListMode.BLACKLIST);

        blacklistedEntities = builder
                .comment("Entities that cannot be tamed.")
                .comment("Supports entity IDs, entity type tags prefixed with '#', and namespace wildcards.")
                .comment("Examples: [\"minecraft:zombie\", \"#better_mine_team:untameable\", \"i113w_camera_lib:*\"]")
                .comment("The namespace wildcard \"i113w_camera_lib:*\" matches every entity type from that mod, including \"i113w_camera_lib:rts_camera\".")
                .defineListAllowEmpty("blacklistedEntities", List.of(), o -> o instanceof String);

        whitelistedEntities = builder
                .comment("Entities that can be tamed when tamingListMode is WHITELIST.")
                .comment("Supports entity IDs, entity type tags prefixed with '#', and namespace wildcards.")
                .comment("Examples: [\"minecraft:cow\", \"#better_mine_team:tameable\", \"i113w_camera_lib:*\"]")
                .comment("The namespace wildcard \"i113w_camera_lib:*\" matches every entity type from that mod, including \"i113w_camera_lib:rts_camera\".")
                .defineListAllowEmpty("whitelistedEntities", List.of(), o -> o instanceof String);

        tamingMaterials = builder
                .comment("List of specific materials for specific entities.")
                .comment("Format: 'entity_id-ingredient_json'")
                .comment("Example 1: [\"minecraft:cow-{\\\"item\\\": \\\"minecraft:wheat\\\"}\"]")
                .comment("Example 2: [\"minecraft:sheep-{\\\"tag\\\": \\\"minecraft:wool\\\"}\"]")
                .defineListAllowEmpty("tamingMaterials", List.of(), value -> {
                    if (value instanceof String str) {
                        return str.contains("-{");
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

        loadTamingRules();

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

        // 解析并缓存 GUI 黑名单
        teamMemberListBlacklistCache.clear();
        for (String id : teamMemberListBlacklist.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) BuiltInRegistries.ENTITY_TYPE.getOptional(rl).ifPresent(teamMemberListBlacklistCache::add);
        }

        entityDetailsScreenBlacklistCache.clear();
        for (String id : entityDetailsScreenBlacklist.get()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) BuiltInRegistries.ENTITY_TYPE.getOptional(rl).ifPresent(entityDetailsScreenBlacklistCache::add);
        }
        GoalSanitizer.loadProtectedClasses();
    }

    public static void bakePatrolSettings() {
        int rawMinAreaSize = patrolMinAreaSize.get();
        int rawMaxAreaSize = patrolMaxAreaSize.get();
        int effectiveMinAreaSize = Math.min(rawMinAreaSize, rawMaxAreaSize);
        int effectiveMaxAreaSize = Math.max(rawMinAreaSize, rawMaxAreaSize);
        if (rawMinAreaSize > rawMaxAreaSize) {
            BetterMineTeam.LOGGER.warn(
                    "Patrol minAreaSize ({}) is greater than maxAreaSize ({}); using {}..{}",
                    rawMinAreaSize,
                    rawMaxAreaSize,
                    effectiveMinAreaSize,
                    effectiveMaxAreaSize
            );
        }

        patrolSettings = new PatrolSettings(
                patrolEnabled.get(),
                patrolPointRadius.get(),
                effectiveMinAreaSize,
                effectiveMaxAreaSize,
                patrolMaxCommandDistance.get(),
                patrolMovementSpeed.get(),
                patrolWaypointSpacing.get(),
                patrolMinimumPointWaypoints.get(),
                patrolMaxWaypointCandidates.get(),
                patrolSafeScanUp.get(),
                patrolSafeScanDown.get(),
                patrolPathRetryLimit.get(),
                patrolRepathIntervalTicks.get(),
                patrolArrivalDistance.get(),
                patrolRouteRetryDelayTicks.get(),
                patrolPathFailureCooldownTicks.get(),
                patrolMaxResumeDelayTicks.get(),
                ++patrolSettingsRevision
        );
    }

    private static void loadTamingRules() {
        blacklistedCache.clear();
        blacklistedNamespaces.clear();
        blacklistedTags.clear();
        loadTamingRuleList(blacklistedEntities.get(), blacklistedCache, blacklistedNamespaces, blacklistedTags);

        whitelistedCache.clear();
        whitelistedNamespaces.clear();
        whitelistedTags.clear();
        loadTamingRuleList(whitelistedEntities.get(), whitelistedCache, whitelistedNamespaces, whitelistedTags);
    }

    private static void loadTamingRuleList(List<? extends String> entries,
                                           Set<EntityType<?>> entities,
                                           Set<String> namespaces,
                                           Set<TagKey<EntityType<?>>> tags) {
        for (String entry : entries) {
            if (entry == null) continue;

            String rule = entry.trim();
            if (rule.isEmpty()) continue;

            if (rule.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(rule.substring(1));
                if (tagId != null) tags.add(TagKey.create(Registries.ENTITY_TYPE, tagId));
                continue;
            }

            if (rule.endsWith(":*")) {
                String namespace = rule.substring(0, rule.length() - 2);
                if (RESOURCE_NAMESPACE.matcher(namespace).matches()) namespaces.add(namespace);
                continue;
            }

            ResourceLocation rl = ResourceLocation.tryParse(rule);
            if (rl != null) BuiltInRegistries.ENTITY_TYPE.getOptional(rl).ifPresent(entities::add);
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
    public static PatrolSettings getPatrolSettings() { return patrolSettings; }
    public static boolean isAutoAssignCaptainEnabled() { return autoAssignCaptain.get(); }
    public static boolean isPersonalTeamsEnabled() { return enablePersonalTeams.get(); }
    public static boolean isAutoJoinPersonalTeamOnLogin() { return autoJoinPersonalTeamOnLogin.get(); }
    public static int getPersonalTeamCleanupDelay() { return personalTeamCleanupDelay.get(); }
    public static boolean isTeammateCarryEnabled() { return enableTeammateCarry.get(); }
    public static boolean isSummonAutoJoinEnabled() { return enableSummonAutoJoin.get(); }
    public static boolean isSummonBlacklisted(EntityType<?> type) { return summonBlacklistCache.contains(type); }

    public static boolean isRTSModeEnabled() { return enableRTSMode.get(); } // [新增]
    public static boolean isBlockUnauthorizedAttacksEnabled() { return blockUnauthorizedAttacks.get(); } // [新增]

    public static boolean isEntityHiddenFromMemberList(EntityType<?> type) { return teamMemberListBlacklistCache.contains(type); }
    public static boolean isEntityDetailsScreenBlacklisted(EntityType<?> type) { return entityDetailsScreenBlacklistCache.contains(type); }

    public static boolean isDefaultFollowEnabled() { return defaultFollowEnabled.get(); }
    public static boolean isDefaultGlowEnabled() { return defaultGlowEnabled.get(); }
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
        if (!canTame(entityType)) return Ingredient.EMPTY;
        if (entityType == EntityType.ENDER_DRAGON) return cachedDragonIngredient;
        return tamingMaterialMap.getOrDefault(entityType, cachedDefaultIngredient);
    }
    public static boolean canTame(EntityType<?> entityType) {
        if (isTamingBlacklisted(entityType)) return false;
        if (tamingListMode.get() == TamingListMode.WHITELIST) return isTamingWhitelisted(entityType);
        return true;
    }
    public static boolean isTamingBlacklisted(EntityType<?> entityType) {
        return entityType.is(ModTags.Entities.UNTAMEABLE)
                || matchesTamingRules(entityType, blacklistedCache, blacklistedNamespaces, blacklistedTags);
    }
    public static boolean isTamingWhitelisted(EntityType<?> entityType) {
        return entityType.is(ModTags.Entities.TAMEABLE)
                || matchesTamingRules(entityType, whitelistedCache, whitelistedNamespaces, whitelistedTags);
    }
    private static boolean matchesTamingRules(EntityType<?> entityType,
                                              Set<EntityType<?>> entities,
                                              Set<String> namespaces,
                                              Set<TagKey<EntityType<?>>> tags) {
        if (entities.contains(entityType)) return true;

        ResourceLocation entityId = entityType.builtInRegistryHolder().key().location();
        if (namespaces.contains(entityId.getNamespace())) return true;

        for (TagKey<EntityType<?>> tag : tags) {
            if (entityType.is(tag)) return true;
        }
        return false;
    }
    public static float getDragonMaxPitch() { return dragonMaxPitch.get().floatValue(); }
    public static int getFollowPathFailThreshold() { return followPathFailThreshold.get(); }
    public static boolean isMobTamingEnabled() { return enableMobTaming.get(); }
    public static boolean isShowInventoryTeamButtons() { return showInventoryTeamButtons.get(); }
    public static int getAttackCommitmentHardTicks()   { return attackCommitmentHardTicks.get(); }
    public static int getAttackCommitmentSoftTicks()    { return attackCommitmentSoftTicks.get(); }
    public static double getAttackCommitmentSwitchRatio() { return attackCommitmentSwitchRatio.get(); }
    public static boolean isAggressiveGoalRemovalEnabled() { return aggressiveGoalRemovalEnabled.get(); }

    @SuppressWarnings("unchecked")
    public static List<String> getProtectedGoalClasses() { return (List<String>) protectedGoalClasses.get(); }

    @SuppressWarnings("unchecked")
    public static List<String> getBlacklistedGoalClasses() { return (List<String>) blacklistedGoalClasses.get(); } // [新增]

    public static int getDefaultAggressiveLevel()      { return defaultAggressiveLevel.get(); }
    public static int getAggressiveScanEntityInterval() { return aggressiveScanEntityInterval.get(); }
    public static int getAggressiveScanTeamCooldown()   { return aggressiveScanTeamCooldown.get(); }
    public static double getAggressiveScanRadius()      { return aggressiveScanRadius.get(); }

    @SuppressWarnings("unchecked")
    public static List<String> getAggressiveEntityBlacklist() {
        return (List<String>) aggressiveEntityBlacklist.get();
    }
}
