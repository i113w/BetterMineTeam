package com.i113w.better_mine_team.client.gui.team;

import com.google.common.collect.Maps;
import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.network.TeamActionPayload;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.function.Consumer;

public class TeamRender {
    // 缓存：将 TeamRender 实例绑定到具体的 Screen 对象上
    // 使用 WeakHashMap 防止内存泄漏，当 Screen 关闭被回收时，TeamRender 也会自动消失
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

    // 私有构造，强制通过 attachTo 创建
    private TeamRender(AbstractContainerScreen<?> screen, Consumer<GuiEventListener> widgetAdder) {
        this.screen = screen;
        this.widgetAdder = widgetAdder;
    }

    /**
     * [核心方法] 将 TeamRender 挂载到 Screen 初始化事件上
     */
    public static void attachTo(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> containerScreen) {
            // 创建实例
            TeamRender render = new TeamRender(containerScreen, event::addListener);
            // 初始化按钮
            render.initButton();
            // 存入缓存
            INSTANCES.put(containerScreen, render);
        }
    }

    /**
     * [核心方法] 在 Screen 渲染事件中调用
     */
    public static void onRender(ScreenEvent.Render.Post event) {
        TeamRender render = INSTANCES.get(event.getScreen());
        if (render != null) {
            render.tick();
        }
    }

    /**
     * 每帧检查状态 (替代原来的 checkAndUpdateState 调用时机)
     */
    public void tick() {
        if (teamIcon == null) return;

        // 检查状态变化并更新按钮纹理
        checkAndUpdateState();

        // 注意：我们不需要手动调用 button.render()
        // 因为在 initButton() 里我们通过 widgetAdder (event::addListener) 把按钮加进了 Screen 的组件列表
        // Screen 会自动渲染它们。
        // 我们只需要控制 visible 属性即可。
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

        // 1. 初始化主图标
        this.teamIcon = new ImageButton(
                guiLeft - MAIN_ICON_SIZE,
                guiTop,
                MAIN_ICON_SIZE, MAIN_ICON_SIZE,
                createWidgetSprites("team/" + teamColor + "_team_icon"),
                button -> {
                    this.teamIcon.visible = false;
                    this.teamPVPOn.visible = false;
                    this.teamPVPOff.visible = false;
                    visibleTeamSmallIcon(true);
                });

        // 2. 初始化 PvP 按钮
        this.teamPVPOff = new ImageButton(
                guiLeft - MAIN_ICON_SIZE,
                guiTop + MAIN_ICON_SIZE + MAIN_ICON_OFFSET,
                MAIN_ICON_SIZE, MAIN_ICON_SIZE,
                createWidgetSprites("team/pvp/" + teamColor + "_pvp_off"),
                button -> sendPvPPacket(true));

        this.teamPVPOn = new ImageButton(
                guiLeft - MAIN_ICON_SIZE,
                guiTop + MAIN_ICON_SIZE + MAIN_ICON_OFFSET,
                MAIN_ICON_SIZE, MAIN_ICON_SIZE,
                createWidgetSprites("team/pvp/" + teamColor + "_pvp_on"),
                button -> sendPvPPacket(false));

        // 3. 初始化颜色选择板
        initSmallIcon(guiLeft, guiTop);

        // 4. 注册到 Screen (这样 Screen 就会自动处理点击和渲染)
        addRenderableWidget();

        // 5. 初始状态同步
        this.lastTeamName = "";
        checkAndUpdateState();
    }

    private void addRenderableWidget() {
        // 使用 Consumer 回调将按钮注册进 Screen
        addWidget(this.teamIcon);
        addWidget(this.teamPVPOn);
        addWidget(this.teamPVPOff);
        for (ImageButton button : teamSmallIcons.values()) {
            addWidget(button);
        }
    }

    // 辅助方法：处理泛型转换，因为 ImageButton 既是 GuiEventListener 又是 Renderable
    private void addWidget(ImageButton btn) {
        this.widgetAdder.accept(btn);
    }

    private void initSmallIcon(int guiLeft, int guiTop) {
        List<String> teamColors = Arrays.stream(DyeColor.values())
                .map(DyeColor::getName)
                .toList().reversed();

        int firstOff = BetterMineTeam.IS_CONFLUENCE_LOADED ? CONFLUENCE_OFFSET : 0;

        for (int i = 0; i < teamColors.size(); i++) {
            String newTeamColor = teamColors.get(i);
            int col = i / 8;
            int row = i % 8;

            int x = guiLeft - SMALL_ICON_SIZE - col * SMALL_ICON_SIZE - col * SMALL_ICON_SPACING;
            int y = guiTop + row * SMALL_ICON_SIZE + row * SMALL_ICON_SPACING;

            ImageButton teamSmallIconBtn = new ImageButton(x, y + firstOff, SMALL_ICON_SIZE, SMALL_ICON_SIZE,
                    createWidgetSprites("team/small/" + newTeamColor + "_team_small_icon"),
                    button -> {
                        sendChangeTeamPacket(newTeamColor);
                        visibleTeamSmallIcon(false);
                        this.teamIcon.visible = true;
                    });
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
            setImageButtonSprites(this.teamIcon, "team/white_team_icon");
            return;
        }

        boolean isPvPEnabled = team.isAllowFriendlyFire();
        if (this.teamIcon.visible) {
            this.teamPVPOn.visible = isPvPEnabled;
            this.teamPVPOff.visible = !isPvPEnabled;
        }

        String colorName = getTextureColorName(team);

        setImageButtonSprites(this.teamIcon, "team/" + colorName + "_team_icon");
        setImageButtonSprites(this.teamPVPOn, "team/pvp/" + colorName + "_pvp_on");
        setImageButtonSprites(this.teamPVPOff, "team/pvp/" + colorName + "_pvp_off");
    }

    private void sendChangeTeamPacket(String colorName) {
        PacketDistributor.sendToServer(new TeamActionPayload(0, colorName, false));
    }

    private void sendPvPPacket(boolean enablePvP) {
        PacketDistributor.sendToServer(new TeamActionPayload(1, "", enablePvP));
    }

    private void visibleTeamSmallIcon(boolean visible) {
        for (ImageButton button : teamSmallIcons.values()) {
            button.visible = visible;
        }
    }

    private void setImageButtonSprites(ImageButton button, String path) {
        ResourceLocation loc = BetterMineTeam.asResource(path);
        button.sprites = new WidgetSprites(loc, loc);
    }

    private WidgetSprites createWidgetSprites(String path) {
        ResourceLocation loc = BetterMineTeam.asResource(path);
        return new WidgetSprites(loc, loc);
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