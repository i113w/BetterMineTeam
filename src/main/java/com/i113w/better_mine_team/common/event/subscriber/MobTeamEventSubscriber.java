package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.compat.LoadedCompat;
import com.i113w.better_mine_team.common.compat.irons_spellbooks.IronsSpellbooksCompat;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.entity.goal.TeamFollowCaptainGoal;
import com.i113w.better_mine_team.common.entity.goal.TeamHurtByTargetGoal;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.better_mine_team.common.team.TeamPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

// [关键修改] 实体事件属于 Forge 总线（默认）
@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID)
public class MobTeamEventSubscriber {

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (BMTConfig.isSummonAutoJoinEnabled() && event.getEntity() instanceof LivingEntity living) {
            handleSummonedEntityTeam(living, (ServerLevel) event.getLevel());
        }
        if (event.getEntity() instanceof Mob mob) {
            mob.targetSelector.addGoal(1, new TeamHurtByTargetGoal(mob));
            mob.goalSelector.addGoal(2, new TeamFollowCaptainGoal(mob,
                    BMTConfig.getGuardFollowSpeed(),
                    BMTConfig.getGuardFollowStartDist(),
                    BMTConfig.getGuardFollowStopDist()));
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (BetterMineTeam.IS_CONFLUENCE_LOADED) return;
        Level level = event.getLevel();

        if (level.isClientSide()) {
            if (event.getHand() == InteractionHand.MAIN_HAND) return;
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        ItemStack itemstack = player.getItemInHand(event.getHand());
        Entity target = event.getTarget();

        if (target instanceof EnderDragonPart part) {
            target = part.parentMob;
        }

        if (target instanceof EnderDragon dragon) {
            handleDragonInteraction(event, player, dragon, itemstack, level);
            return;
        }

        if (target instanceof LivingEntity livingEntity) {
            if (!BMTConfig.isMobTamingEnabled()) return;
            Ingredient tamingMaterial = BMTConfig.getTamingMaterial(target.getType());

            if (!itemstack.isEmpty() && tamingMaterial.test(itemstack)) {
                ServerLevel serverLevel = (ServerLevel) level;
                Scoreboard scoreboard = serverLevel.getScoreboard();

                PlayerTeam targetTeam = scoreboard.getPlayersTeam(target.getStringUUID());
                PlayerTeam playerTeam = scoreboard.getPlayersTeam(player.getScoreboardName());

                if (targetTeam == null && playerTeam != null) {
                    // [关键修改] 1.20.1 使用 shrink() 代替 consume()
                    itemstack.shrink(1);

                    scoreboard.addPlayerToTeam(livingEntity.getStringUUID(), playerTeam);

                    var followAttribute = livingEntity.getAttribute(Attributes.FOLLOW_RANGE);
                    if (followAttribute != null) {
                        double newRange = BMTConfig.getGuardFollowRange();
                        if (followAttribute.getBaseValue() < newRange) {
                            followAttribute.setBaseValue(newRange);
                        }
                    }

                    livingEntity.setHealth(livingEntity.getMaxHealth());
                    livingEntity.getPersistentData().putBoolean("bmt_follow_enabled", BMTConfig.getDefaultFollowState());

                    if (livingEntity instanceof Mob mob) {
                        mob.setPersistenceRequired();
                        mob.targetSelector.addGoal(1, new TeamHurtByTargetGoal(mob));
                        mob.goalSelector.addGoal(2, new TeamFollowCaptainGoal(mob,
                                BMTConfig.getGuardFollowSpeed(),
                                BMTConfig.getGuardFollowStartDist(),
                                BMTConfig.getGuardFollowStopDist()));
                    }
                    livingEntity.setGlowingTag(true);

                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
            }
        }
    }
    private static void handleSummonedEntityTeam(LivingEntity summon, ServerLevel level) {
        if (BMTConfig.isSummonBlacklisted(summon.getType())) return;

        PlayerTeam existingTeam = TeamManager.getTeam(summon);
        if (existingTeam != null) return;

        LivingEntity owner = getSummonOwner(summon);
        if (owner == null) return;

        PlayerTeam ownerTeam = TeamManager.getTeam(owner);
        if (ownerTeam == null) return;

        Scoreboard scoreboard = level.getScoreboard();
        scoreboard.addPlayerToTeam(summon.getStringUUID(), ownerTeam);

        var followAttribute = summon.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        if (followAttribute != null) {
            double newRange = BMTConfig.getGuardFollowRange();
            if (followAttribute.getBaseValue() < newRange) followAttribute.setBaseValue(newRange);
        }

        summon.setGlowingTag(true);
        summon.getPersistentData().putBoolean("bmt_summoned", true);

        BetterMineTeam.debug("Summoned Entity {} auto-joined team {} (Owner: {})",
                summon.getName().getString(), ownerTeam.getName(), owner.getName().getString());
    }

    @Nullable
    private static LivingEntity getSummonOwner(LivingEntity entity) {
        // --- 优先级 1: 模组兼容 ---

        // Iron's Spells n' Spellbooks
        if (LoadedCompat.IRONS_SPELLBOOKS) {
            // 防止未安装模组时引发 ClassNotFoundException
            LivingEntity modOwner = IronsSpellbooksCompat.getSummonOwner(entity);
            if (modOwner != null) return modOwner;
        }

        // --- 优先级 2: 原版通用接口 ---

        // 1. OwnableEntity (原版：狼、猫、鹦鹉等)
        if (entity instanceof OwnableEntity ownable) {
            Entity owner = ownable.getOwner();
            if (owner instanceof LivingEntity living) return living;
        }

        // 2. Vex (恼鬼)
        if (entity instanceof Vex vex) {
            return vex.getOwner();
        }

        // 3. TraceableEntity (通常是一些投射物或其他可溯源实体)
        if (entity instanceof TraceableEntity traceable) {
            Entity owner = traceable.getOwner();
            if (owner instanceof LivingEntity living) return living;
        }

        return null;
    }
    private static void handleDragonInteraction(PlayerInteractEvent.EntityInteract event, Player player, EnderDragon dragon, ItemStack itemstack, Level level) {
        Scoreboard scoreboard = level.getScoreboard();
        PlayerTeam targetTeam = scoreboard.getPlayersTeam(dragon.getStringUUID());
        PlayerTeam playerTeam = scoreboard.getPlayersTeam(player.getScoreboardName());

        if (itemstack.isEmpty() && BMTConfig.isDragonRidingEnabled()) {
            if (targetTeam != null && playerTeam != null && targetTeam.getName().equals(playerTeam.getName())) {
                player.startRiding(dragon);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        if (BMTConfig.isDragonTamingEnabled()) {
            if (!BMTConfig.isMobTamingEnabled()) return;
            Ingredient tamingMaterial = BMTConfig.getTamingMaterial(dragon.getType());

            if (!itemstack.isEmpty() && tamingMaterial.test(itemstack)) {
                if (!TeamPermissions.hasOverridePermission(player)) {
                    player.displayClientMessage(
                            Component.translatable("better_mine_team.msg.dragon_tame_permission_denied").withStyle(ChatFormatting.RED),
                            true
                    );
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                    return;
                }
                if (targetTeam != null) return;
                if (playerTeam == null) {
                    player.displayClientMessage(
                            Component.translatable("message.better_mine_team.error.no_team_specified", player.getName()),
                            true
                    );
                    return;
                }

                // [关键修改] 1.20.1 使用 shrink() 代替 consume()
                itemstack.shrink(1);

                scoreboard.addPlayerToTeam(dragon.getStringUUID(), playerTeam);
                dragon.setHealth(dragon.getMaxHealth());

                player.displayClientMessage(
                        Component.translatable("better_mine_team.msg.dragon_tame_success").withStyle(ChatFormatting.LIGHT_PURPLE),
                        true
                );

                dragon.setGlowingTag(true);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }
}