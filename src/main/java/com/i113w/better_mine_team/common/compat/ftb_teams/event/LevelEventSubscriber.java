package com.i113w.better_mine_team.common.compat.ftb_teams.event;

import com.i113w.better_mine_team.BetterMineTeam;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID)
public class LevelEventSubscriber {
    @SubscribeEvent
    public static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event) {

    }
}