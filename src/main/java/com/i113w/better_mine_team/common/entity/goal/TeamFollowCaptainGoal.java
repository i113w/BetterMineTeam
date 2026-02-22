package com.i113w.better_mine_team.common.entity.goal;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.team.TeamDataStorage;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;

import java.util.EnumSet;
import java.util.UUID;

public class TeamFollowCaptainGoal extends Goal {
    private final Mob mob;
    private ServerPlayer captain;
    private final double speedModifier;
    private final float startDistance;
    private final float stopDistance;
    private final float combatStartDistance;

    private final PathNavigation navigation;
    private int timeToRecalcPath;
    private int failedPathAttempts = 0;

    private int debugCooldown = 0;

    public TeamFollowCaptainGoal(Mob mob, double speedModifier, float startDistance, float stopDistance) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.combatStartDistance = Math.max(startDistance * 2.5F, 24.0F);

        this.navigation = mob.getNavigation();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (debugCooldown > 0) debugCooldown--;

        boolean isFollowEnabled = this.mob.getPersistentData().getBoolean("bmt_follow_enabled");
        if (!isFollowEnabled) return false;

        PlayerTeam mobTeam = TeamManager.getTeam(this.mob);
        if (mobTeam == null || !(this.mob.level() instanceof ServerLevel serverLevel)) return false;

        UUID captainId = TeamDataStorage.get(serverLevel).getCaptain(mobTeam.getName());
        if (captainId == null) {
            if (debugCooldown == 0) {
                BetterMineTeam.debug("Team {} has NO CAPTAIN recorded!", mobTeam.getName());
                debugCooldown = 100;
            }
            return false;
        }

        this.captain = serverLevel.getServer().getPlayerList().getPlayer(captainId);
        if (this.captain == null || this.captain.isSpectator()) return false;

        PlayerTeam captainCurrentTeam = TeamManager.getTeam(this.captain);
        if (captainCurrentTeam == null || !captainCurrentTeam.getName().equals(mobTeam.getName())) {
            if (debugCooldown == 0) {
                BetterMineTeam.debug("Captain {} is no longer in team {}!", captain.getName().getString(), mobTeam.getName());
                debugCooldown = 100;
            }
            return false;
        }

        double distanceSqr = this.mob.distanceToSqr(this.captain);
        float effectiveStartDist = this.startDistance;

        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) {
            effectiveStartDist = this.combatStartDistance;
        }

        return distanceSqr > (effectiveStartDist * effectiveStartDist);
    }

    @Override
    public boolean canContinueToUse() {
        return this.captain != null && !this.navigation.isDone() && this.mob.distanceToSqr(this.captain) > (this.stopDistance * this.stopDistance);
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
        this.failedPathAttempts = 0;
    }

    @Override
    public void stop() {
        this.captain = null;
        this.navigation.stop();
        this.failedPathAttempts = 0;
    }

    @Override
    public void tick() {
        if (this.captain == null) return;

        this.mob.getLookControl().setLookAt(this.captain, 10.0F, (float) this.mob.getMaxHeadXRot());

        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = 10;

            if (!this.mob.isLeashed() && !this.mob.isPassenger()) {
                double distToCaptainSqr = this.mob.distanceToSqr(this.captain);

                // --- 自动传送检查 ---
                if (BMTConfig.isAutoTeleportEnabled() && distToCaptainSqr >= BMTConfig.getAutoTeleportDistanceSqr()) {
                    this.teleportToCaptain();
                    return; // 尝试传送后，本 tick 直接返回，跳过寻路
                }

                // --- 特殊移动生物直接跳过寻路 ---
                if (isSpecialMovementMob(this.mob)) {
                    this.mob.getMoveControl().setWantedPosition(
                            this.captain.getX(), this.captain.getY(), this.captain.getZ(), this.speedModifier
                    );
                    return;
                }

                boolean pathFound = this.navigation.moveTo(this.captain, this.speedModifier);

                if (!pathFound) failedPathAttempts++;
                else failedPathAttempts = 0;

                if (failedPathAttempts >= BMTConfig.getFollowPathFailThreshold()) {
                    this.mob.getMoveControl().setWantedPosition(
                            this.captain.getX(), this.captain.getY(), this.captain.getZ(), this.speedModifier
                    );
                }
            }
        }
    }

// 传送至队长位置
    private void teleportToCaptain() {
        BlockPos captainPos = this.captain.blockPosition();

        for (int i = 0; i < 10; ++i) {
            // 在玩家周围随机找一个点： X/Z轴 ±3格，Y轴 ±1格
            int dx = this.mob.getRandom().nextInt(7) - 3;
            int dy = this.mob.getRandom().nextInt(3) - 1;
            int dz = this.mob.getRandom().nextInt(7) - 3;

            if (this.maybeTeleportTo(captainPos.getX() + dx, captainPos.getY() + dy, captainPos.getZ() + dz)) {
                return; // 传送成功则直接退出
            }
        }
    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        // 防止正好传送到队长头里卡住
        if (Math.abs((double) x - this.captain.getX()) < 2.0D && Math.abs((double) z - this.captain.getZ()) < 2.0D) {
            return false;
        }

        BlockPos pos = new BlockPos(x, y, z);
        if (!this.canTeleportTo(pos)) {
            return false;
        }

        this.mob.moveTo((double) x + 0.5D, (double) y, (double) z + 0.5D, this.mob.getYRot(), this.mob.getXRot());
        this.navigation.stop();
        return true;
    }

    private boolean canTeleportTo(BlockPos pos) {
        Level level = this.mob.level();

        // 1. 目标方块不能有碰撞体积
        BlockState targetState = level.getBlockState(pos);
        if (!targetState.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }

        // 2. 目标方块上方不能有阻挡 (给实体留出足够高度)
        BlockState aboveState = level.getBlockState(pos.above());
        if (!aboveState.getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }

        // 3. 脚下必须是实体方块，不能悬空
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (!belowState.isSolidRender(level, below)) {
            return false;
        }

        // 4. 不能传送到岩浆、火等危险区域
        if (targetState.is(net.minecraft.tags.BlockTags.FIRE) ||
                targetState.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
            return false;
        }

        // 5. 使用包围盒进行最后确认，防止实体过大卡进旁边的墙体
        AABB aabb = this.mob.getBoundingBox().move(pos.subtract(this.mob.blockPosition()));
        return level.noCollision(this.mob, aabb);
    }

    private boolean isSpecialMovementMob(Mob mob) {
        String className = mob.getClass().getSimpleName();
        // 1. 史莱姆类
        if (className.contains("Slime") || className.contains("MagmaCube")) return true;
        // 2. 飞行/漂浮类
        if (className.contains("Ghast") || className.contains("Phantom") || className.contains("Allay") || className.contains("Vex")) return true;
        // 3. 其他特殊移动类
        if (className.contains("Bee") || className.contains("Bat") || className.contains("Parrot")) return true;

        return false;
    }
}