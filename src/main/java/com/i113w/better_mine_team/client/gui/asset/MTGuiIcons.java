package com.i113w.better_mine_team.client.gui.asset;

import com.i113w.better_mine_team.BetterMineTeam;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public enum MTGuiIcons {
    // --- 0. 按钮底座 (20x20) ---
    BUTTON_NORMAL(0, 0, 20, 20),
    BUTTON_HOVER(21, 0, 20, 20),
    BUTTON_PRESSED(42, 0, 20, 20),
    BUTTON_DISABLED(63, 0, 20, 20),

    // --- 1. 功能图标 (16x16) ---
    ICON_INVENTORY(0, 21, 16, 16),
    ICON_TELEPORT(17, 21, 16, 16),
    ICON_FOLLOW_OFF(34, 21, 16, 16),
    ICON_FOLLOW_ON(51, 21, 16, 16),
    ICON_KICK(68, 21, 16, 16),
    ICON_RENAME(85, 21, 16, 16),
    ICON_CAPTAIN(102, 21, 16, 16),
    ICON_RTS(119, 21, 16, 16),

    // 锁定图标 (16x16)
    ICON_LOCKED_INVENTORY(136, 21, 16, 16),

    // --- 2. 滚动条组件 ---
    SCROLL_TRACK(0, 40, 12, 152),
    SCROLL_THUMB(13, 40, 12, 15);

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "textures/gui/icons.png");

    public final int u;
    public final int v;
    public final int width;
    public final int height;

    MTGuiIcons(int u, int v, int width, int height) {
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
    }

    // 1. 标准渲染
    public void render(GuiGraphics gfx, int x, int y) {
        gfx.blit(TEXTURE, x, y, u, v, width, height);
    }

    // 2. [补回] 自定义高度渲染 (用于滚动条 TeamMemberList)
    // 参数: gfx, x, y, customHeight
    public void render(GuiGraphics gfx, int x, int y, int customHeight) {
        // 保持宽度不变(this.width)，高度使用 customHeight
        // 采样时依然采样完整的原始高度(this.height)，让 OpenGL 处理拉伸
        gfx.blit(TEXTURE, x, y, width, customHeight, u, v, width, height, 256, 256);
    }

    // 3. 自定义宽高渲染 (用于缩放图标)
    public void render(GuiGraphics gfx, int x, int y, int targetWidth, int targetHeight) {
        gfx.blit(TEXTURE, x, y, targetWidth, targetHeight, u, v, width, height, 256, 256);
    }

    // 4. 带颜色的渲染
    public void render(GuiGraphics gfx, int x, int y, float r, float g, float b, float a) {
        RenderSystem.setShaderColor(r, g, b, a);
        gfx.blit(TEXTURE, x, y, u, v, width, height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}