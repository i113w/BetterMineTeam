package com.i113w.better_mine_team.client;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.bridge.IDragonSpeed;
import com.i113w.better_mine_team.common.network.DragonControllerPayload;
import com.i113w.better_mine_team.common.network.DragonDismountPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component; // [新增] 导入 Component
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT)
public class DragonClientHandler {

    private static boolean lastAccelerate = false;
    private static boolean lastDecelerate = false;

    // [新增] 记录上一帧是否骑着龙，用于检测“刚骑上”的瞬间
    private static boolean wasMounted = false;

    private static float syncedDragonSpeed = 0.0F;

    public static void setSyncedDragonSpeed(float speed) {
        syncedDragonSpeed = speed;
    }

    private static final Identifier JUMP_BAR_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("hud/jump_bar_background");
    private static final Identifier JUMP_BAR_PROGRESS_SPRITE = Identifier.withDefaultNamespace("hud/jump_bar_progress");

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        boolean isMounted = player.getVehicle() instanceof EnderDragon;

        if (!isMounted) {
            syncedDragonSpeed = 0.0F;
        }
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
                ClientPacketDistributor.sendToServer(new DragonControllerPayload(isAccelerate, isDecelerate));
            }

            while (ModKeyMappings.DRAGON_DISMOUNT.consumeClick()) {
                ClientPacketDistributor.sendToServer(new DragonDismountPayload());
            }
        }
    }

    @SubscribeEvent
    public static void onInputUpdate(MovementInputUpdateEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (player.getVehicle() instanceof EnderDragon) {
            Input input = event.getInput().keyPresses;
            if (input.shift()) {
                event.getInput().keyPresses = new Input(
                        input.forward(),
                        input.backward(),
                        input.left(),
                        input.right(),
                        input.jump(),
                        false,
                        input.sprint()
                );
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiLayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (VanillaGuiLayers.CONTEXTUAL_INFO_BAR.equals(event.getName()) && mc.player.getVehicle() instanceof EnderDragon dragon) {
            event.setCanceled(true);
            renderDragonSpeedBar(event.getGuiGraphics(), mc, dragon);
        }
    }

    private static void renderDragonSpeedBar(GuiGraphicsExtractor gfx, Minecraft mc, EnderDragon dragon) {
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int x = width / 2 - 91;
        int y = height - 32 + 3;

        float speed = syncedDragonSpeed;

        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, JUMP_BAR_BACKGROUND_SPRITE, x, y, 182, 5);

        if (speed > 0) {
            int filledWidth = (int) (speed * 182.0F);
            if (filledWidth > 0) {
                gfx.blitSprite(RenderPipelines.GUI_TEXTURED, JUMP_BAR_PROGRESS_SPRITE, 182, 5, 0, 0, x, y, filledWidth, 5);
            }
        }
    }


}
