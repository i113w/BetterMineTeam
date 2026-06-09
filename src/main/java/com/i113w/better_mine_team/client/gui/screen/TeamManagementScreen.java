package com.i113w.better_mine_team.client.gui.screen;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.ClientTeamUiState;
import com.i113w.better_mine_team.client.gui.component.TeamMemberEntry;
import com.i113w.better_mine_team.client.gui.component.TeamMemberList;
import com.i113w.better_mine_team.client.rts.ClientRTSStateManager;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.network.TeamManagementPacket;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.i113w.better_mine_team.common.team.TeamPermissions;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TeamManagementScreen extends Screen {

    // 缓存 Map，用于追踪已存在的 Entry
    private final java.util.Map<java.util.UUID, TeamMemberEntry> entryCache = new java.util.HashMap<>();
    private String lastKnownTeamName = null;

    private static final ResourceLocation BG_TEXTURE = new ResourceLocation(BetterMineTeam.MODID, "textures/gui/management_bg.png");

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

        this.guiLeft = (this.width - CONTENT_WIDTH) / 2;
        this.guiTop = (this.height - CONTENT_HEIGHT) / 2;

        this.memberList = new TeamMemberList(this.minecraft, this.guiLeft, this.guiTop);
        refreshMembers();
        this.addRenderableWidget(this.memberList);
        requestCaptainStatus();

        int btnX = this.guiLeft + CONTENT_WIDTH + 4;
        int btnY = this.guiTop;
        int btnHeight = 20;
        int spacing = 4;

        // [修改] 根据 Config 判断是否渲染 RTS 相关按钮
        if (BMTConfig.isRTSModeEnabled()) {
            this.addRenderableWidget(Button.builder(
                            Component.translatable("better_mine_team.gui.btn.rts_mode"),
                            button -> {
                                this.onClose();
                                ClientRTSStateManager.get().setMode(ClientRTSStateManager.RTSMode.CONTROL);
                                ClientRTSStateManager.get().enterCameraWithLastStyle();
                            })
                    .bounds(btnX, btnY, 60, btnHeight)
                    .build());
            btnY += btnHeight + spacing;

            if (this.minecraft.player != null && TeamPermissions.hasOverridePermission(this.minecraft.player)) {
                this.addRenderableWidget(Button.builder(
                                Component.translatable("better_mine_team.gui.btn.recruit"),
                                button -> {
                                    this.onClose();
                                    ClientRTSStateManager.get().setMode(ClientRTSStateManager.RTSMode.RECRUIT);
                                    ClientRTSStateManager.get().enterCameraWithLastStyle();
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

        // Close 按钮
        this.addRenderableWidget(Button.builder(
                        Component.translatable("better_mine_team.gui.btn.close"),
                        button -> this.onClose())
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
    }

    // 修复 IDE 警告：添加 @NotNull
    @Override
    public void renderBackground(@NotNull GuiGraphics gfx) {
        // 空实现
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
                // 黑名单生物不会被加入到客户端实体列表中
                if (BMTConfig.isTeamMemberListBlacklisted(living.getType())) continue;

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

        boolean removedAny = entryCache.keySet().removeIf(uuid -> {
            if (!currentUUIDs.contains(uuid)) {
                TeamMemberEntry entry = entryCache.get(uuid);
                this.memberList.removeEntry(entry);
                return true;
            }
            return false;
        });

        boolean addedAny = !newMembers.isEmpty();
        if (addedAny) {
            for (LivingEntity member : newMembers) {
                TeamMemberEntry entry = new TeamMemberEntry(member, this.memberList);
                entryCache.put(member.getUUID(), entry);
                this.memberList.addMember(entry);
            }
        }

        if (removedAny || addedAny) {
            double scrollAmount = this.memberList.getScrollAmount();
            sortMembers();
            this.memberList.setScrollAmount(scrollAmount);
        }
    }

    private void sortMembers() {
        List<TeamMemberEntry> entries = new ArrayList<>(this.memberList.getEntries());

        entries.sort((e1, e2) -> {
            LivingEntity entity1 = e1.getMember();
            LivingEntity entity2 = e2.getMember();

            boolean p1 = entity1 instanceof Player;
            boolean p2 = entity2 instanceof Player;
            if (p1 != p2) return p1 ? 1 : -1;

            return entity1.getName().getString().compareToIgnoreCase(entity2.getName().getString());
        });

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
        if (this.tickCounter >= 10) {
            this.tickCounter = 0;
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
        MTNetworkRegister.CHANNEL.sendToServer(new TeamManagementPacket(
                TeamManagementPacket.ACTION_SET_TEAM_GLOW,
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
        MTNetworkRegister.CHANNEL.sendToServer(new TeamManagementPacket(
                TeamManagementPacket.ACTION_REQUEST_CAPTAIN_STATUS,
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
