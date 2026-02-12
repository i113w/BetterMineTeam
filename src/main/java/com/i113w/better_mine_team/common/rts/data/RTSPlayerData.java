package com.i113w.better_mine_team.common.rts.data;

import com.i113w.better_mine_team.BetterMineTeam;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RTSPlayerData implements INBTSerializable<CompoundTag> {

    private final Set<Integer> selectedEntityIds = new HashSet<>();
    private int selectionRevision = 0;

    public void updateSelection(List<Integer> newIds, int revision) {
        BetterMineTeam.debug("[RTS-PLAYER-DATA] updateSelection called:");
        BetterMineTeam.debug("[RTS-PLAYER-DATA]   Current revision: {}", this.selectionRevision);
        BetterMineTeam.debug("[RTS-PLAYER-DATA]   New revision: {}", revision);
        BetterMineTeam.debug("[RTS-PLAYER-DATA]   New IDs count: {}", newIds.size());
        BetterMineTeam.debug("[RTS-PLAYER-DATA]   New IDs: {}", newIds);

        // ✅ [修复] 改为严格的大于检查，防止重复包覆盖
        if (revision > this.selectionRevision) {
            this.selectedEntityIds.clear();
            this.selectedEntityIds.addAll(newIds);
            this.selectionRevision = revision;

            BetterMineTeam.debug("[RTS-PLAYER-DATA] ✅ Selection updated! Now storing {} entities",
                    this.selectedEntityIds.size());
        } else {
            BetterMineTeam.debug("[RTS-PLAYER-DATA] ⚠️ Rejected update (old revision)");
        }
    }

    public Set<Integer> getSelection() {
        return new HashSet<>(selectedEntityIds);
    }

    public int getRevision() {
        return selectionRevision;
    }

    @Override
    public @NotNull CompoundTag serializeNBT(HolderLookup.@NotNull Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("Ids", selectedEntityIds.stream().mapToInt(i -> i).toArray());
        tag.putInt("Rev", selectionRevision);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider provider, @NotNull CompoundTag tag) {
        this.selectedEntityIds.clear();
        int[] ids = tag.getIntArray("Ids");
        for (int id : ids) {
            this.selectedEntityIds.add(id);
        }
        this.selectionRevision = tag.getInt("Rev");
    }
}