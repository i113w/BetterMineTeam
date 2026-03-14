package com.i113w.better_mine_team.common.entity.goal;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GoalSanitizer {

    private static final Set<String> PROTECTED_CLASS_NAMES = new HashSet<>();
    private static final Set<String> BLACKLISTED_CLASS_NAMES = new HashSet<>();

    private static final Set<String> BUILTIN_PROTECTED = Set.of(
            "com.i113w.spears.event.MountVehicleGoal"
    );

    private GoalSanitizer() {}

    public static void loadProtectedClasses() {
        PROTECTED_CLASS_NAMES.clear();
        PROTECTED_CLASS_NAMES.addAll(BUILTIN_PROTECTED);
        PROTECTED_CLASS_NAMES.addAll(BMTConfig.getProtectedGoalClasses());

        BLACKLISTED_CLASS_NAMES.clear();
        BLACKLISTED_CLASS_NAMES.addAll(BMTConfig.getBlacklistedGoalClasses());

        BetterMineTeam.debug("GoalSanitizer: {} protected goal classes, {} blacklisted goal classes loaded.",
                PROTECTED_CLASS_NAMES.size(), BLACKLISTED_CLASS_NAMES.size());
    }

    public static void sanitize(Mob mob) {
        if (!BMTConfig.isAggressiveGoalRemovalEnabled()) return;

        int removedTarget = removeAggressiveFrom(mob, true);

        if (removedTarget > 0) {
            BetterMineTeam.debug("GoalSanitizer: removed {} targetSelector goals from {}.",
                    removedTarget, mob.getName().getString());
        }
    }

    // 替代 removeAllGoals
    private static int removeAggressiveFrom(Mob mob, boolean useTargetSelector) {
        var selector = useTargetSelector ? mob.targetSelector : mob.goalSelector;
        List<Goal> toRemove = new ArrayList<>();

        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            Goal goal = wrapped.getGoal();

            if (isBlacklisted(goal)) {
                BetterMineTeam.debug("GoalSanitizer: removing BLACKLISTED {} from {}.", goal.getClass().getSimpleName(), mob.getName().getString());
                toRemove.add(goal);
                continue;
            }

            if (!isAggressive(goal)) continue;
            if (isProtected(goal)) continue;

            BetterMineTeam.debug("GoalSanitizer: removing {} from {}.", goal.getClass().getSimpleName(), mob.getName().getString());
            toRemove.add(goal);
        }

        for (Goal goal : toRemove) {
            selector.removeGoal(goal);
        }

        return toRemove.size();
    }

    private static boolean isAggressive(Goal goal) {
        if (goal instanceof TeamGoal) return false;
        return goal instanceof TargetGoal;
    }

    private static boolean isProtected(Goal goal) {
        return PROTECTED_CLASS_NAMES.contains(goal.getClass().getName());
    }

    private static boolean isBlacklisted(Goal goal) {
        return BLACKLISTED_CLASS_NAMES.contains(goal.getClass().getName());
    }
}