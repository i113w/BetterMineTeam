package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// [关键修改] 服务器事件属于 Forge 总线（默认）
@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID)
public class ServerEventSubscriber {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (server != null) {
            TeamManager.initTeams(server);
        }
    }

    // [关键修改] 1.20.1 使用 TickEvent.ServerTickEvent 代替 ServerTickEvent.Post
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // 只在 END 阶段执行，避免重复
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();

        // 每 5 秒 (100 ticks) 清理一次过期仇恨数据
        if (server.getTickCount() % 100 == 0) {
            TeamManager.cleanupExpiredHateData(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // 清空静态缓存，防止内存泄漏
        TeamManager.clearAllData();
    }
}