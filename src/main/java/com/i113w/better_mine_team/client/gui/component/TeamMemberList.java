package com.i113w.better_mine_team.client.gui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;

public class TeamMemberList extends ObjectSelectionList<TeamMemberEntry> {

    private final int guiLeft;
    private final int guiTop;
    private final int listWidth = 150;

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
    protected int scrollBarX() {
        return this.guiLeft + 157 + 6;
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

    public void replaceMembers(java.util.List<TeamMemberEntry> entries) {
        super.replaceEntries(entries);
    }

    //暴露 children 列表供排序使用
    public java.util.List<TeamMemberEntry> getEntries() {
        return super.children();
    }
}
