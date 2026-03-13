package com.i113w.better_mine_team.common.entity.goal;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

import java.util.HashSet;
import java.util.Set;

public final class GoalSanitizer {

    private static final Set<String> PROTECTED_CLASS_NAMES = new HashSet<>();
    private static final Set<String> BLACKLISTED_CLASS_NAMES = new HashSet<>(); // [新增] 黑名单缓存

    private static final Set<String> BUILTIN_PROTECTED = Set.of(
            "com.i113w.spears.event.MountVehicleGoal"
    );

    private GoalSanitizer() {}

    public static void loadProtectedClasses() {
        PROTECTED_CLASS_NAMES.clear();
        PROTECTED_CLASS_NAMES.addAll(BUILTIN_PROTECTED);
        PROTECTED_CLASS_NAMES.addAll(BMTConfig.getProtectedGoalClasses());

        // 加载黑名单
        BLACKLISTED_CLASS_NAMES.clear();
        BLACKLISTED_CLASS_NAMES.addAll(BMTConfig.getBlacklistedGoalClasses());

        BetterMineTeam.debug(
                "GoalSanitizer: {} protected goal classes loaded ({} built-in), {} blacklisted goal classes loaded.",
                PROTECTED_CLASS_NAMES.size(),
                BUILTIN_PROTECTED.size(),
                BLACKLISTED_CLASS_NAMES.size());
    }

    public static void sanitize(Mob mob) {
        if (!BMTConfig.isAggressiveGoalRemovalEnabled()) return;

        int removedTarget = removeAggressiveFrom(mob, true);

        if (removedTarget > 0) {
            BetterMineTeam.debug(
                    "GoalSanitizer: removed {} targetSelector goals from {}.",
                    removedTarget,
                    mob.getName().getString());
        }
    }

    private static int removeAggressiveFrom(Mob mob, boolean useTargetSelector) {
        var selector = useTargetSelector ? mob.targetSelector : mob.goalSelector;
        int[] count = {0};

        selector.removeAllGoals(goal -> {
            // 第 0 层优先级：如果在黑名单中，强制移除
            if (isBlacklisted(goal)) {
                BetterMineTeam.debug(
                        "GoalSanitizer: removing BLACKLISTED {} from {} of {}.",
                        goal.getClass().getSimpleName(),
                        useTargetSelector ? "targetSelector" : "goalSelector",
                        mob.getName().getString());
                count[0]++;
                return true;
            }

            if (!isAggressive(goal)) return false;
            if (isProtected(goal))   return false;

            BetterMineTeam.debug(
                    "GoalSanitizer: removing {} from {} of {}.",
                    goal.getClass().getSimpleName(),
                    useTargetSelector ? "targetSelector" : "goalSelector",
                    mob.getName().getString());
            count[0]++;
            return true;
        });

        return count[0];
    }

    private static boolean isAggressive(Goal goal) {
        if (goal instanceof TeamGoal) return false;
        if (goal instanceof TargetGoal) return true;
        return false;
    }

    private static boolean isProtected(Goal goal) {
        return PROTECTED_CLASS_NAMES.contains(goal.getClass().getName());
    }

    // 判断是否在黑名单中
    private static boolean isBlacklisted(Goal goal) {
        return BLACKLISTED_CLASS_NAMES.contains(goal.getClass().getName());
    }
}