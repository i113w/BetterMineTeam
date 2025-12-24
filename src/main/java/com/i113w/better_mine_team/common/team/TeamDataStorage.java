package com.i113w.better_mine_team.common.team;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.PlayerTeam;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

public class TeamDataStorage extends SavedData {
    // 存储：队伍名 -> 队长UUID
    private final Map<String, UUID> teamCaptains = new HashMap<>();

    // 获取实例的标准方法
    public static TeamDataStorage get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(TeamDataStorage::new, TeamDataStorage::load, null),
                "better_mine_team_data"
        );
    }

    // --- 队长接口 (Captain Interfaces) ---

    /**
     * 设置某个队伍的队长
     * @param teamName 计分板队伍名
     * @param captainUUID 玩家UUID
     */
    public void setCaptain(String teamName, UUID captainUUID) {
        teamCaptains.put(teamName, captainUUID);
        this.setDirty();
    }

    /**
     * 获取队长 UUID
     */
    public UUID getCaptain(String teamName) {
        return teamCaptains.get(teamName);
    }

    /**
     * 检查某人是否是其所在队伍的队长
     */
    public boolean isCaptain(ServerPlayer player) {
        PlayerTeam team = TeamManager.getTeam(player);
        if (team == null) return false;

        UUID captainId = teamCaptains.get(team.getName());
        return captainId != null && captainId.equals(player.getUUID());
    }

    // --- NBT 读写 (必须实现，否则重启服务器数据丢失) ---

    @Override
    @NotNull
    public CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        CompoundTag captainsTag = new CompoundTag();

        // 修复点 1: Lambda 被替换为方法引用 (captainsTag::putUUID)
        // 修复点 2: 参数和返回值添加了 @NotNull 注解
        teamCaptains.forEach(captainsTag::putUUID);

        tag.put("Captains", captainsTag);
        return tag;
    }

    public static TeamDataStorage load(CompoundTag tag, HolderLookup.Provider provider) {
        TeamDataStorage data = new TeamDataStorage();
        if (tag.contains("Captains")) {
            CompoundTag captainsTag = tag.getCompound("Captains");
            for (String key : captainsTag.getAllKeys()) {
                data.teamCaptains.put(key, captainsTag.getUUID(key));
            }
        }
        return data;
    }
}