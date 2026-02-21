package com.i113w.better_mine_team.common.network.data;

public enum CommandType {
    MOVE,
    ATTACK,
    INTERACT,
    STOP,
    HOLD,
    RECRUIT;

    public static CommandType fromOrdinal(int i) {
        CommandType[] values = values();
        return (i >= 0 && i < values.length) ? values[i] : STOP;
    }
}
