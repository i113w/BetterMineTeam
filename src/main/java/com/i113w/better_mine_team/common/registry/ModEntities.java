package com.i113w.better_mine_team.common.registry;

import com.i113w.better_mine_team.BetterMineTeam;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.eventbus.api.IEventBus;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BetterMineTeam.MODID);

    // [已删除] RTS_CAMERA 实体，因为该实体已移至 i113w_camera_lib 中

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}