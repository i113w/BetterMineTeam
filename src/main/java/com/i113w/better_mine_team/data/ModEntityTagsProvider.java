package com.i113w.better_mine_team.data;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.registry.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.world.entity.EntityType;

import java.util.concurrent.CompletableFuture;

public class ModEntityTagsProvider extends EntityTypeTagsProvider {

    public ModEntityTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> provider) {
        super(output, provider);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(ModTags.Entities.TAMEABLE);
        this.tag(ModTags.Entities.UNTAMEABLE);

        // 仅保留护甲架以及平常难以寻路/攻击到的飞行生物
        this.tag(ModTags.Entities.IGNORED_BY_LEVEL2_SCAN)
                .add(EntityType.ARMOR_STAND)
                .add(EntityType.ALLAY)
                .add(EntityType.BAT)
                .add(EntityType.BEE)
                .add(EntityType.BLAZE)
                .add(EntityType.GHAST)
                .add(EntityType.PHANTOM)
                .add(EntityType.PARROT)
                .add(EntityType.VEX);
    }
}
