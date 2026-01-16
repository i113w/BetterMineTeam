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
import net.minecraftforge.event.entity.living.LivingAttackEvent; // [新增导入]
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID)
public class LivingEventSubscriber {

    @SubscribeEvent
    public static void onEntityTick(TickEvent.PlayerTickEvent event) {
    }

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

    /**
     * [修复] 使用 LivingAttackEvent 拦截 PVP/友伤。
     * 这比 LivingHurtEvent 更早触发，可以阻止击退和受伤动画，确保开关生效。
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity hurtEntity = event.getEntity();
        DamageSource source = event.getSource();
        Entity attackEntity = source.getEntity();

        // 必须是服务端，且攻击者是生物，且不是自残
        if (hurtEntity.level() instanceof ServerLevel && attackEntity instanceof LivingEntity livingAttacker && attackEntity != hurtEntity) {
            PlayerTeam hurtTeam = TeamManager.getTeam(hurtEntity);
            PlayerTeam attackTeam = TeamManager.getTeam(livingAttacker);

            // 如果双方都有队伍，且是盟友（包括同一队）
            if (hurtTeam != null && attackTeam != null && hurtTeam.isAlliedTo(attackTeam)) {
                // 检查受害者的队伍是否允许友伤
                if (!hurtTeam.isAllowFriendlyFire()) {
                    event.setCanceled(true);
                }
            }
        }
    }

    /**
     * 保留 LivingHurtEvent 用于处理伤害后的逻辑（如仇恨连锁），
     * 但不再处理 PVP 拦截（已移至 LivingAttackEvent）。
     */
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

        // 双重保险：如果 AttackEvent 没拦住，这里再拦一次
        if (hurtTeam != null && attackTeam != null && hurtTeam.isAlliedTo(attackTeam)) {
            if (!hurtTeam.isAllowFriendlyFire()) {
                event.setCanceled(true);
                return;
            }
        }

        if (hurtTeam != null || attackTeam != null) {
            BetterMineTeam.debug("Damage Event: Attacker={} (Team={}), Victim={} (Team={})",
                    livingAttacker.getName().getString(),
                    attackTeam != null ? attackTeam.getName() : "null",
                    hurtEntity.getName().getString(),
                    hurtTeam != null ? hurtTeam.getName() : "null"
            );
        }

        // 场景1: 团战
        if (hurtTeam != null && attackTeam != null) {
            if (hurtTeam.isAlliedTo(attackTeam)) return;
            TeamManager.scanAndAddThreats(hurtTeam, attackTeam, livingAttacker);

            if (BMTConfig.isTeamFocusFireEnabled()) {
                TeamManager.scanAndAddThreats(attackTeam, hurtTeam, hurtEntity);
            }
        }
        // 场景2: 打野
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