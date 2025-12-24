package com.i113w.better_mine_team.common.entity.goal;

import com.i113w.better_mine_team.common.team.TeamDataStorage;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.level.pathfinder.PathComputationType;

import java.util.EnumSet;
import java.util.UUID;

public class TeamFollowCaptainGoal extends Goal {
    private final Mob mob;
    private ServerPlayer captain; // 缓存当前的队长
    private final double speedModifier;
    private final float startDistance;
    private final float stopDistance;

    private final PathNavigation navigation;
    private int timeToRecalcPath;

    public TeamFollowCaptainGoal(Mob mob, double speedModifier, float startDistance, float stopDistance) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.navigation = mob.getNavigation();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 1. 检查是否开启了跟随 (从 NBT 读取，默认关闭)
        if (!this.mob.getPersistentData().getBoolean("bmt_follow_enabled")) {
            return false;
        }

        // 2. 获取队伍和队长
        PlayerTeam team = TeamManager.getTeam(this.mob);
        if (team == null || !(this.mob.level() instanceof ServerLevel serverLevel)) return false;

        UUID captainId = TeamDataStorage.get(serverLevel).getCaptain(team.getName());
        if (captainId == null) return false;

        // 3. 寻找队长实体
        this.captain = serverLevel.getServer().getPlayerList().getPlayer(captainId);
        if (this.captain == null || this.captain.isSpectator()) return false;

        // 4. 距离检查
        double distanceSqr = this.mob.distanceToSqr(this.captain);
        return distanceSqr > (this.startDistance * this.startDistance);
    }

    @Override
    public boolean canContinueToUse() {
        return this.captain != null && !this.navigation.isDone() && this.mob.distanceToSqr(this.captain) > (this.stopDistance * this.stopDistance);
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        this.captain = null;
        this.navigation.stop();
    }

    @Override
    public void tick() {
        if (this.captain == null) return;

        this.mob.getLookControl().setLookAt(this.captain, 10.0F, (float) this.mob.getMaxHeadXRot());

        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = 10;
            if (!this.mob.isLeashed() && !this.mob.isPassenger()) {
                this.navigation.moveTo(this.captain, this.speedModifier);
            }
        }

        // --- 传送逻辑 ---
        double distSqr = this.mob.distanceToSqr(this.captain);
        // 如果距离超过 144 (12格)，且队长不在飞、不在高空悬停
        if (distSqr > 144.0D) {
            // 核心判断：主人是否接触地面 (防止摔死保护)
            // onGround() 有时在边缘不准，这里加一个简单的方块检测
            boolean isSafeToTeleport = this.captain.onGround() || this.captain.isInWater();

            // 如果主人在 Elytra 飞行或者在摔落中，绝对不传送
            if (this.captain.isFallFlying() || this.captain.fallDistance > 3.0F) {
                isSafeToTeleport = false;
            }

            if (isSafeToTeleport) {
                this.tryToTeleportNearEntity();
            }
        }
    }

    private void tryToTeleportNearEntity() {
        BlockPos pos = this.captain.blockPosition();
        for (int i = 0; i < 10; ++i) {
            int dx = this.randomInt(3, -3);
            int dy = this.randomInt(1, -1);
            int dz = this.randomInt(3, -3);

            // 尝试在队长周围找个落脚点
            if (maybeTeleportTo(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz)) {
                return;
            }
        }
    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        if (Math.abs((double)x - this.captain.getX()) < 2.0D && Math.abs((double)z - this.captain.getZ()) < 2.0D) {
            return false;
        } else if (!this.canTeleportTo(new BlockPos(x, y, z))) {
            return false;
        } else {
            this.mob.moveTo((double)x + 0.5D, y, (double)z + 0.5D, this.mob.getYRot(), this.mob.getXRot());
            this.navigation.stop();
            return true;
        }
    }

    private boolean canTeleportTo(BlockPos pos) {
        // 之前是 generatePathType(state)，现在直接传 pos
        PathType pathType = generatePathType(pos);

        if (pathType != PathType.WALKABLE) {
            return false;
        } else {
            BlockPos blockpos = pos.subtract(this.mob.blockPosition());
            return this.mob.level().noCollision(this.mob, this.mob.getBoundingBox().move(blockpos));
        }
    }

    private PathType generatePathType(BlockPos pos) {
        BlockState state = this.mob.level().getBlockState(pos);

        if (state.isAir()) return PathType.OPEN;
        if (state.getBlock() instanceof LeavesBlock) return PathType.LEAVES;

        // 这里使用 PathComputationType.LAND
        return state.isPathfindable(PathComputationType.LAND) ? PathType.WALKABLE : PathType.BLOCKED;
    }

    private int randomInt(int max, int min) {
        return this.mob.getRandom().nextInt(max - min + 1) + min;
    }
}