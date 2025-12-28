package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.minecraft.world.entity.monster.Slime;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = BetterMineTeam.MODID)
public class LivingEventSubscriber {
    @SubscribeEvent
    public static void onSlimeTick(EntityTickEvent.Pre event) {
        // 仅服务端运行
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof Slime slime) {
            // 保留原版大小检查（可选）
            if (slime.getSize() <= 1) return;

            LivingEntity target = slime.getTarget();

            // 简单的碰撞检查
            if (target != null && target.isAlive() && slime.getBoundingBox().intersects(target.getBoundingBox())) {
                PlayerTeam myTeam = TeamManager.getTeam(slime);
                PlayerTeam targetTeam = TeamManager.getTeam(target);

                // 队友保护
                boolean isAlly = (myTeam != null && targetTeam != null && myTeam.isAlliedTo(targetTeam));

                if (!isAlly && slime.canAttack(target)) {
                    // 直接造成伤害，不触发附魔特效
                    // 这在 1.21.1 中是绝对安全的
                    int damage = slime.getSize();
                    target.hurt(target.damageSources().mobAttack(slime), (float)damage);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity attacker = event.getEntity();
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();

        if (attacker.level().isClientSide || newTarget == null) return;

        PlayerTeam attackerTeam = TeamManager.getTeam(attacker);
        PlayerTeam targetTeam = TeamManager.getTeam(newTarget);

        if (attackerTeam != null && targetTeam != null && attackerTeam.isAlliedTo(targetTeam)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingPvP(LivingIncomingDamageEvent event) {
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
    public static void onSetTeamLastHurtMob(LivingIncomingDamageEvent event) {
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
            // 受害者队伍：标记攻击者全队为敌人
            TeamManager.scanAndAddThreats(hurtTeam, attackTeam, livingAttacker);

            // 攻击者队伍 (若开启集火)：标记受害者全队为敌人
            if (BMTConfig.isTeamFocusFireEnabled()) {
                TeamManager.scanAndAddThreats(attackTeam, hurtTeam, hurtEntity);
            }
            BetterMineTeam.debug("Set Team Threat for Team {}: {}", hurtTeam.getName(), livingAttacker.getName().getString());
        }
        // 场景2: 打野/被野怪打 (只有一方有队伍)
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

    /**
     * [核心修复] 胜者搜寻逻辑
     * 当一个生物死亡时，检查是谁杀的。
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;

        // 1. 常规清理 (移除死者)
        TeamManager.onTargetDeath(victim);

        // 2. 获取击杀者 (Killer)
        // LivingDeathEvent 的 source 包含了伤害来源
        Entity sourceEntity = event.getSource().getEntity();

        if (sourceEntity instanceof LivingEntity killer) {
            PlayerTeam killerTeam = TeamManager.getTeam(killer);
            PlayerTeam victimTeam = TeamManager.getTeam(victim);
            if (killerTeam != null && victimTeam != null && !killerTeam.isAlliedTo(victimTeam)) {
                // [新增] 强制重置冷却，确保击杀后立即进行索敌扫描
                TeamManager.resetScanCooldown(killerTeam.getName());

                TeamManager.scanAndAddThreats(killerTeam, victimTeam, killer);
            }
            // 3. 如果是团队冲突 (A队杀了B队)
            if (killerTeam != null && victimTeam != null && !killerTeam.isAlliedTo(victimTeam)) {

                // [BMT-DEBUG-WAR]
                BetterMineTeam.debug("WAR: Kill confirmed! {} (Team {}) killed {} (Team {}). Initiating Victory Scan...",
                        killer.getName().getString(), killerTeam.getName(),
                        victim.getName().getString(), victimTeam.getName());

                // 4. 以"击杀者"为中心，扫描周围 64 格的 B 队残党
                // 并将它们加入 A 队的仇恨名单
                TeamManager.scanAndAddThreats(killerTeam, victimTeam, killer);
            }
        }
    }
}