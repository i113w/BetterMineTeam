package com.i113w.better_mine_team.common.mixin.compat.carryon;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tschipp.carryon.common.carry.PickupHandler;

import java.util.function.Function;

@Mixin(value = PickupHandler.class, remap = false)
public class PickupHandlerMixin {

    @Unique
    private static final ThreadLocal<ServerPlayer> bmt$currentPlayer = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<Entity> bmt$currentEntity = new ThreadLocal<>();

    @Inject(method = "tryPickupEntity", at = @At("HEAD"), remap = false)
    private static void bmt$captureContext(ServerPlayer player, Entity entity, Function pickupCallback, CallbackInfoReturnable<Boolean> cir) {
        bmt$currentPlayer.set(player);
        bmt$currentEntity.set(entity);

        // 调试输出：检查是否触发了拾取
        if (BMTConfig.isDebugEnabled() && entity instanceof LivingEntity living) {
            boolean isAlly = TeamManager.isAlly(player, living);
            BetterMineTeam.debug("[BMTxCarryOn] Player {} tries to pickup {}. Config Enabled? {} | IsAlly? {}",
                    player.getName().getString(), entity.getName().getString(), BMTConfig.isTeammateCarryEnabled(), isAlly);
        }
    }

    @Inject(method = "tryPickupEntity", at = @At("RETURN"), remap = false)
    private static void bmt$clearContext(ServerPlayer player, Entity entity, Function pickupCallback, CallbackInfoReturnable<Boolean> cir) {
        if (BMTConfig.isDebugEnabled()) {
            BetterMineTeam.debug("[BMTxCarryOn] Pickup result for {}: {}", entity.getName().getString(), cir.getReturnValueZ());
        }
        bmt$currentPlayer.remove();
        bmt$currentEntity.remove();
    }

    /**
     * 1. 绕过 Carry On 的 Config 黑名单检查
     */
    @Redirect(
            method = "tryPickupEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Ltschipp/carryon/common/config/ListHandler;isPermitted(Lnet/minecraft/world/entity/Entity;)Z",
                    remap = false
            ),
            remap = false
    )
    private static boolean bmt$redirectIsPermitted(Entity entityTarget) {
        ServerPlayer player = bmt$currentPlayer.get();
        // 检查 Config 开关
        if (BMTConfig.isTeammateCarryEnabled() && player != null && entityTarget instanceof LivingEntity living) {
            if (TeamManager.isAlly(player, living)) {
                BetterMineTeam.debug("[BMTxCarryOn] Bypassed Blacklist for ally: {}", living.getName().getString());
                return true;
            }
        }
        return tschipp.carryon.common.config.ListHandler.isPermitted(entityTarget);
    }

    /**
     * 2. 绕过敌对生物分类检查 (将其伪装成 CREATURE，即和平生物)
     */
    @Redirect(
            method = "tryPickupEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/EntityType;getCategory()Lnet/minecraft/world/entity/MobCategory;"
            ),
            remap = true
    )
    private static MobCategory bmt$redirectCategory(EntityType<?> instance) {
        ServerPlayer player = bmt$currentPlayer.get();
        Entity targetEntity = bmt$currentEntity.get();

        // 检查 Config 开关
        if (BMTConfig.isTeammateCarryEnabled() && player != null && targetEntity instanceof LivingEntity living) {
            if (TeamManager.isAlly(player, living)) {
                BetterMineTeam.debug("[BMTxCarryOn] Spoofed MobCategory to CREATURE for ally: {}", living.getName().getString());
                return MobCategory.CREATURE;
            }
        }
        return instance.getCategory();
    }

    /**
     * 3. 绕过体型高度检查
     */
    @Redirect(
            method = "tryPickupEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBbHeight()F"
            ),
            remap = true
    )
    private static float bmt$redirectHeight(Entity instance) {
        ServerPlayer player = bmt$currentPlayer.get();
        // 检查 Config 开关
        if (BMTConfig.isTeammateCarryEnabled() && player != null && instance instanceof LivingEntity living) {
            if (TeamManager.isAlly(player, living)) {
                return 0.0F; // 伪装为极小高度
            }
        }
        return instance.getBbHeight();
    }

    /**
     * 4. 绕过体型宽度检查
     */
    @Redirect(
            method = "tryPickupEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBbWidth()F"
            ),
            remap = true
    )
    private static float bmt$redirectWidth(Entity instance) {
        ServerPlayer player = bmt$currentPlayer.get();
        // 检查 Config 开关
        if (BMTConfig.isTeammateCarryEnabled() && player != null && instance instanceof LivingEntity living) {
            if (TeamManager.isAlly(player, living)) {
                return 0.0F; // 伪装为极小宽度
            }
        }
        return instance.getBbWidth();
    }
}