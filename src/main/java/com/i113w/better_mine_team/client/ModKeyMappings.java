package com.i113w.better_mine_team.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.i113w.better_mine_team.BetterMineTeam;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class ModKeyMappings {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(BetterMineTeam.MODID, "better_mine_team")
    );

    public static final KeyMapping DRAGON_ACCELERATE = new KeyMapping(
            "key.better_mine_team.dragon_accelerate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORY);
    public static final KeyMapping DRAGON_DECELERATE = new KeyMapping(
            "key.better_mine_team.dragon_decelerate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, CATEGORY);
    public static final KeyMapping DRAGON_DISMOUNT = new KeyMapping(
            "key.better_mine_team.dragon_dismount", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping OPEN_TEAM_MENU = new KeyMapping(
            "key.better_mine_team.open_team_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(DRAGON_ACCELERATE);
        event.register(DRAGON_DECELERATE);
        event.register(DRAGON_DISMOUNT);
        event.register(OPEN_TEAM_MENU);
    }
}
