package com.i113w.better_mine_team.common.registry;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.rts.entity.RTSCameraEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BetterMineTeam.MODID);

    public static final RegistryObject<EntityType<RTSCameraEntity>> RTS_CAMERA = ENTITIES.register(
            "rts_camera",
            () -> EntityType.Builder.<RTSCameraEntity>of(RTSCameraEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .noSave()
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("rts_camera")
    );

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
