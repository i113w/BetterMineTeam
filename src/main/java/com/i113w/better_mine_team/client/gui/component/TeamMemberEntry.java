package com.i113w.better_mine_team.client.gui.component;

import com.i113w.better_mine_team.client.gui.asset.MTGuiIcons;
import com.i113w.better_mine_team.client.manager.ClientSelectionManager;
import com.i113w.better_mine_team.common.network.TeamManagementPayload;
import com.i113w.better_mine_team.common.team.TeamDataStorage;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
    public void render(@NotNull GuiGraphics gfx, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovered, float partialTick) {
        // 1. 背景渲染 (选中/悬停状态)
        boolean isSelected = ClientSelectionManager.isSelected(member);
        if (isSelected) {
            gfx.fill(left, top, left + width, top + height, 0x40FFFFFF); // 选中高亮
            gfx.renderOutline(left, top, width, height, 0xFFFFFF00);
        } else if (isHovered) {
            gfx.fill(left, top, left + width, top + height, 0x20FFFFFF); // 悬停高亮
        }

        // 2. 3D 实体预览 (左侧)

        // 3. 名字渲染
        int textLeft = left + 10;
        int nameColor = isSelected ? 0xFFFF00 : 0xFFFFFF;
        gfx.drawString(Minecraft.getInstance().font, member.getDisplayName(), textLeft, top + 6, nameColor);

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
        int hpColor = (maxHp > 0 && currentHp / maxHp < 0.3) ? 0xFF5555 : 0xAAAAAA;

        gfx.drawString(Minecraft.getInstance().font, cachedStatus, textLeft, top + 18, hpColor);

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

            // [A] 任命队长 (左边一个)
            // 仅当目标是玩家时才显示任命队长
            if (member instanceof Player) {
                startX -= BTN_SPACING;
                renderMappedButton(gfx, startX, btnY, mouseX, mouseY, MTGuiIcons.ICON_CAPTAIN);
            }
        }
    }

    private boolean isCaptain(Player player) {
        if (player == null) return false;
        // 客户端简单的队长检查，实际权限由服务端校验
        // 这里可以通过 TeamDataStorage 的同步数据检查，或者简单检查计分板队伍所有者(如果是原版逻辑)
        // 由于 TeamDataStorage 在客户端可能不同步，我们暂时假设所有人都显示按钮，点击后由服务端拒绝
        // 或者：优化为检查 TeamManager 的逻辑
        return true; // 暂时让所有人都能看到按钮，服务端会拦截非队长操作
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

    private void renderEntity(GuiGraphics gfx, int x, int y, int scale, float mouseX, float mouseY, LivingEntity entity) {
        // ... 保持原有的 3D 渲染代码不变 ...
        float f = (float) Math.atan(mouseX / 40.0F);
        float f1 = (float) Math.atan(mouseY / 40.0F);
        Quaternionf quaternionf = (new Quaternionf()).rotateZ((float) Math.PI);
        Quaternionf quaternionf1 = (new Quaternionf()).rotateX(f1 * 20.0F * ((float) Math.PI / 180F));
        quaternionf.mul(quaternionf1);
        float yBodyRot = entity.yBodyRot;
        float yHeadRot = entity.yHeadRot;
        float xRot = entity.getXRot();
        float yRotO = entity.yRotO;
        float xRotO = entity.xRotO;
        entity.yBodyRot = 180.0F + f * 20.0F;
        entity.setYRot(180.0F + f * 40.0F);
        entity.setXRot(-f1 * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yRotO = entity.getYRot();
        entity.xRotO = entity.getXRot();
        InventoryScreen.renderEntityInInventory(gfx, (float) x, (float) y, (float) scale, new Vector3f(0, 0, 0), quaternionf, quaternionf1, entity);
        entity.yBodyRot = yBodyRot;
        entity.setYRot(yHeadRot);
        entity.setXRot(xRot);
        entity.yHeadRot = yHeadRot;
        entity.yRotO = yRotO;
        entity.xRotO = xRotO;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listRight = parent.getX() + parent.getRowWidth();
        double distFromRight = listRight - mouseX;

        Player localPlayer = Minecraft.getInstance().player;
        boolean amICaptain = isCaptain(localPlayer);
        boolean isMe = localPlayer != null && member.is(localPlayer);

        // --- 按钮点击判定 ---
        if (amICaptain && !isMe) {
            // [B] 踢出 (Kick) - 距离右边 4~24
            if (distFromRight >= 4 && distFromRight <= 24) {
                PacketDistributor.sendToServer(new TeamManagementPayload(TeamManagementPayload.ACTION_KICK, member.getId(), ""));
                return true;
            }

            // [A] 任命队长 (Set Captain) - 距离右边 26~46
            if (member instanceof Player && distFromRight >= 26 && distFromRight <= 46) {
                PacketDistributor.sendToServer(new TeamManagementPayload(TeamManagementPayload.ACTION_SET_CAPTAIN, member.getId(), ""));
                return true;
            }
        }

        // --- 卡片点击判定 (进入详情) ---
        // 如果没有点到按钮，且点击在条目范围内
        // 逻辑：发送 OPEN_INVENTORY 包，服务端会打开 EntityDetailsMenu
        // 注意：如果是玩家队友，目前服务端逻辑是打开末影箱，如果是生物队友，打开新界面
        if (!isMe) { // 不能查看自己的详情(或者可以？看需求，通常按E就行)
            PacketDistributor.sendToServer(new TeamManagementPayload(TeamManagementPayload.ACTION_OPEN_INVENTORY, member.getId(), ""));
            // 同时处理选中逻辑
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