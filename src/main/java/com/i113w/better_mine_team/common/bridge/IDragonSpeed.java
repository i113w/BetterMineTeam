package com.i113w.better_mine_team.common.bridge;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public interface IDragonSpeed {
    float bmt$getSpeed();

    // [关键修改] 1.20.1 使用 getPassengerRidingPosition 代替 getPassengerAttachmentPoint
    Vec3 getPassengerRidingPosition(Entity passenger);
}