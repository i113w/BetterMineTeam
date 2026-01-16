package com.i113w.better_mine_team.client.gui.component;

import com.i113w.better_mine_team.client.gui.asset.MTGuiIcons;
import com.i113w.better_mine_team.client.manager.ClientSelectionManager;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.network.TeamManagementPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public class TeamMemberEntry extends ObjectSelectionList.Entry<TeamMemberEntry> {

    public static final int ITEM_HEIGHT = 32;
    private static final int BTN_SIZE = 20;
    private static final int BTN_SPACING = 22;

    private final LivingEntity member;
    private final TeamMemberList parent;

    private String cachedStatus = "";
    private float cachedHp = -1;
    private float cachedMaxHp = -1;

    public TeamMemberEntry(LivingEntity member, TeamMemberList parent) {
        this.member = member;
        this.parent = parent;
    }

    public LivingEntity getMember() {
        return member;
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
        boolean isSelected = ClientSelectionManager.isSelected(member);
        if (isSelected) {
            gfx.fill(left, top, left + width, top + height, 0x40FFFFFF);
            gfx.renderOutline(left, top, width, height, 0xFFFFFF00);
        } else if (isHovered) {
            gfx.fill(left, top, left + width, top + height, 0x20FFFFFF);
        }

        int textLeft = left + 10;
        int nameColor = isSelected ? 0xFFFF00 : 0xFFFFFF;
        gfx.drawString(Minecraft.getInstance().font, member.getDisplayName(), textLeft, top + 6, nameColor);

        float currentHp = member.getHealth();
        float maxHp = member.getMaxHealth();

        if (Math.abs(currentHp - cachedHp) > 0.01f || Math.abs(maxHp - cachedMaxHp) > 0.01f) {
            cachedHp = currentHp;
            cachedMaxHp = maxHp;
            cachedStatus = "HP: " + Math.round(currentHp) + "/" + Math.round(maxHp);
        }

        int hpColor = (maxHp > 0 && currentHp / maxHp < 0.3) ? 0xFF5555 : 0xAAAAAA;
        gfx.drawString(Minecraft.getInstance().font, cachedStatus, textLeft, top + 18, hpColor);

        Player localPlayer = Minecraft.getInstance().player;
        boolean amICaptain = isCaptain(localPlayer);
        boolean isMe = localPlayer != null && member.is(localPlayer);

        if (amICaptain && !isMe) {
            int btnY = top + (height - BTN_SIZE) / 2;
            int startX = left + width - BTN_SIZE - 4;

            renderMappedButton(gfx, startX, btnY, mouseX, mouseY, MTGuiIcons.ICON_KICK);

            if (member instanceof Player) {
                startX -= BTN_SPACING;
                renderMappedButton(gfx, startX, btnY, mouseX, mouseY, MTGuiIcons.ICON_CAPTAIN);
            }
        }
    }

    private boolean isCaptain(Player player) {
        if (player == null) return false;
        return true;
    }

    private void renderMappedButton(GuiGraphics gfx, int x, int y, int mouseX, int mouseY, MTGuiIcons icon) {
        boolean hovered = mouseX >= x && mouseX < x + BTN_SIZE && mouseY >= y && mouseY < y + BTN_SIZE;
        if (hovered) {
            MTGuiIcons.BUTTON_HOVER.render(gfx, x, y);
        } else {
            MTGuiIcons.BUTTON_NORMAL.render(gfx, x, y);
        }
        icon.render(gfx, x + 2, y + 2);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listRight = parent.getListRight(); // 使用新添加的方法
        double distFromRight = listRight - mouseX;

        Player localPlayer = Minecraft.getInstance().player;
        boolean amICaptain = isCaptain(localPlayer);
        boolean isMe = localPlayer != null && member.is(localPlayer);

        if (amICaptain && !isMe) {
            if (distFromRight >= 4 && distFromRight <= 24) {
                // Forge 1.20.1 网络发送方式
                MTNetworkRegister.CHANNEL.sendToServer(
                        new TeamManagementPacket(TeamManagementPacket.ACTION_KICK, member.getId(), "")
                );
                return true;
            }

            if (member instanceof Player && distFromRight >= 26 && distFromRight <= 46) {
                MTNetworkRegister.CHANNEL.sendToServer(
                        new TeamManagementPacket(TeamManagementPacket.ACTION_SET_CAPTAIN, member.getId(), "")
                );
                return true;
            }
        }

        if (!isMe) {
            MTNetworkRegister.CHANNEL.sendToServer(
                    new TeamManagementPacket(TeamManagementPacket.ACTION_OPEN_INVENTORY, member.getId(), "")
            );

            if (Screen.hasShiftDown()) {
                ClientSelectionManager.select(member.getId());
            } else {
                ClientSelectionManager.clear();
                ClientSelectionManager.select(member.getId());
            }
            return true;
        }

        return false;
    }

    @Override
    public @NotNull Component getNarration() {
        return member.getName();
    }
}