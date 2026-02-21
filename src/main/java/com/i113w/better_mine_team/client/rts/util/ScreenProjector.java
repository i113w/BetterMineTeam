package com.i113w.better_mine_team.client.rts.util;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.rts.RTSSelectionManager;
import com.i113w.better_mine_team.common.config.BMTConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class ScreenProjector {

    /**
     * 判断实体的 AABB（8 个顶点中至少有 1 个）是否落在屏幕选框内。
     *
     * @param camPos 相机世界坐标（用于平移到相机空间）
     */
    public static boolean isAABBInScreenRect(AABB aabb,
                                             RTSSelectionManager.SelectionRect rect,
                                             Matrix4f view, Matrix4f proj,
                                             Vec3 camPos) {
        Vec3[] corners = {
                new Vec3(aabb.minX, aabb.minY, aabb.minZ), new Vec3(aabb.minX, aabb.maxY, aabb.minZ),
                new Vec3(aabb.maxX, aabb.minY, aabb.minZ), new Vec3(aabb.maxX, aabb.maxY, aabb.minZ),
                new Vec3(aabb.minX, aabb.minY, aabb.maxZ), new Vec3(aabb.minX, aabb.maxY, aabb.maxZ),
                new Vec3(aabb.maxX, aabb.minY, aabb.maxZ), new Vec3(aabb.maxX, aabb.maxY, aabb.maxZ)
        };

        boolean anyBehindCamera = false;
        boolean anyInFront = false;
        float firstScreenX = Float.NaN, firstScreenY = Float.NaN;

        for (Vec3 corner : corners) {
            // 平移至相机空间（view 矩阵只含旋转，平移在此处手动处理）
            Vec3 rel = corner.subtract(camPos);
            Vector4f v = new Vector4f(
                    (float) rel.x, (float) rel.y, (float) rel.z, 1.0f);

            // View 变换（旋转）
            v.mul(view);
            // Projection 变换
            v.mul(proj);

            // w ≤ 0 表示在相机后方，跳过
            if (v.w() <= 0f) {
                anyBehindCamera = true;
                continue;
            }
            anyInFront = true;

            // 透视除法 → NDC
            v.div(v.w());

            // NDC → GUI 坐标（scaled pixels）
            Minecraft mc = Minecraft.getInstance();
            float gw = mc.getWindow().getGuiScaledWidth();
            float gh = mc.getWindow().getGuiScaledHeight();
            float sx = (v.x() * 0.5f + 0.5f) * gw;
            float sy = (1.0f - (v.y() * 0.5f + 0.5f)) * gh;

            if (Float.isNaN(firstScreenX)) { firstScreenX = sx; firstScreenY = sy; }

            if (rect.contains(sx, sy)) {
                return true;   // 任意顶点在框内即算命中
            }
        }

        // ─── 调试输出（仅 debug 模式）────────────────────────────────────
        if (BMTConfig.isDebugEnabled()) {
            Vec3 center = new Vec3(
                    (aabb.minX + aabb.maxX) / 2,
                    (aabb.minY + aabb.maxY) / 2,
                    (aabb.minZ + aabb.maxZ) / 2);

            if (!anyInFront) {
                BetterMineTeam.debug("[RTS-PROJ] MISS (all corners behind camera) world=({},{},{})",
                        fmt(center.x), fmt(center.y), fmt(center.z));
            } else {
                BetterMineTeam.debug(
                        "[RTS-PROJ] MISS world=({},{},{}) → screen≈({},{}) | rect=({},{})→({},{}) | someBehind={}",
                        fmt(center.x), fmt(center.y), fmt(center.z),
                        fmt(firstScreenX), fmt(firstScreenY),
                        fmt(rect.x()), fmt(rect.y()),
                        fmt(rect.x() + rect.width()), fmt(rect.y() + rect.height()),
                        anyBehindCamera);
            }
        }
        return false;
    }

    private static String fmt(double v) { return String.format("%.1f", v); }
    private static String fmt(float v)  { return String.format("%.1f", v); }
}