package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.camera_lib.api.IRTSInteractionDelegate;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class BmtRTSDelegate implements IRTSInteractionDelegate {

    private static final ResourceLocation CURSOR_NORMAL = ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "textures/gui/cursors/cursor_normal.png");
    private static final ResourceLocation CURSOR_ATTACK = ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "textures/gui/cursors/cursor_attack.png");
    private static final ResourceLocation CURSOR_ALLY = ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "textures/gui/cursors/cursor_ally.png");

    @Override
    public boolean isSelectable(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        if (entity == mc.player) return false;
        if (!(entity instanceof net.minecraft.world.entity.PathfinderMob)) return false;
        return true;
    }

    @Override
    public ResourceLocation getCursorIcon(@Nullable Entity hoveredEntity, boolean isAttackDragging) {
        Minecraft mc = Minecraft.getInstance();
        if (isAttackDragging) {
            return CURSOR_ATTACK;
        }
        if (hoveredEntity != null) {
            boolean isAlly = TeamManager.isAlly(mc.player, hoveredEntity instanceof LivingEntity l ? l : null);
            return isAlly ? CURSOR_ALLY : CURSOR_ATTACK;
        }
        return CURSOR_NORMAL;
    }
}