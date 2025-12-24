package com.i113w.better_mine_team.common.team;

import net.minecraft.world.entity.player.Player;

public class TeamPermissions {
    private static final String TAG_ADMIN_OVERRIDE = "bmt_lord_of_teams";

    /**
     * 检查玩家是否拥有管理权限
     */
    public static boolean hasOverridePermission(Player player) {
        return player.getPersistentData().getBoolean(TAG_ADMIN_OVERRIDE);
    }

    /**
     * 设置管理权限 (供指令调用)
     */
    public static void setOverridePermission(Player player, boolean value) {
        player.getPersistentData().putBoolean(TAG_ADMIN_OVERRIDE, value);
    }
}