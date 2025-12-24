package com.i113w.better_mine_team.client.manager;

import net.minecraft.world.entity.Entity;
import java.util.HashSet;
import java.util.Set;

public class ClientSelectionManager {
    // 存储被选中实体的 ID
    private static final Set<Integer> selectedIds = new HashSet<>();

    public static void select(int entityId) {
        selectedIds.add(entityId);
    }

    public static void deselect(int entityId) {
        selectedIds.remove(entityId);
    }

    public static void clear() {
        selectedIds.clear();
    }

    public static boolean isSelected(Entity entity) {
        return entity != null && selectedIds.contains(entity.getId());
    }

    public static Set<Integer> getSelectedIds() {
        return new HashSet<>(selectedIds); // 返回副本
    }

    public static void setSelectedIds(Set<Integer> newSelection) {
        selectedIds.clear();
        selectedIds.addAll(newSelection);
    }
}