package com.i113w.better_mine_team.common.team;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeamDataStorage extends SavedData {
    // 存储：队伍名 -> 队长UUID
    private final Map<String, UUID> teamCaptains = new HashMap<>();
    private final Map<String, Boolean> teamGlowDefaults = new HashMap<>();
    private final Map<String, Integer> teamGlowRevisions = new HashMap<>();

    public static TeamDataStorage get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(TeamDataStorage::new, TeamDataStorage::load, null),
                "better_mine_team_data"
        );
    }

    // --- 队长接口 ---

    public void setCaptain(String teamName, UUID captainUUID) {
        BetterMineTeam.debug("STORAGE: Setting Captain for Team [{}]: {}", teamName, captainUUID);
        teamCaptains.put(teamName, captainUUID);
        this.setDirty();
    }

    /**
     * [新增] 移除指定队伍的队长
     */
    public void removeCaptain(String teamName) {
        if (teamCaptains.containsKey(teamName)) {
            BetterMineTeam.debug("STORAGE: Removing Captain for Team [{}]", teamName);
            teamCaptains.remove(teamName);
            this.setDirty();
        }
    }

    public UUID getCaptain(String teamName) {
        return teamCaptains.get(teamName);
    }

    public boolean isCaptain(ServerPlayer player) {
        PlayerTeam team = TeamManager.getTeam(player);
        if (team == null) return false;

        UUID captainId = teamCaptains.get(team.getName());
        return captainId != null && captainId.equals(player.getUUID());
    }

    // --- 发光默认值 ---

    public boolean getTeamGlowDefault(String teamName) {
        return teamGlowDefaults.getOrDefault(teamName, BMTConfig.isDefaultGlowEnabled());
    }

    public int getTeamGlowRevision(String teamName) {
        return teamGlowRevisions.getOrDefault(teamName, 0);
    }

    public int setTeamGlowDefault(String teamName, boolean enabled) {
        teamGlowDefaults.put(teamName, enabled);
        int revision = getTeamGlowRevision(teamName) + 1;
        teamGlowRevisions.put(teamName, revision);
        this.setDirty();
        BetterMineTeam.debug("STORAGE: Setting Glow for Team [{}]: {} (revision {})", teamName, enabled, revision);
        return revision;
    }

    // --- NBT 读写 ---

    @Override
    @NotNull
    public CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        CompoundTag captainsTag = new CompoundTag();
        teamCaptains.forEach(captainsTag::putUUID);
        tag.put("Captains", captainsTag);

        CompoundTag glowDefaultsTag = new CompoundTag();
        teamGlowDefaults.forEach(glowDefaultsTag::putBoolean);
        tag.put("TeamGlowDefaults", glowDefaultsTag);

        CompoundTag glowRevisionsTag = new CompoundTag();
        teamGlowRevisions.forEach(glowRevisionsTag::putInt);
        tag.put("TeamGlowRevisions", glowRevisionsTag);
        return tag;
    }

    public static TeamDataStorage load(CompoundTag tag, HolderLookup.Provider provider) {
        TeamDataStorage data = new TeamDataStorage();
        if (tag.contains("Captains")) {
            CompoundTag captainsTag = tag.getCompound("Captains");
            for (String key : captainsTag.getAllKeys()) {
                UUID uuid = captainsTag.getUUID(key);
                data.teamCaptains.put(key, uuid);
                BetterMineTeam.debug("STORAGE: Loaded Captain: {} -> {}", key, uuid);
            }
        }
        if (tag.contains("TeamGlowDefaults")) {
            CompoundTag glowDefaultsTag = tag.getCompound("TeamGlowDefaults");
            for (String key : glowDefaultsTag.getAllKeys()) {
                data.teamGlowDefaults.put(key, glowDefaultsTag.getBoolean(key));
                BetterMineTeam.debug("STORAGE: Loaded Glow Default: {} -> {}", key, glowDefaultsTag.getBoolean(key));
            }
        }
        if (tag.contains("TeamGlowRevisions")) {
            CompoundTag glowRevisionsTag = tag.getCompound("TeamGlowRevisions");
            for (String key : glowRevisionsTag.getAllKeys()) {
                data.teamGlowRevisions.put(key, glowRevisionsTag.getInt(key));
            }
        }
        return data;
    }
}
