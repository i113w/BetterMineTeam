package com.i113w.better_mine_team.client;

import com.i113w.better_mine_team.BetterMineTeam;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

// [修复] 移除已弃用的 @EventBusSubscriber 注解
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

    // 该方法现在通过主类手动注册
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DRAGON_ACCELERATE);
        event.register(DRAGON_DECELERATE);
        event.register(DRAGON_DISMOUNT);
    }
}