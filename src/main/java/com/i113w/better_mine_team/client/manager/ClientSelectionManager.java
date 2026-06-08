package com.i113w.better_mine_team.client.manager;

import com.i113w.camera_lib.api.CameraLibAPI;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Set;

public class ClientSelectionManager {
    private static final Set<Integer> selectedIds = new HashSet<>();
    private static int selectionRevision = 0;

    public static void select(int entityId) {
        selectedIds.add(entityId);
        selectionRevision++;
    }

    public static void deselect(int entityId) {
        selectedIds.remove(entityId);
        selectionRevision++;
    }

    public static void clear() {
        selectedIds.clear();
        selectionRevision++;
    }

    public static boolean isSelected(Entity entity) {
        return entity != null && selectedIds.contains(entity.getId());
    }

    public static Set<Integer> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    public static void setSelectedIds(Set<Integer> newSelection) {
        selectedIds.clear();
        selectedIds.addAll(newSelection);
        selectionRevision++;
    }

    public static int getRevision() {
        return selectionRevision;
    }

    public static void syncToLib() {
        CameraLibAPI.get().setSelectedEntities(selectedIds);
    }
}