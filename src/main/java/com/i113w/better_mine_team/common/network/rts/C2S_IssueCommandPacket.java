package com.i113w.better_mine_team.common.network.rts;

import com.i113w.better_mine_team.common.network.data.CommandTarget;
import com.i113w.better_mine_team.common.network.data.CommandType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 → 服务端：发布 RTS 指令
 */
public class C2S_IssueCommandPacket {

    private final CommandType commandType;
    private final CommandTarget target;
    private final List<Integer> secondaryTargetIds;
    private final int selectionRevision;

    public C2S_IssueCommandPacket(CommandType commandType, CommandTarget target,
                                   List<Integer> secondaryTargetIds, int selectionRevision) {
        this.commandType = commandType;
        this.target = target;
        this.secondaryTargetIds = secondaryTargetIds;
        this.selectionRevision = selectionRevision;
    }

    public static void encode(C2S_IssueCommandPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.commandType.ordinal());
        CommandTarget.encode(msg.target, buf);
        buf.writeVarInt(msg.secondaryTargetIds.size());
        for (int id : msg.secondaryTargetIds) {
            buf.writeVarInt(id);
        }
        buf.writeVarInt(msg.selectionRevision);
    }

    public static C2S_IssueCommandPacket decode(FriendlyByteBuf buf) {
        CommandType type = CommandType.fromOrdinal(buf.readVarInt());
        CommandTarget target = CommandTarget.decode(buf);
        int secSize = buf.readVarInt();
        List<Integer> secIds = new ArrayList<>(secSize);
        for (int i = 0; i < secSize; i++) secIds.add(buf.readVarInt());
        int revision = buf.readVarInt();
        return new C2S_IssueCommandPacket(type, target, secIds, revision);
    }

    public CommandType getCommandType() { return commandType; }
    public CommandTarget getTarget() { return target; }
    public List<Integer> getSecondaryTargetIds() { return secondaryTargetIds; }
    public int getSelectionRevision() { return selectionRevision; }
}
