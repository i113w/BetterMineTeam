package com.i113w.better_mine_team.common.entity.goal;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.registry.ModTags;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class AggressiveScanGoal extends TargetGoal implements TeamGoal {

    private final PathfinderMob mob;
    private int scanCooldown = 0; // 将团队共享冷却改为每个实体独立维护的冷却计时器

    public AggressiveScanGoal(PathfinderMob mob) {
        super(mob, false);
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        int aggressiveLevel = TeamManager.getAggressiveLevel(mob);
        if (aggressiveLevel == 0) return false;

        if (scanCooldown > 0) {
            scanCooldown--;
            return false;
        }

        LivingEntity current = mob.getTarget();
        if (current != null && current.isAlive() && !TeamManager.isAlly(mob, current)) {
            scanCooldown = BMTConfig.getAggressiveScanEntityInterval();
            return false;
        }

        PlayerTeam myTeam = TeamManager.getTeam(mob);
        if (myTeam == null) return false;

        LivingEntity t = findTarget(aggressiveLevel, myTeam);
        if (t == null) {
            scanCooldown = BMTConfig.getAggressiveScanEntityInterval();
            return false;
        }

        // 赋值给 targetMob
        this.targetMob = t;
        scanCooldown = BMTConfig.getAggressiveScanEntityInterval();

        BetterMineTeam.debug("[L1-DEBUG] AggressiveScanGoal (L{}): Guard {} Locked onto -> {}",
                aggressiveLevel, mob.getName().getString(), this.targetMob.getName().getString());
        return true;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.targetMob);
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        if (TeamManager.getAggressiveLevel(this.mob) == 0) return false;

        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget == null || !currentTarget.isAlive()) return false;
        if (TeamManager.isAlly(mob, currentTarget)) return false;
        return super.canContinueToUse();
    }

    public void resetScanTicker() {
        this.scanCooldown = 0;
    }

    private LivingEntity findTarget(int aggressiveLevel, PlayerTeam myTeam) {
        double radius = BMTConfig.getAggressiveScanRadius();
        AABB box = mob.getBoundingBox().inflate(radius, 8.0, radius);

        List<LivingEntity> candidates = mob.level().getEntitiesOfClass(
                LivingEntity.class, box,
                e -> isValidTarget(e, aggressiveLevel, myTeam)
        );

        return candidates.stream()
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .filter(e -> this.canAttack(e, TargetingConditions.DEFAULT))
                .orElse(null);
    }

    private boolean isValidTarget(LivingEntity candidate, int aggressiveLevel, PlayerTeam myTeam) {
        if (candidate == mob || !candidate.isAlive() || candidate.isSpectator()) return false;
        if (TeamManager.isAlly(mob, candidate)) return false;
        if (candidate instanceof Mob m && m.isNoAi()) return false;
        if (candidate instanceof Player p && (p.isCreative() || p.isSpectator())) return false;

        String typeId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(candidate.getType()).toString();
        if (BMTConfig.getAggressiveEntityBlacklist().contains(typeId)) return false;

        return aggressiveLevel == 1 ? isLevel1Target(candidate, myTeam) : isLevel2Target(candidate);
    }

    private boolean isLevel1Target(LivingEntity candidate, PlayerTeam myTeam) {
        if (!(candidate instanceof Mob aggressorMob)) {
            return false;
        }

        LivingEntity aggressorTarget = aggressorMob.getTarget();
        if (aggressorTarget == null) {
            try {
                // 必须捕获 IllegalStateException，防止诸如铁傀儡等没有 ATTACK_TARGET 模块的生物导致崩溃
                aggressorTarget = aggressorMob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            } catch (IllegalStateException ignored) {
                // 该生物的 Brain 中没有注册 ATTACK_TARGET 记忆，静默忽略即可
            }
        }

        if (aggressorTarget == null || !aggressorTarget.isAlive()) {
            return false;
        }

        PlayerTeam victimTeam = TeamManager.getTeam(aggressorTarget);
        return victimTeam != null && victimTeam.getName().equals(myTeam.getName());
    }

    private boolean isLevel2Target(LivingEntity candidate) {
        return !candidate.getType().is(ModTags.Entities.IGNORED_BY_LEVEL2_SCAN);
    }
}