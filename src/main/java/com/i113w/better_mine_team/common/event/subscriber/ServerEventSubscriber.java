package com.i113w.better_mine_team.common.event.subscriber;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;


@EventBusSubscriber(modid = BetterMineTeam.MODID)
public class ServerEventSubscriber {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (server != null) {
            TeamManager.initTeams(server);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 每 5 秒 (100 ticks) 清理一次过期仇恨数据
        if (event.getServer().getTickCount() % 100 == 0) {
            // [修改] 传入 server 实例以获取准确时间
            TeamManager.cleanupExpiredHateData(event.getServer());
        }
    }
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // 清空静态缓存
        TeamManager.clearAllData(); // 需要在 TeamManager 里加这个方法
    }
}