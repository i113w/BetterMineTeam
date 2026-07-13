package com.i113w.better_mine_team.common.network.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.IntFunction;

public enum PatrolAction {
    ASSIGN_POINT,
    ASSIGN_AREA,
    CANCEL;

    public static final StreamCodec<ByteBuf, PatrolAction> STREAM_CODEC = ByteBufCodecs.idMapper(
            (IntFunction<PatrolAction>) i -> values()[i],
            PatrolAction::ordinal
    );
}
