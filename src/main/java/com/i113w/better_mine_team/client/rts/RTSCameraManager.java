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

    // 摄像机风格枚举
    public enum CameraStyle {
        FREE, // 原版自由视角
        RTS   // 等距锁定视角
    }

    // 状态
    private boolean isActive = false;
    private RTSMode currentMode = RTSMode.CONTROL;
    private CameraStyle currentStyle = CameraStyle.RTS; // 默认使用 RTS 模式

    private RTSCameraEntity cameraEntity;
    private Entity originalViewEntity;

    // 运动参数 (目标值)
    private Vec3 targetPos = Vec3.ZERO;
    private float targetYaw = 0f;
    private float targetPitch = 40f;
    private float zoomLevel = 20f;

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
        this.currentMode = RTSMode.CONTROL;
        this.currentStyle = CameraStyle.RTS; // 重置
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

    // 切换相机风格
    public void toggleCameraStyle() {
        if (!isActive) return;
        if (this.currentStyle == CameraStyle.FREE) {
            this.currentStyle = CameraStyle.RTS;
            // FREE -> RTS：从当前摄像机位置向下投射找寻地面焦点
            double groundY = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getY() : 64.0;
            Vec3 forward = Vec3.directionFromRotation(targetPitch, targetYaw);
            if (forward.y < -0.1) {
                double dist = (targetPos.y - groundY) / -forward.y;
                this.targetPos = targetPos.add(forward.scale(dist));
            } else {
                this.targetPos = new Vec3(targetPos.x, groundY, targetPos.z);
            }
            // 锁定并对齐至 45° 的倍数
            this.targetYaw = Math.round((targetYaw - 45f) / 90f) * 90f + 45f;
            this.targetPitch = Mth.clamp(targetPitch, 35f, 45f);
        } else {
            this.currentStyle = CameraStyle.FREE;
            // RTS -> FREE：直接把焦点回退到天空中的物理摄像机位置
            double orthoDist = this.zoomLevel * 3.0;
            Vec3 backward = Vec3.directionFromRotation(targetPitch, targetYaw).scale(-orthoDist);
            this.targetPos = targetPos.add(backward);
        }
    }

    public CameraStyle getCameraStyle() { return currentStyle; }
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
        this.zoomLevel = 20f;

        if (this.currentStyle == CameraStyle.RTS) {
            this.targetPos = new Vec3(playerPos.x, playerPos.y, playerPos.z); // 焦点在玩家身上
            float rawYaw = mc.player.getYRot();
            this.targetYaw = Math.round((rawYaw - 45f) / 90f) * 90f + 45f; // 对齐 45°
            this.targetPitch = 40f;
        } else {
            this.targetPos = playerPos.add(0, zoomLevel, 0);
            this.targetYaw = mc.player.getYRot();
            this.targetPitch = DEFAULT_PITCH;
        }

        int minHeight = mc.level.getMinBuildHeight();
        if (this.targetPos.y < minHeight + 5) {
            this.targetPos = new Vec3(this.targetPos.x, minHeight + 10, this.targetPos.z);
        }

        // 创建相机实体但不将其加入 Level 的实体列表，直接设为相机以绕过私有访问问题
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
        this.targetPitch += delta;
        // 根据不同模式限制仰角
        if (this.currentStyle == CameraStyle.RTS) {
            this.targetPitch = Mth.clamp(this.targetPitch, 35f, 45f);
        } else {
            this.targetPitch = Mth.clamp(this.targetPitch, MIN_PITCH, MAX_PITCH);
        }
    }

    // 每次翻转 90° 的触发器
    public void snapYaw(float step) {
        if (!isActive || currentStyle != CameraStyle.RTS) return;
        this.targetYaw += step;
    }

    private void exitRTS() {
        Minecraft mc = Minecraft.getInstance();

        // 恢复原视角
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

        double goalX, goalY, goalZ;

        if (currentStyle == CameraStyle.RTS) {
            // RTS 模式下，物理摄像机位于焦点沿视线反向后退 100~300 格处，配合极小 FOV 实现正交错觉
            double orthoDist = this.zoomLevel * 4.0;
            Vec3 backward = Vec3.directionFromRotation(targetPitch, targetYaw).scale(-orthoDist);
            goalX = targetPos.x + backward.x;
            goalY = targetPos.y + backward.y;
            goalZ = targetPos.z + backward.z;
        } else {
            goalX = targetPos.x;
            goalY = targetPos.y;
            goalZ = targetPos.z;
        }

        double curX = Mth.lerp(LERP_SPEED, cameraEntity.getX(), goalX);
        double curY = Mth.lerp(LERP_SPEED, cameraEntity.getY(), goalY);
        double curZ = Mth.lerp(LERP_SPEED, cameraEntity.getZ(), goalZ);

        // 使用最短路径的平滑旋转补间来避免 360 度鬼畜旋转
        float yawDiff = Mth.wrapDegrees(targetYaw - cameraEntity.getYRot());
        float curYaw = cameraEntity.getYRot() + yawDiff * LERP_SPEED;
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
        double dy = moveY * moveSpeed; // 恢复 Y 轴计算

        if (this.currentStyle == CameraStyle.RTS) {
            // RTS 模式：Space/Shift 控制焦点海拔升降，不再控制缩放！
            this.targetPos = this.targetPos.add(dx, dy, dz);

            int minHeight = Minecraft.getInstance().level.getMinBuildHeight();
            double clampedY = Mth.clamp(this.targetPos.y, minHeight, 320);
            this.targetPos = new Vec3(this.targetPos.x, clampedY, this.targetPos.z);
        } else {
            // Free 模式：原始逻辑
            this.targetPos = this.targetPos.add(dx, dy, dz);
            this.targetYaw += rotateYaw * 5.0f;
            this.targetPos = this.targetPos.add(0, zoomDelta * -2.0, 0);

            int minHeight = Minecraft.getInstance().level.getMinBuildHeight();
            double clampedY = Mth.clamp(this.targetPos.y, minHeight + 5, 320);
            this.targetPos = new Vec3(this.targetPos.x, clampedY, this.targetPos.z);
        }
    }

    public void handleZoom(float scrollDelta) {
        if (!isActive) return;

        if (this.currentStyle == CameraStyle.RTS) {
            // 滚轮向上 (正数) = 放大拉近，滚轮向下 (负数) = 缩小推远
            this.zoomLevel -= scrollDelta * 3.5f;
            this.zoomLevel = Mth.clamp(this.zoomLevel, 10f, 80f);
        } else {
            Vec3 forward = Vec3.directionFromRotation(targetPitch, targetYaw).scale(scrollDelta * 2.0);
            this.targetPos = this.targetPos.add(forward);
        }
    }
}