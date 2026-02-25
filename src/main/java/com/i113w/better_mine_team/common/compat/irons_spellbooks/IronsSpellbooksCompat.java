package com.i113w.better_mine_team.common.compat.irons_spellbooks;

import com.i113w.better_mine_team.BetterMineTeam;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IronsSpellbooksCompat {

    // 记录是否是新版 API
    private static Boolean isNewApi = null;

    @Nullable
    public static LivingEntity getSummonOwner(Entity summon) {
        if (isNewApi == null) {
            try {
                // 尝试寻找新版才有的核心管理类
                Class.forName("io.redspace.ironsspellbooks.capabilities.magic.SummonManager");
                isNewApi = true;
                BetterMineTeam.debug("[ISS-Compat] Detected Iron's Spells 'n Spellbooks (New API: SummonManager)");
            } catch (ClassNotFoundException e) {
                isNewApi = false;
                BetterMineTeam.debug("[ISS-Compat] Detected Iron's Spells 'n Spellbooks (Old API: getSummoner)");
            }
        }

        if (isNewApi) {
            return NewApiHandler.getOwner(summon);
        } else {
            return OldApiHandler.getOwner(summon);
        }
    }

    private static class NewApiHandler {
        @Nullable
        static LivingEntity getOwner(Entity summon) {
            // 直接调用新版接口
            Entity owner = io.redspace.ironsspellbooks.capabilities.magic.SummonManager.getOwner(summon);
            if (owner instanceof LivingEntity living) {
                return living;
            }
            return null;
        }
    }

    private static class OldApiHandler {
        // 方法缓存：记录哪些类拥有 getSummoner() 方法
        private static final Map<Class<?>, Method> METHOD_CACHE = new ConcurrentHashMap<>();
        // 一个空标记方法，用于表示“此类没有 getSummoner 方法”，避免重复反射抛异常
        private static final Method NULL_METHOD;

        static {
            try {
                NULL_METHOD = OldApiHandler.class.getDeclaredMethod("dummyMarker");
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        private static void dummyMarker() {}

        @Nullable
        static LivingEntity getOwner(Entity summon) {
            Class<?> clazz = summon.getClass();

            // 计算或获取缓存的方法
            Method method = METHOD_CACHE.computeIfAbsent(clazz, k -> {
                try {
                    return k.getMethod("getSummoner");
                } catch (NoSuchMethodException e) {
                    return NULL_METHOD;
                }
            });

            // 如果是原版僵尸等无该方法的实体，直接返回
            if (method == NULL_METHOD) {
                return null;
            }

            try {
                Object result = method.invoke(summon);
                if (result instanceof LivingEntity living) {
                    return living;
                }
            } catch (Exception e) {
                BetterMineTeam.LOGGER.warn("[ISS-Compat] Failed to invoke getSummoner on old API", e);
            }
            return null;
        }
    }
}