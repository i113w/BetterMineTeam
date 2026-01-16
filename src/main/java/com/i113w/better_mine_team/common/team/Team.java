package com.i113w.better_mine_team.common.team;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public class Team {
    // [1.20.1 修改] 移除 STREAM_CODEC，1.20.1 不使用网络序列化
    public static final Codec<Team> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("uid").forGetter(Team::getUid),
            Codec.INT.fieldOf("color").forGetter(Team::getRGB)
    ).apply(instance, Team::new));

    private UUID uid;
    private int rgb;
    private int lastHurtByMobTimestamp;
    private LivingEntity lastHurtByMob;

    public Team(UUID uid, int rgb) {
        this.uid = uid;
        this.rgb = rgb;
    }

    public Team() {}

    public @Nullable UUID getUid() {
        return uid;
    }

    public int getRGB() {
        return rgb;
    }

    @Deprecated(since = "1.2.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.3.0")
    public int getColor() {
        return getRGB();
    }

    public int getLastHurtByMobTimestamp() {
        return lastHurtByMobTimestamp;
    }

    public @Nullable LivingEntity getLastHurtByMob() {
        return lastHurtByMob;
    }

    public void setLastHurtByMob(LivingEntity livingEntity) {
        this.lastHurtByMob = livingEntity;
        this.lastHurtByMobTimestamp = livingEntity.tickCount;
    }

    @Override
    public final boolean equals(Object o) {
        return o == this || (o instanceof Team team && rgb == team.rgb && Objects.equals(uid, team.uid));
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(uid);
        result = 31 * result + rgb;
        return result;
    }
}