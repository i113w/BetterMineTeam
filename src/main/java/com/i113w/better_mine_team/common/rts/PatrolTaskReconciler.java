package com.i113w.better_mine_team.common.rts;

import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.config.PatrolSettings;
import com.i113w.better_mine_team.common.network.handler.ServerPacketHandler;
import com.i113w.better_mine_team.common.registry.ModAttachments;
import com.i113w.better_mine_team.common.rts.ai.PatrolTargeting;
import com.i113w.better_mine_team.common.rts.data.PatrolMode;
import com.i113w.better_mine_team.common.rts.data.PatrolTask;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;

public final class PatrolTaskReconciler {
    private PatrolTaskReconciler() {}

    public static void reconcileLoadedTasks(MinecraftServer server) {
        PatrolSettings settings = BMTConfig.getPatrolSettings();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof PathfinderMob mob)) continue;
                RTSUnitData data = mob.getData(ModAttachments.UNIT_DATA);
                PatrolTask task = data.getPatrolTask();
                if (!task.isEnabled()) continue;
                if (!settings.enabled() || task.getMode() == PatrolMode.AREA
                        && !PatrolTargeting.isAreaSizeValid(task.getMinCorner(), task.getMaxCorner(), settings)) {
                    data.clearPatrolTask();
                    mob.getNavigation().stop();
                    ServerPacketHandler.syncPatrol(mob);
                    continue;
                }
                if (task.getMode() == PatrolMode.POINT && task.getRadius() != settings.pointRadius()) {
                    task.setRadius(settings.pointRadius());
                    ServerPacketHandler.syncPatrol(mob);
                }
            }
        }
    }
}
