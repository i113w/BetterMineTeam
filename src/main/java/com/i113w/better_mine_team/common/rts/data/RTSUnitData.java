package com.i113w.better_mine_team.common.rts.data;

import com.i113w.better_mine_team.common.network.data.CommandType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 替代 NeoForge AttachmentType<RTSUnitData>。
 * 通过 getPersistentData() 自动持久化，每次 set 操作后立即写回 NBT。
 * 使用方式: RTSUnitData data = RTSUnitData.get(mob);
 */
public class RTSUnitData {
    private static final String NBT_KEY = "bmt_rts_unit";

    private final Entity entity;

    private CommandType currentCommand = CommandType.STOP;
    private Vec3 targetPos = Vec3.ZERO;
    private Vec3 anchorPos = Vec3.ZERO;
    private int targetEntityId = -1;
    private boolean isRtsControlled = false;

    private RTSUnitData(Entity entity) {
        this.entity = entity;
        CompoundTag pData = entity.getPersistentData();
        if (pData.contains(NBT_KEY)) {
            load(pData.getCompound(NBT_KEY));
        }
    }

    /** 替代 mob.getData(ModAttachments.UNIT_DATA) */
    public static RTSUnitData get(Entity entity) {
        return new RTSUnitData(entity);
    }

    // ===== Setters (自动保存) =====

    public void setMoveCommand(Vec3 pos) {
        this.currentCommand = CommandType.MOVE;
        this.targetPos = pos;
        this.anchorPos = pos;
        save();
    }

    public void setAttackCommand(int entityId) {
        this.currentCommand = CommandType.ATTACK;
        this.targetEntityId = entityId;
        save();
    }

    public void stop() {
        this.currentCommand = CommandType.STOP;
        this.targetPos = Vec3.ZERO;
        this.targetEntityId = -1;
        save();
    }

    public void setControlled(boolean val) {
        this.isRtsControlled = val;
        save();
    }

    // ===== Getters =====

    public CommandType getCommand() { return currentCommand; }
    public Vec3 getTargetPos() { return targetPos; }
    public Vec3 getAnchorPos() { return anchorPos; }
    public int getTargetEntityId() { return targetEntityId; }
    public boolean isControlled() { return isRtsControlled; }

    // ===== NBT =====

    private void save() {
        entity.getPersistentData().put(NBT_KEY, store());
    }

    private CompoundTag store() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Cmd", currentCommand.name());
        tag.putDouble("Tx", targetPos.x);
        tag.putDouble("Ty", targetPos.y);
        tag.putDouble("Tz", targetPos.z);
        tag.putDouble("Ax", anchorPos.x);
        tag.putDouble("Ay", anchorPos.y);
        tag.putDouble("Az", anchorPos.z);
        tag.putInt("TEnt", targetEntityId);
        tag.putBoolean("Ctrl", isRtsControlled);
        return tag;
    }

    private void load(CompoundTag tag) {
        try {
            this.currentCommand = CommandType.valueOf(tag.getString("Cmd"));
        } catch (Exception e) {
            this.currentCommand = CommandType.STOP;
        }
        this.targetPos = new Vec3(tag.getDouble("Tx"), tag.getDouble("Ty"), tag.getDouble("Tz"));
        this.anchorPos = new Vec3(tag.getDouble("Ax"), tag.getDouble("Ay"), tag.getDouble("Az"));
        this.targetEntityId = tag.getInt("TEnt");
        this.isRtsControlled = tag.getBoolean("Ctrl");
    }
}
