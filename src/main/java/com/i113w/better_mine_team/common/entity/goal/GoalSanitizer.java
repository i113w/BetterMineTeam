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

/**
 * Utility class responsible for removing aggressive AI Goals from mobs that join a team.
 *
 * <h3>Identification strategy (three layers, in priority order):</h3>
 * <ol>
 *   <li>{@link TeamGoal} implementors → always exempt (our own goals)</li>
 *   <li>Protected class-name whitelist → exempt (third-party goals we must keep)</li>
 *   <li>Inheritance-chain check → aggressive if the goal is a subclass of
 *       {@link TargetGoal}, {@link MeleeAttackGoal}, or {@link RangedAttackGoal}</li>
 * </ol>
 *
 * <h3>Coverage:</h3>
 * <ul>
 *   <li>{@code TargetGoal} subtypes: NearestAttackableTargetGoal, HurtByTargetGoal,
 *       OwnerHurtByTargetGoal, and any third-party TargetGoal subclass.</li>
 *   <li>{@code MeleeAttackGoal} subtypes: ZombieAttackGoal, and any third-party subclass.</li>
 *   <li>{@code RangedAttackGoal}: explicit check because it extends {@code Goal} directly,
 *       not {@code MeleeAttackGoal}.</li>
 * </ul>
 */
public final class GoalSanitizer {

    /**
     * Full class-names that are always protected from removal, regardless of their
     * inheritance chain.  Populated from Config + a hard-coded set of known third-party
     * goals.  Refreshed on every config load/reload via {@link #loadProtectedClasses()}.
     */
    private static final Set<String> PROTECTED_CLASS_NAMES = new HashSet<>();

    /**
     * Hard-coded entries that are always protected even if the user removes them from
     * the config list.  Add known third-party goals here.
     */
    private static final Set<String> BUILTIN_PROTECTED = Set.of(
            "com.i113w.spears.event.MountVehicleGoal"
    );

    private GoalSanitizer() {}

    // -----------------------------------------------------------------------
    // Config lifecycle
    // -----------------------------------------------------------------------

    /**
     * Rebuilds the protected class-name cache from Config.
     * Call this inside {@code BMTConfig.loadTamingMaterials()} or any config-load hook.
     */
    public static void loadProtectedClasses() {
        PROTECTED_CLASS_NAMES.clear();
        PROTECTED_CLASS_NAMES.addAll(BUILTIN_PROTECTED);
        PROTECTED_CLASS_NAMES.addAll(BMTConfig.getProtectedGoalClasses());
        BetterMineTeam.debug("GoalSanitizer: {} protected goal classes loaded ({} built-in + {} from config).",
                PROTECTED_CLASS_NAMES.size(),
                BUILTIN_PROTECTED.size(),
                BMTConfig.getProtectedGoalClasses().size());
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Removes all aggressive Goals from both {@code goalSelector} and
     * {@code targetSelector} of the given mob, unless:
     * <ul>
     *   <li>The feature is disabled via Config, or</li>
     *   <li>The goal implements {@link TeamGoal}, or</li>
     *   <li>The goal's fully-qualified class name is in the protected whitelist.</li>
     * </ul>
     *
     * <p><strong>Call order requirement:</strong> always invoke this method
     * <em>before</em> adding your own team-specific Goals so that the sanitizer
     * never accidentally removes them (the {@link TeamGoal} marker provides a
     * secondary safety net in case goals were already added).</p>
     *
     * @param mob the mob that just joined a team
     */
    public static void sanitize(Mob mob) {
        if (!BMTConfig.isAggressiveGoalRemovalEnabled()) return;

        int removedGoal   = removeAggressiveFrom(mob, false);
        int removedTarget = removeAggressiveFrom(mob, true);

        if (removedGoal + removedTarget > 0) {
            BetterMineTeam.debug("GoalSanitizer: removed {} goalSelector + {} targetSelector aggressive goals from {}.",
                    removedGoal, removedTarget, mob.getName().getString());
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * @param useTargetSelector {@code true} to operate on {@code targetSelector},
     *                          {@code false} for {@code goalSelector}
     * @return number of goals actually removed
     */
    private static int removeAggressiveFrom(Mob mob, boolean useTargetSelector) {
        var selector = useTargetSelector ? mob.targetSelector : mob.goalSelector;
        int[] count = {0};

        selector.getAvailableGoals().removeIf(wrapper -> {
            Goal goal = wrapper.getGoal();
            if (!isAggressive(goal)) return false;
            if (isProtected(goal))  return false;
            BetterMineTeam.debug("GoalSanitizer: removing {} from {} of {}.",
                    goal.getClass().getSimpleName(),
                    useTargetSelector ? "targetSelector" : "goalSelector",
                    mob.getName().getString());
            count[0]++;
            return true;
        });

        return count[0];
    }

    /**
     * Returns {@code true} when the goal should be treated as aggressive and is
     * a candidate for removal.
     *
     * <p>The {@link TeamGoal} check comes first so our own goals are never
     * considered aggressive regardless of what they extend.</p>
     */
    private static boolean isAggressive(Goal goal) {
        // Layer 0: our own goals — always safe
        if (goal instanceof TeamGoal) return false;

        // Layer 1: target-selection goals (set mob.target, drive combat initiation)
        if (goal instanceof TargetGoal) return true;

        // Layer 2: melee attack execution goals
        if (goal instanceof MeleeAttackGoal) return true;

        // Layer 3: ranged attack goals (extend Goal directly, not MeleeAttackGoal)
        if (goal instanceof RangedAttackGoal) return true;

        return false;
    }

    /**
     * Returns {@code true} when the goal's fully-qualified class name is in the
     * protected whitelist and must never be removed.
     */
    private static boolean isProtected(Goal goal) {
        return PROTECTED_CLASS_NAMES.contains(goal.getClass().getName());
    }
}
