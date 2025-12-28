package com.i113w.better_mine_team.client.gui.screen;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.component.TeamMemberEntry;
import com.i113w.better_mine_team.client.gui.component.TeamMemberList;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TeamManagementScreen extends Screen {

    // 缓存 Map，用于追踪已存在的 Entry
    private final java.util.Map<java.util.UUID, TeamMemberEntry> entryCache = new java.util.HashMap<>();
    private String lastKnownTeamName = null;

    private static final ResourceLocation BG_TEXTURE = ResourceLocation.fromNamespaceAndPath(BetterMineTeam.MODID, "textures/gui/management_bg.png");

    // 1. 纹理尺寸 (256x256)
    private static final int TEXTURE_SIZE = 256;

    // 2. 内容视觉尺寸 (176x166) - 用于计算居中
    private static final int CONTENT_WIDTH = 176;
    private static final int CONTENT_HEIGHT = 166;

    private int guiLeft;
    private int guiTop;

    private TeamMemberList memberList;

    public TeamManagementScreen() {
        super(Component.translatable("better_mine_team.gui.title.management"));
    }

    @Override
    protected void init() {
        super.init();

        // 计算居中 (以 176x166 为基准)
        this.guiLeft = (this.width - CONTENT_WIDTH) / 2;
        this.guiTop = (this.height - CONTENT_HEIGHT) / 2;

        // 初始化列表 (自动对齐到 guiLeft + 7)
        this.memberList = new TeamMemberList(this.minecraft, this.guiLeft, this.guiTop);
        refreshMembers();
        this.addRenderableWidget(this.memberList);

        // 初始化按钮 (紧贴内容区右侧)
        int btnX = this.guiLeft + CONTENT_WIDTH + 4;
        int btnY = this.guiTop;

        // RTS 按钮
        if (ModList.get().isLoaded("bmt_extended")) {
            this.addRenderableWidget(Button.builder(Component.translatable("better_mine_team.gui.btn.rts_mode"), button -> this.onClose())
                    .bounds(btnX, btnY, 60, 20)
                    .build());
        }

        // 关闭按钮
        this.addRenderableWidget(Button.builder(Component.translatable("better_mine_team.gui.btn.close"), button -> this.onClose())
                .bounds(btnX, btnY + 24, 60, 20)
                .build());
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // 1. 强制像素清晰
        Minecraft.getInstance().getTextureManager().getTexture(BG_TEXTURE).setFilter(false, false);

        // 2. 绘制背景 (256x256)
        RenderSystem.enableBlend();
        gfx.blit(BG_TEXTURE, this.guiLeft, this.guiTop, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE);
        RenderSystem.disableBlend();

        // 3. 绘制组件
        super.render(gfx, mouseX, mouseY, partialTick);

        // 4. 绘制文字
        renderLabels(gfx);
    }

    private void renderLabels(GuiGraphics gfx) {
        // --- 标题：放在背景板上方 ---
        // 计算文字宽度以居中
        int titleWidth = this.font.width(this.title);
        int titleX = this.guiLeft + (CONTENT_WIDTH - titleWidth) / 2;
        // guiTop - 12 让文字浮在背景板上面
        gfx.drawString(this.font, this.title, titleX, this.guiTop - 12, 0xFFFFFF, true);

        // --- 队伍名称：放在背景板下方 ---
        if (this.minecraft != null && this.minecraft.player != null) {
            PlayerTeam team = TeamManager.getTeam(this.minecraft.player);
            if (team != null) {
                Component teamText = Component.translatable("better_mine_team.gui.label.current_team", team.getDisplayName());
                int textWidth = this.font.width(teamText);
                int textX = this.guiLeft + (CONTENT_WIDTH - textWidth) / 2;

                // guiTop + CONTENT_HEIGHT + 4 让文字浮在背景板下面
                gfx.drawString(this.font, teamText, textX, this.guiTop + CONTENT_HEIGHT + 4, 0xAAAAAA, true);
            }
        }
    }

    // 修复 IDE 警告：添加 @NotNull
    @Override
    public void renderBackground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // 保持为空，防止毛玻璃
    }

    private void refreshMembers() {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) return;

        PlayerTeam myTeam = TeamManager.getTeam(this.minecraft.player);

        // 情况1: 玩家没有队伍
        if (myTeam == null) {
            if (!entryCache.isEmpty()) {
                this.memberList.clearMembers();
                entryCache.clear();
                lastKnownTeamName = null;
            }
            return;
        }

        // 情况2: 玩家换队了 (完全重置)
        if (!myTeam.getName().equals(lastKnownTeamName)) {
            this.memberList.clearMembers();
            entryCache.clear();
            lastKnownTeamName = myTeam.getName();
        }

        // 收集当前世界中属于该队伍的实体 UUID
        java.util.Set<java.util.UUID> currentUUIDs = new java.util.HashSet<>();
        List<LivingEntity> newMembers = new ArrayList<>();

        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living) {
                PlayerTeam entityTeam = TeamManager.getTeam(living);
                if (entityTeam != null && entityTeam.getName().equals(myTeam.getName())) {
                    java.util.UUID uuid = living.getUUID();
                    currentUUIDs.add(uuid);

                    // 如果缓存里没有，说明是新成员
                    if (!entryCache.containsKey(uuid)) {
                        newMembers.add(living);
                    }
                }
            }
        }

        // 1. 移除已离队的成员 (在缓存中但不在当前 UUID 集合中)
        boolean removedAny = entryCache.keySet().removeIf(uuid -> {
            if (!currentUUIDs.contains(uuid)) {
                // 从 GUI 列表中移除
                TeamMemberEntry entry = entryCache.get(uuid);
                // TeamMemberList 需要暴露 removeEntry 方法，或者我们直接操作 children
                // 假设 TeamMemberList 继承自 ObjectSelectionList，它有 removeEntry 方法
                this.memberList.removeEntry(entry);
                return true;
            }
            return false;
        });

        // 2. 添加新成员
        boolean addedAny = !newMembers.isEmpty();
        if (addedAny) {
            for (LivingEntity member : newMembers) {
                TeamMemberEntry entry = new TeamMemberEntry(member, this.memberList);
                entryCache.put(member.getUUID(), entry);
                this.memberList.addMember(entry);
            }
        }

        // 3. 仅当列表发生变动时，重新排序并保持滚动位置
        if (removedAny || addedAny) {
            double scrollAmount = this.memberList.getScrollAmount();
            sortMembers();
            this.memberList.setScrollAmount(scrollAmount);
        }
    }

    // [新增] 排序辅助方法
    private void sortMembers() {
        // 获取当前所有 Entry
        List<TeamMemberEntry> entries = new ArrayList<>(this.memberList.getEntries());

        entries.sort((e1, e2) -> {
            LivingEntity entity1 = e1.getMember(); // 调用刚才在 Entry 中添加的 getter
            LivingEntity entity2 = e2.getMember();

            // 玩家排在前面
            boolean p1 = entity1 instanceof Player;
            boolean p2 = entity2 instanceof Player;
            if (p1 != p2) return p1 ? 1 : -1;

            // 按名字排序
            return entity1.getName().getString().compareToIgnoreCase(entity2.getName().getString());
        });

        // 清空并重新添加 (ObjectSelectionList 没有直接的 replaceEntries，只能 clear + add)
        this.memberList.clearMembers();
        for (TeamMemberEntry entry : entries) {
            this.memberList.addMember(entry);
        }
    }


private int tickCounter = 0;
    @Override
    public void tick() {
        super.tick();
        this.tickCounter++;

        // 性能优化：每 10 tick (0.5秒) 检查一次列表同步状态
        // 这比每帧检查极大地节省了 CPU，同时对用户来说几乎是实时的
        if (this.tickCounter >= 10) {
            this.tickCounter = 0;
            // 重新读取数据并刷新列表
            // 因为 refreshMembers 内部是基于 Minecraft Client World 的真实数据构建的
            // 所以只要服务端同步了踢人操作，这里就会自动移除该队友
            refreshMembers();
        }
    }



    @Override
    public boolean isPauseScreen() {
        return false;
    }
}