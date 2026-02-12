package com.i113w.better_mine_team.client.rts.util;

import com.i113w.better_mine_team.client.rts.RTSSelectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class ScreenProjector {

    /**
     * 判断一个 AABB 是否与当前的 2D 选框相交
     * @param aabb 实体的世界坐标包围盒
     * @param rect 2D 选框 (GUI 坐标系)
     * @param view 视图矩阵
     * @param proj 投影矩阵
     * @param camPos 摄像机当前位置 (用于相对坐标计算)
     */
    public static boolean isAABBInScreenRect(AABB aabb, RTSSelectionManager.SelectionRect rect,
                                             Matrix4f view, Matrix4f proj, Vec3 camPos) {
        // 获取 AABB 的 8 个顶点
        Vec3[] corners = new Vec3[]{
                new Vec3(aabb.minX, aabb.minY, aabb.minZ),
                new Vec3(aabb.minX, aabb.maxY, aabb.minZ),
                new Vec3(aabb.maxX, aabb.minY, aabb.minZ),
                new Vec3(aabb.maxX, aabb.maxY, aabb.minZ),
                new Vec3(aabb.minX, aabb.minY, aabb.maxZ),
                new Vec3(aabb.minX, aabb.maxY, aabb.maxZ),
                new Vec3(aabb.maxX, aabb.minY, aabb.maxZ),
                new Vec3(aabb.maxX, aabb.maxY, aabb.maxZ)
        };

        boolean anyPointIn = false;

        for (Vec3 corner : corners) {
            Vector4f screenPos = project(corner, view, proj, camPos);
            // w > 0 表示在摄像机前方
            if (screenPos.w() > 0) {
                // 转换为 GUI 坐标
                float guiX = screenPos.x();
                float guiY = screenPos.y();

                if (rect.contains(guiX, guiY)) {
                    anyPointIn = true;
                    break;
                }
            }
        }

        return anyPointIn;
    }

    private static Vector4f project(Vec3 worldPos, Matrix4f view, Matrix4f proj, Vec3 camPos) {
        // 1. 世界坐标 -> 观察坐标 (相对摄像机)
        float x = (float) (worldPos.x - camPos.x);
        float y = (float) (worldPos.y - camPos.y);
        float z = (float) (worldPos.z - camPos.z);

        Vector4f pos = new Vector4f(x, y, z, 1.0f);

        // 2. 应用视图和投影矩阵
        pos.mul(view);
        pos.mul(proj);

        // 3. 透视除法
        if (pos.w() != 0) {
            pos.div(pos.w());
        }

        // 4. NDC (-1, 1) -> GUI 坐标
        Minecraft mc = Minecraft.getInstance();
        float winW = mc.getWindow().getGuiScaledWidth();
        float winH = mc.getWindow().getGuiScaledHeight();

        pos.x = (pos.x() * 0.5f + 0.5f) * winW;
        pos.y = (1.0f - (pos.y() * 0.5f + 0.5f)) * winH; // Y轴翻转，Minecraft GUI 原点在左上角

        return pos;
    }
}