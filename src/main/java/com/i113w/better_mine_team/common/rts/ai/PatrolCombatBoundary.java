package com.i113w.better_mine_team.common.rts.ai;

import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.config.PatrolSettings;
import com.i113w.better_mine_team.common.registry.ModAttachments;
import com.i113w.better_mine_team.common.rts.data.PatrolMode;
import com.i113w.better_mine_team.common.rts.data.PatrolTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class PatrolCombatBoundary {
    private PatrolCombatBoundary() {}

    public static boolean canEngage(Mob mob, LivingEntity target) {
        PatrolTask task = mob.getData(ModAttachments.UNIT_DATA).getPatrolTask();
        if (!task.isEnabled() || !BMTConfig.getPatrolSettings().enabled()) return true;
        if (target == null || target.level() != mob.level()) return false;
        return contains(task, mob) && contains(task, target);
    }

    public static boolean contains(PatrolTask task, Entity entity) {
        return contains(task, entity.getX(), entity.getZ(), BMTConfig.getPatrolSettings());
    }

    static boolean contains(PatrolTask task, double x, double z, PatrolSettings settings) {
        if (!task.isEnabled()) return true;
        double minimumPadding = settings.combatLeashMinPadding();
        double scale = settings.combatLeashScale();
        if (task.getMode() == PatrolMode.POINT) {
            double radius = Math.max(0.0D, task.getRadius());
            double padding = Math.max(minimumPadding, radius * scale);
            BlockPos center = task.getCenter();
            double dx = x - (center.getX() + 0.5D);
            double dz = z - (center.getZ() + 0.5D);
            double combatRadius = radius + padding;
            return dx * dx + dz * dz <= combatRadius * combatRadius;
        }
        BlockPos min = PatrolTargeting.normalizedMin(task.getMinCorner(), task.getMaxCorner());
        BlockPos max = PatrolTargeting.normalizedMax(task.getMinCorner(), task.getMaxCorner());
        double halfWidth = (max.getX() - min.getX() + 1.0D) * 0.5D;
        double halfDepth = (max.getZ() - min.getZ() + 1.0D) * 0.5D;
        double paddingX = Math.max(minimumPadding, halfWidth * scale);
        double paddingZ = Math.max(minimumPadding, halfDepth * scale);
        return x >= min.getX() - paddingX && x <= max.getX() + 1.0D + paddingX
                && z >= min.getZ() - paddingZ && z <= max.getZ() + 1.0D + paddingZ;
    }
}
