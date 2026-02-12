package com.i113w.better_mine_team.common.rts.data;

import com.i113w.better_mine_team.common.network.data.CommandType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;

public class RTSUnitData implements INBTSerializable<CompoundTag> {

    private CommandType currentCommand = CommandType.STOP;
    private Vec3 targetPos = Vec3.ZERO;
    private Vec3 anchorPos = Vec3.ZERO; // [新增] 锚点，用于 HOLD 模式下的回防
    private int targetEntityId = -1;
    private boolean isRtsControlled = false; // 是否已被纳入 RTS 控制体系

    // === Setters ===
    public void setMoveCommand(Vec3 pos) {
        this.currentCommand = CommandType.MOVE;
        this.targetPos = pos;
        this.anchorPos = pos; // 移动目标的终点即为新的防守锚点
    }

    public void setAttackCommand(int entityId) {
        this.currentCommand = CommandType.ATTACK;
        this.targetEntityId = entityId;
    }

    public void stop() {
        this.currentCommand = CommandType.STOP;
        this.targetPos = Vec3.ZERO;
        this.targetEntityId = -1;
    }

    public void setControlled(boolean val) { this.isRtsControlled = val; }

    // === Getters ===
    public CommandType getCommand() { return currentCommand; }
    public Vec3 getTargetPos() { return targetPos; }
    public Vec3 getAnchorPos() { return anchorPos; }
    public int getTargetEntityId() { return targetEntityId; }
    public boolean isControlled() { return isRtsControlled; }

    // === NBT ===
    @Override
    public @NotNull CompoundTag serializeNBT(HolderLookup.@NotNull Provider provider) {
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

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider provider, @NotNull CompoundTag tag) {
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