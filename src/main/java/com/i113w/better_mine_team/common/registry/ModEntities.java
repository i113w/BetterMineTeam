package com.i113w.better_mine_team.common.registry;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.rts.entity.RTSCameraEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BetterMineTeam.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<RTSCameraEntity>> RTS_CAMERA = ENTITIES.register("rts_camera",
            () -> EntityType.Builder.<RTSCameraEntity>of(RTSCameraEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f) // 极小的碰撞箱
                    .noSave() // 不需要保存到磁盘
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("rts_camera"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}