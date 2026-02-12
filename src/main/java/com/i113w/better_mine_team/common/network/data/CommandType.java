package com.i113w.better_mine_team.common.network.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.IntFunction;

public enum CommandType {
    MOVE,       // 移动到地点
    ATTACK,     // 攻击实体
    INTERACT,   // 交互方块/实体
    STOP,       // 停止/原地待命
    HOLD;       // 坚守阵地

    // 使用 idMapper 将 Enum 映射为 VarInt，兼容 ByteBuf
    public static final StreamCodec<ByteBuf, CommandType> STREAM_CODEC = ByteBufCodecs.idMapper(
            (IntFunction<CommandType>) i -> values()[i],
            CommandType::ordinal
    );
}