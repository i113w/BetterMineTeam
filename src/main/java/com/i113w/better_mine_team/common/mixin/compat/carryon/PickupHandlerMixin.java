package com.i113w.better_mine_team.common.mixin.compat.carryon;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// @Pseudo 注解告诉 Mixin 处理器，这个目标类可能不存在（如果没装 Carry On），不要报错
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PickupHandler", remap = false)
public class PickupHandlerMixin {

    /**
     * 拦截 PickupHandler.tryPickupEntity 中的 overrideChecks 变量。
     * 如果目标是队友，强制将其设为 true，从而绕过敌对生物检查和黑名单检查。
     */
    @ModifyVariable(
            method = "tryPickupEntity",
            at = @At(value = "STORE"), // 在 overrideChecks 被赋值时拦截
            name = "overrideChecks",   // 变量名
            ordinal = 0,               // 方法中第 0 个 boolean 类型的局部变量
            remap = false,             // Carry On 的类通常没有被混淆映射覆盖
            argsOnly = false
    )
    private static boolean bmt$modifyOverrideChecks(boolean originalValue, ServerPlayer player, Entity entity) {
        // 1. 如果原始逻辑已经是 true (例如有脚本支持)，直接返回
        if (originalValue) return true;

        // 2. 检查 BMT 配置是否开启
        if (!BMTConfig.isTeammateCarryEnabled()) return false;

        // 3. 检查是否为生物且为队友
        if (entity instanceof LivingEntity livingEntity && TeamManager.isAlly(player, livingEntity)) {
            if (BMTConfig.isDebugEnabled()) {
                BetterMineTeam.debug("[BMT-CarryOn] Force allowing pickup for teammate: {}", entity.getName().getString());
            }
            return true; // 强制允许
        }

        return false;
    }
}