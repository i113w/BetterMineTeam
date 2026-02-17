package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
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
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = BetterMineTeam.MODID)
public class MobTeamEventSubscriber {

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;

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
    private static void handleSummonedEntityTeam(LivingEntity summon, ServerLevel level) {
        // 1. 检查配置黑名单
        if (BMTConfig.isSummonBlacklisted(summon.getType())) return;

        // 2. 检查是否已经有队伍 (避免重复处理或覆盖原有逻辑)
        PlayerTeam existingTeam = TeamManager.getTeam(summon);
        if (existingTeam != null) return;

        // 3. 获取主人
        LivingEntity owner = getSummonOwner(summon);
        if (owner == null) return;

        // 4. 检查主人是否有队伍
        PlayerTeam ownerTeam = TeamManager.getTeam(owner);
        if (ownerTeam == null) return;

        // 5. 加入队伍
        Scoreboard scoreboard = level.getScoreboard();
        scoreboard.addPlayerToTeam(summon.getStringUUID(), ownerTeam);

        // 6. 配置属性 (增加跟随距离)
        var followAttribute = summon.getAttribute(Attributes.FOLLOW_RANGE);
        if (followAttribute != null) {
            double newRange = BMTConfig.getGuardFollowRange();
            if (followAttribute.getBaseValue() < newRange) {
                followAttribute.setBaseValue(newRange);
            }
        }

        // 7. 标记与特效
        summon.setGlowingTag(true);
        summon.getPersistentData().putBoolean("bmt_summoned", true);
        // 默认禁用跟随，防止召唤物一出来就乱跑，让它们执行默认AI (比如Vex乱飞)
        // 或者设置为 true 强制跟随。通常召唤物有自己的逻辑，建议 false 或视情况而定。
        // 这里我们暂时不强制设置 "bmt_follow_enabled"，让它们保持自由行动，但在队伍中。

        BetterMineTeam.debug("Summoned Entity {} auto-joined team {} (Owner: {})",
                summon.getName().getString(), ownerTeam.getName(), owner.getName().getString());
    }

    @Nullable
    private static LivingEntity getSummonOwner(LivingEntity entity) {
        // 1. OwnableEntity
        if (entity instanceof OwnableEntity ownable) {
            Entity owner = ownable.getOwner();
            if (owner instanceof LivingEntity living) return living;
        }

        // 2. Vex
        if (entity instanceof Vex vex) {
            return vex.getOwner();
        }

        // 3. TraceableEntity
        if (entity instanceof TraceableEntity traceable) {
            Entity owner = traceable.getOwner();
            if (owner instanceof LivingEntity living) return living;
        }

        return null;
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
                    itemstack.consume(1, player);
                    scoreboard.addPlayerToTeam(livingEntity.getStringUUID(), playerTeam);

                    var followAttribute = livingEntity.getAttribute(Attributes.FOLLOW_RANGE);
                    if (followAttribute != null) {
                        double newRange = BMTConfig.getGuardFollowRange();
                        if (followAttribute.getBaseValue() < newRange) {
                            followAttribute.setBaseValue(newRange);
                        }
                    }

                    livingEntity.setHealth(livingEntity.getMaxHealth());
                    livingEntity.getPersistentData().putBoolean("bmt_follow_enabled", false);

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
            // [修改] 使用新的配置方法
            Ingredient tamingMaterial = BMTConfig.getTamingMaterial(dragon.getType());

            if (!itemstack.isEmpty() && tamingMaterial.test(itemstack)) {
                if (!TeamPermissions.hasOverridePermission(player)) {
                    player.displayClientMessage(Component.translatable("better_mine_team.msg.dragon_tame_permission_denied").withStyle(ChatFormatting.RED), true);
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                    return;
                }
                if (targetTeam != null) return;
                if (playerTeam == null) {
                    player.displayClientMessage(Component.translatable("message.better_mine_team.error.no_team_specified", player.getName()), true);
                    return;
                }
                itemstack.consume(1, player);
                scoreboard.addPlayerToTeam(dragon.getStringUUID(), playerTeam);
                dragon.setHealth(dragon.getMaxHealth());

                // [修改] 使用本地化键
                player.displayClientMessage(Component.translatable("better_mine_team.msg.dragon_tame_success").withStyle(ChatFormatting.LIGHT_PURPLE), true);

                dragon.setGlowingTag(true);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }
}