package com.i113w.better_mine_team.client.rts;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

public class RTSSelectionManager {
    private static final RTSSelectionManager INSTANCE = new RTSSelectionManager();

    private int hoveredEntityId = -1;
    private boolean isDragging = false;
    private boolean isAttackDragging = false;
    private Vec2 attackDragStart = Vec2.ZERO;
    private Vec2 attackDragEnd = Vec2.ZERO;
    private Vec2 dragStart = Vec2.ZERO;
    private Vec2 dragEnd = Vec2.ZERO;

    private final Set<Integer> selectedEntityIds = new HashSet<>();

    private final Matrix4f lastProjectionMatrix = new Matrix4f();
    private final Matrix4f lastViewMatrix = new Matrix4f();

    private int selectionRevision = 0;

    private RTSSelectionManager() {}

    public static RTSSelectionManager get() { return INSTANCE; }

    public void reset() {
        selectedEntityIds.clear();
        hoveredEntityId = -1;
        isDragging = false;
        isAttackDragging = false;
        selectionRevision = 0;
        dragStart = Vec2.ZERO;
        dragEnd = Vec2.ZERO;
        attackDragStart = Vec2.ZERO;
        attackDragEnd = Vec2.ZERO;
    }

    public boolean isDragging() { return isDragging; }
    public Set<Integer> getSelectedIds() { return new HashSet<>(selectedEntityIds); }
    public boolean isAttackDragging() { return isAttackDragging; }

    public void startAttackDrag(float x, float y) {
        isAttackDragging = true;
        attackDragStart = new Vec2(x, y);
        attackDragEnd = attackDragStart;
    }

    public void updateAttackDrag(float x, float y) {
        if (isAttackDragging) attackDragEnd = new Vec2(x, y);
    }

    public void endAttackDrag() { isAttackDragging = false; }

    public SelectionRect getAttackRect() {
        float minX = Math.min(attackDragStart.x, attackDragEnd.x);
        float minY = Math.min(attackDragStart.y, attackDragEnd.y);
        float maxX = Math.max(attackDragStart.x, attackDragEnd.x);
        float maxY = Math.max(attackDragStart.y, attackDragEnd.y);
        return new SelectionRect(minX, minY, maxX - minX, maxY - minY);
    }

    public void startDrag(float x, float y) {
        isDragging = true;
        dragStart = new Vec2(x, y);
        dragEnd = dragStart;
    }

    public void updateDrag(float x, float y) {
        if (isDragging) dragEnd = new Vec2(x, y);
    }

    public void endDrag() { isDragging = false; }

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

    public void updateMatrices(Matrix4f view, Matrix4f projection) {
        lastViewMatrix.set(view);
        lastProjectionMatrix.set(projection);
    }

    public Matrix4f getViewMatrix() { return lastViewMatrix; }
    public Matrix4f getProjectionMatrix() { return lastProjectionMatrix; }

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

    public int getHoveredEntityId() { return hoveredEntityId; }

    public boolean isHovered(Entity entity) {
        return entity != null && entity.getId() == hoveredEntityId;
    }
}
