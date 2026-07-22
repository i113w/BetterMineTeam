package com.i113w.better_mine_team.client.gui.asset;

import com.i113w.better_mine_team.BetterMineTeam;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public enum MTGuiIcons {
    // --- 0. 按钮底座 (20x20) ---
    BUTTON_NORMAL(0, 0, 20, 20),
    BUTTON_HOVER(21, 0, 20, 20),
    BUTTON_PRESSED(42, 0, 20, 20),
    BUTTON_DISABLED(63, 0, 20, 20),

    // --- 1. 功能图标 (16x16) ---
    ICON_INVENTORY(0, 21, 16, 16),
    ICON_FOLLOW_OFF(17, 21, 16, 16),
    ICON_FOLLOW_ON(34, 21, 16, 16),
    ICON_KICK(51, 21, 16, 16),
    ICON_CAPTAIN(68, 21, 16, 16),
    ICON_RTS(85, 21, 16, 16),
    ICON_LOCKED_INVENTORY(102, 21, 16, 16),
    ICON_LEVEL_0(119, 21, 16, 16), // 被动 (Passive)
    ICON_LEVEL_1(136, 21, 16, 16), // 警戒 (Guard)
    ICON_LEVEL_2(153, 21, 16, 16), // 侵略 (Aggressive)
    ICON_PERSONAL_TEAM_OFF(170, 21, 16, 16),
    ICON_PERSONAL_TEAM_ON(187, 21, 16, 16),

    // --- 2. 滚动条组件 ---
    SCROLL_TRACK(0, 40, 12, 152),
    SCROLL_THUMB(13, 40, 12, 15);

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            BetterMineTeam.MODID,
            "textures/gui/icons.png"
    );

    private static final int TEXTURE_SIZE = 256;
    private static final int WHITE_ARGB = 0xFFFFFFFF;

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
    public void render(GuiGraphicsExtractor gfx, int x, int y) {
        gfx.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                x,
                y,
                u,
                v,
                width,
                height,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                WHITE_ARGB
        );
    }

    // 2. 自定义高度渲染 (用于滚动条 TeamMemberList)
    public void render(GuiGraphicsExtractor gfx, int x, int y, int customHeight) {
        gfx.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                x,
                y,
                u,
                v,
                width,
                customHeight,
                width,
                height,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                WHITE_ARGB
        );
    }

    // 3. 自定义宽高渲染 (用于缩放图标)
    public void render(GuiGraphicsExtractor gfx, int x, int y, int targetWidth, int targetHeight) {
        gfx.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                x,
                y,
                u,
                v,
                targetWidth,
                targetHeight,
                width,
                height,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                WHITE_ARGB
        );
    }

    // 4. 带颜色的渲染
    public void render(GuiGraphicsExtractor gfx, int x, int y, float r, float g, float b, float a) {
        int color = ARGB.colorFromFloat(a, r, g, b);
        gfx.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                x,
                y,
                u,
                v,
                width,
                height,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                color
        );
    }
}
