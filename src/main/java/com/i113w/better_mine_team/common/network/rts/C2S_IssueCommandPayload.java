package com.i113w.better_mine_team.common.network.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.network.data.CommandTarget;
import com.i113w.better_mine_team.common.network.data.CommandType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

// [修改] 新增 secondaryTargetIds 字段
public record C2S_IssueCommandPayload(CommandType commandType, CommandTarget target, List<Integer> secondaryTargetIds, int selectionRevision) implements CustomPacketPayload {

    public static final Type<C2S_IssueCommandPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "rts_issue_cmd"));

    public static final StreamCodec<ByteBuf, C2S_IssueCommandPayload> STREAM_CODEC = StreamCodec.composite(
            CommandType.STREAM_CODEC, C2S_IssueCommandPayload::commandType,
            CommandTarget.STREAM_CODEC, C2S_IssueCommandPayload::target,
            // 新增：整数列表编解码
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), C2S_IssueCommandPayload::secondaryTargetIds,
            ByteBufCodecs.VAR_INT, C2S_IssueCommandPayload::selectionRevision,
            C2S_IssueCommandPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}