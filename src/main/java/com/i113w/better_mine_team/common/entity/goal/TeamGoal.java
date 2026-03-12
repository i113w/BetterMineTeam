package com.i113w.better_mine_team.common.entity.goal;

/**
 * Marker interface for all Goals added by Better Mine Team.
 *
 * Any Goal implementing this interface is exempt from aggressive-goal sanitization,
 * preventing our own goals from being accidentally removed when a mob joins a team.
 *
 * All mod-provided Goals (TeamHurtByTargetGoal, TeamFollowCaptainGoal,
 * RTSMoveGoal, RTSAttackGoal) must implement this interface.
 */
public interface TeamGoal {
}
