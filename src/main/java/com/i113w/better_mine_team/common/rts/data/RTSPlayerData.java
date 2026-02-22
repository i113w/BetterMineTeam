package com.i113w.better_mine_team.common.rts.data;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RTSPlayerData {

    private final Set<Integer> selectedEntityIds = new HashSet<>();
    private int selectionRevision = 0;

    public void updateSelection(List<Integer> newIds, int revision) {
        if (BMTConfig.isDebugEnabled()) {
            BetterMineTeam.debug("[RTS-PLAYER-DATA] updateSelection called:");
            BetterMineTeam.debug("[RTS-PLAYER-DATA]   Current revision: {}", this.selectionRevision);
            BetterMineTeam.debug("[RTS-PLAYER-DATA]   New revision: {}", revision);
            BetterMineTeam.debug("[RTS-PLAYER-DATA]   New IDs count: {}", newIds.size());
        }

        // 仅在严格大于时更新
        if (revision > this.selectionRevision) {
            this.selectedEntityIds.clear();
            this.selectedEntityIds.addAll(newIds);
            this.selectionRevision = revision;

            if (BMTConfig.isDebugEnabled()) {
                BetterMineTeam.debug("[RTS-PLAYER-DATA] ✅ Selection updated! Now storing {} entities",
                        this.selectedEntityIds.size());
            }
        } else {
            if (BMTConfig.isDebugEnabled()) {
                BetterMineTeam.debug("[RTS-PLAYER-DATA] ⚠️ Rejected update (old revision)");
            }
        }
    }

    public Set<Integer> getSelection() {
        return new HashSet<>(selectedEntityIds);
    }

    public int getRevision() {
        return selectionRevision;
    }
}