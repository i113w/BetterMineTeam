package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.common.registry.ModEntities;
import com.i113w.better_mine_team.common.rts.entity.RTSCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class RTSCameraManager {
    private static final RTSCameraManager INSTANCE = new RTSCameraManager();

    // 状态
    private boolean isActive = false;
    private RTSCameraEntity cameraEntity;
    private Entity originalViewEntity; // 记录进入前的视角（通常是玩家）

    // 运动参数 (目标值)
    private Vec3 targetPos = Vec3.ZERO;
    private float targetYaw = 0f;
    private float targetPitch = 60f; // 默认俯视 60度
    private float zoomLevel = 20f;   // 离地高度
    private static final float DEFAULT_PITCH = 60f;
    // 限制范围：默认值 ±30度 (即 30度 ~ 90度)
    private static final float MIN_PITCH = DEFAULT_PITCH - 30f; // 30度 (较平视)
    private static final float MAX_PITCH = DEFAULT_PITCH + 30f; // 90度 (完全垂直俯视)
    // 平滑参数
    private static final float LERP_SPEED = 0.2f;

    public static RTSCameraManager get() { return INSTANCE; }

    public void toggleRTSMode() {
        if (isActive) exitRTS();
        else enterRTS();
    }

    public boolean isActive() { return isActive; }

    private void enterRTS() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // 1. 保存当前状态
        this.originalViewEntity = mc.getCameraEntity();

        // 2. 初始化目标位置 (玩家上方)
        Vec3 playerPos = mc.player.getPosition(1.0f);
        this.targetPos = playerPos.add(0, zoomLevel, 0);
        this.targetYaw = mc.player.getYRot();
        this.targetPitch = DEFAULT_PITCH;

        // 3. 创建摄像机实体 (仅客户端世界)
        this.cameraEntity = new RTSCameraEntity(ModEntities.RTS_CAMERA.get(), mc.level);
        this.cameraEntity.setPos(this.targetPos);
        this.cameraEntity.setYRot(targetYaw);
        this.cameraEntity.setXRot(targetPitch);

        mc.level.addEntity(this.cameraEntity); // 加入世界以便渲染

        // 4. 接管视角
        mc.setCameraEntity(this.cameraEntity);

        // 5. 隐藏玩家模型 (可选，避免挡住视野)
        // mc.player.setInvisible(true); // 警告：直接设隐形可能会导致服务端同步问题，建议用渲染 Mixin 隐藏

        this.isActive = true;
    }

    public void adjustPitch(float delta) {
        if (!isActive) return;

        this.targetPitch += delta;

        // 限制范围
        this.targetPitch = net.minecraft.util.Mth.clamp(this.targetPitch, MIN_PITCH, MAX_PITCH);
    }
    private void exitRTS() {
        Minecraft mc = Minecraft.getInstance();

        // 1. 恢复视角
        if (originalViewEntity != null) {
            mc.setCameraEntity(originalViewEntity);
        } else if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }

        // 2. 清理实体
        if (cameraEntity != null) {
            cameraEntity.remove(Entity.RemovalReason.DISCARDED);
            cameraEntity = null;
        }

        this.isActive = false;
    }

    /**
     * 每帧调用 (RenderTick) 进行平滑插值
     */
    public void tick(float partialTick) {
        if (!isActive || cameraEntity == null) return;

        // 1. 平滑插值
        double curX = Mth.lerp(LERP_SPEED, cameraEntity.getX(), targetPos.x);
        double curY = Mth.lerp(LERP_SPEED, cameraEntity.getY(), targetPos.y);
        double curZ = Mth.lerp(LERP_SPEED, cameraEntity.getZ(), targetPos.z);

        float curYaw = Mth.lerp(LERP_SPEED, cameraEntity.getYRot(), targetYaw);
        float curPitch = Mth.lerp(LERP_SPEED, cameraEntity.getXRot(), targetPitch);

        // 2. 应用位置
        cameraEntity.setPos(curX, curY, curZ);
        cameraEntity.setYRot(curYaw);
        cameraEntity.setXRot(curPitch);

        // 关键：强制更新实体的 xo, yo, zo 以保证渲染插值正确
        cameraEntity.xo = curX;
        cameraEntity.yo = curY;
        cameraEntity.zo = curZ;
        cameraEntity.yRotO = curYaw;
        cameraEntity.xRotO = curPitch;
    }

    /**
     * 处理键盘/鼠标输入带来的位置变化
     */
    public void handleInput(float moveX, float moveZ, float rotateYaw, float zoomDelta, float moveY) {
        if (!isActive) return;

        float moveSpeed = 1.0f;
        if (Minecraft.getInstance().options.keySprint.isDown()) moveSpeed = 2.0f;

        float sin = Mth.sin(targetYaw * Mth.DEG_TO_RAD);
        float cos = Mth.cos(targetYaw * Mth.DEG_TO_RAD);

        double dx = (moveX * cos - moveZ * sin) * moveSpeed;
        double dz = (moveZ * cos + moveX * sin) * moveSpeed;
        double dy = moveY * moveSpeed; // [新增] 垂直移动

        Vec3 newTarget = targetPos.add(dx, dy, dz); // [修改] 加上 dy

        // ... (区块约束逻辑保持不变) ...
        // ...

        this.targetPos = newTarget;
        this.targetYaw += rotateYaw * 5.0f;
        this.targetPos = this.targetPos.add(0, zoomDelta * -2.0, 0);

        // 限制高度范围
        double clampedY = Mth.clamp(this.targetPos.y, 60, 320);
        this.targetPos = new Vec3(this.targetPos.x, clampedY, this.targetPos.z);
    }

}