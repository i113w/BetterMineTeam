package com.i113w.better_mine_team.client.gui.component;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.asset.MTGuiIcons;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class TeamMemberList extends ObjectSelectionList<TeamMemberEntry> {

    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "textures/gui/icons.png");

    private final int guiLeft;
    private final int guiTop;
    private final int listWidth = 150;
    private final int listHeight = 152;

    public TeamMemberList(Minecraft mc, int guiLeft, int guiTop) {
        super(mc, 150, 152, guiTop + 7, TeamMemberEntry.ITEM_HEIGHT);
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        this.setX(guiLeft + 7);
        this.setY(guiTop + 7);
    }

    @Override
    public int getRowWidth() {
        return this.listWidth;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.guiLeft + 157 + 6;
    }

    // 修复点：访问权限改为 public
    @Override
    public void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int listX = this.getX();
        int listY = this.getY();

        gfx.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int itemCount = this.getItemCount();
        for (int i = 0; i < itemCount; ++i) {
            int itemTop = listY + i * TeamMemberEntry.ITEM_HEIGHT - (int) this.getScrollAmount();
            int itemBottom = itemTop + TeamMemberEntry.ITEM_HEIGHT;

            if (itemBottom >= listY && itemTop <= listY + listHeight) {
                TeamMemberEntry entry = this.getEntry(i);
                boolean isHovered = this.isMouseOver(mouseX, mouseY) && entry.equals(this.getHovered());
                entry.render(gfx, i, itemTop, listX, this.getRowWidth(), TeamMemberEntry.ITEM_HEIGHT, mouseX, mouseY, isHovered, partialTick);
            }
        }

        gfx.disableScissor();
        renderCustomScrollbar(gfx);
    }

    private void renderCustomScrollbar(GuiGraphics gfx) {
        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarX = this.guiLeft + 157;
            int scrollbarY = this.guiTop + 7;
            int scrollbarHeight = 152;

            RenderSystem.enableBlend();

            // 1. 使用枚举渲染背景槽
            MTGuiIcons.SCROLL_TRACK.render(gfx, scrollbarX, scrollbarY);

            // 2. 计算滑块
            int totalContentHeight = this.getItemCount() * TeamMemberEntry.ITEM_HEIGHT;
            int thumbHeight = (int) ((float) (scrollbarHeight * scrollbarHeight) / totalContentHeight);
            thumbHeight = Mth.clamp(thumbHeight, 15, scrollbarHeight);

            double scrollRatio = this.getScrollAmount() / (double) maxScroll;
            int thumbY = (int) (scrollRatio * (scrollbarHeight - thumbHeight)) + scrollbarY;

            // 3. 使用枚举渲染滑块 (注意：这里需要支持自定义高度)
            // 我们在 MTGuiIcons 里加了一个重载的 render(gfx, x, y, customHeight)
            MTGuiIcons.SCROLL_THUMB.render(gfx, scrollbarX, thumbY, thumbHeight);

            RenderSystem.disableBlend();
        }
    }


    public void clearMembers() {
        this.clearEntries();
    }

    public void addMember(TeamMemberEntry entry) {
        this.addEntry(entry);
    }
}