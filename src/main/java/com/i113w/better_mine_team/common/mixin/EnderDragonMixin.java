package com.i113w.better_mine_team.common.mixin;

import com.i113w.better_mine_team.common.bridge.IDragonSpeed;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin extends Mob implements IDragonSpeed {

    @Shadow @Final public double[][] positions;
    @Shadow public int posPointer;

    @Unique
    @SuppressWarnings("all")
    private static final EntityDataAccessor<Float> BMT_DRAGON_SPEED =
            SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.FLOAT);

    @Unique private int bmt$shootCooldown = 0;
    @Unique private boolean bmt$wasRidden = false;

    protected EnderDragonMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public float bmt$getSpeed() {
        return this.entityData.get(BMT_DRAGON_SPEED);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void bmt$defineData(CallbackInfo ci) {
        this.entityData.define(BMT_DRAGON_SPEED, 0.05F);
    }

    // [关键修改] 1.20.1 使用 getPassengerRidingPosition 代替 getPassengerAttachmentPoint
    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        // 动态计算座位高度：龙高度的 75%
        // 这比硬编码 3.2 更灵活，适配不同状态下的龙
        return new Vec3(0, this.getBbHeight() * 0.75, 0);
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void bmt$overrideDragonControl(CallbackInfo ci) {
        if (!BMTConfig.isDragonRidingEnabled()) return;

        Entity passenger = this.getFirstPassenger();
        if (passenger == null) {
            if (bmt$wasRidden) {
                bmt$wasRidden = false;
                this.entityData.set(BMT_DRAGON_SPEED, 0.05F);
            }
            return;
        }

        if (passenger instanceof ServerPlayer player) {
            PlayerTeam dragonTeam = TeamManager.getTeam(this);
            PlayerTeam playerTeam = TeamManager.getTeam(player);

            if (dragonTeam != null && playerTeam != null && dragonTeam.getName().equals(playerTeam.getName())) {
                bmt$wasRidden = true;

                bmt$updateDragonFlight(player);

                // 更新龙的历史位置缓冲区（用于模型渲染插值）
                this.posPointer = (this.posPointer + 1) % this.positions.length;
                this.positions[this.posPointer][0] = this.getX();
                this.positions[this.posPointer][1] = this.getY();
                this.positions[this.posPointer][2] = this.getZ();

                ci.cancel();
            }
        }
    }

    @Unique
    private void bmt$updateDragonFlight(ServerPlayer player) {
        float forward = player.zza;
        float strafe = player.xxa;

        boolean isAccelerating = player.getPersistentData().getBoolean("bmt_dragon_space");
        boolean isDecelerating = player.getPersistentData().getBoolean("bmt_dragon_shift");

        float currentSpeed = this.entityData.get(BMT_DRAGON_SPEED);
        if (isAccelerating) {
            currentSpeed += BMTConfig.getDragonAcceleration();
        } else if (isDecelerating) {
            currentSpeed -= BMTConfig.getDragonDeceleration();
        }
        currentSpeed = Mth.clamp(currentSpeed, 0.0F, 1.0F);
        this.entityData.set(BMT_DRAGON_SPEED, currentSpeed);

        float rotSpeed = BMTConfig.getDragonRotationSpeed();
        float pitchSpeed = BMTConfig.getDragonPitchSpeed();
        float maxPitch = BMTConfig.getDragonMaxPitch();

        if (Math.abs(strafe) > 0.01) {
            this.setYRot(this.getYRot() - (strafe * rotSpeed));
        }

        if (Math.abs(forward) > 0.01) {
            float newPitch = this.getXRot() + (forward * pitchSpeed);
            this.setXRot(Mth.clamp(newPitch, -maxPitch, maxPitch));
        }

        this.yHeadRot = this.getYRot();

        double baseSpeed = BMTConfig.getDragonBaseSpeed();
        double finalSpeed = baseSpeed * currentSpeed;

        if (finalSpeed > 0) {
            float moveYRot = this.getYRot() + 180.0F;
            float moveXRot = this.getXRot();

            Vec3 moveVec = this.calculateViewVector(moveXRot, moveYRot).scale(finalSpeed);

            this.setDeltaMovement(moveVec);
            this.move(MoverType.SELF, this.getDeltaMovement());
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }

        this.checkInsideBlocks();

        if (!this.level().isClientSide) {
            bmt$handleCollisionDamage();
            bmt$handleAutoAttack(player);
        }
    }

    @Unique
    private void bmt$handleCollisionDamage() {
        List<Entity> targets = this.level().getEntities(this, this.getBoundingBox().inflate(2.0D), EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        for (Entity target : targets) {
            if (target instanceof LivingEntity living) {
                if (this.hasPassenger(target)) continue;
                PlayerTeam myTeam = TeamManager.getTeam(this);
                PlayerTeam otherTeam = TeamManager.getTeam(living);
                if (myTeam != null && otherTeam != null && myTeam.isAlliedTo(otherTeam) && !myTeam.isAllowFriendlyFire()) continue;
                living.hurt(this.damageSources().mobAttack(this), 10.0F);
                double d0 = living.getX() - this.getX();
                double d1 = living.getZ() - this.getZ();
                double d2 = Math.max(d0 * d0 + d1 * d1, 0.01D);
                living.push(d0 / d2 * 4.0D, 0.2D, d1 / d2 * 4.0D);
            }
        }
    }

    @Unique
    private void bmt$handleAutoAttack(ServerPlayer player) {
        if (bmt$shootCooldown > 0) {
            bmt$shootCooldown--;
            return;
        }
        PlayerTeam team = TeamManager.getTeam(player);
        LivingEntity target = TeamManager.getBestThreat(team, this);

        if (target == null) target = player.getLastHurtMob();
        if (target == null) target = player.getLastHurtByMob();

        if (target != null && target.isAlive() && target.distanceToSqr(this) < 4096.0D) {
            double d0 = target.getX() - this.getX();
            double d1 = target.getY(0.5D) - this.getY(0.5D);
            double d2 = target.getZ() - this.getZ();
            Vec3 headPos = this.getEyePosition().add(this.getViewVector(1.0F).scale(5.0));
            float shootYRot = this.getYRot() + 180.0F;

            DragonFireball fireball = new DragonFireball(this.level(), this, d0, d1, d2);
            fireball.moveTo(headPos.x, headPos.y, headPos.z, shootYRot, this.getXRot());
            this.level().addFreshEntity(fireball);
            bmt$shootCooldown = 40;
        }
    }

    // [可选修改] 如果 hurt(List<Entity>) 方法在 1.20.1 中签名变化，可以删除此 Inject
    // 友伤保护已在 LivingHurtEvent 中实现，这里是双重保险
    @Inject(method = "hurt(Ljava/util/List;)V", at = @At("HEAD"))
    private void bmt$preventFriendlyFire(List<Entity> entities, CallbackInfo ci) {
        if (entities == null || entities.isEmpty()) return;
        Entity passenger = this.getFirstPassenger();
        PlayerTeam myTeam = TeamManager.getTeam(this);
        entities.removeIf(entity -> {
            if (entity == passenger) return true;
            if (entity instanceof LivingEntity living) {
                PlayerTeam otherTeam = TeamManager.getTeam(living);
                if (myTeam != null && otherTeam != null && myTeam.isAlliedTo(otherTeam)) {
                    return !myTeam.isAllowFriendlyFire();
                }
            }
            return false;
        });
    }
}