package com.i113w.better_mine_team.common.rts.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.IntFunction;

public enum PatrolMode {
    POINT,
    AREA;

    public static final StreamCodec<ByteBuf, PatrolMode> STREAM_CODEC = ByteBufCodecs.idMapper(
            (IntFunction<PatrolMode>) i -> values()[i],
            PatrolMode::ordinal
    );
}
