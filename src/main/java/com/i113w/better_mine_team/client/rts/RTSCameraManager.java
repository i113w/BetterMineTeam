package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.common.registry.ModEntities;
import com.i113w.better_mine_team.common.rts.entity.RTSCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 管理 RTS 俯视相机的状态与运动。
 *
 * 修复说明：
 * - RTSCameraEntity 不再添加到 ClientLevel（addEntity 为 private）。
 *   它仅作为内存中的独立对象存在，通过 mc.setCameraEntity() 设置为视角实体。
 *   这完全满足需求：Minecraft 的相机系统只需要 getCameraEntity() 返回一个有位置/旋转的
 *   Entity 对象，并不强制要求它在 level 的实体列表中。
 */
public class RTSCameraManager {

    private static final RTSCameraManager INSTANCE = new RTSCameraManager();

    public enum RTSMode {
        CONTROL,
        RECRUIT
    }

    private boolean isActive = false;
    private RTSMode currentMode = RTSMode.CONTROL;

    private RTSCameraEntity cameraEntity;
    private Entity originalViewEntity;

    private Vec3 targetPos = Vec3.ZERO;
    private float targetYaw = 0f;
    private float targetPitch = 60f;
    private float zoomLevel = 20f;

    private static final float DEFAULT_PITCH = 60f;
    private static final float MIN_PITCH = DEFAULT_PITCH - 30f;
    private static final float MAX_PITCH = DEFAULT_PITCH + 30f;
    private static final float LERP_SPEED = 0.2f;

    public static RTSCameraManager get() { return INSTANCE; }

    public void reset() {
        if (isActive) exitRTS();
        this.cameraEntity = null;
        this.originalViewEntity = null;
        this.isActive = false;
        this.currentMode = RTSMode.CONTROL;
        this.targetPos = Vec3.ZERO;
    }

    public void toggleRTSMode() { toggleRTSMode(RTSMode.CONTROL); }

    public void toggleRTSMode(RTSMode mode) {
        if (isActive) exitRTS();
        else enterRTS(mode);
    }

    public boolean isActive() { return isActive; }
    public RTSMode getMode() { return currentMode; }

    private void enterRTS(RTSMode mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        this.currentMode = mode;
        this.originalViewEntity = mc.getCameraEntity();

        Vec3 playerPos = mc.player.getPosition(1.0f);
        this.targetPos = playerPos.add(0, zoomLevel, 0);
        this.targetYaw = mc.player.getYRot();
        this.targetPitch = DEFAULT_PITCH;

        int minHeight = mc.level.getMinBuildHeight();
        if (this.targetPos.y < minHeight + 5) {
            this.targetPos = new Vec3(this.targetPos.x, minHeight + 10, this.targetPos.z);
        }

        // 创建相机实体，传入 level 是为了让 Entity 构造器正常初始化，
        // 但我们不调用 level.addEntity()，避免 private 访问限制。
        // Minecraft 的相机系统只查询 getCameraEntity() 的位置/旋转，
        // 不要求实体必须在 level 的实体列表中。
        this.cameraEntity = new RTSCameraEntity(ModEntities.RTS_CAMERA.get(), mc.level);
        this.cameraEntity.setPos(this.targetPos);
        this.cameraEntity.setYRot(targetYaw);
        this.cameraEntity.setXRot(targetPitch);

        // 直接设置为相机实体（不入 world）
        mc.setCameraEntity(this.cameraEntity);

        this.isActive = true;
    }

    public void adjustPitch(float delta) {
        if (!isActive) return;
        this.targetPitch = Mth.clamp(this.targetPitch + delta, MIN_PITCH, MAX_PITCH);
    }

    private void exitRTS() {
        Minecraft mc = Minecraft.getInstance();

        // 恢复原视角
        if (originalViewEntity != null) {
            mc.setCameraEntity(originalViewEntity);
        } else if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }

        // 相机实体不在 level 中，直接置空即可（GC 回收）
        this.cameraEntity = null;
        this.isActive = false;
    }

    /** 在 ClientTickEvent (Phase.END) 中调用 */
    public void tick(float partialTick) {
        if (!isActive || cameraEntity == null) return;

        double curX = Mth.lerp(LERP_SPEED, cameraEntity.getX(), targetPos.x);
        double curY = Mth.lerp(LERP_SPEED, cameraEntity.getY(), targetPos.y);
        double curZ = Mth.lerp(LERP_SPEED, cameraEntity.getZ(), targetPos.z);

        float curYaw   = Mth.lerp(LERP_SPEED, cameraEntity.getYRot(),  targetYaw);
        float curPitch = Mth.lerp(LERP_SPEED, cameraEntity.getXRot(), targetPitch);

        cameraEntity.setPos(curX, curY, curZ);
        cameraEntity.setYRot(curYaw);
        cameraEntity.setXRot(curPitch);

        // 同步上一帧坐标，防止插值抖动
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

        this.targetPos = targetPos.add(dx, dy, dz);
        this.targetYaw += rotateYaw * 5.0f;
        this.targetPos = this.targetPos.add(0, zoomDelta * -2.0, 0);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            int minHeight = mc.level.getMinBuildHeight();
            double clampedY = Mth.clamp(this.targetPos.y, minHeight + 5, 320);
            this.targetPos = new Vec3(this.targetPos.x, clampedY, this.targetPos.z);
        }
    }
}
