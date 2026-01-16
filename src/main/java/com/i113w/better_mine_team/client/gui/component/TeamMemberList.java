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

    private static final ResourceLocation ICONS = new ResourceLocation(BetterMineTeam.MODID, "textures/gui/icons.png");

    private final int listWidth = 150;
    private final int listHeight = 152;

    public TeamMemberList(Minecraft mc, int guiLeft, int guiTop) {
        super(
                mc,
                150,
                152,
                guiTop + 7,
                guiTop + 7 + 152,
                TeamMemberEntry.ITEM_HEIGHT
        );
    }

    @Override
    public int getRowWidth() {
        return this.listWidth;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getRowRight() + 6;
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int left = this.getRowLeft();
        int right = this.getRowRight();
        int top = this.getTop();
        int bottom = this.getBottom();

        gfx.enableScissor(left, top, right, bottom);

        for (int i = 0; i < this.getItemCount(); ++i) {
            int itemTop = top + i * TeamMemberEntry.ITEM_HEIGHT - (int) this.getScrollAmount();
            int itemBottom = itemTop + TeamMemberEntry.ITEM_HEIGHT;

            if (itemBottom >= top && itemTop <= bottom) {
                TeamMemberEntry entry = this.getEntry(i);
                boolean hovered = entry.equals(this.getHovered());

                entry.render(
                        gfx,
                        i,
                        itemTop,
                        left,
                        this.getRowWidth(),
                        TeamMemberEntry.ITEM_HEIGHT,
                        mouseX,
                        mouseY,
                        hovered,
                        partialTick
                );
            }
        }

        gfx.disableScissor();
        renderCustomScrollbar(gfx);
    }


    private void renderCustomScrollbar(GuiGraphics gfx) {
        int maxScroll = this.getMaxScroll();
        if (maxScroll <= 0) return;

        int scrollbarX = this.getRowRight() + 6;
        int scrollbarY = this.getTop();
        int scrollbarHeight = this.getBottom() - this.getTop();

        RenderSystem.enableBlend();

        MTGuiIcons.SCROLL_TRACK.render(gfx, scrollbarX, scrollbarY);

        int totalContentHeight = this.getItemCount() * TeamMemberEntry.ITEM_HEIGHT;
        int thumbHeight = (int) ((float) (scrollbarHeight * scrollbarHeight) / totalContentHeight);
        thumbHeight = Mth.clamp(thumbHeight, 15, scrollbarHeight);

        double scrollRatio = this.getScrollAmount() / (double) maxScroll;
        int thumbY = (int) (scrollRatio * (scrollbarHeight - thumbHeight)) + scrollbarY;

        MTGuiIcons.SCROLL_THUMB.render(gfx, scrollbarX, thumbY, thumbHeight);

        RenderSystem.disableBlend();
    }



    public void clearMembers() {
        this.clearEntries();
    }

    public void addMember(TeamMemberEntry entry) {
        this.addEntry(entry);
    }

    //暴露 removeEntry (如果父类是 protected)
    public boolean removeEntry(TeamMemberEntry entry) {
        return super.removeEntry(entry);
    }

    //暴露 children 列表供排序使用
    public java.util.List<TeamMemberEntry> getEntries() {
        return super.children();
    }


    public int getListRight() {
        return this.getRowLeft() + this.listWidth;
    }

}