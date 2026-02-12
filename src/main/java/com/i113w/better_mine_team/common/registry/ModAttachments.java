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

    // 注册生物数据
    public static final Supplier<AttachmentType<RTSUnitData>> UNIT_DATA = ATTACHMENTS.register(
            "rts_unit_data", () -> AttachmentType.serializable(RTSUnitData::new).build()
    );

    // 注册玩家数据（不需 copyOnDeath，重生后选框应清空）
    public static final Supplier<AttachmentType<RTSPlayerData>> PLAYER_DATA = ATTACHMENTS.register(
            "rts_player_data", () -> AttachmentType.serializable(RTSPlayerData::new).build()
    );

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }
}