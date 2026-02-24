package com.i113w.better_mine_team.common.compat.irons_spellbooks;

import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class IronsSpellbooksCompat {

    @Nullable
    public static LivingEntity getSummonOwner(Entity summon) {
        // 直接调用 ISS API 获取召唤物的主人
        Entity owner = SummonManager.getOwner(summon);
        if (owner instanceof LivingEntity living) {
            return living;
        }
        return null;
    }
}