package com.i113w.better_mine_team.common.network.handler;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.network.rts.C2S_IssueCommandPayload;
import com.i113w.better_mine_team.common.network.rts.C2S_SelectionSyncPayload;
import com.i113w.better_mine_team.common.network.rts.S2C_CommandAckPayload;
import com.i113w.better_mine_team.common.registry.ModAttachments;
import com.i113w.better_mine_team.common.rts.ai.RTSUnitAIController;
import com.i113w.better_mine_team.common.rts.data.RTSPlayerData;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ServerPacketHandler {

    // 处理选区同步
    public static void handleSelectionSync(final C2S_SelectionSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RTSPlayerData data = player.getData(ModAttachments.PLAYER_DATA);

                BetterMineTeam.LOGGER.info("[RTS-SERVER] 📥 Selection sync received from {}: {} entities (revision: {})",
                        player.getName().getString(),
                        payload.entityIds().size(),
                        payload.revision());

                for (Integer id : payload.entityIds()) {
                    Entity entity = player.level().getEntity(id);
                    BetterMineTeam.LOGGER.info("[RTS-SERVER]   - Entity ID {}: exists={}, type={}",
                            id,
                            entity != null,
                            entity != null ? entity.getType() : "null");
                }

                data.updateSelection(payload.entityIds(), payload.revision());

                BetterMineTeam.LOGGER.info("[RTS-SERVER] ✅ Player data updated, stored {} entities",
                        data.getSelection().size());
            }
        });
    }


    // 处理指令发布
    public static void handleIssueCommand(final C2S_IssueCommandPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            BetterMineTeam.LOGGER.info("========================================");
            BetterMineTeam.LOGGER.info("[RTS-SERVER] 📥 Command received");
            BetterMineTeam.LOGGER.info("[RTS-SERVER] Player: {}", player.getName().getString());
            BetterMineTeam.LOGGER.info("[RTS-SERVER] Command Type: {}", payload.commandType());
            BetterMineTeam.LOGGER.info("[RTS-SERVER] Target: {}", payload.target());
            BetterMineTeam.LOGGER.info("[RTS-SERVER] Selection Revision: {}", payload.selectionRevision());

            RTSPlayerData playerData = player.getData(ModAttachments.PLAYER_DATA);
            Set<Integer> selectedIds = playerData.getSelection();

            BetterMineTeam.LOGGER.info("[RTS-SERVER] Selected IDs from player data: {}", selectedIds);

            if (selectedIds.isEmpty()) {
                BetterMineTeam.LOGGER.warn("[RTS-SERVER] ⚠️ No entities in selection! Check if C2S_SelectionSync was received.");
                sendAck(player, false, 0, Component.translatable("better_mine_team.msg.cmd_no_units").withStyle(ChatFormatting.RED));
                return;
            }

            Level level = player.level();
            List<Mob> validUnits = new ArrayList<>();
            PlayerTeam playerTeam = TeamManager.getTeam(player);

            BetterMineTeam.LOGGER.info("[RTS-SERVER] Player team: {}",
                    playerTeam != null ? playerTeam.getName() : "null");

            BetterMineTeam.LOGGER.info("[RTS-SERVER] Validating {} selected entities...", selectedIds.size());

            for (int id : selectedIds) {
                Entity entity = level.getEntity(id);

                BetterMineTeam.LOGGER.info("[RTS-SERVER]   Entity ID {}: exists={}", id, entity != null);

                if (entity == null) {
                    BetterMineTeam.LOGGER.warn("[RTS-SERVER]     ❌ Entity not found in server world!");
                    continue;
                }

                BetterMineTeam.LOGGER.info("[RTS-SERVER]     Type: {}", entity.getType());
                BetterMineTeam.LOGGER.info("[RTS-SERVER]     Name: {}", entity.getName().getString());
                BetterMineTeam.LOGGER.info("[RTS-SERVER]     Is Mob: {}", entity instanceof Mob);
                BetterMineTeam.LOGGER.info("[RTS-SERVER]     Is Alive: {}", entity instanceof LivingEntity && ((LivingEntity) entity).isAlive());

                if (!(entity instanceof Mob mob)) {
                    BetterMineTeam.LOGGER.warn("[RTS-SERVER]     ❌ Not a Mob, skipping");
                    continue;
                }

                if (!mob.isAlive()) {
                    BetterMineTeam.LOGGER.warn("[RTS-SERVER]     ❌ Mob is dead, skipping");
                    continue;
                }

                // ✅ [关键] 详细的权限检查日志
                PlayerTeam mobTeam = TeamManager.getTeam(mob);
                BetterMineTeam.LOGGER.info("[RTS-SERVER]     Mob team: {}",
                        mobTeam != null ? mobTeam.getName() : "null");

                boolean isTeamMember = playerTeam != null && mobTeam != null
                        && playerTeam.getName().equals(mobTeam.getName());
                BetterMineTeam.LOGGER.info("[RTS-SERVER]     Is team member: {}", isTeamMember);

                boolean isTamable = mob instanceof net.minecraft.world.entity.TamableAnimal;
                BetterMineTeam.LOGGER.info("[RTS-SERVER]     Is tamable: {}", isTamable);

                if (isTamable) {
                    net.minecraft.world.entity.TamableAnimal tamable = (net.minecraft.world.entity.TamableAnimal) mob;
                    boolean isOwned = tamable.isOwnedBy(player);
                    BetterMineTeam.LOGGER.info("[RTS-SERVER]     Is owned by player: {}", isOwned);
                }

                boolean isValid = isValidController(player, playerTeam, mob);
                BetterMineTeam.LOGGER.info("[RTS-SERVER]     Final validation result: {}", isValid);

                if (isValid) {
                    validUnits.add(mob);
                    BetterMineTeam.LOGGER.info("[RTS-SERVER]     ✅ Added to valid units");
                } else {
                    BetterMineTeam.LOGGER.warn("[RTS-SERVER]     ❌ Failed validation, NOT added");
                }
            }

            int successCount = validUnits.size();
            BetterMineTeam.LOGGER.info("[RTS-SERVER] Valid units count: {}/{}", successCount, selectedIds.size());

            if (successCount == 0) {
                BetterMineTeam.LOGGER.warn("[RTS-SERVER] ⚠️ No valid units found!");
                BetterMineTeam.LOGGER.warn("[RTS-SERVER] Possible reasons:");
                BetterMineTeam.LOGGER.warn("[RTS-SERVER]   1. Entities not in player's team");
                BetterMineTeam.LOGGER.warn("[RTS-SERVER]   2. Entities not owned by player (if tamable)");
                BetterMineTeam.LOGGER.warn("[RTS-SERVER]   3. Entities don't exist on server");
                sendAck(player, false, 0, Component.translatable("better_mine_team.msg.cmd_no_units").withStyle(ChatFormatting.RED));
                return;
            }

            // 执行指令
            switch (payload.commandType()) {
                case MOVE -> executeMoveCommand(validUnits, payload.target().pos());
                case ATTACK -> executeAttackCommand(validUnits, level, payload.target().targetEntityId());
                case STOP -> executeStopCommand(validUnits);
            }

            BetterMineTeam.LOGGER.info("[RTS-SERVER] ✅ Command executed successfully");
            BetterMineTeam.LOGGER.info("========================================");

            sendAck(player, true, successCount, Component.translatable("better_mine_team.msg.cmd_ack", successCount).withStyle(ChatFormatting.GREEN));
        });
    }

    private static void executeAttackCommand(List<Mob> units, Level level, int primaryTargetId, List<Integer> secondaryTargetIds) {
        // 1. 收集所有目标 (主 + 副)
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
        // 这一步告诉 TeamManager：这些目标是敌人
        for (Mob unit : units) {
            PlayerTeam unitTeam = TeamManager.getTeam(unit);
            if (unitTeam == null) continue;

            for (Entity target : allTargets) {
                if (!(target instanceof LivingEntity livingTarget)) continue;
                if (TeamManager.isAlly(unit, livingTarget)) continue; // 不打自己人

                PlayerTeam targetTeam = TeamManager.getTeam(target);

                // 情况 A: 目标有队伍 -> 触发全队 vs 全队混战
                if (targetTeam != null) {
                    TeamManager.scanAndAddThreats(unitTeam, targetTeam, livingTarget);
                    // [可选] 同时也让对方知道我们在打他们 (双向宣战)，让 AI 反应更快
                    TeamManager.scanAndAddThreats(targetTeam, unitTeam, unit);
                }
                // 情况 B: 目标无队伍 -> 仅标记该个体
                else {
                    TeamManager.addThreat(unitTeam, livingTarget);
                }
            }
        }

        // 3. 分配攻击目标
        // 目前策略：所有选中的单位优先攻击主目标，主目标死后 AI 会自动找列表里的其他人
        // 或者：如果目标很多，可以做智能分配（这里暂时保持简单：集火主目标）

        if (primaryTarget != null) {
            for (Mob unit : units) {
                if (unit == primaryTarget) continue;
                if (TeamManager.isAlly(unit, primaryTarget instanceof LivingEntity l ? l : null)) continue;

                RTSUnitAIController.setAttackTarget(unit, primaryTarget);
            }
        } else if (!allTargets.isEmpty()) {
            // 如果主目标无效（比如框选了一群但没点中特定一个），随便选一个作为起手目标
            Entity fallbackTarget = allTargets.get(0);
            for (Mob unit : units) {
                if (!TeamManager.isAlly(unit, fallbackTarget instanceof LivingEntity l ? l : null)) {
                    RTSUnitAIController.setAttackTarget(unit, fallbackTarget);
                }
            }
        }
    }

    // 简单的权限校验逻辑
    private static boolean isValidController(ServerPlayer player, PlayerTeam playerTeam, Mob mob) {
        // 1. 检查 Team
        /*
        BetterMineTeam.LOGGER.warn("[RTS-SERVER] ⚠️ Using TEMPORARY bypass - all mobs are valid!");
        return true;

         */
        PlayerTeam mobTeam = TeamManager.getTeam(mob);
        if (playerTeam != null && mobTeam != null && playerTeam.getName().equals(mobTeam.getName())) {
            return true;
        }

        // 2. 检查 Owner (如果是可驯服生物)
        if (mob instanceof net.minecraft.world.entity.TamableAnimal tamable) {
            return tamable.isOwnedBy(player);
        }
        return false;
    }

    // === 核心：阵型移动 ===
    private static void executeMoveCommand(List<Mob> units, Vec3 centerTarget) {
        int count = units.size();

        BetterMineTeam.debug("[RTS-MOVE-CMD] Executing for {} units to {}", count, centerTarget);

        if (count == 0) return;

        // 如果只有一个单位，直接走
        if (count == 1) {
            Mob unit = units.get(0);
            BetterMineTeam.debug("[RTS-MOVE-CMD] Single unit: {}", unit.getName().getString());
            RTSUnitAIController.setMoveTarget(unit, centerTarget);
            return;
        }

        // 简单的网格阵型
        int cols = (int) Math.ceil(Math.sqrt(count));
        double spacing = 2.0;

        BetterMineTeam.debug("[RTS-MOVE-CMD] Formation: {} units in {} columns", count, cols);

        for (int i = 0; i < count; i++) {
            Mob unit = units.get(i);

            int row = i / cols;
            int col = i % cols;

            double offsetX = (col - (cols - 1) / 2.0) * spacing;
            double offsetZ = (row - (cols - 1) / 2.0) * spacing;

            Vec3 unitTarget = centerTarget.add(offsetX, 0, offsetZ);

            BetterMineTeam.debug("[RTS-MOVE-CMD]   Unit[{}] {}: offset=({}, {}), target={}",
                    i,
                    unit.getName().getString(),
                    offsetX, offsetZ,
                    unitTarget);

            RTSUnitAIController.setMoveTarget(unit, unitTarget);
        }
    }

    private static void executeAttackCommand(List<Mob> units, Level level, int targetId) {
        Entity target = level.getEntity(targetId);

        BetterMineTeam.debug("[RTS-ATTACK-CMD] Executing for {} units, targetId={}, found={}",
                units.size(),
                targetId,
                target != null);

        if (target == null) return;

        for (Mob unit : units) {
            if (unit == target) continue;
            if (TeamManager.isAlly(unit, target instanceof net.minecraft.world.entity.LivingEntity l ? l : null)) continue;

            RTSUnitAIController.setAttackTarget(unit, target);
        }
    }

    private static void executeStopCommand(List<Mob> units) {
        BetterMineTeam.debug("[RTS-STOP-CMD] Executing for {} units", units.size());

        for (Mob unit : units) {
            RTSUnitAIController.stop(unit);
        }
    }

    private static void sendAck(ServerPlayer player, boolean success, int count, Component msg) {
        PacketDistributor.sendToPlayer(player, new S2C_CommandAckPayload(success, count, msg));
    }
}