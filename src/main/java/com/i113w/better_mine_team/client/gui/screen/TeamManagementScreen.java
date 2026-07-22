package com.i113w.better_mine_team.client.gui.screen;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.ClientTeamUiState;
import com.i113w.better_mine_team.client.gui.component.TeamMemberEntry;
import com.i113w.better_mine_team.client.gui.component.TeamMemberList;
import com.i113w.better_mine_team.client.rts.BmtRTSManager;
import com.i113w.better_mine_team.client.rts.ClientPatrolSettings;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.network.TeamManagementPayload;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.better_mine_team.common.team.TeamPermissions;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private static final int SEARCH_X_OFFSET = 7;
    private static final int SEARCH_Y_OFFSET = 7;
    private static final int SEARCH_WIDTH = 162;
    private static final int SEARCH_HEIGHT = 18;
    private static final int LIST_Y_OFFSET = 27;
    private static final int LIST_HEIGHT = 132;
    private static final int LIST_ROW_WIDTH = 150;

    private int guiLeft;
    private int guiTop;

    private TeamMemberList memberList;
    private EditBox searchBox;
    private Button teamGlowButton;
    private final List<TeamMemberEntry> sortedEntries = new ArrayList<>();
    private String searchQuery = "";

    public TeamManagementScreen() {
        super(Component.translatable("better_mine_team.gui.title.management"));
    }

    @Override
    protected void init() {
        super.init();

        // 计算居中 (以 176x166 为基准)
        this.guiLeft = (this.width - CONTENT_WIDTH) / 2;
        this.guiTop = (this.height - CONTENT_HEIGHT) / 2;

        // Entry 持有其所属列表的引用；界面重建时必须针对新列表重新创建。
        this.entryCache.clear();
        this.sortedEntries.clear();

        // 搜索框占用列表顶部空间，列表和滚动条从其下方开始。
        this.memberList = new TeamMemberList(this.minecraft, this.guiLeft,
                this.guiTop + LIST_Y_OFFSET, LIST_HEIGHT);
        refreshMembers();
        this.addRenderableWidget(this.memberList);

        Component searchLabel = Component.translatable("better_mine_team.gui.search_members");
        this.searchBox = new EditBox(this.font,
                this.guiLeft + SEARCH_X_OFFSET,
                this.guiTop + SEARCH_Y_OFFSET,
                SEARCH_WIDTH,
                SEARCH_HEIGHT,
                searchLabel);
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(searchLabel);
        this.searchBox.setValue(this.searchQuery);
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);
        requestCaptainStatus();

        // 初始化按钮 (紧贴内容区右侧)
        int btnX = this.guiLeft + CONTENT_WIDTH + 4;
        int btnY = this.guiTop;
        int btnHeight = 20;
        int spacing = 4;

        // Config 判断包裹 RTS 相关的两个按钮
        if (BMTConfig.isRTSModeEnabled()) {
            // 1. RTS 按钮
            this.addRenderableWidget(Button.builder(Component.translatable("better_mine_team.gui.btn.rts_mode"), button -> {
                        this.onClose();
                        BmtRTSManager.setMode(BmtRTSManager.RTSMode.CONTROL);
                        BmtRTSManager.enterCameraWithLastStyle();
                    })
                    .bounds(btnX, btnY, 60, btnHeight)
                    .build());
            btnY += btnHeight + spacing;

            if (ClientPatrolSettings.get().enabled()) {
                this.addRenderableWidget(Button.builder(Component.translatable("better_mine_team.gui.btn.patrol"), button -> {
                            this.onClose();
                            BmtRTSManager.setMode(BmtRTSManager.RTSMode.PATROL);
                            BmtRTSManager.enterCameraWithLastStyle();
                        })
                        .bounds(btnX, btnY, 60, btnHeight)
                        .build());
                btnY += btnHeight + spacing;
            }

            // 2. Recruit 按钮 (仅限 TeamsLord)
            if (this.minecraft.player != null && TeamPermissions.hasOverridePermission(this.minecraft.player)) {
                this.addRenderableWidget(Button.builder(Component.literal("Recruit"), button -> {
                            this.onClose();
                            BmtRTSManager.setMode(BmtRTSManager.RTSMode.RECRUIT);
                            BmtRTSManager.enterCameraWithLastStyle();
                        })
                        .bounds(btnX, btnY, 60, btnHeight)
                        .build());

                btnY += btnHeight + spacing;
            }
        }

        this.teamGlowButton = Button.builder(getTeamGlowButtonText(), button -> toggleTeamGlow())
                .bounds(btnX, btnY, 60, btnHeight)
                .build();
        this.addRenderableWidget(this.teamGlowButton);
        refreshTeamGlowButton();
        btnY += btnHeight + spacing;

        // 3. Close 按钮 (始终在最后)
        this.addRenderableWidget(Button.builder(Component.translatable("better_mine_team.gui.btn.close"), button -> this.onClose())
                .bounds(btnX, btnY, 60, btnHeight)
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

        if (hasActiveSearch() && this.memberList != null && this.memberList.getEntries().isEmpty()) {
            Component noResults = Component.translatable("better_mine_team.gui.search_no_results");
            int centerX = this.guiLeft + SEARCH_X_OFFSET + LIST_ROW_WIDTH / 2;
            int textY = this.guiTop + LIST_Y_OFFSET + (LIST_HEIGHT - this.font.lineHeight) / 2;
            gfx.drawCenteredString(this.font, noResults, centerX, textY, 0xAAAAAA);
        }
    }

    // 修复 IDE 警告：添加 @NotNull
    @Override
    public void renderBackground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {}

    private void refreshMembers() {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) return;

        PlayerTeam myTeam = TeamManager.getTeam(this.minecraft.player);

        // 情况1: 玩家没有队伍
        if (myTeam == null) {
            entryCache.clear();
            sortedEntries.clear();
            lastKnownTeamName = null;
            resetSearch();
            this.memberList.replaceMembers(List.of());
            return;
        }

        // 情况2: 玩家换队了 (完全重置)
        if (!myTeam.getName().equals(lastKnownTeamName)) {
            entryCache.clear();
            sortedEntries.clear();
            lastKnownTeamName = myTeam.getName();
            resetSearch();
        }

        // 收集当前世界中属于该队伍的实体 UUID
        java.util.Set<java.util.UUID> currentUUIDs = new java.util.HashSet<>();
        List<LivingEntity> newMembers = new ArrayList<>();

        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living) {
                // 黑名单拦截，如果存在于 teamMemberListBlacklist 中，直接跳过 (不在列表中展示)
                if (BMTConfig.isEntityHiddenFromMemberList(living.getType())) continue;

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

        // 1. 移除已离队的成员。可见列表随后会从完整缓存重新生成。
        entryCache.entrySet().removeIf(entry -> !currentUUIDs.contains(entry.getKey()));

        // 2. 添加新成员
        for (LivingEntity member : newMembers) {
            TeamMemberEntry entry = new TeamMemberEntry(member, this.memberList);
            entryCache.put(member.getUUID(), entry);
        }

        // 名称可能在成员数量不变时发生变化，因此每次轮询都刷新排序和搜索结果。
        rebuildSortedEntries();
        applySearchFilter(false);
    }

    private void rebuildSortedEntries() {
        this.sortedEntries.clear();
        this.sortedEntries.addAll(this.entryCache.values());
        this.sortedEntries.sort((e1, e2) -> {
            LivingEntity entity1 = e1.getMember(); // 调用刚才在 Entry 中添加的 getter
            LivingEntity entity2 = e2.getMember();

            // 玩家排在前面
            boolean p1 = entity1 instanceof Player;
            boolean p2 = entity2 instanceof Player;
            if (p1 != p2) return p1 ? 1 : -1;

            // 按名字排序
            return entity1.getName().getString().compareToIgnoreCase(entity2.getName().getString());
        });
    }

    private void onSearchChanged(String value) {
        this.searchQuery = value;
        applySearchFilter(true);
    }

    private void applySearchFilter(boolean resetScroll) {
        if (this.memberList == null) return;

        double previousScroll = resetScroll ? 0.0 : this.memberList.getScrollAmount();
        if (resetScroll) this.memberList.stopScrollbarDrag();
        String normalizedQuery = normalizeSearchText(this.searchQuery);
        List<TeamMemberEntry> visibleEntries = new ArrayList<>();
        for (TeamMemberEntry entry : this.sortedEntries) {
            if (normalizedQuery.isEmpty() || matchesSearch(entry, normalizedQuery)) {
                visibleEntries.add(entry);
            }
        }

        if (!this.memberList.getEntries().equals(visibleEntries)) {
            this.memberList.replaceMembers(visibleEntries);
        }
        this.memberList.setScrollAmount(previousScroll);
    }

    private boolean matchesSearch(TeamMemberEntry entry, String normalizedQuery) {
        LivingEntity member = entry.getMember();
        return containsSearchText(member.getDisplayName().getString(), normalizedQuery)
                || containsSearchText(member.getName().getString(), normalizedQuery)
                || containsSearchText(member.getType().getDescription().getString(), normalizedQuery)
                || containsSearchText(BuiltInRegistries.ENTITY_TYPE.getKey(member.getType()).toString(), normalizedQuery);
    }

    private boolean containsSearchText(String candidate, String normalizedQuery) {
        return normalizeSearchText(candidate).contains(normalizedQuery);
    }

    private String normalizeSearchText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasActiveSearch() {
        return !normalizeSearchText(this.searchQuery).isEmpty();
    }

    private void resetSearch() {
        this.searchQuery = "";
        if (this.searchBox != null && !this.searchBox.getValue().isEmpty()) {
            this.searchBox.setValue("");
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
            refreshTeamGlowButton();
        }
    }

    private void toggleTeamGlow() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (!isLocalPlayerCaptain() || !ClientTeamUiState.tryMarkGlowClick()) return;

        boolean newState = shouldEnableTeamGlow();
        applyVisibleTeamGlow(newState);
        refreshTeamGlowButton();
        PacketDistributor.sendToServer(new TeamManagementPayload(
                TeamManagementPayload.ACTION_SET_TEAM_GLOW,
                this.minecraft.player.getId(),
                String.valueOf(newState)));
    }

    public void refreshGlowControls() {
        refreshTeamGlowButton();
    }

    private void refreshTeamGlowButton() {
        if (this.teamGlowButton != null) {
            this.teamGlowButton.active = isLocalPlayerCaptain();
            this.teamGlowButton.setMessage(getTeamGlowButtonText());
        }
    }

    private Component getTeamGlowButtonText() {
        return Component.translatable(shouldEnableTeamGlow()
                ? "better_mine_team.gui.btn.team_glow_on"
                : "better_mine_team.gui.btn.team_glow_off");
    }

    private boolean shouldEnableTeamGlow() {
        for (TeamMemberEntry entry : this.sortedEntries) {
            LivingEntity member = entry.getMember();
            if (!(member instanceof Player) && member.isAlive() && !TeamManager.isGlowEnabled(member)) {
                return true;
            }
        }
        return false;
    }

    private void applyVisibleTeamGlow(boolean enabled) {
        for (TeamMemberEntry entry : this.sortedEntries) {
            LivingEntity member = entry.getMember();
            if (!(member instanceof Player) && member.isAlive()) {
                ClientTeamUiState.setClientGlowState(member, enabled);
            }
        }
    }

    private void requestCaptainStatus() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        ClientTeamUiState.setLocalPlayerCaptain(this.minecraft.player, false);
        PacketDistributor.sendToServer(new TeamManagementPayload(
                TeamManagementPayload.ACTION_REQUEST_CAPTAIN_STATUS,
                this.minecraft.player.getId(),
                ""));
    }

    private boolean isLocalPlayerCaptain() {
        return this.minecraft != null && ClientTeamUiState.isLocalPlayerCaptain(this.minecraft.player);
    }



    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
