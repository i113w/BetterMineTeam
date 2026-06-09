package com.i113w.better_mine_team.client.gui;

import com.i113w.better_mine_team.common.network.TeamManagementPayload;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class ClientTeamUiState {
    private static final long GLOW_CLICK_COOLDOWN_MS = 500L;
    private static final IntegerProperty LIGHT_LEVEL = IntegerProperty.create("level", 0, 15);
    private static final ItemStack LIGHT_OFF_ICON = createLightIcon(0);
    private static final ItemStack LIGHT_ON_ICON = createLightIcon(15);

    private static long lastGlowClickMs = 0L;

    private ClientTeamUiState() {
    }

    public static boolean isLocalPlayerCaptain(Player player) {
        return player != null && player.getPersistentData().getBoolean(TeamManagementPayload.TAG_CLIENT_IS_CAPTAIN);
    }

    public static void setLocalPlayerCaptain(Player player, boolean isCaptain) {
        if (player != null) {
            player.getPersistentData().putBoolean(TeamManagementPayload.TAG_CLIENT_IS_CAPTAIN, isCaptain);
        }
    }

    public static boolean tryMarkGlowClick() {
        long now = System.currentTimeMillis();
        if (now - lastGlowClickMs < GLOW_CLICK_COOLDOWN_MS) {
            return false;
        }
        lastGlowClickMs = now;
        return true;
    }

    public static ItemStack getLightIcon(boolean enabled) {
        return enabled ? LIGHT_ON_ICON : LIGHT_OFF_ICON;
    }

    public static void setClientGlowState(LivingEntity entity, boolean enabled) {
        entity.setGlowingTag(enabled);
        entity.getPersistentData().putBoolean(TeamManager.TAG_GLOW_ENABLED, enabled);
    }

    private static ItemStack createLightIcon(int level) {
        ItemStack stack = new ItemStack(Items.LIGHT);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(LIGHT_LEVEL, level));
        return stack;
    }
}
