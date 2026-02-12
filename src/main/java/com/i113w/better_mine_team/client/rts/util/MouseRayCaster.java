package com.i113w.better_mine_team.client.rts.util;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class MouseRayCaster {

    public static HitResult pickFromMouse(double mouseX, double mouseY, double pickRange) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();

        if (camera == null || mc.level == null) {
            return BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);
        }

        Vec3 eyePos = camera.getPosition();

        // [核心修复] 使用基于捕获矩阵的射线计算
        Vec3 rayDir = calculateRayDirection(mouseX, mouseY);

        Vec3 endPos = eyePos.add(rayDir.scale(pickRange));

        // 1. 方块碰撞检测
        HitResult blockHit = mc.level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                camera.getEntity()
        ));

        double distToBlock = blockHit.getLocation().distanceToSqr(eyePos);

        // 2. 实体碰撞检测
        Vec3 entityCheckEnd = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : endPos;
        AABB searchBox = new AABB(eyePos, endPos).inflate(1.0);

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                mc.level,
                camera.getEntity(),
                eyePos,
                entityCheckEnd,
                searchBox,
                (e) -> !e.isSpectator() && e.isPickable()
        );

        if (entityHit != null) {
            double distToEntity = eyePos.distanceToSqr(entityHit.getLocation());
            if (distToEntity < distToBlock) {
                return entityHit;
            }
        }

        return blockHit;
    }

    /**
     * [核心修复] 使用捕获的 MVP 矩阵进行反投影
     * 解决了手动重建 ViewMatrix 导致的射线始终指向屏幕中心的问题
     */
    private static Vec3 calculateRayDirection(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();

        // 使用物理分辨率，配合下面的视口变换
        int windowWidth = mc.getWindow().getWidth();
        int windowHeight = mc.getWindow().getHeight();

        // 1. 计算 NDC (Normalized Device Coordinates) [-1, 1]
        // 注意 Y 轴翻转：屏幕坐标 Y 向下，NDC Y 向上
        float ndcX = (float) (2.0 * mouseX / windowWidth - 1.0);
        float ndcY = (float) (1.0 - 2.0 * mouseY / windowHeight);

        // 2. 构建 MVP 逆矩阵 (Projection * ModelView)^-1
        Matrix4f invMVP = new Matrix4f(RenderMatrixStorage.capturedProjectionMatrix);
        invMVP.mul(RenderMatrixStorage.capturedModelViewMatrix);
        invMVP.invert();

        // 3. 反投影近平面点 (Z = -1.0) 和 远平面点 (Z = 1.0)
        // 这是图形学中定义射线的标准方法
        Vector4f nearPoint = new Vector4f(ndcX, ndcY, -1.0f, 1.0f);
        Vector4f farPoint = new Vector4f(ndcX, ndcY, 1.0f, 1.0f);

        nearPoint.mul(invMVP);
        farPoint.mul(invMVP);

        // 4. 透视除法 (Perspective Divide)
        if (nearPoint.w != 0) nearPoint.div(nearPoint.w);
        if (farPoint.w != 0) farPoint.div(farPoint.w);

        // 5. 计算方向向量 (Far - Near)
        return new Vec3(
                farPoint.x() - nearPoint.x(),
                farPoint.y() - nearPoint.y(),
                farPoint.z() - nearPoint.z()
        ).normalize();
    }
}