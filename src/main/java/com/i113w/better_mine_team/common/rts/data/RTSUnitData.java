package com.i113w.better_mine_team.common.rts.data;

import com.i113w.better_mine_team.common.network.data.CommandType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

public class RTSUnitData implements ValueIOSerializable {

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

    @Override
    public void serialize(ValueOutput output) {
        output.putString("Cmd", currentCommand.name());
        output.putDouble("Tx", targetPos.x);
        output.putDouble("Ty", targetPos.y);
        output.putDouble("Tz", targetPos.z);
        output.putDouble("Ax", anchorPos.x);
        output.putDouble("Ay", anchorPos.y);
        output.putDouble("Az", anchorPos.z);
        output.putInt("TEnt", targetEntityId);
        output.putBoolean("Ctrl", isRtsControlled);
    }

    @Override
    public void deserialize(ValueInput input) {
        try {
            this.currentCommand = CommandType.valueOf(input.getStringOr("Cmd", CommandType.STOP.name()));
        } catch (Exception e) {
            this.currentCommand = CommandType.STOP;
        }
        this.targetPos = new Vec3(input.getDoubleOr("Tx", 0.0), input.getDoubleOr("Ty", 0.0), input.getDoubleOr("Tz", 0.0));
        this.anchorPos = new Vec3(input.getDoubleOr("Ax", 0.0), input.getDoubleOr("Ay", 0.0), input.getDoubleOr("Az", 0.0));
        this.targetEntityId = input.getIntOr("TEnt", -1);
        this.isRtsControlled = input.getBooleanOr("Ctrl", false);
    }
}
