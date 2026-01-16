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

    // 1.20.1 中，width 是列表组件的宽度，itemHeight 是条目高度
    public TeamMemberList(Minecraft mc, int guiLeft, int guiTop) {
        super(
                mc,
                150, // width
                152, // height
                guiTop + 7, // top (Y coordinate)
                guiTop + 7 + 152, // bottom (Y coordinate)
                TeamMemberEntry.ITEM_HEIGHT // itemHeight
        );
        // [关键] 设置列表的左侧起始位置
        this.setLeftPos(guiLeft + 7);
        // 设置 RenderBackground 为 false，因为我们在 Screen 里画了背景图
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false); // 不画默认的黑色背景
    }

    @Override
    public int getRowWidth() {
        return 150;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getLeft() + this.width - 6;
    }

    // 重写 render，移除 scissor 逻辑，因为 ObjectSelectionList 内部会自动处理 scissor
    // 如果列表显示不全，说明 super.render 里的逻辑有问题，或者 setLeftPos 没生效
    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // 调用父类渲染，父类会调用 renderList，renderList 会调用 Entry.render
        super.render(gfx, mouseX, mouseY, partialTick);

        // 渲染自定义滚动条
        renderCustomScrollbar(gfx);
    }

    private void renderCustomScrollbar(GuiGraphics gfx) {
        int maxScroll = this.getMaxScroll();
        if (maxScroll <= 0) return;

        // 重新计算滚动条位置，基于列表的绝对位置
        int scrollbarX = this.getLeft() + this.width + 1; // 微调位置
        int scrollbarY = this.getTop();
        int scrollbarHeight = this.getHeight();

        RenderSystem.enableBlend();

        MTGuiIcons.SCROLL_TRACK.render(gfx, scrollbarX, scrollbarY);

        int totalContentHeight = this.getItemCount() * TeamMemberEntry.ITEM_HEIGHT;

        // 防止除以零
        if (totalContentHeight == 0) totalContentHeight = 1;

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

    public boolean removeEntry(TeamMemberEntry entry) {
        return super.removeEntry(entry);
    }

    public java.util.List<TeamMemberEntry> getEntries() {
        return super.children();
    }

    public int getListRight() {
        return this.getLeft() + this.width;
    }
}