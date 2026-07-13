package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.config.PatrolSettings;
import com.i113w.better_mine_team.common.network.data.CommandTarget;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.network.data.PatrolAction;
import com.i113w.better_mine_team.common.network.rts.C2S_IssueCommandPacket;
import com.i113w.better_mine_team.common.network.rts.C2S_PatrolCommandPacket;
import com.i113w.better_mine_team.common.network.rts.C2S_SelectionSyncPacket;
import com.i113w.better_mine_team.common.rts.ai.PatrolTargeting;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.camera_lib.api.CameraLibAPI;
import com.i113w.camera_lib.api.event.RTSBoxSelectEvent;
import com.i113w.camera_lib.api.event.RTSRightClickEvent;
import com.i113w.camera_lib.camera.RTSCameraController;
import com.i113w.camera_lib.math.MouseRayCaster;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RTSLibEventHandler {
    private static BlockPos patrolDragStart;
    private static BlockPos patrolDragCurrent;
    private static boolean patrolRightDragging;

    @SubscribeEvent
    public static void onBoxSelect(RTSBoxSelectEvent event) {
        if (!RTSCameraController.get().isActive()) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        ClientRTSStateManager.RTSMode mode = ClientRTSStateManager.get().getMode();
        if (mode == ClientRTSStateManager.RTSMode.PATROL) {
            handlePatrolSelection(event);
            return;
        }

        PlayerTeam playerTeam = TeamManager.getTeam(player);
        List<Integer> validIds = new ArrayList<>();
        for (Entity entity : event.getCandidates()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (mode == ClientRTSStateManager.RTSMode.RECRUIT) {
                if (TeamManager.getTeam(living) == null) validIds.add(entity.getId());
            } else {
                PlayerTeam entityTeam = TeamManager.getTeam(living);
                boolean sameTeam = playerTeam != null && entityTeam != null
                        && playerTeam.getName().equals(entityTeam.getName());
                boolean owned = living instanceof TamableAnimal tamable && tamable.isOwnedBy(player);
                if (sameTeam || owned) validIds.add(entity.getId());
            }
        }
        setSelectionAndSync(new HashSet<>(validIds));
    }

    @SubscribeEvent
    public static void onRightClick(RTSRightClickEvent event) {
        if (!RTSCameraController.get().isActive()) return;
        ClientRTSStateManager.RTSMode mode = ClientRTSStateManager.get().getMode();
        if (mode == ClientRTSStateManager.RTSMode.PATROL) {
            handlePatrolRightClick(event);
            return;
        }

        Set<Integer> selected = CameraLibAPI.get().getSelectedEntities();
        if (selected.isEmpty()) return;
        int revision = ClientRTSStateManager.get().getRevision();
        if (mode == ClientRTSStateManager.RTSMode.RECRUIT) {
            MTNetworkRegister.CHANNEL.sendToServer(new C2S_IssueCommandPacket(
                    CommandType.RECRUIT, CommandTarget.EMPTY, Collections.emptyList(), revision));
            CameraLibAPI.get().clearSelection();
            return;
        }

        if (event.isDrag()) {
            List<Integer> targets = event.getDragTargets().stream().map(Entity::getId).toList();
            if (!targets.isEmpty()) {
                CommandTarget primary = new CommandTarget(Vec3.ZERO, targets.get(0), BlockPos.ZERO);
                List<Integer> secondary = targets.size() > 1
                        ? targets.subList(1, targets.size()) : Collections.emptyList();
                MTNetworkRegister.CHANNEL.sendToServer(new C2S_IssueCommandPacket(
                        CommandType.ATTACK, primary, secondary, revision));
            }
            return;
        }

        HitResult hit = event.getSingleHitResult();
        if (hit == null || hit.getType() == HitResult.Type.MISS) return;
        CommandType type;
        CommandTarget target;
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            type = BMTInteractionDelegate.isEnemyLike(entity) ? CommandType.ATTACK : CommandType.MOVE;
            target = new CommandTarget(entity.position(), entity.getId(), entity.blockPosition());
        } else if (hit instanceof BlockHitResult blockHit) {
            type = CommandType.MOVE;
            target = new CommandTarget(hit.getLocation(), -1, blockHit.getBlockPos());
        } else {
            return;
        }
        MTNetworkRegister.CHANNEL.sendToServer(new C2S_IssueCommandPacket(type, target,
                Collections.emptyList(), revision));
    }

    private static void handlePatrolSelection(RTSBoxSelectEvent event) {
        if (!ClientPatrolSettings.get().enabled()) return;
        List<Entity> candidates = event.getCandidates().stream().filter(RTSLibEventHandler::isPatrolSelectable).toList();
        Set<Integer> selected = new HashSet<>(CameraLibAPI.get().getSelectedEntities());
        if (Screen.hasShiftDown() && candidates.size() == 1) {
            int id = candidates.get(0).getId();
            if (!selected.add(id)) selected.remove(id);
        } else {
            selected.clear();
            candidates.forEach(entity -> selected.add(entity.getId()));
        }
        setSelectionAndSync(selected);
    }

    private static void handlePatrolRightClick(RTSRightClickEvent event) {
        if (!ClientPatrolSettings.get().enabled()) { resetPatrolDragState(); return; }
        if (event.isDrag()) {
            try {
                BlockPos end = getCurrentPatrolGroundPos();
                if (end != null) patrolDragCurrent = end;
                if (patrolRightDragging && patrolDragStart != null && patrolDragCurrent != null
                        && !patrolDragStart.equals(patrolDragCurrent)
                        && !CameraLibAPI.get().getSelectedEntities().isEmpty()) {
                    sendPatrolArea(new ArrayList<>(CameraLibAPI.get().getSelectedEntities()),
                            patrolDragStart, patrolDragCurrent);
                }
            } finally {
                resetPatrolDragState();
            }
            return;
        }

        resetPatrolDragState();
        Set<Integer> selected = CameraLibAPI.get().getSelectedEntities();
        if (selected.isEmpty() || !(event.getSingleHitResult() instanceof BlockHitResult blockHit)) return;
        BlockPos target = getStandPos(blockHit);
        List<Integer> cancelIds = selected.stream()
                .filter(id -> ClientPatrolManager.get(id).map(task -> task.contains(target)).orElse(false))
                .toList();
        if (!cancelIds.isEmpty()) sendPatrolCancel(cancelIds);
        else sendPatrolPoint(new ArrayList<>(selected), target);
    }

    private static boolean isPatrolSelectable(Entity entity) {
        if (!ClientPatrolSettings.get().enabled()) return false;
        Player player = Minecraft.getInstance().player;
        if (player == null || !(entity instanceof PathfinderMob mob) || !mob.isAlive() || entity == player) return false;
        return TeamManager.isAlly(player, mob)
                || entity instanceof TamableAnimal tamable && tamable.isOwnedBy(player);
    }

    private static void setSelectionAndSync(Set<Integer> selected) {
        CameraLibAPI.get().setSelectedEntities(selected);
        int revision = ClientRTSStateManager.get().getNextRevision();
        MTNetworkRegister.CHANNEL.sendToServer(new C2S_SelectionSyncPacket(new ArrayList<>(selected), revision));
    }

    private static void sendPatrolPoint(List<Integer> entityIds, BlockPos target) {
        Minecraft mc = Minecraft.getInstance();
        PatrolSettings settings = ClientPatrolSettings.get();
        if (mc.level == null || mc.player == null || !settings.enabled()) return;
        if (!isWithinPatrolCommandDistance(mc.player.position(), settings, target)) {
            mc.player.displayClientMessage(Component.translatable("better_mine_team.msg.patrol_too_far")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        MTNetworkRegister.CHANNEL.sendToServer(new C2S_PatrolCommandPacket(PatrolAction.ASSIGN_POINT, entityIds,
                mc.level.dimension().location().toString(), target, settings.pointRadius(), target, target));
    }

    private static void sendPatrolArea(List<Integer> entityIds, BlockPos start, BlockPos end) {
        Minecraft mc = Minecraft.getInstance();
        PatrolSettings settings = ClientPatrolSettings.get();
        if (mc.level == null || mc.player == null || !settings.enabled()) return;
        BlockPos rawMin = PatrolTargeting.normalizedMin(start, end);
        BlockPos rawMax = PatrolTargeting.normalizedMax(start, end);
        int y = start.getY();
        BlockPos min = new BlockPos(rawMin.getX(), y, rawMin.getZ());
        BlockPos max = new BlockPos(rawMax.getX(), y, rawMax.getZ());
        if (!PatrolTargeting.isAreaSizeValid(min, max, settings)) {
            mc.player.displayClientMessage(Component.translatable("better_mine_team.msg.patrol_invalid_area",
                            PatrolTargeting.areaWidth(min, max), PatrolTargeting.areaDepth(min, max),
                            settings.minAreaSize(), settings.maxAreaSize())
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        BlockPos minMax = new BlockPos(min.getX(), y, max.getZ());
        BlockPos maxMin = new BlockPos(max.getX(), y, min.getZ());
        if (!isWithinPatrolCommandDistance(mc.player.position(), settings, min, max, minMax, maxMin)) {
            mc.player.displayClientMessage(Component.translatable("better_mine_team.msg.patrol_too_far")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        BlockPos center = new BlockPos((min.getX() + max.getX()) / 2, y, (min.getZ() + max.getZ()) / 2);
        MTNetworkRegister.CHANNEL.sendToServer(new C2S_PatrolCommandPacket(PatrolAction.ASSIGN_AREA, entityIds,
                mc.level.dimension().location().toString(), center, 0, min, max));
    }

    private static void sendPatrolCancel(List<Integer> entityIds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MTNetworkRegister.CHANNEL.sendToServer(new C2S_PatrolCommandPacket(PatrolAction.CANCEL, entityIds,
                mc.level.dimension().location().toString(), BlockPos.ZERO, 0, BlockPos.ZERO, BlockPos.ZERO));
    }

    private static BlockPos getStandPos(BlockHitResult hit) {
        return hit.getBlockPos().relative(hit.getDirection());
    }

    private static BlockPos getCurrentPatrolGroundPos() {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = MouseRayCaster.pickFromMouse(mc.mouseHandler.xpos(), mc.mouseHandler.ypos(),
                ClientPatrolSettings.get().maxCommandDistance());
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                ? getStandPos(blockHit) : null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!RTSCameraController.get().isActive()
                || ClientRTSStateManager.get().getMode() != ClientRTSStateManager.RTSMode.PATROL) {
            resetPatrolDragState();
            return;
        }
        if (patrolRightDragging) {
            BlockPos current = getCurrentPatrolGroundPos();
            if (current != null) patrolDragCurrent = current;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPatrolMouseClick(InputEvent.MouseButton.Pre event) {
        if (!RTSCameraController.get().isActive()
                || ClientRTSStateManager.get().getMode() != ClientRTSStateManager.RTSMode.PATROL
                || !ClientPatrolSettings.get().enabled()
                || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        if (event.getAction() == GLFW.GLFW_PRESS) {
            patrolDragStart = getCurrentPatrolGroundPos();
            patrolDragCurrent = patrolDragStart;
            patrolRightDragging = patrolDragStart != null;
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            resetPatrolDragState();
        }
    }

    public static void resetPatrolDragState() {
        patrolDragStart = null;
        patrolDragCurrent = null;
        patrolRightDragging = false;
    }

    static boolean isPatrolRightDragging() { return patrolRightDragging; }
    static BlockPos getPatrolDragStart() { return patrolDragStart; }
    static BlockPos getPatrolDragCurrent() { return patrolDragCurrent; }

    private static boolean isWithinPatrolCommandDistance(Vec3 origin, PatrolSettings settings, BlockPos... positions) {
        for (BlockPos pos : positions) {
            if (origin.distanceToSqr(Vec3.atCenterOf(pos)) > settings.maxCommandDistanceSqr()) return false;
        }
        return true;
    }
}
