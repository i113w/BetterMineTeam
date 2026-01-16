package com.i113w.better_mine_team.client.gui.team;

import com.google.common.collect.Maps;
import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.init.MTNetworkRegister;
import com.i113w.better_mine_team.common.network.TeamActionPacket;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.client.event.ScreenEvent;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TeamRender {
    private static final Map<Screen, TeamRender> INSTANCES = new WeakHashMap<>();

    private static final int MAIN_ICON_SIZE = 16;
    private static final int MAIN_ICON_OFFSET = 6;
    private static final int SMALL_ICON_SIZE = 8;
    private static final int SMALL_ICON_SPACING = 2;
    private static final int CONFLUENCE_OFFSET = 22;

    private final AbstractContainerScreen<?> screen;
    private final Consumer<GuiEventListener> widgetAdder;

    private ImageButton teamIcon;
    private ImageButton teamPVPOn;
    private ImageButton teamPVPOff;
    private final Map<String, ImageButton> teamSmallIcons = Maps.newHashMap();

    private String lastTeamName = "";
    private boolean lastPvPState = false;

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
        TeamRender render = INSTANCES.get(event.getScreen());
        if (render != null) {
            render.tick();
        }
    }

    public void tick() {
        if (teamIcon == null) return;
        checkAndUpdateState();
    }

    private void initButton() {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) return;

        String teamColor = DyeColor.WHITE.getName();
        Scoreboard scoreboard = localPlayer.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(localPlayer.getScoreboardName());

        if (team != null) {
            teamColor = getTextureColorName(team);
        }

        int guiLeft = screen.getGuiLeft();
        int guiTop = screen.getGuiTop();

        ResourceLocation teamIconTexture = BetterMineTeam.asResource("textures/gui/team/" + teamColor + "_team_icon.png");
        this.teamIcon = new ImageButton(
                guiLeft - MAIN_ICON_SIZE,
                guiTop,
                MAIN_ICON_SIZE, MAIN_ICON_SIZE,
                0, 0, // u, v
                MAIN_ICON_SIZE, // yDiffText
                teamIconTexture,
                MAIN_ICON_SIZE, MAIN_ICON_SIZE, // textureWidth, textureHeight
                button -> {
                    this.teamIcon.visible = false;
                    this.teamPVPOn.visible = false;
                    this.teamPVPOff.visible = false;
                    visibleTeamSmallIcon(true);
                }
        );

        ResourceLocation teamPVPOffTexture = BetterMineTeam.asResource("textures/gui/team/pvp/" + teamColor + "_pvp_off.png");
        this.teamPVPOff = new ImageButton(
                guiLeft - MAIN_ICON_SIZE,
                guiTop + MAIN_ICON_SIZE + MAIN_ICON_OFFSET,
                MAIN_ICON_SIZE, MAIN_ICON_SIZE,
                0, 0, // u, v
                MAIN_ICON_SIZE, // yDiffText
                teamPVPOffTexture,
                MAIN_ICON_SIZE, MAIN_ICON_SIZE, // textureWidth, textureHeight
                button -> sendPvPPacket(true)
        );

        ResourceLocation teamPVPOnTexture = BetterMineTeam.asResource("textures/gui/team/pvp/" + teamColor + "_pvp_on.png");
        this.teamPVPOn = new ImageButton(
                guiLeft - MAIN_ICON_SIZE,
                guiTop + MAIN_ICON_SIZE + MAIN_ICON_OFFSET,
                MAIN_ICON_SIZE, MAIN_ICON_SIZE,
                0, 0, // u, v
                MAIN_ICON_SIZE, // yDiffText
                teamPVPOnTexture,
                MAIN_ICON_SIZE, MAIN_ICON_SIZE, // textureWidth, textureHeight
                button -> sendPvPPacket(false)
        );
        initSmallIcon(guiLeft, guiTop);
        addRenderableWidget();

        this.lastTeamName = "";
        checkAndUpdateState();
    }

    private void addRenderableWidget() {
        addWidget(this.teamIcon);
        addWidget(this.teamPVPOn);
        addWidget(this.teamPVPOff);
        for (ImageButton button : teamSmallIcons.values()) {
            addWidget(button);
        }
    }

    private void addWidget(ImageButton btn) {
        this.widgetAdder.accept(btn);
    }

    private void initSmallIcon(int guiLeft, int guiTop) {
        List<String> teamColors = Arrays.stream(DyeColor.values())
                .map(DyeColor::getName)
                .collect(java.util.stream.Collectors.toList());
        java.util.Collections.reverse(teamColors);

        int firstOff = BetterMineTeam.IS_CONFLUENCE_LOADED ? CONFLUENCE_OFFSET : 0;

        for (int i = 0; i < teamColors.size(); i++) {
            String newTeamColor = teamColors.get(i);
            int col = i / 8;
            int row = i % 8;

            int x = guiLeft - SMALL_ICON_SIZE - col * SMALL_ICON_SIZE - col * SMALL_ICON_SPACING;
            int y = guiTop + row * SMALL_ICON_SIZE + row * SMALL_ICON_SPACING;

            ResourceLocation smallIconLoc = BetterMineTeam.asResource("textures/gui/team/small/" + newTeamColor + "_team_small_icon.png");

            ImageButton teamSmallIconBtn = new ImageButton(
                    x, y + firstOff,
                    SMALL_ICON_SIZE, SMALL_ICON_SIZE,
                    0, 0, SMALL_ICON_SIZE,
                    smallIconLoc,
                    SMALL_ICON_SIZE, SMALL_ICON_SIZE,
                    button -> {
                        sendChangeTeamPacket(newTeamColor);
                        visibleTeamSmallIcon(false);
                        this.teamIcon.visible = true;
                    }
            );
            teamSmallIconBtn.visible = false;
            teamSmallIcons.put(newTeamColor, teamSmallIconBtn);
        }
    }

    private void checkAndUpdateState() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Scoreboard scoreboard = player.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());

        String currentTeamName = (team != null) ? team.getName() : "null";
        boolean currentPvPState = (team != null) && team.isAllowFriendlyFire();

        if (Objects.equals(currentTeamName, lastTeamName) && currentPvPState == lastPvPState) {
            return;
        }

        this.lastTeamName = currentTeamName;
        this.lastPvPState = currentPvPState;

        updateButtonsState(team);
    }

    private void updateButtonsState(PlayerTeam team) {
        if (team == null) {
            this.teamPVPOn.visible = false;
            this.teamPVPOff.visible = false;
            // 无法更改纹理，保持原样
            return;
        }

        boolean isPvPEnabled = team.isAllowFriendlyFire();
        if (this.teamIcon.visible) {
            this.teamPVPOn.visible = isPvPEnabled;
            this.teamPVPOff.visible = !isPvPEnabled;
        }

        // [注意] 由于无法动态更改纹理，颜色切换后需要重新创建按钮
        // 这是 1.20.1 的限制
    }

    private void sendChangeTeamPacket(String colorName) {
        // Forge 1.20.1 网络发送方式
        MTNetworkRegister.CHANNEL.sendToServer(new TeamActionPacket(0, colorName, false));
    }

    private void sendPvPPacket(boolean enablePvP) {
        MTNetworkRegister.CHANNEL.sendToServer(new TeamActionPacket(1, "", enablePvP));
    }

    private void visibleTeamSmallIcon(boolean visible) {
        for (ImageButton button : teamSmallIcons.values()) {
            button.visible = visible;
        }
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
}