package com.i113w.better_mine_team.client;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.bridge.IDragonSpeed;
import com.i113w.better_mine_team.common.network.DragonControllerPayload;
import com.i113w.better_mine_team.common.network.DragonDismountPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component; // [新增] 导入 Component
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class DragonClientHandler {

    private static boolean lastAccelerate = false;
    private static boolean lastDecelerate = false;

    // [新增] 记录上一帧是否骑着龙，用于检测“刚骑上”的瞬间
    private static boolean wasMounted = false;

    private static final ResourceLocation JUMP_BAR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/jump_bar_background");
    private static final ResourceLocation JUMP_BAR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("hud/jump_bar_progress");

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        boolean isMounted = player.getVehicle() instanceof EnderDragon;

        // [新增] 覆盖原版下马提示逻辑
        if (isMounted && !wasMounted) {
            // 玩家刚刚骑上龙，原版逻辑会发送 "Press Shift to dismount"
            // 我们立即发送一条新消息覆盖它，使用自定义的按键名称
            Component keyName = ModKeyMappings.DRAGON_DISMOUNT.getTranslatedKeyMessage();
            // "mount.onboard" 是原版语言键，格式通常为 "Press %s to dismount"
            mc.gui.setOverlayMessage(Component.translatable("mount.onboard", keyName), false);
        }
        wasMounted = isMounted;

        if (isMounted) {
            boolean isAccelerate = ModKeyMappings.DRAGON_ACCELERATE.isDown();
            boolean isDecelerate = ModKeyMappings.DRAGON_DECELERATE.isDown();

            if (isAccelerate != lastAccelerate || isDecelerate != lastDecelerate) {
                lastAccelerate = isAccelerate;
                lastDecelerate = isDecelerate;
                PacketDistributor.sendToServer(new DragonControllerPayload(isAccelerate, isDecelerate));
            }

            while (ModKeyMappings.DRAGON_DISMOUNT.consumeClick()) {
                PacketDistributor.sendToServer(new DragonDismountPayload());
            }
        }
    }

    @SubscribeEvent
    public static void onInputUpdate(MovementInputUpdateEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (player.getVehicle() instanceof EnderDragon) {
            if (event.getInput().shiftKeyDown) {
                event.getInput().shiftKeyDown = false;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (VanillaGuiLayers.EXPERIENCE_BAR.equals(event.getName()) && mc.player.getVehicle() instanceof EnderDragon dragon) {
            event.setCanceled(true);
            renderDragonSpeedBar(event.getGuiGraphics(), mc, dragon);
        }
    }

    private static void renderDragonSpeedBar(GuiGraphics gfx, Minecraft mc, EnderDragon dragon) {
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int x = width / 2 - 91;
        int y = height - 32 + 3;

        float speed = getSyncedSpeed(dragon);

        RenderSystem.enableBlend();

        gfx.blitSprite(JUMP_BAR_BACKGROUND_SPRITE, x, y, 182, 5);

        if (speed > 0) {
            int filledWidth = (int) (speed * 182.0F);
            if (filledWidth > 0) {
                gfx.blitSprite(JUMP_BAR_PROGRESS_SPRITE, 182, 5, 0, 0, x, y, filledWidth, 5);
            }
        }

        RenderSystem.disableBlend();
    }

    private static float getSyncedSpeed(EnderDragon dragon) {
        if (dragon instanceof IDragonSpeed speedDragon) {
            return speedDragon.bmt$getSpeed();
        }
        return 0.0f;
    }
}