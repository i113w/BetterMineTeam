package com.i113w.better_mine_team.common.registry;

import com.i113w.better_mine_team.BetterMineTeam;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public class ModTags {
    public static class Entities {
        public static final TagKey<EntityType<?>> IGNORED_BY_LEVEL2_SCAN = TagKey.create(
                Registries.ENTITY_TYPE,
                BetterMineTeam.asResource("ignored_by_level2_scan")
        );
    }
}