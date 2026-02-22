package com.i113w.better_mine_team.common.registry;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.rts.data.RTSPlayerData;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BetterMineTeam.MODID);

    // 注册生物数据 (保留序列化，因为生物的指令状态需要持久化)
    public static final Supplier<AttachmentType<RTSUnitData>> UNIT_DATA = ATTACHMENTS.register(
            "rts_unit_data", () -> AttachmentType.serializable(RTSUnitData::new).build()
    );

    // 注册玩家数据（使用 builder 而不是 serializable，仅在内存中存活，解决重登错位 Bug）
    public static final Supplier<AttachmentType<RTSPlayerData>> PLAYER_DATA = ATTACHMENTS.register(
            "rts_player_data", () -> AttachmentType.builder(RTSPlayerData::new).build()
    );

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }
}