package com.i113w.better_mine_team.client.rts;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.network.data.CommandTarget;
import com.i113w.better_mine_team.common.network.data.CommandType;
import com.i113w.better_mine_team.common.network.rts.C2S_IssueCommandPacket;
import com.i113w.better_mine_team.common.network.rts.C2S_SelectionSyncPacket;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.camera_lib.api.CameraLibAPI;
import com.i113w.camera_lib.api.event.RTSBoxSelectEvent;
import com.i113w.camera_lib.api.event.RTSRightClickEvent;
import com.i113w.camera_lib.camera.RTSCameraController;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@Mod.EventBusSubscriber(modid = BetterMineTeam.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RTSLibEventHandler {

    @SubscribeEvent
    public static void onBoxSelect(RTSBoxSelectEvent event) {
        if (!RTSCameraController.get().isActive()) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        ClientRTSStateManager.RTSMode mode = ClientRTSStateManager.get().getMode();
        PlayerTeam playerTeam = TeamManager.getTeam(player);

        List<Integer> validIds = new ArrayList<>();

        for (Entity e : event.getCandidates()) {
            if (!(e instanceof LivingEntity living)) continue;

            if (mode == ClientRTSStateManager.RTSMode.RECRUIT) {
                // 招募模式下，仅能选中无阵营单位
                if (TeamManager.getTeam(living) == null) {
                    validIds.add(e.getId());
                }
            } else {
                // 指挥模式下，仅能选中己方单位或已驯服宠物
                PlayerTeam entityTeam = TeamManager.getTeam(living);
                boolean isSameTeam = playerTeam != null && entityTeam != null && playerTeam.getName().equals(entityTeam.getName());
                boolean isOwned = living instanceof TamableAnimal tamable && tamable.isOwnedBy(player);

                if (isSameTeam || isOwned) {
                    validIds.add(e.getId());
                }
            }
        }

        // 将最终结果写回 Lib
        CameraLibAPI.get().setSelectedEntities(new HashSet<>(validIds));

        // 发送到服务端同步
        int rev = ClientRTSStateManager.get().getNextRevision();
        MTNetworkRegister.CHANNEL.sendToServer(new C2S_SelectionSyncPacket(validIds, rev));
    }

    @SubscribeEvent
    public static void onRightClick(RTSRightClickEvent event) {
        if (!RTSCameraController.get().isActive() || CameraLibAPI.get().getSelectedEntities().isEmpty()) return;

        ClientRTSStateManager.RTSMode mode = ClientRTSStateManager.get().getMode();
        int rev = ClientRTSStateManager.get().getRevision();

        if (mode == ClientRTSStateManager.RTSMode.RECRUIT) {
            MTNetworkRegister.CHANNEL.sendToServer(new C2S_IssueCommandPacket(
                    CommandType.RECRUIT, CommandTarget.EMPTY, Collections.emptyList(), rev));
            CameraLibAPI.get().clearSelection();
            return;
        }

        // 框选攻击
        if (event.isDrag()) {
            List<Integer> targets = event.getDragTargets().stream().map(Entity::getId).toList();
            if (!targets.isEmpty()) {
                CommandTarget primary = new CommandTarget(Vec3.ZERO, targets.get(0), BlockPos.ZERO);
                List<Integer> secondary = targets.size() > 1 ? targets.subList(1, targets.size()) : Collections.emptyList();
                MTNetworkRegister.CHANNEL.sendToServer(new C2S_IssueCommandPacket(
                        CommandType.ATTACK, primary, secondary, rev));
            }
        }
        // 单点点击
        else {
            HitResult hit = event.getSingleHitResult();
            if (hit == null || hit.getType() == HitResult.Type.MISS) return;

            CommandType type;
            CommandTarget target;

            if (hit.getType() == HitResult.Type.ENTITY) {
                Entity entity = ((EntityHitResult) hit).getEntity();
                type = BMTInteractionDelegate.isEnemyLike(entity) ? CommandType.ATTACK : CommandType.MOVE;
                target = new CommandTarget(entity.position(), entity.getId(), entity.blockPosition());
            } else {
                BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                type = CommandType.MOVE;
                target = new CommandTarget(hit.getLocation(), -1, pos);
            }

            MTNetworkRegister.CHANNEL.sendToServer(new C2S_IssueCommandPacket(
                    type, target, Collections.emptyList(), rev));
        }
    }
}
