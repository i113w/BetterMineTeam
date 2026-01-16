package com.i113w.better_mine_team.client.gui.team;

// ... (Imports 保持不变, 确保包含 ImageButton, GuiGraphics 等)
import com.google.common.collect.Maps;
import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.network.TeamActionPacket;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.client.event.ScreenEvent;

import java.util.*;
import java.util.function.Consumer;

public class TeamRender {
    private static final Map<AbstractContainerScreen<?>, TeamRender> INSTANCES = new WeakHashMap<>();
    private static final int MAIN_ICON_SIZE = 16;
    private static final int MAIN_ICON_OFFSET = 6;
    private static final int SMALL_ICON_SIZE = 8;
    private static final int SMALL_ICON_SPACING = 2;
    private static final int CONFLUENCE_OFFSET = 22;

    private final AbstractContainerScreen<?> screen;
    private final Consumer<GuiEventListener> widgetAdder;

    private DynamicImageButton teamIcon;
    private DynamicImageButton teamPVPBtn;
    private final Map<String, DynamicImageButton> teamSmallIcons = Maps.newHashMap();

    private String lastTeamName = "";
    private boolean lastPvPState = false;
    private String currentTeamColor = "white";

    private TeamRender(AbstractContainerScreen<?> screen, Consumer<GuiEventListener> widgetAdder) {
        this.screen = screen;
        this.widgetAdder = widgetAdder;
    }

    public static void attachTo(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            TeamRender render = new TeamRender(containerScreen, event::addListener);
            render.initButton();
            INSTANCES.put(containerScreen, render);
        }
    }

    public static void onRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            TeamRender render = INSTANCES.get(containerScreen);
            if (render != null) {
                render.tick();
            }
        }
    }

    public void tick() {
        if (teamIcon == null) return;
        checkAndUpdateState();
    }

    private void initButton() {
        int guiLeft = screen.getGuiLeft();
        int guiTop = screen.getGuiTop();

        // 1. 主队伍图标
        this.teamIcon = new DynamicImageButton(
                guiLeft - MAIN_ICON_SIZE, guiTop,
                MAIN_ICON_SIZE, MAIN_ICON_SIZE,
                () -> new ResourceLocation(BetterMineTeam.MODID, "textures/gui/team/" + this.currentTeamColor + "_team_icon.png"),
                button -> {
                    boolean showingSmall = !teamSmallIcons.isEmpty() && teamSmallIcons.values().iterator().next().visible;
                    toggleSmallIcons(!showingSmall);
                }
        );

        // 2. PVP 按钮
        this.teamPVPBtn = new DynamicImageButton(
                guiLeft - MAIN_ICON_SIZE, guiTop + MAIN_ICON_SIZE + MAIN_ICON_OFFSET,
                MAIN_ICON_SIZE, MAIN_ICON_SIZE,
                () -> {
                    String state = this.lastPvPState ? "on" : "off";
                    return new ResourceLocation(BetterMineTeam.MODID, "textures/gui/team/pvp/" + this.currentTeamColor + "_pvp_" + state + ".png");
                },
                button -> sendPvPPacket(!this.lastPvPState)
        );

        initSmallIcons(guiLeft, guiTop);

        addWidget(this.teamIcon);
        addWidget(this.teamPVPBtn);
        this.teamSmallIcons.values().forEach(this::addWidget);

        checkAndUpdateState();
    }

    private void initSmallIcons(int guiLeft, int guiTop) {
        List<String> teamColors = Arrays.stream(DyeColor.values())
                .map(DyeColor::getName)
                .collect(java.util.stream.Collectors.toList());
        java.util.Collections.reverse(teamColors);

        int firstOff = BetterMineTeam.IS_CONFLUENCE_LOADED ? CONFLUENCE_OFFSET : 0;

        for (int i = 0; i < teamColors.size(); i++) {
            String color = teamColors.get(i);
            int col = i / 8;
            int row = i % 8;
            int x = guiLeft - SMALL_ICON_SIZE - col * SMALL_ICON_SIZE - col * SMALL_ICON_SPACING;
            int y = guiTop + row * SMALL_ICON_SIZE + row * SMALL_ICON_SPACING + firstOff;

            DynamicImageButton btn = new DynamicImageButton(
                    x, y, SMALL_ICON_SIZE, SMALL_ICON_SIZE,
                    () -> new ResourceLocation(BetterMineTeam.MODID, "textures/gui/team/small/" + color + "_team_small_icon.png"),
                    button -> {
                        sendChangeTeamPacket(color);
                        toggleSmallIcons(false);
                    }
            );
            btn.visible = false;
            teamSmallIcons.put(color, btn);
        }
    }

    private void toggleSmallIcons(boolean show) {
        this.teamIcon.visible = !show;
        this.teamPVPBtn.visible = !show;
        this.teamSmallIcons.values().forEach(btn -> btn.visible = show);
    }

    private void checkAndUpdateState() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Scoreboard scoreboard = player.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());

        String teamName = (team != null) ? team.getName() : "null";
        boolean pvpState = (team != null) && team.isAllowFriendlyFire();

        this.lastTeamName = teamName;
        this.lastPvPState = pvpState;

        if (team != null) {
            this.currentTeamColor = getTextureColorName(team);
        } else {
            this.currentTeamColor = "white";
        }

        if (team == null) {
            this.teamPVPBtn.visible = false;
        } else if (this.teamIcon.visible) {
            this.teamPVPBtn.visible = true;
        }
    }

    private void addWidget(Button btn) {
        this.widgetAdder.accept(btn);
    }

    private void sendChangeTeamPacket(String colorName) {
        MTNetworkRegister.CHANNEL.sendToServer(new TeamActionPacket(0, colorName, false));
    }

    private void sendPvPPacket(boolean enablePvP) {
        MTNetworkRegister.CHANNEL.sendToServer(new TeamActionPacket(1, "", enablePvP));
    }

    private String getTextureColorName(PlayerTeam team) {
        if (team.getName().startsWith(TeamManager.TEAM_PREFIX)) {
            return team.getName().substring(TeamManager.TEAM_PREFIX.length());
        }
        ChatFormatting formatting = team.getColor();
        if (formatting != ChatFormatting.RESET) {
            String name = formatting.getName().toLowerCase();
            if (DyeColor.byName(name, null) != null) return name;
        }
        return "white";
    }

    // --- 内部类：动态 ImageButton ---
    private static class DynamicImageButton extends Button {
        private final java.util.function.Supplier<ResourceLocation> textureSupplier;

        public DynamicImageButton(int x, int y, int width, int height,
                                  java.util.function.Supplier<ResourceLocation> textureSupplier,
                                  OnPress onPress) {
            super(x, y, width, height, net.minecraft.network.chat.Component.empty(), onPress, DEFAULT_NARRATION);
            this.textureSupplier = textureSupplier;
        }

        @Override
        public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            ResourceLocation texture = textureSupplier.get();

            // 简单的 Hover 变色效果
            if (this.isHoveredOrFocused()) {
                gfx.setColor(0.8f, 0.8f, 0.8f, 1.0f);
            } else {
                gfx.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            // 绘制纹理
            // 在 1.20.1 中，blit 参数通常是 (texture, x, y, u, v, width, height, textureWidth, textureHeight)
            // 假设你的图标是 16x16 或 8x8 的单张图片，没有 atlas
            gfx.blit(texture, this.getX(), this.getY(), 0, 0, this.width, this.height, this.width, this.height);

            // 重置颜色
            gfx.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
}