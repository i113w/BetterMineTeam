package com.i113w.better_mine_team.common.network.handler;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.config.PatrolSettings;
import com.i113w.better_mine_team.common.event.subscriber.MobTeamEventSubscriber;
import com.i113w.better_mine_team.common.network.data.PatrolAction;
import com.i113w.better_mine_team.common.network.rts.C2S_IssueCommandPayload;
import com.i113w.better_mine_team.common.network.rts.C2S_PatrolCommandPayload;
import com.i113w.better_mine_team.common.network.rts.C2S_SelectionSyncPayload;
import com.i113w.better_mine_team.common.network.rts.S2C_CommandAckPayload;
import com.i113w.better_mine_team.common.network.rts.S2C_PatrolSyncPayload;
import com.i113w.better_mine_team.common.registry.ModAttachments;
import com.i113w.better_mine_team.common.rts.ai.PatrolTargeting;
import com.i113w.better_mine_team.common.rts.ai.RTSUnitAIController;
import com.i113w.better_mine_team.common.rts.data.RTSPlayerData;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import com.i113w.better_mine_team.common.rts.data.PatrolTask;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ServerPacketHandler {

    // 最大控制距离 (格)
    private static final double MAX_CONTROL_DISTANCE_SQR = 256.0 * 256.0;

    // 处理选区同步
    public static void handleSelectionSync(final C2S_SelectionSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RTSPlayerData data = player.getData(ModAttachments.PLAYER_DATA);

                BetterMineTeam.debug("[RTS-SERVER] 📥 Selection sync received from {}: {} entities (revision: {})",
                        player.getName().getString(),
                        payload.entityIds().size(),
                        payload.revision());

                data.updateSelection(payload.entityIds(), payload.revision());
            }
        });
    }

    // 处理指令发布
    public static void handleIssueCommand(final C2S_IssueCommandPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // [优化] 使用 Debug 日志防止刷屏
            if (BMTConfig.isDebugEnabled()) {
                BetterMineTeam.debug("========================================");
                BetterMineTeam.debug("[RTS-SERVER] 📥 Command received");
                BetterMineTeam.debug("[RTS-SERVER] Player: {}", player.getName().getString());
                BetterMineTeam.debug("[RTS-SERVER] Command Type: {}", payload.commandType());
                BetterMineTeam.debug("[RTS-SERVER] Target: {}", payload.target());
            }

            RTSPlayerData playerData = player.getData(ModAttachments.PLAYER_DATA);
            Set<Integer> selectedIds = playerData.getSelection();

            if (selectedIds.isEmpty()) {
                BetterMineTeam.debug("[RTS-SERVER] ⚠️ No entities in selection! Check if C2S_SelectionSync was received.");
                sendAck(player, false, 0, Component.translatable("better_mine_team.msg.cmd_no_units").withStyle(ChatFormatting.RED));
                return;
            }

            Level level = player.level();
            List<Mob> validUnits = new ArrayList<>();
            PlayerTeam playerTeam = TeamManager.getTeam(player);

            // [优化] 移除大量 INFO 日志，改为 Debug
            BetterMineTeam.debug("[RTS-SERVER] Validating {} selected entities...", selectedIds.size());

            for (int id : selectedIds) {
                Entity entity = level.getEntity(id);
                if (entity == null || !(entity instanceof Mob mob) || !mob.isAlive()) continue;

                if (isValidController(player, playerTeam, mob, payload.commandType())) {
                    validUnits.add(mob);
                } else {
                    BetterMineTeam.debug("[RTS-SERVER] ❌ Permission denied for entity {}", id);
                }
            }

            int successCount = validUnits.size();
            BetterMineTeam.debug("[RTS-SERVER] Valid units: {}/{}", successCount, selectedIds.size());

            if (successCount == 0) {
                sendAck(player, false, 0,
                        Component.translatable("better_mine_team.msg.cmd_no_units").withStyle(ChatFormatting.RED));
                return;
            }

            // 执行指令
            switch (payload.commandType()) {
                case MOVE    -> executeMoveCommand(validUnits, payload.target().pos());
                case ATTACK  -> executeAttackCommand(validUnits, level, payload.target().targetEntityId(), payload.secondaryTargetIds());
                case STOP    -> executeStopCommand(validUnits);
                case RECRUIT -> executeRecruitCommand(player, validUnits);
            }

            BetterMineTeam.debug("[RTS-SERVER] ✅ Command executed");
            sendAck(player, true, successCount,
                    Component.translatable("better_mine_team.msg.cmd_ack", successCount).withStyle(ChatFormatting.GREEN));
        });
    }

    public static void handlePatrolCommand(final C2S_PatrolCommandPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            PatrolSettings settings = BMTConfig.getPatrolSettings();
            if (!settings.enabled()) {
                sendAck(player, false, 0,
                        Component.translatable("better_mine_team.msg.patrol_disabled").withStyle(ChatFormatting.RED));
                return;
            }

            Level level = player.level();
            String playerDimension = level.dimension().location().toString();
            if (!playerDimension.equals(payload.dimensionId())) {
                sendAck(player, false, 0,
                        Component.translatable("better_mine_team.msg.patrol_wrong_dimension").withStyle(ChatFormatting.RED));
                return;
            }

            PlayerTeam playerTeam = TeamManager.getTeam(player);
            List<PathfinderMob> validUnits = getValidPatrolUnits(player, playerTeam, payload.entityIds());
            if (validUnits.isEmpty()) {
                sendAck(player, false, 0,
                        Component.translatable("better_mine_team.msg.patrol_no_units").withStyle(ChatFormatting.RED));
                return;
            }

            if (payload.action() == PatrolAction.CANCEL) {
                int cancelled = executePatrolCancel(validUnits);
                if (cancelled > 0) {
                    sendAck(player, true, cancelled,
                            Component.translatable("better_mine_team.msg.patrol_cancelled", cancelled).withStyle(ChatFormatting.GREEN));
                } else {
                    sendAck(player, false, 0,
                            Component.translatable("better_mine_team.msg.patrol_no_units").withStyle(ChatFormatting.RED));
                }
                return;
            }

            if (payload.action() == PatrolAction.ASSIGN_AREA) {
                executePatrolArea(player, playerTeam, validUnits, payload, settings);
            } else {
                executePatrolPoint(player, playerTeam, validUnits, payload, settings);
            }
        });
    }

    // ── Command executors ────────────────────────────────────────────────────

    private static void executeMoveCommand(List<Mob> units, Vec3 centerTarget) {
        int count = units.size();
        BetterMineTeam.debug("[RTS-MOVE-CMD] {} units → {}", count, centerTarget);
        if (count == 0) return;

        if (count == 1) {
            RTSUnitAIController.setMoveTarget(units.get(0), centerTarget);
            return;
        }

        int cols = (int) Math.ceil(Math.sqrt(count));
        double spacing = 2.0;
        for (int i = 0; i < count; i++) {
            int row = i / cols;
            int col = i % cols;
            double offsetX = (col - (cols - 1) / 2.0) * spacing;
            double offsetZ = (row - (cols - 1) / 2.0) * spacing;
            RTSUnitAIController.setMoveTarget(units.get(i), centerTarget.add(offsetX, 0, offsetZ));
        }
    }

    private static void executeAttackCommand(List<Mob> units, Level level,
                                             int primaryTargetId, List<Integer> secondaryTargetIds) {
        List<Entity> allTargets = new ArrayList<>();
        Entity primaryTarget = level.getEntity(primaryTargetId);
        if (primaryTarget != null) allTargets.add(primaryTarget);

        for (int id : secondaryTargetIds) {
            Entity e = level.getEntity(id);
            if (e != null && e != primaryTarget) allTargets.add(e);
        }

        if (allTargets.isEmpty()) return;

        BetterMineTeam.debug("[RTS-ATTACK-CMD] Units: {}, Targets: {}", units.size(), allTargets.size());

        // 2. 处理团队混战逻辑 (Team Aggression)
        for (Mob unit : units) {
            PlayerTeam unitTeam = TeamManager.getTeam(unit);
            if (unitTeam == null) continue;

            for (Entity target : allTargets) {
                if (!(target instanceof LivingEntity livingTarget)) continue;
                if (TeamManager.isAlly(unit, livingTarget)) continue;
                PlayerTeam targetTeam = TeamManager.getTeam(target);

                if (targetTeam != null) {
                    TeamManager.scanAndAddThreats(unitTeam, targetTeam, livingTarget);
                    // [可选] 双向宣战
                    TeamManager.scanAndAddThreats(targetTeam, unitTeam, unit);
                } else {
                    TeamManager.addThreat(unitTeam, livingTarget);
                }
            }
        }

        // 3. 分配攻击目标
        // [修复] 增加类型检查，确保攻击目标是 LivingEntity
        if (primaryTarget instanceof LivingEntity livingPrimary) {
            for (Mob unit : units) {
                if (unit == livingPrimary || TeamManager.isAlly(unit, livingPrimary)) continue;
                RTSUnitAIController.setAttackTarget(unit, livingPrimary);
            }
        } else if (!allTargets.isEmpty()) {
            LivingEntity fallback = null;
            for (Entity e : allTargets) {
                if (e instanceof LivingEntity le && le.isAlive()) { fallback = le; break; }
            }
            if (fallback != null) {
                for (Mob unit : units) {
                    if (!TeamManager.isAlly(unit, fallback)) {
                        RTSUnitAIController.setAttackTarget(unit, fallback);
                    }
                }
            }
        }
    }

    private static void executeStopCommand(List<Mob> units) {
        BetterMineTeam.debug("[RTS-STOP-CMD] {} units", units.size());
        for (Mob unit : units) RTSUnitAIController.stop(unit);
    }

    private static void executeRecruitCommand(ServerPlayer player, List<Mob> units) {
        if (!com.i113w.better_mine_team.common.team.TeamPermissions.hasOverridePermission(player)) {
            player.displayClientMessage(
                    Component.translatable("better_mine_team.msg.permission_denied").withStyle(ChatFormatting.RED), true);
            return;
        }

        PlayerTeam playerTeam = TeamManager.getTeam(player);
        if (playerTeam == null) {
            player.displayClientMessage(
                    Component.translatable("message.better_mine_team.error.no_team_specified", player.getName())
                            .withStyle(ChatFormatting.RED), true);
            return;
        }

        int successCount = 0;
        net.minecraft.world.scores.Scoreboard scoreboard = player.getScoreboard();

        for (Mob mob : units) {
            if (TeamManager.getTeam(mob) != null) continue; // 已有队伍，跳过

            scoreboard.addPlayerToTeam(mob.getStringUUID(), playerTeam);

            var followAttribute = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (followAttribute != null) {
                double newRange = BMTConfig.getGuardFollowRange();
                if (followAttribute.getBaseValue() < newRange) followAttribute.setBaseValue(newRange);
            }

            mob.setHealth(mob.getMaxHealth());
            mob.getPersistentData().putBoolean("bmt_follow_enabled", BMTConfig.isDefaultFollowEnabled());
            mob.setPersistenceRequired();

            MobTeamEventSubscriber.setupTeamAI(mob);

            TeamManager.syncGlowWithTeamDefault(mob);

            // 7. 特效反馈 (可选：播放声音或粒子)
            // level.broadcastEntityEvent(mob, (byte) ...);

            successCount++;
        }

        if (successCount > 0) {
            player.displayClientMessage(
                    Component.translatable("better_mine_team.msg.recruit_success", successCount)
                            .withStyle(ChatFormatting.GREEN), true);
        } else {
            player.displayClientMessage(
                    Component.translatable("better_mine_team.msg.recruit_fail_no_target")
                            .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    private static void executePatrolPoint(ServerPlayer player, PlayerTeam playerTeam,
                                           List<PathfinderMob> units, C2S_PatrolCommandPayload payload,
                                           PatrolSettings settings) {
        Level level = player.level();
        BlockPos center = payload.center();
        if (!level.isLoaded(center) || !isWithinPatrolCommandDistance(player, settings, center)) {
            sendAck(player, false, 0,
                    Component.translatable("better_mine_team.msg.patrol_unreachable").withStyle(ChatFormatting.RED));
            return;
        }

        int radius = settings.pointRadius();
        String dimension = level.dimension().location().toString();
        String teamName = getTeamName(playerTeam);
        PatrolTask probe = PatrolTask.point(player.getUUID(), teamName, dimension, center, radius, center);
        List<BlockPos> route = PatrolTargeting.createPerimeterRoute(probe, settings);
        int assigned = 0;

        for (PathfinderMob unit : units) {
            int nearestIndex = PatrolTargeting.findNearestPerimeterIndex(route, unit.blockPosition());
            int direction = unit.getRandom().nextBoolean() ? 1 : -1;
            Optional<PatrolTargeting.PerimeterTarget> initial = PatrolTargeting.findReachablePerimeterTarget(
                    unit, route, nearestIndex, direction, settings);
            if (initial.isEmpty()) continue;

            RTSUnitData data = unit.getData(ModAttachments.UNIT_DATA);
            data.setPatrolTask(PatrolTask.point(
                    player.getUUID(), teamName, dimension, center, radius, initial.get().position()));
            syncPatrol(unit);
            assigned++;
        }

        sendPatrolAssignAck(player, assigned, units.size());
    }

    private static void executePatrolArea(ServerPlayer player, PlayerTeam playerTeam,
                                          List<PathfinderMob> units, C2S_PatrolCommandPayload payload,
                                          PatrolSettings settings) {
        Level level = player.level();
        BlockPos rawMin = PatrolTargeting.normalizedMin(payload.minCorner(), payload.maxCorner());
        BlockPos rawMax = PatrolTargeting.normalizedMax(payload.minCorner(), payload.maxCorner());
        int centerY = payload.minCorner().getY();
        BlockPos min = new BlockPos(rawMin.getX(), centerY, rawMin.getZ());
        BlockPos max = new BlockPos(rawMax.getX(), centerY, rawMax.getZ());

        BlockPos minMaxCorner = new BlockPos(min.getX(), centerY, max.getZ());
        BlockPos maxMinCorner = new BlockPos(max.getX(), centerY, min.getZ());
        if (payload.minCorner().getY() != payload.maxCorner().getY()
                || !PatrolTargeting.isAreaSizeValid(min, max, settings)
                || !PatrolTargeting.isAreaLoaded(level, min, max)
                || !isWithinPatrolCommandDistance(player, settings, min, max, minMaxCorner, maxMinCorner)) {
            sendAck(player, false, 0,
                    Component.translatable(
                            "better_mine_team.msg.patrol_invalid_area",
                            PatrolTargeting.areaWidth(min, max),
                            PatrolTargeting.areaDepth(min, max),
                            settings.minAreaSize(),
                            settings.maxAreaSize()
                    ).withStyle(ChatFormatting.RED));
            return;
        }

        BlockPos center = new BlockPos(
                (min.getX() + max.getX()) / 2,
                centerY,
                (min.getZ() + max.getZ()) / 2
        );
        String dimension = level.dimension().location().toString();
        String teamName = getTeamName(playerTeam);
        PatrolTask probe = PatrolTask.area(player.getUUID(), teamName, dimension, center, min, max, center);
        List<BlockPos> route = PatrolTargeting.createPerimeterRoute(probe, settings);
        int assigned = 0;

        for (PathfinderMob unit : units) {
            int nearestIndex = PatrolTargeting.findNearestPerimeterIndex(route, unit.blockPosition());
            int direction = unit.getRandom().nextBoolean() ? 1 : -1;
            Optional<PatrolTargeting.PerimeterTarget> initial = PatrolTargeting.findReachablePerimeterTarget(
                    unit, route, nearestIndex, direction, settings);
            if (initial.isEmpty()) continue;

            RTSUnitData data = unit.getData(ModAttachments.UNIT_DATA);
            data.setPatrolTask(PatrolTask.area(
                    player.getUUID(), teamName, dimension, center, min, max, initial.get().position()));
            syncPatrol(unit);
            assigned++;
        }

        sendPatrolAssignAck(player, assigned, units.size());
    }

    private static int executePatrolCancel(List<PathfinderMob> units) {
        int cancelled = 0;
        for (PathfinderMob unit : units) {
            RTSUnitData data = unit.getData(ModAttachments.UNIT_DATA);
            if (!data.getPatrolTask().isEnabled()) continue;
            data.clearPatrolTask();
            unit.getNavigation().stop();
            syncPatrol(unit);
            cancelled++;
        }
        return cancelled;
    }

    private static List<PathfinderMob> getValidPatrolUnits(ServerPlayer player, PlayerTeam playerTeam, List<Integer> entityIds) {
        List<PathfinderMob> units = new ArrayList<>();
        Level level = player.level();

        for (int id : entityIds) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof PathfinderMob mob) || !mob.isAlive()) continue;
            if (isValidController(player, playerTeam, mob, com.i113w.better_mine_team.common.network.data.CommandType.MOVE)) {
                units.add(mob);
            }
        }
        return units;
    }

    private static void sendPatrolAssignAck(ServerPlayer player, int assigned, int requested) {
        if (assigned <= 0) {
            sendAck(player, false, 0,
                    Component.translatable("better_mine_team.msg.patrol_unreachable").withStyle(ChatFormatting.RED));
            return;
        }

        String key = assigned < requested
                ? "better_mine_team.msg.patrol_partial"
                : "better_mine_team.msg.patrol_assigned";
        sendAck(player, true, assigned,
                Component.translatable(key, assigned).withStyle(assigned < requested ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
    }

    public static void syncPatrol(Mob mob) {
        RTSUnitData data = mob.getData(ModAttachments.UNIT_DATA);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(mob,
                S2C_PatrolSyncPayload.fromTask(mob.getId(), data.getPatrolTask()));
    }

    private static String getTeamName(PlayerTeam team) {
        return team == null ? "" : team.getName();
    }

    private static boolean isWithinPatrolCommandDistance(ServerPlayer player, PatrolSettings settings,
                                                          BlockPos... positions) {
        double maxDistanceSqr = settings.maxCommandDistanceSqr();
        for (BlockPos pos : positions) {
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > maxDistanceSqr) return false;
        }
        return true;
    }

    // ── Validation ───────────────────────────────────────────────────────────

    private static boolean isValidController(ServerPlayer player, PlayerTeam playerTeam,
                                             Mob mob,
                                             com.i113w.better_mine_team.common.network.data.CommandType commandType) {
        if (player.level() != mob.level()) return false;
        if (!mob.level().isLoaded(mob.blockPosition())) return false;
        if (player.distanceToSqr(mob) > MAX_CONTROL_DISTANCE_SQR) return false;

        if (commandType == com.i113w.better_mine_team.common.network.data.CommandType.RECRUIT) {
            return com.i113w.better_mine_team.common.team.TeamPermissions.hasOverridePermission(player)
                    && TeamManager.getTeam(mob) == null;
        }

        PlayerTeam mobTeam = TeamManager.getTeam(mob);
        if (playerTeam != null && mobTeam != null && playerTeam.getName().equals(mobTeam.getName())) return true;
        if (mob instanceof net.minecraft.world.entity.TamableAnimal tamable && tamable.isOwnedBy(player)) return true;
        return false;
    }

    // ── Ack ──────────────────────────────────────────────────────────────────

    private static void sendAck(ServerPlayer player, boolean success, int count, Component msg) {
        PacketDistributor.sendToPlayer(player, new S2C_CommandAckPayload(success, count, msg));
    }
}
