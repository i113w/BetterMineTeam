package com.i113w.better_mine_team.common.mixin;

import com.i113w.better_mine_team.common.bridge.IDragonSpeed;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.network.S2C_DragonSpeedPayload;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
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

    @Shadow @Final public DragonFlightHistory flightHistory;

    @Unique
    private static final float BMT_DEFAULT_DRAGON_SPEED = 0.05F;

    /**
     * 不再使用 SynchedEntityData 给原版 EnderDragon 额外挂数据槽。
     *
     * 原来的写法：
     * SynchedEntityData.defineId(EnderDragon.class, EntityDataSerializers.FLOAT)
     *
     * 在 Mixin 场景里很容易触发实体类不匹配警告，并且可能在 bootstrap / registry 阶段
     * 触发 ExceptionInInitializerError。
     *
     * 现在速度只保存在 Mixin 字段里；客户端 UI 需要的速度通过 S2C_DragonSpeedPayload 同步。
     */
    @Unique
    private float bmt$dragonSpeed = BMT_DEFAULT_DRAGON_SPEED;

    @Unique
    private int bmt$shootCooldown = 0;

    @Unique
    private boolean bmt$wasRidden = false;

    protected EnderDragonMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public float bmt$getSpeed() {
        return this.bmt$dragonSpeed;
    }

    @Override
    protected @NotNull Vec3 getPassengerAttachmentPoint(
            @NotNull Entity entity,
            @NotNull EntityDimensions dimensions,
            float partialTick
    ) {
        // 3.2 格通常能让玩家坐在龙背上，而不是卡进龙身体里。
        return new Vec3(0, 3.2D, 0);
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void bmt$overrideDragonControl(CallbackInfo ci) {
        if (!BMTConfig.isDragonRidingEnabled()) {
            return;
        }

        Entity passenger = this.getFirstPassenger();

        if (passenger == null) {
            if (this.bmt$wasRidden) {
                this.bmt$wasRidden = false;
                bmt$resetDragonSpeed();
            }
            return;
        }

        if (!(passenger instanceof ServerPlayer player)) {
            return;
        }

        PlayerTeam dragonTeam = TeamManager.getTeam(this);
        PlayerTeam playerTeam = TeamManager.getTeam(player);

        if (dragonTeam == null || playerTeam == null) {
            return;
        }

        if (!dragonTeam.getName().equals(playerTeam.getName())) {
            return;
        }

        this.bmt$wasRidden = true;

        bmt$updateDragonFlight(player);

        // 更新龙的历史位置缓冲区。
        // 即使我们不再用它计算座位位置，也应该维护它，否则龙模型插值可能异常。
        this.flightHistory.record(this.getY(), this.getYRot());

        ci.cancel();
    }

    @Unique
    private void bmt$resetDragonSpeed() {
        this.bmt$dragonSpeed = BMT_DEFAULT_DRAGON_SPEED;
    }

    @Unique
    private void bmt$updateDragonFlight(ServerPlayer player) {
        float forward = player.zza;
        float strafe = player.xxa;

        boolean isAccelerating = player.getPersistentData().getBooleanOr("bmt_dragon_space", false);
        boolean isDecelerating = player.getPersistentData().getBooleanOr("bmt_dragon_shift", false);

        float currentSpeed = this.bmt$dragonSpeed;

        if (isAccelerating) {
            currentSpeed += BMTConfig.getDragonAcceleration();
        } else if (isDecelerating) {
            currentSpeed -= BMTConfig.getDragonDeceleration();
        }

        currentSpeed = Mth.clamp(currentSpeed, 0.0F, 1.0F);
        this.bmt$dragonSpeed = currentSpeed;

        // 同步给正在骑龙的玩家，用于客户端速度条。
        bmt$syncDragonSpeedToRider(player, currentSpeed);

        float rotSpeed = BMTConfig.getDragonRotationSpeed();
        float pitchSpeed = BMTConfig.getDragonPitchSpeed();
        float maxPitch = BMTConfig.getDragonMaxPitch();

        if (Math.abs(strafe) > 0.01F) {
            this.setYRot(this.getYRot() - strafe * rotSpeed);
        }

        if (Math.abs(forward) > 0.01F) {
            float newPitch = this.getXRot() + forward * pitchSpeed;
            this.setXRot(Mth.clamp(newPitch, -maxPitch, maxPitch));
        }

        this.yHeadRot = this.getYRot();

        double baseSpeed = BMTConfig.getDragonBaseSpeed();
        double finalSpeed = baseSpeed * currentSpeed;

        if (finalSpeed > 0.0D) {
            float moveYRot = this.getYRot() + 180.0F;
            float moveXRot = this.getXRot();

            Vec3 moveVec = this.calculateViewVector(moveXRot, moveYRot).scale(finalSpeed);

            this.setDeltaMovement(moveVec);
            this.move(MoverType.SELF, this.getDeltaMovement());
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }

        this.applyEffectsFromBlocks();

        if (!this.level().isClientSide()) {
            bmt$handleCollisionDamage();
            bmt$handleAutoAttack(player);
        }
    }

    @Unique
    private void bmt$syncDragonSpeedToRider(ServerPlayer player, float speed) {
        PacketDistributor.sendToPlayer(player, new S2C_DragonSpeedPayload(speed));
    }

    @Unique
    private void bmt$handleCollisionDamage() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        List<Entity> targets = serverLevel.getEntities(
                this,
                this.getBoundingBox().inflate(2.0D),
                EntitySelector.NO_CREATIVE_OR_SPECTATOR
        );

        PlayerTeam myTeam = TeamManager.getTeam(this);

        for (Entity target : targets) {
            if (!(target instanceof LivingEntity living)) {
                continue;
            }

            if (this.hasPassenger(target)) {
                continue;
            }

            PlayerTeam otherTeam = TeamManager.getTeam(living);

            if (
                    myTeam != null
                            && otherTeam != null
                            && myTeam.isAlliedTo(otherTeam)
                            && !myTeam.isAllowFriendlyFire()
            ) {
                continue;
            }

            living.hurtServer(serverLevel, this.damageSources().mobAttack(this), 10.0F);

            double d0 = living.getX() - this.getX();
            double d1 = living.getZ() - this.getZ();
            double d2 = Math.max(d0 * d0 + d1 * d1, 0.01D);

            living.push(d0 / d2 * 4.0D, 0.2D, d1 / d2 * 4.0D);
        }
    }

    @Unique
    private void bmt$handleAutoAttack(ServerPlayer player) {
        if (this.bmt$shootCooldown > 0) {
            this.bmt$shootCooldown--;
            return;
        }

        PlayerTeam team = TeamManager.getTeam(player);
        LivingEntity target = TeamManager.getBestThreat(team, this);

        if (target == null) {
            target = player.getLastHurtMob();
        }

        if (target == null) {
            target = player.getLastHurtByMob();
        }

        if (target == null || !target.isAlive() || target.distanceToSqr(this) >= 4096.0D) {
            return;
        }

        double d0 = target.getX() - this.getX();
        double d1 = target.getY(0.5D) - this.getY(0.5D);
        double d2 = target.getZ() - this.getZ();

        Vec3 headPos = this.getEyePosition().add(this.getViewVector(1.0F).scale(5.0D));
        float shootYRot = this.getYRot() + 180.0F;

        DragonFireball fireball = new DragonFireball(
                this.level(),
                this,
                new Vec3(d0, d1, d2)
        );

        fireball.snapTo(headPos.x, headPos.y, headPos.z, shootYRot, this.getXRot());
        this.level().addFreshEntity(fireball);

        this.bmt$shootCooldown = 40;
    }

    @Inject(
            method = "hurt(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V",
            at = @At("HEAD")
    )
    private void bmt$preventFriendlyFire(ServerLevel level, List<Entity> entities, CallbackInfo ci) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        Entity passenger = this.getFirstPassenger();
        PlayerTeam myTeam = TeamManager.getTeam(this);

        entities.removeIf(entity -> {
            if (entity == passenger) {
                return true;
            }

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