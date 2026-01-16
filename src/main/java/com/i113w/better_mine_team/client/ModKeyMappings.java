package com.i113w.better_mine_team.client;

import com.i113w.better_mine_team.BetterMineTeam;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

// [关键修改] RegisterKeyMappingsEvent 属于 MOD 总线
@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
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

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(DRAGON_ACCELERATE);
        event.register(DRAGON_DECELERATE);
        event.register(DRAGON_DISMOUNT);
    }
}