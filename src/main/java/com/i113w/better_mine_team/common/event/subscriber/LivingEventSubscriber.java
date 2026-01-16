package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// [关键修改] 所有实体事件属于 Forge 总线（默认），无需指定 bus 参数
@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID)
public class LivingEventSubscriber {

    // [关键修改] 1.20.1 使用 TickEvent.PlayerTickEvent，没有泛型 EntityTickEvent
    @SubscribeEvent
    public static void onEntityTick(TickEvent.PlayerTickEvent event) {
        // Slime 特殊处理移到下方的专用方法
    }

    // [新增] Slime 碰撞伤害的替代实现
    // 由于 1.20.1 没有 EntityTickEvent.Pre，我们使用 LivingEvent.LivingTickEvent
    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide) return;

        LivingEntity entity = event.getEntity();
        if (entity instanceof Slime slime) {
            if (slime.getSize() <= 1) return;

            LivingEntity target = slime.getTarget();

            if (target != null && target.isAlive() && slime.getBoundingBox().intersects(target.getBoundingBox())) {
                PlayerTeam myTeam = TeamManager.getTeam(slime);
                PlayerTeam targetTeam = TeamManager.getTeam(target);

                boolean isAlly = (myTeam != null && targetTeam != null && myTeam.isAlliedTo(targetTeam));

                if (!isAlly && slime.canAttack(target)) {
                    int damage = slime.getSize();
                    target.hurt(target.damageSources().mobAttack(slime), (float)damage);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity attacker = event.getEntity();
        LivingEntity newTarget = event.getNewTarget();

        if (attacker.level().isClientSide || newTarget == null) return;

        PlayerTeam attackerTeam = TeamManager.getTeam(attacker);
        PlayerTeam targetTeam = TeamManager.getTeam(newTarget);

        if (attackerTeam != null && targetTeam != null && attackerTeam.isAlliedTo(targetTeam)) {
            event.setCanceled(true);
        }
    }

    // [关键修改] 1.21.1 的 LivingIncomingDamageEvent 在 1.20.1 中是 LivingHurtEvent
    @SubscribeEvent
    public static void onLivingPvP(LivingHurtEvent event) {
        LivingEntity hurtEntity = event.getEntity();
        DamageSource source = event.getSource();
        Entity attackEntity = source.getEntity();

        if (hurtEntity.level() instanceof ServerLevel && attackEntity instanceof LivingEntity livingAttacker && attackEntity != hurtEntity) {
            PlayerTeam hurtTeam = TeamManager.getTeam(hurtEntity);
            PlayerTeam attackTeam = TeamManager.getTeam(livingAttacker);

            if (hurtTeam != null && attackTeam != null && hurtTeam.isAlliedTo(attackTeam)) {
                if (!hurtTeam.isAllowFriendlyFire()) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onSetTeamLastHurtMob(LivingHurtEvent event) {
        LivingEntity hurtEntity = event.getEntity();
        DamageSource source = event.getSource();
        Entity attackEntity = source.getEntity();

        if (!(hurtEntity.level() instanceof ServerLevel)) return;
        if (!(attackEntity instanceof LivingEntity livingAttacker)) return;
        if (hurtEntity == livingAttacker) return;

        PlayerTeam hurtTeam = TeamManager.getTeam(hurtEntity);
        PlayerTeam attackTeam = TeamManager.getTeam(livingAttacker);

        if (hurtTeam != null || attackTeam != null) {
            BetterMineTeam.debug("Damage Event: Attacker={} (Team={}), Victim={} (Team={})",
                    livingAttacker.getName().getString(),
                    attackTeam != null ? attackTeam.getName() : "null",
                    hurtEntity.getName().getString(),
                    hurtTeam != null ? hurtTeam.getName() : "null"
            );
        }

        // 场景1: 团战 (双方都有队伍)
        if (hurtTeam != null && attackTeam != null) {
            if (hurtTeam.isAlliedTo(attackTeam)) return;
            TeamManager.scanAndAddThreats(hurtTeam, attackTeam, livingAttacker);

            if (BMTConfig.isTeamFocusFireEnabled()) {
                TeamManager.scanAndAddThreats(attackTeam, hurtTeam, hurtEntity);
            }
            BetterMineTeam.debug("Set Team Threat for Team {}: {}", hurtTeam.getName(), livingAttacker.getName().getString());
        }
        // 场景2: 打野/被野怪打
        else {
            if (hurtTeam != null) {
                TeamManager.addThreat(hurtTeam, livingAttacker);
            }
            if (BMTConfig.isTeamFocusFireEnabled() && attackTeam != null) {
                TeamManager.addThreat(attackTeam, hurtEntity);
            }
            BetterMineTeam.debug("Set Team Target for Team {}: {}",
                    (attackTeam != null ? attackTeam.getName() : "Unknown Team"),
                    hurtEntity.getName().getString()
            );
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;

        TeamManager.onTargetDeath(victim);

        Entity sourceEntity = event.getSource().getEntity();

        if (sourceEntity instanceof LivingEntity killer) {
            PlayerTeam killerTeam = TeamManager.getTeam(killer);
            PlayerTeam victimTeam = TeamManager.getTeam(victim);

            if (killerTeam != null && victimTeam != null && !killerTeam.isAlliedTo(victimTeam)) {
                TeamManager.resetScanCooldown(killerTeam.getName());

                BetterMineTeam.debug("WAR: Kill confirmed! {} (Team {}) killed {} (Team {}). Initiating Victory Scan...",
                        killer.getName().getString(), killerTeam.getName(),
                        victim.getName().getString(), victimTeam.getName());

                TeamManager.scanAndAddThreats(killerTeam, victimTeam, killer);
            }
        }
    }
}