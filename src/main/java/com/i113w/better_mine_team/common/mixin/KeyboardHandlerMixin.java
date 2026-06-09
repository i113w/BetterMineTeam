package com.i113w.better_mine_team.common.mixin;

import com.i113w.camera_lib.camera.RTSCameraController;
import com.i113w.camera_lib.input.CameraLibKeyMappings;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void bmt$blockRtsKeyboardInput(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (!RTSCameraController.get().isActive()) return;
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_F1) return;
        if (bmt$isCameraControlKey(key, scanCode)) return;

        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            ci.cancel();
        }
    }

    @Unique
    private boolean bmt$isCameraControlKey(int key, int scanCode) {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.keyUp.matches(key, scanCode)
                || mc.options.keyDown.matches(key, scanCode)
                || mc.options.keyLeft.matches(key, scanCode)
                || mc.options.keyRight.matches(key, scanCode)
                || mc.options.keyJump.matches(key, scanCode)
                || mc.options.keyShift.matches(key, scanCode)
                || mc.options.keySprint.matches(key, scanCode)
                || CameraLibKeyMappings.CAMERA_ROTATE.matches(key, scanCode);
    }
}
