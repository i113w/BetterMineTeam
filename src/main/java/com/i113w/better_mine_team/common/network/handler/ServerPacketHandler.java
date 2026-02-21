package com.i113w.better_mine_team.common.network.handler;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.entity.goal.TeamFollowCaptainGoal;
import com.i113w.better_mine_team.common.entity.goal.TeamHurtByTargetGoal;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.network.rts.C2S_IssueCommandPacket;
import com.i113w.better_mine_team.common.network.rts.C2S_SelectionSyncPacket;
import com.i113w.better_mine_team.common.network.rts.S2C_CommandAckPacket;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.rts.ai.RTSUnitAIController;
import com.i113w.better_mine_team.common.rts.data.RTSPlayerData;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.better_mine_team.common.team.TeamPermissions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
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

            switch (msg.getCommandType()) {
                case MOVE   -> executeMoveCommand(validUnits, msg.getTarget().pos());
                case ATTACK -> executeAttackCommand(validUnits, level, msg.getTarget().targetEntityId(), msg.getSecondaryTargetIds());
                case STOP   -> executeStopCommand(validUnits);
                case RECRUIT -> executeRecruitCommand(player, validUnits);
            }

            sendAck(player, true, successCount,
                    Component.translatable("better_mine_team.msg.cmd_ack", successCount).withStyle(ChatFormatting.GREEN));
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

    private static void executeAttackCommand(List<Mob> units, Level level, int primaryTargetId, List<Integer> secondaryTargetIds) {
        List<Entity> allTargets = new ArrayList<>();
        Entity primaryTarget = level.getEntity(primaryTargetId);
        if (primaryTarget != null) allTargets.add(primaryTarget);
        for (int id : secondaryTargetIds) {
            Entity e = level.getEntity(id);
            if (e != null && e != primaryTarget) allTargets.add(e);
        }
        if (allTargets.isEmpty()) return;

        // 传播威胁（团队混战）
        for (Mob unit : units) {
            PlayerTeam unitTeam = TeamManager.getTeam(unit);
            if (unitTeam == null) continue;
            for (Entity target : allTargets) {
                if (!(target instanceof LivingEntity livingTarget)) continue;
                if (TeamManager.isAlly(unit, livingTarget)) continue;
                PlayerTeam targetTeam = TeamManager.getTeam(target);
                if (targetTeam != null) {
                    TeamManager.scanAndAddThreats(unitTeam, targetTeam, livingTarget);
                    TeamManager.scanAndAddThreats(targetTeam, unitTeam, unit);
                } else {
                    TeamManager.addThreat(unitTeam, livingTarget);
                }
            }
        }

        // 分配攻击目标
        LivingEntity effectivePrimary = null;
        if (primaryTarget instanceof LivingEntity lp && lp.isAlive()) {
            effectivePrimary = lp;
        } else {
            for (Entity e : allTargets) {
                if (e instanceof LivingEntity le && le.isAlive()) { effectivePrimary = le; break; }
            }
        }

        if (effectivePrimary == null) return;
        final LivingEntity finalTarget = effectivePrimary;

        for (Mob unit : units) {
            if (!TeamManager.isAlly(unit, finalTarget)) {
                RTSUnitAIController.setAttackTarget(unit, finalTarget);
            }
        }
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
            mob.getPersistentData().putBoolean("bmt_follow_enabled", BMTConfig.getDefaultFollowState()); // 修改处
            mob.setPersistenceRequired();

            mob.targetSelector.addGoal(1, new TeamHurtByTargetGoal(mob));
            mob.goalSelector.addGoal(2, new TeamFollowCaptainGoal(mob,
                    BMTConfig.getGuardFollowSpeed(),
                    BMTConfig.getGuardFollowStartDist(),
                    BMTConfig.getGuardFollowStopDist()));

            mob.setGlowingTag(true);
            successCount++;
        }

        if (successCount > 0) {
            player.displayClientMessage(Component.translatable("better_mine_team.msg.recruit_success", successCount).withStyle(ChatFormatting.GREEN), true);
        } else {
            player.displayClientMessage(Component.translatable("better_mine_team.msg.recruit_fail_no_target").withStyle(ChatFormatting.YELLOW), true);
        }
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