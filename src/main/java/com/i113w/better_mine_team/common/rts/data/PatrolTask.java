package com.i113w.better_mine_team.common.rts.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Enabled", enabled);
        if (!enabled) return tag;

        if (ownerUuid != null) {
            tag.putString("Owner", ownerUuid.toString());
        }
        tag.putString("Team", teamName);
        tag.putString("Dimension", dimensionId);
        tag.putString("Mode", mode.name());
        putBlockPos(tag, "Center", center);
        tag.putInt("Radius", radius);
        putBlockPos(tag, "Min", minCorner);
        putBlockPos(tag, "Max", maxCorner);
        putBlockPos(tag, "Last", lastValidPos);
        return tag;
    }

    public static PatrolTask load(CompoundTag tag) {
        PatrolTask task = new PatrolTask();
        task.enabled = tag.getBoolean("Enabled");
        if (!task.enabled) return task;

        String owner = tag.getString("Owner");
        if (!owner.isBlank()) {
            try {
                task.ownerUuid = UUID.fromString(owner);
            } catch (IllegalArgumentException ignored) {
                task.ownerUuid = null;
            }
        }
        task.teamName = tag.getString("Team");
        task.dimensionId = tag.getString("Dimension");
        try {
            task.mode = PatrolMode.valueOf(tag.getString("Mode"));
        } catch (Exception ignored) {
            task.mode = PatrolMode.POINT;
        }
        task.center = getBlockPos(tag, "Center");
        task.radius = tag.getInt("Radius");
        task.minCorner = getBlockPos(tag, "Min");
        task.maxCorner = getBlockPos(tag, "Max");
        task.lastValidPos = getBlockPos(tag, "Last");
        return task;
    }

    private static void putBlockPos(CompoundTag tag, String prefix, BlockPos pos) {
        tag.putInt(prefix + "X", pos.getX());
        tag.putInt(prefix + "Y", pos.getY());
        tag.putInt(prefix + "Z", pos.getZ());
    }

    private static BlockPos getBlockPos(CompoundTag tag, String prefix) {
        return new BlockPos(tag.getInt(prefix + "X"), tag.getInt(prefix + "Y"), tag.getInt(prefix + "Z"));
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

    public void setLastValidPos(BlockPos lastValidPos) {
        this.lastValidPos = lastValidPos;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }
}
