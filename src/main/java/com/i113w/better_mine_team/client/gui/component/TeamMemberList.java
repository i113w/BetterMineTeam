package com.i113w.better_mine_team.client.gui.component;

import com.i113w.better_mine_team.client.gui.asset.MTGuiIcons;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class TeamMemberList extends ObjectSelectionList<TeamMemberEntry> {

    private static final int ROW_WIDTH = 150;
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int MIN_THUMB_HEIGHT = 15;

    private final int guiLeft;
    private final int listTop;
    private final int listHeight;
    private boolean draggingScrollbar;

    public TeamMemberList(Minecraft mc, int guiLeft, int listTop, int listHeight) {
        super(
                mc,
                ROW_WIDTH,
                listHeight,
                listTop,
                listTop + listHeight,
                TeamMemberEntry.ITEM_HEIGHT
        );
        this.guiLeft = guiLeft;
        this.listTop = listTop;
        this.listHeight = listHeight;
        this.setLeftPos(guiLeft + 7);
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    @Override
    protected int getScrollbarPosition() {
        return getScrollbarX();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        boolean overRows = mouseX >= this.getLeft() && mouseX < this.getLeft() + ROW_WIDTH;
        boolean overScrollbar = mouseX >= getScrollbarX() && mouseX < getScrollbarX() + SCROLLBAR_WIDTH;
        return (overRows || overScrollbar)
                && mouseY >= this.listTop
                && mouseY < this.listTop + this.listHeight;
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        renderCustomScrollbar(gfx);
    }

    private void renderCustomScrollbar(GuiGraphics gfx) {
        if (!hasScrollableContent()) {
            this.draggingScrollbar = false;
            return;
        }

        RenderSystem.enableBlend();
        MTGuiIcons.SCROLL_TRACK.render(gfx, getScrollbarX(), this.listTop, this.listHeight);
        MTGuiIcons.SCROLL_THUMB.render(gfx, getScrollbarX(), getThumbY(), getThumbHeight());
        RenderSystem.disableBlend();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverScrollbar(mouseX, mouseY)) {
            if (isOverThumb(mouseX, mouseY)) {
                this.draggingScrollbar = true;
                return true;
            }
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.draggingScrollbar) {
            int travel = this.listHeight - getThumbHeight();
            if (travel > 0) {
                double scrollScale = (double) this.getMaxScroll() / travel;
                this.setScrollAmount(this.getScrollAmount() + dragY * scrollScale);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int getScrollbarX() {
        return this.guiLeft + 157;
    }

    private int getThumbHeight() {
        int totalContentHeight = Math.max(1, this.getItemCount() * TeamMemberEntry.ITEM_HEIGHT);
        int calculatedHeight = (int) ((float) (this.listHeight * this.listHeight) / totalContentHeight);
        return Mth.clamp(calculatedHeight, MIN_THUMB_HEIGHT, this.listHeight);
    }

    private int getThumbY() {
        int maxScroll = this.getMaxScroll();
        if (maxScroll <= 0) return this.listTop;
        double scrollRatio = this.getScrollAmount() / maxScroll;
        return this.listTop + (int) Math.round(scrollRatio * (this.listHeight - getThumbHeight()));
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        return hasScrollableContent()
                && mouseX >= getScrollbarX()
                && mouseX < getScrollbarX() + SCROLLBAR_WIDTH
                && mouseY >= this.listTop
                && mouseY < this.listTop + this.listHeight;
    }

    private boolean isOverThumb(double mouseX, double mouseY) {
        int thumbY = getThumbY();
        return isOverScrollbar(mouseX, mouseY)
                && mouseY >= thumbY
                && mouseY < thumbY + getThumbHeight();
    }

    public void stopScrollbarDrag() {
        this.draggingScrollbar = false;
    }

    private boolean hasScrollableContent() {
        return this.getMaxScroll() > 0 && getThumbHeight() < this.listHeight;
    }

    @Override
    public void setScrollAmount(double scrollAmount) {
        super.setScrollAmount(hasScrollableContent() ? scrollAmount : 0.0);
    }

    public void clearMembers() {
        this.clearEntries();
    }

    public void addMember(TeamMemberEntry entry) {
        this.addEntry(entry);
    }

    public boolean removeEntry(TeamMemberEntry entry) {
        return super.removeEntry(entry);
    }

    public void replaceMembers(Collection<TeamMemberEntry> entries) {
        this.replaceEntries(entries);
        stopScrollbarDrag();
    }

    public java.util.List<TeamMemberEntry> getEntries() {
        return super.children();
    }

    public int getListRight() {
        return this.getLeft() + ROW_WIDTH;
    }
}
