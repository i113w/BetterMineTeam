package com.i113w.better_mine_team.common.team;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.scores.PlayerTeam;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TeamDataStorage extends SavedData {
    private static final Codec<TeamDataStorage> CODEC = CompoundTag.CODEC.xmap(TeamDataStorage::load, TeamDataStorage::save);
    private static final SavedDataType<TeamDataStorage> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("better_mine_team_data"),
            TeamDataStorage::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    // 存储：队伍名 -> 队长UUID
    private final Map<String, UUID> teamCaptains = new HashMap<>();
    private final Map<String, Boolean> teamGlowDefaults = new HashMap<>();
    private final Map<String, Integer> teamGlowRevisions = new HashMap<>();
    private final Map<UUID, Boolean> personalTeamPreferences = new HashMap<>();
    private final Map<String, UUID> personalTeamOwners = new HashMap<>();
    private final Map<String, Long> personalTeamEmptySince = new HashMap<>();

    public static TeamDataStorage get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
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

    // --- 个人队伍 ---

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

    // --- NBT 读写 ---

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag captainsTag = new CompoundTag();
        teamCaptains.forEach((teamName, uuid) -> captainsTag.putString(teamName, uuid.toString()));
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
        personalTeamOwners.forEach((teamName, uuid) -> personalOwnersTag.putString(teamName, uuid.toString()));
        tag.put("PersonalTeamOwners", personalOwnersTag);

        CompoundTag personalEmptySinceTag = new CompoundTag();
        personalTeamEmptySince.forEach(personalEmptySinceTag::putLong);
        tag.put("PersonalTeamEmptySince", personalEmptySinceTag);
        return tag;
    }

    public static TeamDataStorage load(CompoundTag tag) {
        TeamDataStorage data = new TeamDataStorage();
        if (tag.contains("Captains")) {
            CompoundTag captainsTag = tag.getCompoundOrEmpty("Captains");
            for (String key : captainsTag.keySet()) {
                parseUuid(captainsTag, key).ifPresent(uuid -> {
                    data.teamCaptains.put(key, uuid);
                    BetterMineTeam.debug("STORAGE: Loaded Captain: {} -> {}", key, uuid);
                });
            }
        }
        if (tag.contains("TeamGlowDefaults")) {
            CompoundTag glowDefaultsTag = tag.getCompoundOrEmpty("TeamGlowDefaults");
            for (String key : glowDefaultsTag.keySet()) {
                boolean enabled = glowDefaultsTag.getBooleanOr(key, BMTConfig.isDefaultGlowEnabled());
                data.teamGlowDefaults.put(key, enabled);
                BetterMineTeam.debug("STORAGE: Loaded Glow Default: {} -> {}", key, enabled);
            }
        }
        if (tag.contains("TeamGlowRevisions")) {
            CompoundTag glowRevisionsTag = tag.getCompoundOrEmpty("TeamGlowRevisions");
            for (String key : glowRevisionsTag.keySet()) {
                data.teamGlowRevisions.put(key, glowRevisionsTag.getIntOr(key, 0));
            }
        }
        if (tag.contains("PersonalTeamPreferences")) {
            CompoundTag preferencesTag = tag.getCompoundOrEmpty("PersonalTeamPreferences");
            for (String key : preferencesTag.keySet()) {
                try {
                    data.personalTeamPreferences.put(UUID.fromString(key), preferencesTag.getBooleanOr(key, false));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (tag.contains("PersonalTeamOwners")) {
            CompoundTag ownersTag = tag.getCompoundOrEmpty("PersonalTeamOwners");
            for (String key : ownersTag.keySet()) {
                parseUuid(ownersTag, key).ifPresent(uuid -> {
                    data.personalTeamOwners.put(key, uuid);
                    BetterMineTeam.debug("STORAGE: Loaded Personal Team: {} -> {}", key, uuid);
                });
            }
        }
        if (tag.contains("PersonalTeamEmptySince")) {
            CompoundTag emptySinceTag = tag.getCompoundOrEmpty("PersonalTeamEmptySince");
            for (String key : emptySinceTag.keySet()) {
                data.personalTeamEmptySince.put(key, emptySinceTag.getLongOr(key, -1L));
            }
        }
        return data;
    }

    private static java.util.Optional<UUID> parseUuid(CompoundTag tag, String key) {
        try {
            return tag.getString(key).map(UUID::fromString);
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }
}
