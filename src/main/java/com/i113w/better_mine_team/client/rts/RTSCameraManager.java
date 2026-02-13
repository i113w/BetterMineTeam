package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.common.registry.ModEntities;
import com.i113w.better_mine_team.common.rts.entity.RTSCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class RTSCameraManager {
    private static final RTSCameraManager INSTANCE = new RTSCameraManager();

    // RTS 模式枚举
    public enum RTSMode {
        CONTROL, // 指挥模式
        RECRUIT  // 征召模式
    }

    // 状态
    private boolean isActive = false;
    private RTSMode currentMode = RTSMode.CONTROL; // 当前模式

    private RTSCameraEntity cameraEntity;
    private Entity originalViewEntity; // 记录进入前的视角（通常是玩家）

    // 运动参数 (目标值)
    private Vec3 targetPos = Vec3.ZERO;
    private float targetYaw = 0f;
    private float targetPitch = 60f; // 默认俯视 60度
    private float zoomLevel = 20f;   // 离地高度
    private static final float DEFAULT_PITCH = 60f;
    private static final float MIN_PITCH = DEFAULT_PITCH - 30f;
    private static final float MAX_PITCH = DEFAULT_PITCH + 30f;
    private static final float LERP_SPEED = 0.2f;

    public static RTSCameraManager get() { return INSTANCE; }

    public void reset() {
        if (isActive) {
            exitRTS();
        }
        this.cameraEntity = null;
        this.originalViewEntity = null;
        this.isActive = false;
        this.currentMode = RTSMode.CONTROL; // 重置为默认
        this.targetPos = Vec3.ZERO;
    }

    // 默认 toggle 进入 CONTROL 模式
    public void toggleRTSMode() {
        toggleRTSMode(RTSMode.CONTROL);
    }

    // 指定模式切换
    public void toggleRTSMode(RTSMode mode) {
        if (isActive) exitRTS();
        else enterRTS(mode);
    }

    public boolean isActive() { return isActive; }

    // 获取当前模式
    public RTSMode getMode() { return currentMode; }

    // 增加 mode 参数
    private void enterRTS(RTSMode mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        this.currentMode = mode; // 设置模式
        this.originalViewEntity = mc.getCameraEntity();

        Vec3 playerPos = mc.player.getPosition(1.0f);
        this.targetPos = playerPos.add(0, zoomLevel, 0);
        this.targetYaw = mc.player.getYRot();
        this.targetPitch = DEFAULT_PITCH;
        int minHeight = mc.level.getMinBuildHeight();
        if (this.targetPos.y < minHeight + 5) {
            this.targetPos = new Vec3(this.targetPos.x, minHeight + 10, this.targetPos.z);
        }

        this.cameraEntity = new RTSCameraEntity(ModEntities.RTS_CAMERA.get(), mc.level);
        this.cameraEntity.setPos(this.targetPos);
        this.cameraEntity.setYRot(targetYaw);
        this.cameraEntity.setXRot(targetPitch);

        mc.level.addEntity(this.cameraEntity);
        mc.setCameraEntity(this.cameraEntity);

        this.isActive = true;
    }

    public void adjustPitch(float delta) {
        if (!isActive) return;
        this.targetPitch += delta;
        this.targetPitch = net.minecraft.util.Mth.clamp(this.targetPitch, MIN_PITCH, MAX_PITCH);
    }

    private void exitRTS() {
        Minecraft mc = Minecraft.getInstance();
        if (originalViewEntity != null) {
            mc.setCameraEntity(originalViewEntity);
        } else if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        if (cameraEntity != null) {
            cameraEntity.remove(Entity.RemovalReason.DISCARDED);
            cameraEntity = null;
        }
        this.isActive = false;
    }

    public void tick(float partialTick) {
        if (!isActive || cameraEntity == null) return;

        double curX = Mth.lerp(LERP_SPEED, cameraEntity.getX(), targetPos.x);
        double curY = Mth.lerp(LERP_SPEED, cameraEntity.getY(), targetPos.y);
        double curZ = Mth.lerp(LERP_SPEED, cameraEntity.getZ(), targetPos.z);

        float curYaw = Mth.lerp(LERP_SPEED, cameraEntity.getYRot(), targetYaw);
        float curPitch = Mth.lerp(LERP_SPEED, cameraEntity.getXRot(), targetPitch);

        cameraEntity.setPos(curX, curY, curZ);
        cameraEntity.setYRot(curYaw);
        cameraEntity.setXRot(curPitch);

        cameraEntity.xo = curX;
        cameraEntity.yo = curY;
        cameraEntity.zo = curZ;
        cameraEntity.yRotO = curYaw;
        cameraEntity.xRotO = curPitch;
    }

    public void handleInput(float moveX, float moveZ, float rotateYaw, float zoomDelta, float moveY) {
        if (!isActive) return;

        float moveSpeed = 1.0f;
        if (Minecraft.getInstance().options.keySprint.isDown()) moveSpeed = 2.0f;

        float sin = Mth.sin(targetYaw * Mth.DEG_TO_RAD);
        float cos = Mth.cos(targetYaw * Mth.DEG_TO_RAD);

        double dx = (moveX * cos - moveZ * sin) * moveSpeed;
        double dz = (moveZ * cos + moveX * sin) * moveSpeed;
        double dy = moveY * moveSpeed;

        Vec3 newTarget = targetPos.add(dx, dy, dz);
        this.targetPos = newTarget;
        this.targetYaw += rotateYaw * 5.0f;
        this.targetPos = this.targetPos.add(0, zoomDelta * -2.0, 0);

        int minHeight = Minecraft.getInstance().level.getMinBuildHeight();
        double clampedY = Mth.clamp(this.targetPos.y, minHeight + 5, 320);
        this.targetPos = new Vec3(this.targetPos.x, clampedY, this.targetPos.z);
    }
}