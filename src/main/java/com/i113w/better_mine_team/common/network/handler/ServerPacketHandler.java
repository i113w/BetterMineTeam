package com.i113w.better_mine_team.common.network.handler;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.config.PatrolSettings;
import com.i113w.better_mine_team.common.event.subscriber.MobTeamEventSubscriber;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.network.rts.C2S_IssueCommandPacket;
import com.i113w.better_mine_team.common.network.rts.C2S_PatrolCommandPacket;
import com.i113w.better_mine_team.common.network.rts.C2S_SelectionSyncPacket;
import com.i113w.better_mine_team.common.network.rts.S2C_CommandAckPacket;
import com.i113w.better_mine_team.common.network.rts.S2C_PatrolSyncPacket;
import com.i113w.better_mine_team.common.network.data.PatrolAction;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.rts.ai.PatrolTargeting;
import com.i113w.better_mine_team.common.rts.ai.PatrolCombatBoundary;
import com.i113w.better_mine_team.common.rts.ai.RTSUnitAIController;
import com.i113w.better_mine_team.common.rts.data.PatrolTask;
import com.i113w.better_mine_team.common.rts.data.RTSPlayerData;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.better_mine_team.common.team.TeamPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class ServerPacketHandler {

    private static final double MAX_CONTROL_DISTANCE_SQR = 256.0 * 256.0;

    // =====================================================================
    // 选区同步
    // =====================================================================

    public static void handleSelectionSync(C2S_SelectionSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            RTSPlayerData data = RTSPlayerData.get(player);
            BetterMineTeam.debug("[RTS-SERVER] 📥 Selection sync from {}: {} entities (rev {})",
                    player.getName().getString(), msg.getEntityIds().size(), msg.getRevision());
            data.updateSelection(msg.getEntityIds(), msg.getRevision());
        });
        ctx.setPacketHandled(true);
    }

    // =====================================================================
    // 指令执行
    // =====================================================================

    public static void handleIssueCommand(C2S_IssueCommandPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            if (BMTConfig.isDebugEnabled()) {
                BetterMineTeam.debug("========================================");
                BetterMineTeam.debug("[RTS-SERVER] 📥 Command: player={}, type={}, target={}",
                        player.getName().getString(), msg.getCommandType(), msg.getTarget());
            }

            RTSPlayerData playerData = RTSPlayerData.get(player);
            Set<Integer> selectedIds = playerData.getSelection();

            if (selectedIds.isEmpty()) {
                BetterMineTeam.debug("[RTS-SERVER] No entities in selection! (Memory is empty)");
                sendAck(player, false, 0, Component.translatable("better_mine_team.msg.cmd_no_units").withStyle(ChatFormatting.RED));
                return;
            }

            Level level = player.level();
            List<Mob> validUnits = new ArrayList<>();
            PlayerTeam playerTeam = TeamManager.getTeam(player);

            for (int id : selectedIds) {
                Entity entity = level.getEntity(id);
                if (entity == null) {
                    BetterMineTeam.debug("[RTS-SERVER] Filtered Out: Entity ID {} is null or not in this dimension", id);
                    continue;
                }
                if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                    BetterMineTeam.debug("[RTS-SERVER] Filtered Out: Entity ID {} ({}) is dead or not a Mob", id, entity.getName().getString());
                    continue;
                }
                if (isValidController(player, playerTeam, mob, msg.getCommandType())) {
                    validUnits.add(mob);
                } else {
                    BetterMineTeam.debug("[RTS-SERVER] Filtered Out: Entity ID {} ({}) failed control validation", id, entity.getName().getString());
                }
            }

            int successCount = validUnits.size();
            BetterMineTeam.debug("[RTS-SERVER] Valid units: {}/{}", successCount, selectedIds.size());

            if (successCount == 0) {
                sendAck(player, false, 0, Component.translatable("better_mine_team.msg.cmd_no_units").withStyle(ChatFormatting.RED));
                return;
            }

            if (msg.getCommandType() == CommandType.ATTACK) {
                int attackCount = executeAttackCommand(validUnits, level,
                        msg.getTarget().targetEntityId(), msg.getSecondaryTargetIds());
                if (attackCount == 0) {
                    sendAck(player, false, 0, Component.translatable("better_mine_team.msg.cmd_attack_patrol_blocked")
                            .withStyle(ChatFormatting.RED));
                } else if (attackCount < successCount) {
                    sendAck(player, true, attackCount,
                            Component.translatable("better_mine_team.msg.cmd_attack_patrol_partial", attackCount, successCount)
                                    .withStyle(ChatFormatting.YELLOW));
                } else {
                    sendAck(player, true, attackCount,
                            Component.translatable("better_mine_team.msg.cmd_ack", attackCount).withStyle(ChatFormatting.GREEN));
                }
                return;
            }

            switch (msg.getCommandType()) {
                case MOVE   -> executeMoveCommand(validUnits, msg.getTarget().pos());
                case ATTACK -> { }
                case STOP   -> executeStopCommand(validUnits);
                case RECRUIT -> executeRecruitCommand(player, validUnits);
            }

            sendAck(player, true, successCount,
                    Component.translatable("better_mine_team.msg.cmd_ack", successCount).withStyle(ChatFormatting.GREEN));
        });
        ctx.setPacketHandled(true);
    }

    public static void handlePatrolCommand(C2S_PatrolCommandPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            PatrolSettings settings = BMTConfig.getPatrolSettings();
            if (!settings.enabled()) {
                sendAck(player, false, 0, Component.translatable("better_mine_team.msg.patrol_disabled")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            Level level = player.level();
            if (!level.dimension().location().toString().equals(msg.getDimensionId())) {
                sendAck(player, false, 0, Component.translatable("better_mine_team.msg.patrol_wrong_dimension")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            PlayerTeam playerTeam = TeamManager.getTeam(player);
            List<PathfinderMob> validUnits = getValidPatrolUnits(player, playerTeam, msg.getEntityIds());
            if (validUnits.isEmpty()) {
                sendAck(player, false, 0, Component.translatable("better_mine_team.msg.patrol_no_units")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            if (msg.getAction() == PatrolAction.CANCEL) {
                int cancelled = executePatrolCancel(validUnits);
                sendAck(player, cancelled > 0, cancelled, cancelled > 0
                        ? Component.translatable("better_mine_team.msg.patrol_cancelled", cancelled)
                                .withStyle(ChatFormatting.GREEN)
                        : Component.translatable("better_mine_team.msg.patrol_no_units")
                                .withStyle(ChatFormatting.RED));
                return;
            }

            if (msg.getAction() == PatrolAction.ASSIGN_AREA) {
                executePatrolArea(player, playerTeam, validUnits, msg, settings);
            } else {
                executePatrolPoint(player, playerTeam, validUnits, msg, settings);
            }
        });
        ctx.setPacketHandled(true);
    }

    // =====================================================================
    // 内部逻辑
    // =====================================================================

    private static void executeMoveCommand(List<Mob> units, Vec3 centerTarget) {
        int count = units.size();
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

    private static int executeAttackCommand(List<Mob> units, Level level, int primaryTargetId, List<Integer> secondaryTargetIds) {
        List<Entity> allTargets = new ArrayList<>();
        Entity primaryTarget = level.getEntity(primaryTargetId);
        if (primaryTarget != null) allTargets.add(primaryTarget);
        for (int id : secondaryTargetIds) {
            Entity e = level.getEntity(id);
            if (e != null && e != primaryTarget) allTargets.add(e);
        }
        if (allTargets.isEmpty()) return 0;

        int accepted = 0;
        for (Mob unit : units) {
            PlayerTeam unitTeam = TeamManager.getTeam(unit);
            if (unitTeam == null) continue;

            LivingEntity assignedTarget = null;
            for (Entity target : allTargets) {
                if (!(target instanceof LivingEntity livingTarget)) continue;
                if (!livingTarget.isAlive()) continue;
                if (TeamManager.isAlly(unit, livingTarget)) continue;
                if (!PatrolCombatBoundary.canEngage(unit, livingTarget)) continue;
                assignedTarget = livingTarget;
                break;
            }

            if (assignedTarget == null) continue;
            PlayerTeam targetTeam = TeamManager.getTeam(assignedTarget);
            if (targetTeam != null) {
                TeamManager.scanAndAddThreats(unitTeam, targetTeam, assignedTarget);
                TeamManager.scanAndAddThreats(targetTeam, unitTeam, unit);
            } else {
                TeamManager.addThreat(unitTeam, assignedTarget);
            }
            RTSUnitAIController.setAttackTarget(unit, assignedTarget);
            accepted++;
        }
        return accepted;
    }

    private static void executeStopCommand(List<Mob> units) {
        units.forEach(RTSUnitAIController::stop);
    }

    private static void executeRecruitCommand(ServerPlayer player, List<Mob> units) {
        if (!TeamPermissions.hasOverridePermission(player)) {
            player.displayClientMessage(Component.translatable("better_mine_team.msg.permission_denied").withStyle(ChatFormatting.RED), true);
            return;
        }

        PlayerTeam playerTeam = TeamManager.getTeam(player);
        if (playerTeam == null) {
            player.displayClientMessage(Component.translatable("message.better_mine_team.error.no_team_specified", player.getName()).withStyle(ChatFormatting.RED), true);
            return;
        }

        int successCount = 0;
        Scoreboard scoreboard = player.getScoreboard();

        for (Mob mob : units) {
            if (TeamManager.getTeam(mob) != null) continue;

            scoreboard.addPlayerToTeam(mob.getStringUUID(), playerTeam);

            var followAttr = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (followAttr != null) {
                double newRange = BMTConfig.getGuardFollowRange();
                if (followAttr.getBaseValue() < newRange) followAttr.setBaseValue(newRange);
            }

            mob.setHealth(mob.getMaxHealth());
            mob.getPersistentData().putBoolean("bmt_follow_enabled", BMTConfig.isDefaultFollowEnabled());
            mob.setPersistenceRequired();

            MobTeamEventSubscriber.setupTeamAI(mob);

            TeamManager.syncGlowWithTeamDefault(mob);
            successCount++;
        }

        if (successCount > 0) {
            player.displayClientMessage(Component.translatable("better_mine_team.msg.recruit_success", successCount).withStyle(ChatFormatting.GREEN), true);
        } else {
            player.displayClientMessage(Component.translatable("better_mine_team.msg.recruit_fail_no_target").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    private static void executePatrolPoint(ServerPlayer player, PlayerTeam playerTeam,
                                           List<PathfinderMob> units, C2S_PatrolCommandPacket msg,
                                           PatrolSettings settings) {
        Level level = player.level();
        BlockPos center = msg.getCenter();
        if (!level.isLoaded(center)) {
            sendAck(player, false, 0, Component.translatable("better_mine_team.msg.patrol_unreachable")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!isWithinPatrolCommandDistance(player, settings, center)) {
            sendAck(player, false, 0, Component.translatable("better_mine_team.msg.patrol_too_far")
                    .withStyle(ChatFormatting.RED));
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
            RTSUnitData.get(unit).setPatrolTask(PatrolTask.point(player.getUUID(), teamName, dimension,
                    center, radius, initial.get().position()));
            syncPatrol(unit);
            assigned++;
        }
        sendPatrolAssignAck(player, assigned, units.size());
    }

    private static void executePatrolArea(ServerPlayer player, PlayerTeam playerTeam,
                                          List<PathfinderMob> units, C2S_PatrolCommandPacket msg,
                                          PatrolSettings settings) {
        Level level = player.level();
        BlockPos rawMin = PatrolTargeting.normalizedMin(msg.getMinCorner(), msg.getMaxCorner());
        BlockPos rawMax = PatrolTargeting.normalizedMax(msg.getMinCorner(), msg.getMaxCorner());
        int centerY = msg.getMinCorner().getY();
        BlockPos min = new BlockPos(rawMin.getX(), centerY, rawMin.getZ());
        BlockPos max = new BlockPos(rawMax.getX(), centerY, rawMax.getZ());
        BlockPos minMaxCorner = new BlockPos(min.getX(), centerY, max.getZ());
        BlockPos maxMinCorner = new BlockPos(max.getX(), centerY, min.getZ());
        if (msg.getMinCorner().getY() != msg.getMaxCorner().getY()
                || !PatrolTargeting.isAreaSizeValid(min, max, settings)
                || !PatrolTargeting.isAreaLoaded(level, min, max)
                || !isWithinPatrolCommandDistance(player, settings, min, max, minMaxCorner, maxMinCorner)) {
            sendAck(player, false, 0, Component.translatable("better_mine_team.msg.patrol_invalid_area",
                            PatrolTargeting.areaWidth(min, max), PatrolTargeting.areaDepth(min, max),
                            settings.minAreaSize(), settings.maxAreaSize())
                    .withStyle(ChatFormatting.RED));
            return;
        }

        BlockPos center = new BlockPos((min.getX() + max.getX()) / 2, centerY, (min.getZ() + max.getZ()) / 2);
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
            RTSUnitData.get(unit).setPatrolTask(PatrolTask.area(player.getUUID(), teamName, dimension,
                    center, min, max, initial.get().position()));
            syncPatrol(unit);
            assigned++;
        }
        sendPatrolAssignAck(player, assigned, units.size());
    }

    private static int executePatrolCancel(List<PathfinderMob> units) {
        int cancelled = 0;
        for (PathfinderMob unit : units) {
            RTSUnitData data = RTSUnitData.get(unit);
            if (!data.getPatrolTask().isEnabled()) continue;
            data.clearPatrolTask();
            unit.getNavigation().stop();
            syncPatrol(unit);
            cancelled++;
        }
        return cancelled;
    }

    private static List<PathfinderMob> getValidPatrolUnits(ServerPlayer player, PlayerTeam playerTeam,
                                                            List<Integer> entityIds) {
        List<PathfinderMob> units = new ArrayList<>();
        for (int id : new LinkedHashSet<>(entityIds)) {
            Entity entity = player.level().getEntity(id);
            if (entity instanceof PathfinderMob mob && mob.isAlive()
                    && isValidController(player, playerTeam, mob, CommandType.MOVE)) {
                units.add(mob);
            }
        }
        return units;
    }

    private static void sendPatrolAssignAck(ServerPlayer player, int assigned, int requested) {
        if (assigned <= 0) {
            sendAck(player, false, 0, Component.translatable("better_mine_team.msg.patrol_unreachable")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        String key = assigned < requested ? "better_mine_team.msg.patrol_partial" : "better_mine_team.msg.patrol_assigned";
        sendAck(player, true, assigned, Component.translatable(key, assigned)
                .withStyle(assigned < requested ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
    }

    public static void syncPatrol(Mob mob) {
        MTNetworkRegister.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> mob),
                S2C_PatrolSyncPacket.fromTask(mob.getId(), RTSUnitData.get(mob).getPatrolTask()));
    }

    private static String getTeamName(PlayerTeam team) {
        return team == null ? "" : team.getName();
    }

    private static boolean isWithinPatrolCommandDistance(ServerPlayer player, PatrolSettings settings,
                                                          BlockPos... positions) {
        for (BlockPos pos : positions) {
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > settings.maxCommandDistanceSqr()) return false;
        }
        return true;
    }

    private static boolean isValidController(ServerPlayer player, PlayerTeam playerTeam, Mob mob, CommandType commandType) {
        if (player.level() != mob.level()) return false;
        if (!mob.level().isLoaded(mob.blockPosition())) return false;
        if (player.distanceToSqr(mob) > MAX_CONTROL_DISTANCE_SQR) return false;

        if (commandType == CommandType.RECRUIT) {
            return TeamPermissions.hasOverridePermission(player) && TeamManager.getTeam(mob) == null;
        }

        PlayerTeam mobTeam = TeamManager.getTeam(mob);
        if (playerTeam != null && mobTeam != null && playerTeam.getName().equals(mobTeam.getName())) {
            return true;
        }
        if (mob instanceof net.minecraft.world.entity.TamableAnimal tamable) {
            return tamable.isOwnedBy(player);
        }
        return false;
    }

    private static void sendAck(ServerPlayer player, boolean success, int count, Component msg) {
        MTNetworkRegister.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2C_CommandAckPacket(success, count, msg)
        );
    }
}
