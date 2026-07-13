package com.i113w.better_mine_team.common.config;

import com.i113w.better_mine_team.BetterMineTeam;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Locale;
import java.util.regex.Pattern;

public final class BMTClientConfig {
    private static final Pattern RGBA_HEX = Pattern.compile("#[0-9a-fA-F]{8}");
    public static final ForgeConfigSpec CONFIG;

    private static final ForgeConfigSpec.BooleanValue showPatrolMarkers;
    private static final ForgeConfigSpec.BooleanValue showSelectedPatrolBounds;
    private static final ForgeConfigSpec.BooleanValue showPatrolDragPreview;
    private static final ForgeConfigSpec.ConfigValue<String> patrolMarkerColor;
    private static final ForgeConfigSpec.ConfigValue<String> assignedBoundsColor;
    private static final ForgeConfigSpec.ConfigValue<String> validPreviewColor;
    private static final ForgeConfigSpec.ConfigValue<String> invalidPreviewColor;
    private static final ForgeConfigSpec.DoubleValue markerMinHalfSize;
    private static final ForgeConfigSpec.DoubleValue markerWidthMultiplier;
    private static final ForgeConfigSpec.DoubleValue markerVerticalOffset;
    private static final ForgeConfigSpec.DoubleValue markerHeight;
    private static final ForgeConfigSpec.DoubleValue groundBoxVerticalOffset;
    private static final ForgeConfigSpec.DoubleValue groundBoxHeight;

    private static volatile PatrolVisualSettings patrolVisualSettings = PatrolVisualSettings.DEFAULT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Better Mine Team - Client Configuration").push("patrol_visual");
        showPatrolMarkers = builder.comment("Render a marker above mobs that currently have a Patrol task.")
                .define("showPatrolMarkers", true);
        showSelectedPatrolBounds = builder.comment("Render the Patrol bounds for currently selected mobs.")
                .define("showSelectedPatrolBounds", true);
        showPatrolDragPreview = builder.comment("Render the area preview while right-dragging in Patrol mode.")
                .define("showPatrolDragPreview", true);
        patrolMarkerColor = defineColor(builder, "patrolMarkerColor", "#FF0000F2");
        assignedBoundsColor = defineColor(builder, "assignedBoundsColor", "#FF0000F2");
        validPreviewColor = defineColor(builder, "validPreviewColor", "#FF0000D9");
        invalidPreviewColor = defineColor(builder, "invalidPreviewColor", "#BF7373A6");
        markerMinHalfSize = builder.defineInRange("markerMinHalfSize", 0.25D, 0.05D, 4.0D);
        markerWidthMultiplier = builder.defineInRange("markerWidthMultiplier", 0.35D, 0.0D, 2.0D);
        markerVerticalOffset = builder.defineInRange("markerVerticalOffset", 0.35D, -2.0D, 8.0D);
        markerHeight = builder.defineInRange("markerHeight", 0.45D, 0.01D, 8.0D);
        groundBoxVerticalOffset = builder.defineInRange("groundBoxVerticalOffset", 0.05D, -1.0D, 2.0D);
        groundBoxHeight = builder.defineInRange("groundBoxHeight", 0.04D, 0.001D, 1.0D);
        builder.pop();
        CONFIG = builder.build();
    }

    private BMTClientConfig() {}

    private static ForgeConfigSpec.ConfigValue<String> defineColor(ForgeConfigSpec.Builder builder,
                                                                    String name, String defaultValue) {
        return builder.comment("RGBA color in #RRGGBBAA format.")
                .define(name, defaultValue, value -> value instanceof String text && RGBA_HEX.matcher(text).matches());
    }

    public static void bakePatrolVisualSettings() {
        patrolVisualSettings = new PatrolVisualSettings(
                showPatrolMarkers.get(), showSelectedPatrolBounds.get(), showPatrolDragPreview.get(),
                parseColor(patrolMarkerColor.get(), PatrolVisualSettings.DEFAULT.patrolMarkerColor()),
                parseColor(assignedBoundsColor.get(), PatrolVisualSettings.DEFAULT.assignedBoundsColor()),
                parseColor(validPreviewColor.get(), PatrolVisualSettings.DEFAULT.validPreviewColor()),
                parseColor(invalidPreviewColor.get(), PatrolVisualSettings.DEFAULT.invalidPreviewColor()),
                markerMinHalfSize.get(), markerWidthMultiplier.get(), markerVerticalOffset.get(), markerHeight.get(),
                groundBoxVerticalOffset.get(), groundBoxHeight.get()
        );
    }

    public static PatrolVisualSettings getPatrolVisualSettings() {
        return patrolVisualSettings;
    }

    private static PatrolColor parseColor(String value, PatrolColor fallback) {
        try {
            long rgba = Long.parseLong(value.toUpperCase(Locale.ROOT).substring(1), 16);
            return new PatrolColor(((rgba >> 24) & 0xFF) / 255.0F, ((rgba >> 16) & 0xFF) / 255.0F,
                    ((rgba >> 8) & 0xFF) / 255.0F, (rgba & 0xFF) / 255.0F);
        } catch (RuntimeException exception) {
            BetterMineTeam.LOGGER.warn("Invalid Patrol color '{}', using fallback", value);
            return fallback;
        }
    }

    public record PatrolVisualSettings(
            boolean showPatrolMarkers, boolean showSelectedPatrolBounds, boolean showPatrolDragPreview,
            PatrolColor patrolMarkerColor, PatrolColor assignedBoundsColor,
            PatrolColor validPreviewColor, PatrolColor invalidPreviewColor,
            double markerMinHalfSize, double markerWidthMultiplier, double markerVerticalOffset,
            double markerHeight, double groundBoxVerticalOffset, double groundBoxHeight
    ) {
        public static final PatrolVisualSettings DEFAULT = new PatrolVisualSettings(
                true, true, true,
                new PatrolColor(1.0F, 0.0F, 0.0F, 0.95F),
                new PatrolColor(1.0F, 0.0F, 0.0F, 0.95F),
                new PatrolColor(1.0F, 0.0F, 0.0F, 0.85F),
                new PatrolColor(0.75F, 0.45F, 0.45F, 0.65F),
                0.25D, 0.35D, 0.35D, 0.45D, 0.05D, 0.04D
        );
    }

    public record PatrolColor(float red, float green, float blue, float alpha) {}
}
