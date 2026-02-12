package com.i113w.better_mine_team.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class ModKeyMappings {

    public static final KeyMapping DRAGON_ACCELERATE = new KeyMapping(
            "key.better_mine_team.dragon_accelerate",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_SPACE,
            "key.categories.better_mine_team"
    );

    public static final KeyMapping DRAGON_DECELERATE = new KeyMapping(
            "key.better_mine_team.dragon_decelerate",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            "key.categories.better_mine_team"
    );

    public static final KeyMapping DRAGON_DISMOUNT = new KeyMapping(
            "key.better_mine_team.dragon_dismount",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.better_mine_team"
    );

    // [修改] 改为打开队伍菜单
    public static final KeyMapping OPEN_TEAM_MENU = new KeyMapping(
            "key.better_mine_team.open_team_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, // 默认 K 键
            "key.categories.better_mine_team"
    );
    public static final KeyMapping RTS_CAMERA_ROTATE = new KeyMapping(
            "key.better_mine_team.rts_camera_rotate",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            "key.categories.better_mine_team"
    );
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DRAGON_ACCELERATE);
        event.register(DRAGON_DECELERATE);
        event.register(DRAGON_DISMOUNT);
        event.register(OPEN_TEAM_MENU);
        event.register(RTS_CAMERA_ROTATE);
    }
}