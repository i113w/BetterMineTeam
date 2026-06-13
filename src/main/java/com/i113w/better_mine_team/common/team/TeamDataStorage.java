package com.i113w.better_mine_team.common.team;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TeamDataStorage extends SavedData {

    private final Map<String, UUID> teamCaptains = new HashMap<>();
    private final Map<String, Boolean> teamGlowDefaults = new HashMap<>();
    private final Map<String, Integer> teamGlowRevisions = new HashMap<>();
    private final Map<UUID, Boolean> personalTeamPreferences = new HashMap<>();
    private final Map<String, UUID> personalTeamOwners = new HashMap<>();
    private final Map<String, Long> personalTeamEmptySince = new HashMap<>();

    public static TeamDataStorage get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                TeamDataStorage::load,
                TeamDataStorage::new,
                "better_mine_team_data"
        );
    }

    // ---------------- 队长逻辑 ----------------

    public void setCaptain(String teamName, UUID captainUUID) {
        BetterMineTeam.debug("STORAGE: Setting Captain for Team [{}]: {}", teamName, captainUUID);
        teamCaptains.put(teamName, captainUUID);
        this.setDirty();
    }

    public void removeCaptain(String teamName) {
        if (teamCaptains.remove(teamName) != null) {
            BetterMineTeam.debug("STORAGE: Removing Captain for Team [{}]", teamName);
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

    // ---------------- 发光默认值 ----------------

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

    // ---------------- 个人队伍 ----------------

    public boolean getPersonalTeamPreference(ServerPlayer player) {
        return personalTeamPreferences.getOrDefault(player.getUUID(), BMTConfig.isAutoJoinPersonalTeamOnLogin());
    }

    public void setPersonalTeamPreference(UUID playerId, boolean enabled) {
        personalTeamPreferences.put(playerId, enabled);
        this.setDirty();
    }

    public void markPersonalTeam(String teamName, UUID ownerId) {
        personalTeamOwners.put(teamName, ownerId);
        this.setDirty();
    }

    public boolean isPersonalTeam(String teamName) {
        return personalTeamOwners.containsKey(teamName);
    }

    public boolean isPersonalTeamOwner(String teamName, UUID playerId) {
        UUID ownerId = personalTeamOwners.get(teamName);
        return ownerId != null && ownerId.equals(playerId);
    }

    public UUID getPersonalTeamOwner(String teamName) {
        return personalTeamOwners.get(teamName);
    }

    public String getPersonalTeamName(UUID ownerId) {
        for (Map.Entry<String, UUID> entry : personalTeamOwners.entrySet()) {
            if (entry.getValue().equals(ownerId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Set<String> getPersonalTeamNames() {
        return new HashSet<>(personalTeamOwners.keySet());
    }

    public long getPersonalTeamEmptySince(String teamName) {
        return personalTeamEmptySince.getOrDefault(teamName, -1L);
    }

    public void setPersonalTeamEmptySince(String teamName, long gameTime) {
        personalTeamEmptySince.put(teamName, gameTime);
        this.setDirty();
    }

    public void clearPersonalTeamEmptySince(String teamName) {
        if (personalTeamEmptySince.remove(teamName) != null) {
            this.setDirty();
        }
    }

    public void removePersonalTeam(String teamName) {
        boolean changed = false;
        changed |= personalTeamOwners.remove(teamName) != null;
        changed |= personalTeamEmptySince.remove(teamName) != null;
        changed |= teamCaptains.remove(teamName) != null;
        changed |= teamGlowDefaults.remove(teamName) != null;
        changed |= teamGlowRevisions.remove(teamName) != null;
        if (changed) {
            this.setDirty();
        }
    }

    // ---------------- NBT ----------------

    @Override
    @NotNull
    public CompoundTag save(@NotNull CompoundTag tag) {
        CompoundTag captainsTag = new CompoundTag();
        teamCaptains.forEach(captainsTag::putUUID);
        tag.put("Captains", captainsTag);

        CompoundTag glowDefaultsTag = new CompoundTag();
        teamGlowDefaults.forEach(glowDefaultsTag::putBoolean);
        tag.put("TeamGlowDefaults", glowDefaultsTag);

        CompoundTag glowRevisionsTag = new CompoundTag();
        teamGlowRevisions.forEach(glowRevisionsTag::putInt);
        tag.put("TeamGlowRevisions", glowRevisionsTag);

        CompoundTag personalPreferencesTag = new CompoundTag();
        personalTeamPreferences.forEach((playerId, enabled) -> personalPreferencesTag.putBoolean(playerId.toString(), enabled));
        tag.put("PersonalTeamPreferences", personalPreferencesTag);

        CompoundTag personalOwnersTag = new CompoundTag();
        personalTeamOwners.forEach(personalOwnersTag::putUUID);
        tag.put("PersonalTeamOwners", personalOwnersTag);

        CompoundTag personalEmptySinceTag = new CompoundTag();
        personalTeamEmptySince.forEach(personalEmptySinceTag::putLong);
        tag.put("PersonalTeamEmptySince", personalEmptySinceTag);
        return tag;
    }

    public static TeamDataStorage load(CompoundTag tag) {
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
        if (tag.contains("PersonalTeamPreferences")) {
            CompoundTag preferencesTag = tag.getCompound("PersonalTeamPreferences");
            for (String key : preferencesTag.getAllKeys()) {
                try {
                    data.personalTeamPreferences.put(UUID.fromString(key), preferencesTag.getBoolean(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (tag.contains("PersonalTeamOwners")) {
            CompoundTag ownersTag = tag.getCompound("PersonalTeamOwners");
            for (String key : ownersTag.getAllKeys()) {
                UUID uuid = ownersTag.getUUID(key);
                data.personalTeamOwners.put(key, uuid);
                BetterMineTeam.debug("STORAGE: Loaded Personal Team: {} -> {}", key, uuid);
            }
        }
        if (tag.contains("PersonalTeamEmptySince")) {
            CompoundTag emptySinceTag = tag.getCompound("PersonalTeamEmptySince");
            for (String key : emptySinceTag.getAllKeys()) {
                data.personalTeamEmptySince.put(key, emptySinceTag.getLong(key));
            }
        }
        return data;
    }
}
