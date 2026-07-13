package com.i113w.better_mine_team.common.rts.ai;

import com.i113w.better_mine_team.common.config.PatrolSettings;
import com.i113w.better_mine_team.common.rts.data.PatrolMode;
import com.i113w.better_mine_team.common.rts.data.PatrolTask;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class PatrolTargeting {
    private PatrolTargeting() {}

    public static BlockPos normalizedMin(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static BlockPos normalizedMax(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public static int areaWidth(BlockPos min, BlockPos max) { return max.getX() - min.getX() + 1; }
    public static int areaDepth(BlockPos min, BlockPos max) { return max.getZ() - min.getZ() + 1; }

    public static boolean isAreaSizeValid(BlockPos min, BlockPos max, PatrolSettings settings) {
        int width = areaWidth(min, max);
        int depth = areaDepth(min, max);
        return width >= settings.minAreaSize() && depth >= settings.minAreaSize()
                && width <= settings.maxAreaSize() && depth <= settings.maxAreaSize();
    }

    public static boolean isTaskLocationLoaded(Level level, PatrolTask task) {
        return task.getMode() == PatrolMode.POINT ? level.isLoaded(task.getCenter())
                : isAreaLoaded(level, task.getMinCorner(), task.getMaxCorner());
    }

    public static boolean isAreaLoaded(Level level, BlockPos min, BlockPos max) {
        for (int x = min.getX(); x <= max.getX(); x += 16) {
            for (int z = min.getZ(); z <= max.getZ(); z += 16) {
                if (!level.isLoaded(new BlockPos(x, min.getY(), z))) return false;
            }
        }
        return level.isLoaded(min) && level.isLoaded(max)
                && level.isLoaded(new BlockPos(min.getX(), min.getY(), max.getZ()))
                && level.isLoaded(new BlockPos(max.getX(), max.getY(), min.getZ()));
    }

    public static List<BlockPos> createPerimeterRoute(PatrolTask task, PatrolSettings settings) {
        if (task.getMode() == PatrolMode.AREA) {
            return createAreaPerimeter(task.getMinCorner(), task.getMaxCorner(), task.getCenter().getY(),
                    settings.waypointSpacing());
        }
        return createPointPerimeter(task.getCenter(), Math.max(1, task.getRadius()), settings);
    }

    public static int findNearestPerimeterIndex(List<BlockPos> route, BlockPos origin) {
        if (route.isEmpty()) return -1;
        int nearestIndex = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            BlockPos pos = route.get(i);
            double dx = pos.getX() - origin.getX();
            double dz = pos.getZ() - origin.getZ();
            double distance = dx * dx + dz * dz;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    public static Optional<PerimeterTarget> findReachablePerimeterTarget(PathfinderMob mob, List<BlockPos> route,
                                                                         int startIndex, int direction,
                                                                         PatrolSettings settings) {
        if (route.isEmpty()) return Optional.empty();
        int step = direction < 0 ? -1 : 1;
        int attempts = Math.min(route.size(), Math.max(1, settings.maxWaypointCandidates()));
        for (int i = 0; i < attempts; i++) {
            int index = Math.floorMod(startIndex + step * i, route.size());
            Optional<BlockPos> safe = findSafeStandPosition(mob, route.get(index), settings);
            if (safe.isPresent() && canReach(mob, safe.get())) return Optional.of(new PerimeterTarget(index, safe.get()));
        }
        return Optional.empty();
    }

    public static Optional<BlockPos> findSafeStandPosition(PathfinderMob mob, BlockPos around, PatrolSettings settings) {
        for (int y = around.getY() + settings.safeScanUp(); y >= around.getY() - settings.safeScanDown(); y--) {
            BlockPos candidate = new BlockPos(around.getX(), y, around.getZ());
            if (isSafeStandPosition(mob, candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public static boolean isSafeStandPosition(PathfinderMob mob, BlockPos pos) {
        Level level = mob.level();
        if (!level.isLoaded(pos)) return false;
        BlockState targetState = level.getBlockState(pos);
        if (!targetState.getCollisionShape(level, pos).isEmpty()) return false;
        if (targetState.is(BlockTags.FIRE) || targetState.getFluidState().is(FluidTags.LAVA)) return false;
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (!aboveState.getCollisionShape(level, above).isEmpty() || aboveState.getFluidState().is(FluidTags.LAVA)) return false;
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isSolidRender(level, below)) return false;
        AABB movedBox = mob.getBoundingBox().move(pos.subtract(mob.blockPosition()));
        return level.noCollision(mob, movedBox);
    }

    public static boolean canReach(PathfinderMob mob, BlockPos pos) {
        Path path = mob.getNavigation().createPath(pos, 0);
        return path != null && path.canReach();
    }

    private static List<BlockPos> createAreaPerimeter(BlockPos first, BlockPos second, int y, int spacing) {
        BlockPos min = normalizedMin(first, second);
        BlockPos max = normalizedMax(first, second);
        List<BlockPos> route = new ArrayList<>();
        appendSegment(route, min.getX(), min.getZ(), max.getX(), min.getZ(), y, spacing);
        appendSegment(route, max.getX(), min.getZ(), max.getX(), max.getZ(), y, spacing);
        appendSegment(route, max.getX(), max.getZ(), min.getX(), max.getZ(), y, spacing);
        appendSegment(route, min.getX(), max.getZ(), min.getX(), min.getZ(), y, spacing);
        return List.copyOf(route);
    }

    private static List<BlockPos> createPointPerimeter(BlockPos center, int radius, PatrolSettings settings) {
        int waypointCount = Math.max(settings.minimumPointWaypoints(),
                (int) Math.ceil((Math.PI * 2.0D * radius) / settings.waypointSpacing()));
        Set<BlockPos> route = new LinkedHashSet<>();
        for (int i = 0; i < waypointCount; i++) {
            double angle = Math.PI * 2.0D * i / waypointCount;
            route.add(new BlockPos(center.getX() + (int) Math.round(Math.cos(angle) * radius), center.getY(),
                    center.getZ() + (int) Math.round(Math.sin(angle) * radius)));
        }
        return List.copyOf(route);
    }

    private static void appendSegment(List<BlockPos> route, int startX, int startZ, int endX, int endZ, int y, int spacing) {
        int deltaX = Integer.compare(endX, startX);
        int deltaZ = Integer.compare(endZ, startZ);
        int length = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        for (int distance = 0; distance < length; distance += spacing) {
            addDistinct(route, new BlockPos(startX + deltaX * distance, y, startZ + deltaZ * distance));
        }
        addDistinct(route, new BlockPos(endX, y, endZ));
    }

    private static void addDistinct(List<BlockPos> route, BlockPos pos) {
        if (route.isEmpty() || !route.get(route.size() - 1).equals(pos)) {
            if (route.size() > 1 && route.get(0).equals(pos)) return;
            route.add(pos);
        }
    }

    public record PerimeterTarget(int index, BlockPos position) {}
}
