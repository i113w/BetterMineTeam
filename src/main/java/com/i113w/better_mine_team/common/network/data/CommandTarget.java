package com.i113w.better_mine_team.common.network.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

public record CommandTarget(Vec3 pos, int targetEntityId, BlockPos blockPos) {

    public static final CommandTarget EMPTY = new CommandTarget(Vec3.ZERO, -1, BlockPos.ZERO);

    public static final StreamCodec<ByteBuf, CommandTarget> STREAM_CODEC = StreamCodec.composite(
            // 1. Vec3 (使用 composite 组合 3个 double)
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, Vec3::x,
                    ByteBufCodecs.DOUBLE, Vec3::y,
                    ByteBufCodecs.DOUBLE, Vec3::z,
                    Vec3::new
            ), CommandTarget::pos,

            // 2. Entity ID
            ByteBufCodecs.VAR_INT, CommandTarget::targetEntityId,

            // 3. BlockPos (原版自带 ByteBuf 兼容的 Codec)
            BlockPos.STREAM_CODEC, CommandTarget::blockPos,

            CommandTarget::new
    );
}