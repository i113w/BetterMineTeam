package com.i113w.better_mine_team.common.compat;


import net.minecraftforge.fml.ModList;

public class LoadedCompat {
    public final static boolean FTB_TEAMS = ModList.get().isLoaded("ftbteams");
    public final static boolean IRONS_SPELLBOOKS = ModList.get().isLoaded("irons_spellbooks");
}
