package com.i113w.better_mine_team.common.rts.ai.goal;

import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.entity.goal.TeamGoal;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.registry.ModAttachments;
import com.i113w.better_mine_team.common.rts.ai.PatrolCombatBoundary;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class PatrolCombatLeashGoal extends Goal implements TeamGoal {
    private final PathfinderMob mob;
    private RTSUnitData data;
    private int checkTicker;

    public PatrolCombatLeashGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        this.data = mob.getData(ModAttachments.UNIT_DATA);
        return data.getPatrolTask().isEnabled();
    }

    @Override
    public boolean canContinueToUse() {
        return data.getPatrolTask().isEnabled();
    }

    @Override
    public void start() {
        checkTicker = 0;
    }

    @Override
    public void tick() {
        if (++checkTicker < BMTConfig.getPatrolSettings().combatLeashCheckIntervalTicks()) return;
        checkTicker = 0;

        LivingEntity target = mob.getTarget();
        if (target == null || PatrolCombatBoundary.canEngage(mob, target)) return;

        mob.setTarget(null);
        mob.getNavigation().stop();
        if (data.getCommand() == CommandType.ATTACK) {
            data.stop();
        }
    }
}
