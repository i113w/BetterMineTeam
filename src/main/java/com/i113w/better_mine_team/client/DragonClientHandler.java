package com.i113w.better_mine_team.client;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.bridge.IDragonSpeed;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.network.DragonControllerPacket;
import com.i113w.better_mine_team.common.network.DragonDismountPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class DragonClientHandler {

    private static boolean lastAccelerate = false;
    private static boolean lastDecelerate = false;
    private static boolean wasMounted = false;

    private static final ResourceLocation JUMP_BAR_BACKGROUND = new ResourceLocation(BetterMineTeam.MODID, "textures/gui/hud/jump_bar_background.png");
    private static final ResourceLocation JUMP_BAR_PROGRESS = new ResourceLocation(BetterMineTeam.MODID, "textures/gui/hud/jump_bar_progress.png");
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        boolean isMounted = player.getVehicle() instanceof EnderDragon;

        // 覆盖原版下马提示逻辑
        if (isMounted && !wasMounted) {
            Component keyName = ModKeyMappings.DRAGON_DISMOUNT.getTranslatedKeyMessage();
            mc.gui.setOverlayMessage(Component.translatable("mount.onboard", keyName), false);
        }
        wasMounted = isMounted;

        if (isMounted) {
            boolean isAccelerate = ModKeyMappings.DRAGON_ACCELERATE.isDown();
            boolean isDecelerate = ModKeyMappings.DRAGON_DECELERATE.isDown();

            if (isAccelerate != lastAccelerate || isDecelerate != lastDecelerate) {
                lastAccelerate = isAccelerate;
                lastDecelerate = isDecelerate;
                // Forge 1.20.1 网络发送方式
                MTNetworkRegister.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.SERVER.noArg(),
                        new DragonControllerPacket(isAccelerate, isDecelerate)
                );
            }

            while (ModKeyMappings.DRAGON_DISMOUNT.consumeClick()) {
                MTNetworkRegister.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.SERVER.noArg(),
                        new DragonDismountPacket()
                );
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
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Forge 1.20.1 使用 VanillaGuiOverlay 而不是 VanillaGuiLayers
        if (VanillaGuiOverlay.EXPERIENCE_BAR.id().equals(event.getOverlay().id())
                && mc.player.getVehicle() instanceof EnderDragon dragon) {
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

        // Forge 1.20.1 的 blit 方法
        gfx.blit(JUMP_BAR_BACKGROUND, x, y, 0, 0, 182, 5, 182, 5);

        if (speed > 0) {
            int filledWidth = (int) (speed * 182.0F);
            if (filledWidth > 0) {
                gfx.blit(JUMP_BAR_PROGRESS, x, y, 0, 0, filledWidth, 5, 182, 5);
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