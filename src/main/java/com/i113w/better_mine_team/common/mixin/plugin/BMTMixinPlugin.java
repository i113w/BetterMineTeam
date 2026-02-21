package com.i113w.better_mine_team.common.mixin.plugin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class BMTMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Carry On 兼容 Mixin 仅在 Carry On 加载时应用
        if (mixinClassName.contains("com.i113w.better_mine_team.common.mixin.compat.carryon")) {
            return LoadingModList.get().getModFileById("carryon") != null;
        }
        return true;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String t, ClassNode c, String m, IMixinInfo i) {}
    @Override public void postApply(String t, ClassNode c, String m, IMixinInfo i) {}
}
