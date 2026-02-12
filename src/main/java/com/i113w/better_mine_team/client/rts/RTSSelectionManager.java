package com.i113w.better_mine_team.client.rts;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/**
 * 客户端选择管理器
 * 维护当前的选框状态、选中的实体ID以及用于计算的矩阵缓存
 */
public class RTSSelectionManager {
    private static final RTSSelectionManager INSTANCE = new RTSSelectionManager();
    private int hoveredEntityId = -1;
    // 状态位
    private boolean isDragging = false;
    private boolean isAttackDragging = false;
    private Vec2 attackDragStart = Vec2.ZERO;
    private Vec2 attackDragEnd = Vec2.ZERO;
    // 屏幕坐标 (GUI坐标)
    private Vec2 dragStart = Vec2.ZERO;
    private Vec2 dragEnd = Vec2.ZERO;

    // 选中的实体 ID 集合
    private final Set<Integer> selectedEntityIds = new HashSet<>();

    // 矩阵缓存 (需要在渲染帧捕获)
    private final Matrix4f lastProjectionMatrix = new Matrix4f();
    private final Matrix4f lastViewMatrix = new Matrix4f();

    // 版本号，用于解决网络竞态
    private int selectionRevision = 0;

    private RTSSelectionManager() {}

    public static RTSSelectionManager get() {
        return INSTANCE;
    }

    public boolean isDragging() { return isDragging; }
    public Set<Integer> getSelectedIds() { return new HashSet<>(selectedEntityIds); } // 返回副本保护
    public boolean isAttackDragging() { return isAttackDragging; }

    public void startAttackDrag(float x, float y) {
        this.isAttackDragging = true;
        this.attackDragStart = new Vec2(x, y);
        this.attackDragEnd = this.attackDragStart;
    }

    public void updateAttackDrag(float x, float y) {
        if (isAttackDragging) {
            this.attackDragEnd = new Vec2(x, y);
        }
    }

    public void endAttackDrag() {
        this.isAttackDragging = false;
    }

    public SelectionRect getAttackRect() {
        float minX = Math.min(attackDragStart.x, attackDragEnd.x);
        float minY = Math.min(attackDragStart.y, attackDragEnd.y);
        float maxX = Math.max(attackDragStart.x, attackDragEnd.x);
        float maxY = Math.max(attackDragStart.y, attackDragEnd.y);
        return new SelectionRect(minX, minY, maxX - minX, maxY - minY);
    }

    public void startDrag(float x, float y) {
        this.isDragging = true;
        this.dragStart = new Vec2(x, y);
        this.dragEnd = this.dragStart;
    }

    public void updateDrag(float x, float y) {
        if (isDragging) {
            this.dragEnd = new Vec2(x, y);
        }
    }

    public void endDrag() {
        this.isDragging = false;
    }

    public void clearSelection() {
        selectedEntityIds.clear();
        selectionRevision++;
    }

    public void setSelected(Set<Integer> ids) {
        selectedEntityIds.clear();
        selectedEntityIds.addAll(ids);
        selectionRevision++;
    }

    public int getRevision() { return selectionRevision; }

    // 供渲染线程更新矩阵
    public void updateMatrices(Matrix4f view, Matrix4f projection) {
        this.lastViewMatrix.set(view);
        this.lastProjectionMatrix.set(projection);
    }

    public Matrix4f getViewMatrix() { return lastViewMatrix; }
    public Matrix4f getProjectionMatrix() { return lastProjectionMatrix; }

    // 获取标准化的选框 (x, y, width, height)
    public SelectionRect getSelectionRect() {
        float minX = Math.min(dragStart.x, dragEnd.x);
        float minY = Math.min(dragStart.y, dragEnd.y);
        float maxX = Math.max(dragStart.x, dragEnd.x);
        float maxY = Math.max(dragStart.y, dragEnd.y);
        return new SelectionRect(minX, minY, maxX - minX, maxY - minY);
    }

    public record SelectionRect(float x, float y, float width, float height) {
        public boolean contains(float px, float py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }
    public void setHoveredEntity(@Nullable Entity entity) {
        this.hoveredEntityId = (entity == null) ? -1 : entity.getId();
    }

    public int getHoveredEntityId() {
        return hoveredEntityId;
    }

    // [辅助] 检查是否悬停
    public boolean isHovered(Entity entity) {
        return entity != null && entity.getId() == hoveredEntityId;
    }

}