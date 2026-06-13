package com.i113w.better_mine_team.common.mixin;

import com.i113w.camera_lib.camera.RTSCameraController;
import com.i113w.camera_lib.input.CameraLibKeyMappings;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    /**
     * MC / NeoForge 26.1.2 的 KeyboardHandler#keyPress 签名是：
     *
     * keyPress(long windowPointer, int action, KeyEvent event)
     *
     * 旧版签名：
     * keyPress(long windowPointer, int key, int scanCode, int action, int modifiers)
     *
     * 会导致 Mixin Invalid descriptor。
     */
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void bmt$blockRtsKeyboardInput(long windowPointer, int action, KeyEvent event, CallbackInfo ci) {
        if (!RTSCameraController.get().isActive()) {
            return;
        }

        int key = event.key();

        // 保留 ESC / F1，避免玩家无法退出 RTS 或切换 HUD。
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_F1) {
            return;
        }

        // 保留相机控制键。
        if (bmt$isCameraControlKey(event)) {
            return;
        }

        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            ci.cancel();
        }
    }

    @Unique
    private boolean bmt$isCameraControlKey(KeyEvent event) {
        Minecraft mc = Minecraft.getInstance();

        return mc.options.keyUp.matches(event)
                || mc.options.keyDown.matches(event)
                || mc.options.keyLeft.matches(event)
                || mc.options.keyRight.matches(event)
                || mc.options.keyJump.matches(event)
                || mc.options.keyShift.matches(event)
                || mc.options.keySprint.matches(event)
                || CameraLibKeyMappings.CAMERA_ROTATE.matches(event);
    }
}