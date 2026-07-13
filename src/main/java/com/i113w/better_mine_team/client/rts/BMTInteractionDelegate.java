package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.camera_lib.api.IRTSInteractionDelegate;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class BMTInteractionDelegate implements IRTSInteractionDelegate {

    private static final ResourceLocation CURSOR_NORMAL = new ResourceLocation(BetterMineTeam.MODID, "textures/gui/cursors/cursor_normal.png");
    private static final ResourceLocation CURSOR_ATTACK = new ResourceLocation(BetterMineTeam.MODID, "textures/gui/cursors/cursor_attack.png");
    private static final ResourceLocation CURSOR_ALLY   = new ResourceLocation(BetterMineTeam.MODID, "textures/gui/cursors/cursor_ally.png");

    @Override
    public boolean isSelectable(Entity entity) {
        if (!(entity instanceof PathfinderMob mob) || !entity.isAlive()
                || entity.getY() < -64 || entity.getY() > 320) return false;
        if (ClientRTSStateManager.get().getMode() == ClientRTSStateManager.RTSMode.PATROL) {
            if (!ClientPatrolSettings.get().enabled()) return false;
            Player player = Minecraft.getInstance().player;
            if (player == null) return false;
            return TeamManager.isAlly(player, mob)
                    || entity instanceof TamableAnimal tamable && tamable.isOwnedBy(player);
        }
        return true;
    }

    public static boolean isEnemyLike(Entity entity) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !(entity instanceof LivingEntity living)) return false;
        return !TeamManager.isAlly(player, living);
    }

    @Override
    public ResourceLocation getCursorIcon(@Nullable Entity hoveredEntity, boolean isAttackDragging) {
        if (isAttackDragging) {
            return CURSOR_ATTACK;
        }
        if (hoveredEntity != null && isEnemyLike(hoveredEntity)) {
            return CURSOR_ATTACK;
        }
        if (hoveredEntity != null && isSelectable(hoveredEntity)) {
            return CURSOR_ALLY;
        }
        return CURSOR_NORMAL;
    }
}
