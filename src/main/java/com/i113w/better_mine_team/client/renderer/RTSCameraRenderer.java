package com.i113w.better_mine_team.client.renderer;

import com.i113w.better_mine_team.common.rts.entity.RTSCameraEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.jetbrains.annotations.NotNull;

public class RTSCameraRenderer extends EntityRenderer<RTSCameraEntity> {

    public RTSCameraRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(@NotNull RTSCameraEntity entity, @NotNull Frustum frustum, double camX, double camY, double camZ) {
        // 核心：直接返回 false，告诉引擎跳过渲染
        return false;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull RTSCameraEntity entity) {
        // 虽然不渲染，但为了防止某些 Mod 或原版逻辑调用此方法报错，返回一个通用纹理
        return InventoryMenu.BLOCK_ATLAS;
    }
}