package com.i113w.better_mine_team.common.mixin.compat.carryon;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

// @Pseudo 防止未安装 Carry On 时崩溃
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PickupHandler", remap = false)
public class PickupHandlerMixin {

    // 使用 ThreadLocal 跨方法传递上下文，这是处理 Redirect 无法访问局部变量的标准做法
    @Unique
    private static final ThreadLocal<ServerPlayer> bmt$currentPlayer = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Entity> bmt$currentEntity = new ThreadLocal<>();

    /**
     * 1. 上下文捕获 (HEAD)
     * 在方法开始时，将 player 和 entity 存入 ThreadLocal
     */
    @Inject(
            method = "tryPickupEntity(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ljava/util/function/Function;)Z",
            at = @At("HEAD"),
            remap = false
    )
    private static void bmt$captureContext(ServerPlayer player, Entity entity, Function<?, ?> pickupCallback, CallbackInfoReturnable<Boolean> cir) {
        bmt$currentPlayer.set(player);
        bmt$currentEntity.set(entity);
    }

    /**
     * 2. 上下文清理 (RETURN)
     * 方法结束时清理，防止内存泄漏
     */
    @Inject(
            method = "tryPickupEntity(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ljava/util/function/Function;)Z",
            at = @At("RETURN"),
            remap = false
    )
    private static void bmt$clearContext(ServerPlayer player, Entity entity, Function<?, ?> pickupCallback, CallbackInfoReturnable<Boolean> cir) {
        bmt$currentPlayer.remove();
        bmt$currentEntity.remove();
    }

    /**
     * 3. 绕过【黑名单】和【敌对生物】检查
     * 通过修改 overrideChecks 变量为 true，Carry On 会自动跳过 isPermitted 和 getCategory 检查
     */
    @ModifyVariable(
            method = "tryPickupEntity(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ljava/util/function/Function;)Z",
            at = @At(value = "STORE"),
            name = "overrideChecks",
            ordinal = 0,
            remap = false,
            argsOnly = false
    )
    private static boolean bmt$forceOverrideChecks(boolean originalValue, ServerPlayer player, Entity entity) {
        if (originalValue) return true; // 如果已经是 true，保持不变
        if (!BMTConfig.isTeammateCarryEnabled()) return false; // 如果配置关闭，保持原样

        if (entity instanceof LivingEntity living && TeamManager.isAlly(player, living)) {
            if (BMTConfig.isDebugEnabled()) {
                BetterMineTeam.debug("[BMT-CarryOn] Setting overrideChecks=true for ally: {}", entity.getName().getString());
            }
            return true; // 强制设为 true
        }
        return false;
    }

    /**
     * 4. 绕过【高度】检查
     * 对于 Boss（如凋灵、巨人），Carry On 会检查 getBbHeight。我们将其重定向。
     */
    @Redirect(
            method = "tryPickupEntity(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ljava/util/function/Function;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBbHeight()F"
            ),
            remap = false
    )
    private static float bmt$redirectHeight(Entity instance) {
        // 从 ThreadLocal 获取上下文
        ServerPlayer player = bmt$currentPlayer.get();
        Entity currentEntity = bmt$currentEntity.get();

        // 确保我们在处理同一个实体
        if (player != null && currentEntity == instance && BMTConfig.isTeammateCarryEnabled()) {
            if (currentEntity instanceof LivingEntity living && TeamManager.isAlly(player, living)) {
                return 0.0F; // 欺骗 Carry On，使其认为该生物高度为 0
            }
        }
        return instance.getBbHeight();
    }

    /**
     * 5. 绕过【宽度】检查
     */
    @Redirect(
            method = "tryPickupEntity(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ljava/util/function/Function;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBbWidth()F"
            ),
            remap = false
    )
    private static float bmt$redirectWidth(Entity instance) {
        ServerPlayer player = bmt$currentPlayer.get();
        Entity currentEntity = bmt$currentEntity.get();

        if (player != null && currentEntity == instance && BMTConfig.isTeammateCarryEnabled()) {
            if (currentEntity instanceof LivingEntity living && TeamManager.isAlly(player, living)) {
                return 0.0F; // 欺骗 Carry On，使其认为该生物宽度为 0
            }
        }
        return instance.getBbWidth();
    }
}