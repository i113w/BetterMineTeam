package com.i113w.better_mine_team.client.gui.component;

import com.i113w.better_mine_team.client.gui.ClientTeamUiState;
import com.i113w.better_mine_team.client.gui.asset.MTGuiIcons;
import com.i113w.better_mine_team.client.manager.ClientSelectionManager;
import com.i113w.better_mine_team.client.rts.BmtRTSEvents;
import com.i113w.better_mine_team.common.network.TeamManagementPayload;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.NotNull;

public class TeamMemberEntry extends ObjectSelectionList.Entry<TeamMemberEntry> {

    public static final int ITEM_HEIGHT = 32;
    private static final int BTN_SIZE = 20;
    private static final int BTN_SPACING = 22;

    private final LivingEntity member;
    private final TeamMemberList parent;

    public TeamMemberEntry(LivingEntity member, TeamMemberList parent) {
        this.member = member;
        this.parent = parent;

    }

    // [新增] 缓存字段
    private String cachedStatus = "";
    private float cachedHp = -1;
    private float cachedMaxHp = -1;

    // [新增] Getter 用于排序优化 (方案A需要)
    public LivingEntity getMember() {
        return member;
    }

    @Override
    public void extractContent(@NotNull GuiGraphicsExtractor gfx, int mouseX, int mouseY, boolean isHovered, float partialTick) {
        int left = this.getContentX();
        int top = this.getContentY();
        int width = this.getContentWidth();
        int height = this.getContentHeight();

        // 1. 背景渲染 (选中/悬停状态)
        boolean isSelected = ClientSelectionManager.isSelected(member);
        if (isSelected) {
            gfx.fill(left, top, left + width, top + height, 0x40FFFFFF); // 选中高亮
            gfx.outline(left, top, width, height, 0xFFFFFF00);
        } else if (isHovered) {
            gfx.fill(left, top, left + width, top + height, 0x20FFFFFF); // 悬停高亮
        }

        // 2. 3D 实体预览 (左侧)

        // 3. 名字渲染
        int textLeft = left + 10;
        int nameColor = isSelected ? 0xFFFFFF00 : 0xFFFFFFFF;
        gfx.text(Minecraft.getInstance().font, member.getDisplayName(), textLeft, top + 6, nameColor);

        // 4. 【修改】HP 显示格式: "HP: 20/20"
        float currentHp = member.getHealth();
        float maxHp = member.getMaxHealth();

        // 只有数值变化时才重新生成字符串
        // 使用 Math.abs 比较浮点数，或者直接 != (因为 getHealth 返回值通常稳定)
        if (Math.abs(currentHp - cachedHp) > 0.01f || Math.abs(maxHp - cachedMaxHp) > 0.01f) {
            cachedHp = currentHp;
            cachedMaxHp = maxHp;
            // 使用 StringBuilder 或直接拼接，避免 String.format 的正则开销
            cachedStatus = "HP: " + Math.round(currentHp) + "/" + Math.round(maxHp);
        }

        // 颜色逻辑：血量低变红
        int hpColor = (maxHp > 0 && currentHp / maxHp < 0.3) ? 0xFFFF5555 : 0xFFAAAAAA;

        gfx.text(Minecraft.getInstance().font, cachedStatus, textLeft, top + 18, hpColor);

        // 5. 快捷按钮渲染 (仅队长可见，且不能对自己操作)
        // 我们需要判断当前客户端玩家是否是队长
        Player localPlayer = Minecraft.getInstance().player;
        boolean amICaptain = isCaptain(localPlayer);
        boolean isMe = localPlayer != null && member.is(localPlayer);

        if (amICaptain && !isMe) {
            int btnY = top + (height - BTN_SIZE) / 2;
            int startX = left + width - BTN_SIZE - 4;

            // [B] 踢出 (最右侧)
            renderMappedButton(gfx, startX, btnY, mouseX, mouseY, MTGuiIcons.ICON_KICK);

            if (!(member instanceof Player)) {
                startX -= BTN_SPACING;
                boolean glowEnabled = TeamManager.isGlowEnabled(member);
                renderItemButton(gfx, startX, btnY, mouseX, mouseY,
                        ClientTeamUiState.getLightIcon(glowEnabled), glowEnabled);
            }

            // [A] 任命队长 (左边一个)
            // 仅当目标是玩家时才显示任命队长
            if (member instanceof Player) {
                startX -= BTN_SPACING;
                renderMappedButton(gfx, startX, btnY, mouseX, mouseY, MTGuiIcons.ICON_CAPTAIN);
            }
        }
    }

    private boolean isCaptain(Player player) {
        return ClientTeamUiState.isLocalPlayerCaptain(player);
    }

    private void renderMappedButton(GuiGraphicsExtractor gfx, int x, int y, int mouseX, int mouseY, MTGuiIcons icon) {
        boolean hovered = mouseX >= x && mouseX < x + BTN_SIZE && mouseY >= y && mouseY < y + BTN_SIZE;
        if (hovered) {
            MTGuiIcons.BUTTON_HOVER.render(gfx, x, y);
        } else {
            MTGuiIcons.BUTTON_NORMAL.render(gfx, x, y);
        }
        icon.render(gfx, x + 2, y + 2);
    }

    private void renderItemButton(GuiGraphicsExtractor gfx, int x, int y, int mouseX, int mouseY, ItemStack itemStack, boolean active) {
        boolean hovered = mouseX >= x && mouseX < x + BTN_SIZE && mouseY >= y && mouseY < y + BTN_SIZE;
        if (active) {
            gfx.fill(x - 1, y - 1, x + BTN_SIZE + 1, y + BTN_SIZE + 1, 0xFFFFD700);
        }
        if (hovered) {
            MTGuiIcons.BUTTON_HOVER.render(gfx, x, y);
        } else {
            MTGuiIcons.BUTTON_NORMAL.render(gfx, x, y);
        }
        gfx.item(itemStack, x + 2, y + 2);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        int listRight = parent.getX() + parent.getRowWidth();
        double distFromRight = listRight - mouseX;

        Player localPlayer = Minecraft.getInstance().player;
        boolean amICaptain = isCaptain(localPlayer);
        boolean isMe = localPlayer != null && member.is(localPlayer);

        // --- 按钮点击判定 ---
        if (amICaptain && !isMe) {
            // [B] 踢出 (Kick) - 距离右边 4~24
            if (distFromRight >= 4 && distFromRight <= 24) {
                ClientPacketDistributor.sendToServer(new TeamManagementPayload(TeamManagementPayload.ACTION_KICK, member.getId(), ""));
                return true;
            }

            if (!(member instanceof Player) && distFromRight >= 26 && distFromRight <= 46) {
                if (!ClientTeamUiState.tryMarkGlowClick()) {
                    return true;
                }
                boolean newState = !TeamManager.isGlowEnabled(member);
                ClientTeamUiState.setClientGlowState(member, newState);
                ClientPacketDistributor.sendToServer(new TeamManagementPayload(
                        TeamManagementPayload.ACTION_SET_GLOW,
                        member.getId(),
                        String.valueOf(newState)));
                return true;
            }

            // [A] 任命队长 (Set Captain) - 距离右边 26~46
            if (member instanceof Player && distFromRight >= 26 && distFromRight <= 46) {
                ClientPacketDistributor.sendToServer(new TeamManagementPayload(TeamManagementPayload.ACTION_SET_CAPTAIN, member.getId(), ""));
                return true;
            }
        }

        // --- 卡片点击判定 (进入详情) ---
        // 如果没有点到按钮，且点击在条目范围内
        // 逻辑：发送 OPEN_INVENTORY 包，服务端会打开 EntityDetailsMenu
        // 注意：如果是玩家队友，目前服务端逻辑是打开末影箱，如果是生物队友，打开新界面
        if (!isMe) {
            ClientPacketDistributor.sendToServer(new TeamManagementPayload(TeamManagementPayload.ACTION_OPEN_INVENTORY, member.getId(), ""));
            if (event.hasShiftDown()) {
                ClientSelectionManager.select(member.getId());
            } else {
                ClientSelectionManager.clear();
                ClientSelectionManager.select(member.getId());
            }
            ClientSelectionManager.syncToLib();
            BmtRTSEvents.syncSelectionToServer();
            return true;
        }

        return false;
    }

    @Override
    public @NotNull Component getNarration() {
        return member.getName();
    }
}
