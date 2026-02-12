package com.i113w.better_mine_team.common.rts.ai;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.registry.ModAttachments;
import com.i113w.better_mine_team.common.rts.data.RTSUnitData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;

public class RTSUnitAIController {

    public static void setMoveTarget(Mob mob, Vec3 pos) {
        // ✅ 直接用 getData，不检查 hasData
        RTSUnitData data = mob.getData(ModAttachments.UNIT_DATA);

        BetterMineTeam.debug("[RTS-CONTROLLER] 📍 setMoveTarget for {}: pos={}, currentCommand={}",
                mob.getName().getString(),
                pos,
                data.getCommand());

        data.setMoveCommand(pos);

        // 清除驯服动物的坐姿状态
        if (mob instanceof TamableAnimal tamable) {
            boolean wasSitting = tamable.isInSittingPose() || tamable.isOrderedToSit();
            tamable.setInSittingPose(false);
            tamable.setOrderedToSit(false);

            if (wasSitting) {
                BetterMineTeam.debug("[RTS-CONTROLLER] ✅ Cleared sitting state for {}",
                        mob.getName().getString());
            }
        }

        // ✅ 不在这里调用 moveTo，让 RTSMoveGoal.start() 统一控制
        mob.setTarget(null);
        mob.getNavigation().stop();

        BetterMineTeam.debug("[RTS-CONTROLLER] ✅ Move command set for {}, command is now: {}",
                mob.getName().getString(),
                data.getCommand());
    }

    public static void setAttackTarget(Mob mob, Entity target) {
        RTSUnitData data = mob.getData(ModAttachments.UNIT_DATA);

        BetterMineTeam.debug("[RTS-CONTROLLER] 🎯 setAttackTarget for {}: targetId={}, targetName={}",
                mob.getName().getString(),
                target.getId(),
                target.getName().getString());

        data.setAttackCommand(target.getId());

        if (target instanceof net.minecraft.world.entity.LivingEntity living) {
            mob.setTarget(living);
        }

        if (mob instanceof TamableAnimal tamable) {
            tamable.setInSittingPose(false);
            tamable.setOrderedToSit(false);
        }

        mob.getNavigation().stop();

        BetterMineTeam.debug("[RTS-CONTROLLER] ✅ Attack command set for {}",
                mob.getName().getString());
    }

    public static void stop(Mob mob) {
        RTSUnitData data = mob.getData(ModAttachments.UNIT_DATA);

        BetterMineTeam.debug("[RTS-CONTROLLER] ⛔ STOP command for {}",
                mob.getName().getString());

        data.stop();
        mob.getNavigation().stop();
        mob.setTarget(null);
    }
}