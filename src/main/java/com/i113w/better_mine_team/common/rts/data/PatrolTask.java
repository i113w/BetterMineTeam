package com.i113w.better_mine_team.common.rts.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PatrolTask {
    private boolean enabled;
    private UUID ownerUuid;
    private String teamName = "";
    private String dimensionId = "";
    private PatrolMode mode = PatrolMode.POINT;
    private BlockPos center = BlockPos.ZERO;
    private int radius = 10;
    private BlockPos minCorner = BlockPos.ZERO;
    private BlockPos maxCorner = BlockPos.ZERO;
    private BlockPos lastValidPos = BlockPos.ZERO;

    public static PatrolTask disabled() {
        return new PatrolTask();
    }

    public static PatrolTask point(@Nullable UUID ownerUuid, String teamName, String dimensionId,
                                   BlockPos center, int radius, BlockPos lastValidPos) {
        PatrolTask task = new PatrolTask();
        task.enabled = true;
        task.ownerUuid = ownerUuid;
        task.teamName = teamName == null ? "" : teamName;
        task.dimensionId = dimensionId == null ? "" : dimensionId;
        task.mode = PatrolMode.POINT;
        task.center = center;
        task.radius = radius;
        task.minCorner = center;
        task.maxCorner = center;
        task.lastValidPos = lastValidPos;
        return task;
    }

    public static PatrolTask area(@Nullable UUID ownerUuid, String teamName, String dimensionId,
                                  BlockPos center, BlockPos minCorner, BlockPos maxCorner,
                                  BlockPos lastValidPos) {
        PatrolTask task = new PatrolTask();
        task.enabled = true;
        task.ownerUuid = ownerUuid;
        task.teamName = teamName == null ? "" : teamName;
        task.dimensionId = dimensionId == null ? "" : dimensionId;
        task.mode = PatrolMode.AREA;
        task.center = center;
        task.radius = 0;
        task.minCorner = minCorner;
        task.maxCorner = maxCorner;
        task.lastValidPos = lastValidPos;
        return task;
    }

    public PatrolTask copy() {
        PatrolTask task = new PatrolTask();
        task.enabled = this.enabled;
        task.ownerUuid = this.ownerUuid;
        task.teamName = this.teamName;
        task.dimensionId = this.dimensionId;
        task.mode = this.mode;
        task.center = this.center;
        task.radius = this.radius;
        task.minCorner = this.minCorner;
        task.maxCorner = this.maxCorner;
        task.lastValidPos = this.lastValidPos;
        return task;
    }

    public void save(ValueOutput output) {
        output.putBoolean("Enabled", enabled);
        if (!enabled) return;

        if (ownerUuid != null) output.putString("Owner", ownerUuid.toString());
        output.putString("Team", teamName);
        output.putString("Dimension", dimensionId);
        output.putString("Mode", mode.name());
        putBlockPos(output, "Center", center);
        output.putInt("Radius", radius);
        putBlockPos(output, "Min", minCorner);
        putBlockPos(output, "Max", maxCorner);
        putBlockPos(output, "Last", lastValidPos);
    }

    public static PatrolTask load(ValueInput input) {
        PatrolTask task = new PatrolTask();
        task.enabled = input.getBooleanOr("Enabled", false);
        if (!task.enabled) return task;

        String owner = input.getStringOr("Owner", "");
        if (!owner.isBlank()) {
            try {
                task.ownerUuid = UUID.fromString(owner);
            } catch (IllegalArgumentException ignored) {
                task.ownerUuid = null;
            }
        }
        task.teamName = input.getStringOr("Team", "");
        task.dimensionId = input.getStringOr("Dimension", "");
        try {
            task.mode = PatrolMode.valueOf(input.getStringOr("Mode", PatrolMode.POINT.name()));
        } catch (Exception ignored) {
            task.mode = PatrolMode.POINT;
        }
        task.center = getBlockPos(input, "Center");
        task.radius = input.getIntOr("Radius", 10);
        task.minCorner = getBlockPos(input, "Min");
        task.maxCorner = getBlockPos(input, "Max");
        task.lastValidPos = getBlockPos(input, "Last");
        return task;
    }

    private static void putBlockPos(ValueOutput output, String prefix, BlockPos pos) {
        output.putInt(prefix + "X", pos.getX());
        output.putInt(prefix + "Y", pos.getY());
        output.putInt(prefix + "Z", pos.getZ());
    }

    private static BlockPos getBlockPos(ValueInput input, String prefix) {
        return new BlockPos(
                input.getIntOr(prefix + "X", 0),
                input.getIntOr(prefix + "Y", 0),
                input.getIntOr(prefix + "Z", 0)
        );
    }

    public boolean isEnabled() { return enabled; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getTeamName() { return teamName; }
    public String getDimensionId() { return dimensionId; }
    public PatrolMode getMode() { return mode; }
    public BlockPos getCenter() { return center; }
    public int getRadius() { return radius; }
    public BlockPos getMinCorner() { return minCorner; }
    public BlockPos getMaxCorner() { return maxCorner; }
    public BlockPos getLastValidPos() { return lastValidPos; }

    public void setLastValidPos(BlockPos lastValidPos) { this.lastValidPos = lastValidPos; }
    public void setRadius(int radius) { this.radius = radius; }
}
