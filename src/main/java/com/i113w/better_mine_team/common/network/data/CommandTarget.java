package com.i113w.better_mine_team.common.network.data;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

public record CommandTarget(Vec3 pos, int targetEntityId, BlockPos blockPos) {

    public static final CommandTarget EMPTY = new CommandTarget(Vec3.ZERO, -1, BlockPos.ZERO);

    public static void encode(CommandTarget target, FriendlyByteBuf buf) {
        buf.writeDouble(target.pos.x);
        buf.writeDouble(target.pos.y);
        buf.writeDouble(target.pos.z);
        buf.writeVarInt(target.targetEntityId);
        buf.writeBlockPos(target.blockPos);
    }

    public static CommandTarget decode(FriendlyByteBuf buf) {
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int entityId = buf.readVarInt();
        BlockPos blockPos = buf.readBlockPos();
        return new CommandTarget(pos, entityId, blockPos);
    }
}
