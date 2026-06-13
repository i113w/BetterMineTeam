package com.i113w.better_mine_team.client.gui.screen;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.ClientTeamUiState;
import com.i113w.better_mine_team.client.gui.component.TeamMemberEntry;
import com.i113w.better_mine_team.client.gui.component.TeamMemberList;
import com.i113w.better_mine_team.client.rts.BmtRTSManager;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.network.TeamManagementPayload;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.better_mine_team.common.team.TeamPermissions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TeamManagementScreen extends Screen {

    // 缓存 Map，用于追踪已存在的 Entry
    private final java.util.Map<java.util.UUID, TeamMemberEntry> entryCache = new java.util.HashMap<>();
    private String lastKnownTeamName = null;

    private static final Identifier BG_TEXTURE = Identifier.fromNamespaceAndPath(BetterMineTeam.MODID, "textures/gui/management_bg.png");

    // 1. 纹理尺寸 (256x256)
    private static final int TEXTURE_SIZE = 256;

    // 2. 内容视觉尺寸 (176x166) - 用于计算居中
    private static final int CONTENT_WIDTH = 176;
    private static final int CONTENT_HEIGHT = 166;

    private int guiLeft;
    private int guiTop;

    private TeamMemberList memberList;
    private Button teamGlowButton;

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
    public void extractRenderState(@NotNull GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(gfx, mouseX, mouseY, partialTick);
        renderLabels(gfx);
    }

    private void renderLabels(GuiGraphicsExtractor gfx) {
        // --- 标题：放在背景板上方 ---
        // 计算文字宽度以居中
        int titleWidth = this.font.width(this.title);
        int titleX = this.guiLeft + (CONTENT_WIDTH - titleWidth) / 2;
        // guiTop - 12 让文字浮在背景板上面
        gfx.text(this.font, this.title, titleX, this.guiTop - 12, 0xFFFFFFFF, true);

        // --- 队伍名称：放在背景板下方 ---
        if (this.minecraft != null && this.minecraft.player != null) {
            PlayerTeam team = TeamManager.getTeam(this.minecraft.player);
            if (team != null) {
                Component teamText = Component.translatable("better_mine_team.gui.label.current_team", team.getDisplayName());
                int textWidth = this.font.width(teamText);
                int textX = this.guiLeft + (CONTENT_WIDTH - textWidth) / 2;

                // guiTop + CONTENT_HEIGHT + 4 让文字浮在背景板下面
                gfx.text(this.font, teamText, textX, this.guiTop + CONTENT_HEIGHT + 4, 0xFFAAAAAA, true);
            }
        }
    }

    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
        gfx.blit(RenderPipelines.GUI_TEXTURED, BG_TEXTURE, this.guiLeft, this.guiTop, 0, 0,
                TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
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

        // 1. 移除已离队的成员 (在缓存中但不在当前 UUID 集合中)
        boolean removedAny = entryCache.keySet().removeIf(uuid -> {
            if (!currentUUIDs.contains(uuid)) {
                // 从 GUI 列表中移除
                TeamMemberEntry entry = entryCache.get(uuid);
                // TeamMemberList 需要暴露 removeEntry 方法，或者我们直接操作 children
                // 假设 TeamMemberList 继承自 ObjectSelectionList，它有 removeEntry 方法
                this.memberList.removeMember(entry);
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
            sortMembers();
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

        this.memberList.replaceMembers(entries);
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
        ClientPacketDistributor.sendToServer(new TeamManagementPayload(
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
        if (this.memberList == null) return true;
        for (TeamMemberEntry entry : this.memberList.getEntries()) {
            LivingEntity member = entry.getMember();
            if (!(member instanceof Player) && member.isAlive() && !TeamManager.isGlowEnabled(member)) {
                return true;
            }
        }
        return false;
    }

    private void applyVisibleTeamGlow(boolean enabled) {
        if (this.memberList == null) return;
        for (TeamMemberEntry entry : this.memberList.getEntries()) {
            LivingEntity member = entry.getMember();
            if (!(member instanceof Player) && member.isAlive()) {
                ClientTeamUiState.setClientGlowState(member, enabled);
            }
        }
    }

    private void requestCaptainStatus() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        ClientTeamUiState.setLocalPlayerCaptain(this.minecraft.player, false);
        ClientPacketDistributor.sendToServer(new TeamManagementPayload(
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
