package com.i113w.better_mine_team.client.rts.util;

import com.i113w.better_mine_team.BetterMineTeam;
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

    /**
     * 从鼠标屏幕坐标（物理像素）发射射线，返回 HitResult。
     *
     * 依赖 RenderMatrixStorage 中已由 Camera+FOV 计算好的矩阵。
     */
    public static HitResult pickFromMouse(double mouseX, double mouseY, double pickRange) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();

        if (camera == null || mc.level == null) {
            BetterMineTeam.debug("[RTS-RAY] camera or level is null");
            return BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);
        }

        int winW = mc.getWindow().getWidth();
        int winH = mc.getWindow().getHeight();

        // NDC：[-1, 1]，Y 轴向上
        float ndcX = (float)(2.0 * mouseX / winW  - 1.0);
        float ndcY = (float)(1.0 - 2.0 * mouseY / winH);

        Matrix4f view = RenderMatrixStorage.capturedModelViewMatrix;
        Matrix4f proj = RenderMatrixStorage.capturedProjectionMatrix;

        // 校验：view 不应是单位矩阵（表示捕获失败）
        boolean viewIsIdentity = Math.abs(view.m00() - 1f) < 0.001f
                && Math.abs(view.m01()) < 0.001f && Math.abs(view.m02()) < 0.001f;

        BetterMineTeam.debug("[RTS-RAY] === pickFromMouse ===");
        BetterMineTeam.debug("[RTS-RAY] Win={}x{} Mouse=({},{}) NDC=({},{})",
                winW, winH, (int)mouseX, (int)mouseY,
                String.format("%.3f", ndcX), String.format("%.3f", ndcY));
        if (viewIsIdentity) {
            BetterMineTeam.debug("[RTS-RAY] View matrix is identity — matrices not captured yet! Ray will be wrong.");
        }

        Vec3 eyePos = camera.getPosition();
        Vec3 rayDir = unprojectRay(ndcX, ndcY, view, proj);

        BetterMineTeam.debug("[RTS-RAY] Eye=({},{},{}) Ray=({},{},{}) len={}",
                fmt(eyePos.x), fmt(eyePos.y), fmt(eyePos.z),
                fmt(rayDir.x), fmt(rayDir.y), fmt(rayDir.z),
                String.format("%.4f", rayDir.length()));

        Vec3 endPos = eyePos.add(rayDir.scale(pickRange));

        // 方块拣选
        HitResult blockHit = mc.level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                camera.getEntity()
        ));
        double distBlock = blockHit.getLocation().distanceToSqr(eyePos);

        // 实体拣选
        Vec3 entityEnd = blockHit.getType() != HitResult.Type.MISS
                ? blockHit.getLocation() : endPos;
        AABB box = new AABB(eyePos, entityEnd).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                mc.level, camera.getEntity(), eyePos, entityEnd, box,
                e -> !e.isSpectator() && e.isPickable()
        );

        if (entityHit != null && eyePos.distanceToSqr(entityHit.getLocation()) < distBlock) {
            BetterMineTeam.debug("[RTS-RAY] Hit ENTITY: {} at ({},{},{})",
                    entityHit.getEntity().getName().getString(),
                    fmt(entityHit.getLocation().x),
                    fmt(entityHit.getLocation().y),
                    fmt(entityHit.getLocation().z));
            return entityHit;
        }

        BetterMineTeam.debug("[RTS-RAY] Hit {} at ({},{},{})",
                blockHit.getType(),
                fmt(blockHit.getLocation().x),
                fmt(blockHit.getLocation().y),
                fmt(blockHit.getLocation().z));
        return blockHit;
    }

    /**
     * 通过 MVP 逆矩阵将 NDC 坐标反投影为世界空间射线方向。
     */
    private static Vec3 unprojectRay(float ndcX, float ndcY, Matrix4f view, Matrix4f proj) {
        // MVP = Proj * View（平移=0，因为我们用 relPos）
        Matrix4f mvp = new Matrix4f(proj).mul(view);
        Matrix4f invMVP = mvp.invert(new Matrix4f());

        // Near plane 点（NDC z=-1）和 Far plane 点（NDC z=+1）
        Vector4f near4 = new Vector4f(ndcX, ndcY, -1f, 1f).mul(invMVP);
        Vector4f far4  = new Vector4f(ndcX, ndcY,  1f, 1f).mul(invMVP);

        if (near4.w() != 0f) near4.div(near4.w());
        if (far4.w()  != 0f) far4.div(far4.w());

        BetterMineTeam.debug("[RTS-RAY] Near3=({},{},{}) Far3=({},{},{})",
                fmt(near4.x()), fmt(near4.y()), fmt(near4.z()),
                fmt(far4.x()),  fmt(far4.y()),  fmt(far4.z()));

        return new Vec3(
                far4.x() - near4.x(),
                far4.y() - near4.y(),
                far4.z() - near4.z()
        ).normalize();
    }

    private static String fmt(double v) { return String.format("%.4f", v); }
    private static String fmt(float v)  { return String.format("%.4f", v); }
}