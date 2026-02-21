package com.i113w.better_mine_team.common.rts.data;

import com.i113w.better_mine_team.BetterMineTeam;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 修复：RTS 选区数据为会话级临时数据，不应存入 NBT 持久化。
 * 使用静态 Map 来保证在服务器生命周期内存活，并在玩家登出时自动清理。
 */
public class RTSPlayerData {
    private static final Map<UUID, RTSPlayerData> SESSIONS = new ConcurrentHashMap<>();

    private final Set<Integer> selectedEntityIds = new HashSet<>();
    private int selectionRevision = 0;

    private RTSPlayerData() {}

    public static RTSPlayerData get(Player player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), k -> new RTSPlayerData());
    }

    public static void clear(Player player) {
        SESSIONS.remove(player.getUUID());
    }

    public void updateSelection(List<Integer> newIds, int revision) {
        // 允许 revision 递增更新，或者在客户端重置后 (revision == 0 或 1) 重新接受
        if (revision > this.selectionRevision || revision <= 1) {
            this.selectedEntityIds.clear();
            this.selectedEntityIds.addAll(newIds);
            this.selectionRevision = revision;
            BetterMineTeam.debug("[RTS-PLAYER-DATA] Selection updated: count={}, rev={}", this.selectedEntityIds.size(), this.selectionRevision);
        } else {
            BetterMineTeam.debug("[RTS-PLAYER-DATA] Ignored stale sync: rev {} <= current {}", revision, this.selectionRevision);
        }
    }

    public Set<Integer> getSelection() {
        return new HashSet<>(selectedEntityIds);
    }

    public int getRevision() {
        return selectionRevision;
    }
}