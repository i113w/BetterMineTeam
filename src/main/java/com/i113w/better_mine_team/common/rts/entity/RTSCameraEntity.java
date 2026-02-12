package com.i113w.better_mine_team.common.rts.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class RTSCameraEntity extends Entity {

    public RTSCameraEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true; // 禁用物理碰撞
    }

    @Override
    public void tick() {
        // 不调用 super.tick() 以避免不必要的物理计算
        // 它的位置将完全由 RTSCameraManager 强制覆盖
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        // 保持实体“活着”以防被清理
        if (!this.isAlive()) this.remove(RemovalReason.DISCARDED);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(@NotNull CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(@NotNull CompoundTag tag) {}

    // [修复] 1.21.1 方法签名变更为需要 ServerEntity 参数
    // 这是一个仅客户端实体，理论上不应在服务端生成数据包，返回 null 即可
    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        throw new UnsupportedOperationException("RTSCameraEntity should not be spawned on server!");
    }
}