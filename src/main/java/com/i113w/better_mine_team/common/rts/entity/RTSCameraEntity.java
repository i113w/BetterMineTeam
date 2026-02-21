package com.i113w.better_mine_team.common.rts.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

/**
 * 仅客户端使用的 RTS 相机实体，不在服务端生成。
 * Forge 1.20.1: getAddEntityPacket() 无需 ServerEntity 参数。
 */
public class RTSCameraEntity extends Entity {

    public RTSCameraEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    public void tick() {
        // 不调用 super.tick() — 位置由 RTSCameraManager 强制覆盖
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        if (!this.isAlive()) this.remove(RemovalReason.DISCARDED);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {}

    // Forge 1.20.1: 签名不含 ServerEntity 参数
    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        // 仅客户端实体，不应从服务端生成
        throw new UnsupportedOperationException("RTSCameraEntity is client-only and should not be sent from server!");
    }
}
