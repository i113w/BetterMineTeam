package com.i113w.better_mine_team.common.rts.ai.goal;

import com.i113w.better_mine_team.common.entity.goal.TeamGoal;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.config.PatrolSettings;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.network.handler.ServerPacketHandler;
import com.i113w.better_mine_team.common.registry.ModAttachments;
import com.i113w.better_mine_team.common.rts.ai.PatrolTargeting;
import com.i113w.better_mine_team.common.rts.data.PatrolMode;
import com.i113w.better_mine_team.common.rts.data.PatrolTask;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.scores.PlayerTeam;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PatrolGoal extends Goal implements TeamGoal {
    private final PathfinderMob mob;
    private RTSUnitData data;
    private BlockPos targetPos;
    private PatrolTask activeTask;
    private List<BlockPos> perimeterRoute = List.of();
    private int patrolDirection = 1;
    private int nextWaypointIndex = -1;
    private int repathTimer;
    private int retryCount;
    private int waitTicks;
    private long activeSettingsRevision = -1L;

    public PatrolGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.data = mob.getData(ModAttachments.UNIT_DATA);
        return canPatrolNow();
    }

    @Override
    public boolean canContinueToUse() {
        return canPatrolNow();
    }

    @Override
    public void start() {
        this.repathTimer = 0;
        this.retryCount = 0;
    }

    @Override
    public void tick() {
        PatrolSettings settings = BMTConfig.getPatrolSettings();
        PatrolTask task = data.getPatrolTask();
        ensurePerimeterRoute(task, settings);

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (targetPos == null) {
            chooseNextTarget(task, settings);
            return;
        }

        mob.getLookControl().setLookAt(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);

        if (mob.distanceToSqr(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D)
                <= settings.arrivalDistanceSqr()) {
            task.setLastValidPos(targetPos);
            mob.getNavigation().stop();
            targetPos = null;
            retryCount = 0;
            return;
        }

        if (--repathTimer <= 0 || mob.getNavigation().isDone()) {
            repathTimer = settings.repathIntervalTicks();
            boolean moving = mob.getNavigation().moveTo(
                    targetPos.getX() + 0.5D,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5D,
                    settings.movementSpeed()
            );
            if (!moving) {
                handlePathFailure(task);
            }
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        repathTimer = 0;
        waitTicks = Math.min(waitTicks, BMTConfig.getPatrolSettings().maxResumeDelayTicks());
    }

    private boolean canPatrolNow() {
        PatrolSettings settings = BMTConfig.getPatrolSettings();
        PatrolTask task = data.getPatrolTask();
        if (!task.isEnabled()) return false;

        if (!settings.enabled()) {
            clearInvalidTask();
            return false;
        }

        if (!isTaskStillValid(task, settings)) {
            clearInvalidTask();
            return false;
        }

        if (task.getMode() == PatrolMode.POINT && task.getRadius() != settings.pointRadius()) {
            task.setRadius(settings.pointRadius());
            ServerPacketHandler.syncPatrol(mob);
        }

        if (data.getCommand() != CommandType.STOP) return false;
        if (!PatrolTargeting.isTaskLocationLoaded(mob.level(), task)) return false;
        return !hasCombatPriority();
    }

    private boolean isTaskStillValid(PatrolTask task, PatrolSettings settings) {
        if (!mob.level().dimension().location().toString().equals(task.getDimensionId())) return false;
        if (task.getMode() == PatrolMode.AREA
                && !PatrolTargeting.isAreaSizeValid(task.getMinCorner(), task.getMaxCorner(), settings)) {
            return false;
        }

        PlayerTeam mobTeam = TeamManager.getTeam(mob);
        boolean teamMatches = !task.getTeamName().isBlank()
                && mobTeam != null
                && task.getTeamName().equals(mobTeam.getName());

        UUID owner = task.getOwnerUuid();
        boolean ownerMatches = false;
        if (owner != null && mob instanceof TamableAnimal tamable) {
            ownerMatches = owner.equals(tamable.getOwnerUUID());
        }

        return teamMatches || ownerMatches;
    }

    private void clearInvalidTask() {
        data.clearPatrolTask();
        mob.getNavigation().stop();
        ServerPacketHandler.syncPatrol(mob);
    }

    private boolean hasCombatPriority() {
        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget != null && currentTarget.isAlive() && !TeamManager.isAlly(mob, currentTarget)) {
            return true;
        }

        PlayerTeam team = TeamManager.getTeam(mob);
        return TeamManager.getBestThreat(team, mob) != null;
    }

    private void chooseNextTarget(PatrolTask task, PatrolSettings settings) {
        ensurePerimeterRoute(task, settings);
        if (perimeterRoute.isEmpty()) {
            waitTicks = settings.routeRetryDelayTicks();
            return;
        }

        int startIndex = nextWaypointIndex;
        if (startIndex < 0 || startIndex >= perimeterRoute.size()) {
            startIndex = PatrolTargeting.findNearestPerimeterIndex(perimeterRoute, mob.blockPosition());
        }

        Optional<PatrolTargeting.PerimeterTarget> next = PatrolTargeting.findReachablePerimeterTarget(
                mob,
                perimeterRoute,
                startIndex,
                patrolDirection,
                settings
        );
        if (next.isPresent()) {
            PatrolTargeting.PerimeterTarget waypoint = next.get();
            targetPos = waypoint.position();
            nextWaypointIndex = Math.floorMod(
                    waypoint.index() + patrolDirection,
                    perimeterRoute.size()
            );
            repathTimer = 0;
            return;
        }

        task.setLastValidPos(mob.blockPosition());
        waitTicks = settings.routeRetryDelayTicks();
    }

    private void handlePathFailure(PatrolTask task) {
        retryCount++;
        targetPos = null;
        PatrolSettings settings = BMTConfig.getPatrolSettings();
        if (retryCount <= settings.pathRetryLimit()) {
            return;
        }

        task.setLastValidPos(mob.blockPosition());
        mob.getNavigation().stop();
        retryCount = 0;
        waitTicks = settings.pathFailureCooldownTicks();
    }

    private void ensurePerimeterRoute(PatrolTask task, PatrolSettings settings) {
        if (activeTask == task && activeSettingsRevision == settings.revision()) return;

        activeTask = task;
        activeSettingsRevision = settings.revision();
        perimeterRoute = PatrolTargeting.createPerimeterRoute(task, settings);
        patrolDirection = mob.getRandom().nextBoolean() ? 1 : -1;
        nextWaypointIndex = PatrolTargeting.findNearestPerimeterIndex(
                perimeterRoute,
                mob.blockPosition()
        );
        targetPos = null;
        repathTimer = 0;
        retryCount = 0;
        waitTicks = 0;
    }
}
