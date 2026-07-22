package com.i113w.better_mine_team.client.gui.component;

import com.i113w.better_mine_team.client.gui.asset.MTGuiIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

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
        super(mc, ROW_WIDTH, listHeight, listTop, TeamMemberEntry.ITEM_HEIGHT);
        this.guiLeft = guiLeft;
        this.listTop = listTop;
        this.listHeight = listHeight;
        this.setX(guiLeft + 7);
        this.setY(listTop);
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    @Override
    protected int scrollBarX() {
        return this.guiLeft + 157;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        boolean overRows = mouseX >= this.getX() && mouseX < this.getX() + ROW_WIDTH;
        boolean overScrollbar = mouseX >= scrollBarX() && mouseX < scrollBarX() + SCROLLBAR_WIDTH;
        return (overRows || overScrollbar)
                && mouseY >= this.listTop
                && mouseY < this.listTop + this.listHeight;
    }

    @Override
    protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!hasScrollableContent()) {
            this.draggingScrollbar = false;
            return;
        }

        MTGuiIcons.SCROLL_TRACK.render(graphics, scrollBarX(), this.listTop, this.listHeight);
        MTGuiIcons.SCROLL_THUMB.render(graphics, scrollBarX(), getThumbY(), getThumbHeight());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isOverScrollbar(event.x(), event.y())) {
            if (isOverThumb(event.x(), event.y())) {
                this.draggingScrollbar = true;
                return true;
            }
            return false;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0 && this.draggingScrollbar) {
            int travel = this.listHeight - getThumbHeight();
            if (travel > 0) {
                double scrollScale = (double) this.maxScrollAmount() / travel;
                this.setScrollAmount(this.scrollAmount() + dragY * scrollScale);
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    private int getThumbHeight() {
        int totalContentHeight = Math.max(1, this.getItemCount() * TeamMemberEntry.ITEM_HEIGHT);
        int calculatedHeight = (int) ((float) (this.listHeight * this.listHeight) / totalContentHeight);
        return Mth.clamp(calculatedHeight, MIN_THUMB_HEIGHT, this.listHeight);
    }

    private int getThumbY() {
        int maxScroll = this.maxScrollAmount();
        if (maxScroll <= 0) return this.listTop;
        double scrollRatio = this.scrollAmount() / maxScroll;
        return this.listTop + (int) Math.round(scrollRatio * (this.listHeight - getThumbHeight()));
    }

    @Override
    protected boolean isOverScrollbar(double mouseX, double mouseY) {
        return hasScrollableContent()
                && mouseX >= scrollBarX()
                && mouseX < scrollBarX() + SCROLLBAR_WIDTH
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
        return this.maxScrollAmount() > 0 && getThumbHeight() < this.listHeight;
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

    public void removeMember(TeamMemberEntry entry) {
        super.removeEntry(entry);
    }

    public void replaceMembers(Collection<TeamMemberEntry> entries) {
        super.replaceEntries(entries);
        stopScrollbarDrag();
    }

    //暴露 children 列表供排序使用
    public java.util.List<TeamMemberEntry> getEntries() {
        return super.children();
    }
}
