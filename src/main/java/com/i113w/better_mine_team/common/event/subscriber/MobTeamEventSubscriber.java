package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.MineTeamConfig;
import com.i113w.better_mine_team.common.entity.goal.TeamOwnerHurtTargetGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import com.i113w.better_mine_team.common.entity.goal.TeamFollowCaptainGoal;

@EventBusSubscriber(modid = BetterMineTeam.MODID)
public class MobTeamEventSubscriber {
    @SubscribeEvent
    public static void onPlayerInteractEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (BetterMineTeam.IS_CONFLUENCE_LOADED) return;
        Level level = event.getLevel();

        if (level.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player = event.getEntity();
        ItemStack itemstack = player.getItemInHand(event.getHand());
        Entity target = event.getTarget();
        Ingredient tamingMaterial = MineTeamConfig.getTamingMaterial(target.getType());

        if (tamingMaterial.test(itemstack) && target instanceof LivingEntity livingEntity) {
            ServerLevel serverLevel = (ServerLevel) level;
            Scoreboard scoreboard = serverLevel.getScoreboard();

            PlayerTeam targetTeam = scoreboard.getPlayersTeam(target.getStringUUID());
            PlayerTeam playerTeam = scoreboard.getPlayersTeam(player.getScoreboardName());

            if (targetTeam == null && playerTeam != null) {
                itemstack.consume(1, player);

                // 1. 加入队伍
                scoreboard.addPlayerToTeam(livingEntity.getStringUUID(), playerTeam);

                // 2. 增强属性 (跟随距离)
                var followAttribute = livingEntity.getAttribute(Attributes.FOLLOW_RANGE);
                if (followAttribute != null) {
                    double newRange = 128.0;
                    if (followAttribute.getBaseValue() < newRange) {
                        followAttribute.setBaseValue(newRange);
                    }
                }
                livingEntity.setHealth(livingEntity.getMaxHealth());

                // 3. 注入 AI 和 抗卸载设置
                if (livingEntity instanceof Mob mob) {
                    mob.targetSelector.addGoal(1, new TeamOwnerHurtTargetGoal(mob));
                    mob.goalSelector.addGoal(2, new TeamFollowCaptainGoal(mob, 1.2D, 10.0F, 2.0F));
                    mob.setPersistenceRequired();
                }

                // 视觉反馈
                livingEntity.setGlowingTag(true);
            }
        }
    }
}