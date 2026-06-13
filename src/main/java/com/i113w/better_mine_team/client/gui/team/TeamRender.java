package com.i113w.better_mine_team.client.gui.team;

import com.google.common.collect.Maps;
import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.ClientTeamUiState;
import com.i113w.better_mine_team.client.gui.asset.MTGuiIcons;
import com.i113w.better_mine_team.common.network.TeamActionPayload;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import org.jetbrains.annotations.NotNull;

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

    // 按钮向左移动 5 pixel
    private static final int TEAM_BUTTON_X_OFFSET = -5;

    private final AbstractContainerScreen<?> screen;
    private final Consumer<GuiEventListener> widgetAdder;

    private ImageButton teamIcon;
    private ImageButton teamPVPOn;
    private ImageButton teamPVPOff;
    private PersonalTeamIconButton personalTeamToggle;
    private final Map<String, ImageButton> teamSmallIcons = Maps.newHashMap();

    private String lastTeamName = "";
    private boolean lastPvPState = false;
    private boolean lastPersonalTeamsAvailable = false;
    private boolean lastPersonalTeamEnabled = false;

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
        int mainButtonX = guiLeft - MAIN_ICON_SIZE + TEAM_BUTTON_X_OFFSET;

// 1. 初始化主图标
        this.teamIcon = new ImageButton(
                mainButtonX,
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
                mainButtonX,
                guiTop + MAIN_ICON_SIZE + MAIN_ICON_OFFSET,
                MAIN_ICON_SIZE, MAIN_ICON_SIZE,
                createWidgetSprites("team/pvp/" + teamColor + "_pvp_off"),
                button -> sendPvPPacket(true));

        this.teamPVPOn = new ImageButton(
                mainButtonX,
                guiTop + MAIN_ICON_SIZE + MAIN_ICON_OFFSET,
                MAIN_ICON_SIZE, MAIN_ICON_SIZE,
                createWidgetSprites("team/pvp/" + teamColor + "_pvp_on"),
                button -> sendPvPPacket(false));

        // 3. 初始化颜色选择板
        initSmallIcon(guiLeft, guiTop);

        // 4. 初始化个人队伍按钮
        this.personalTeamToggle = new PersonalTeamIconButton(
                mainButtonX,
                guiTop + 8 * (SMALL_ICON_SIZE + SMALL_ICON_SPACING) + MAIN_ICON_OFFSET,
                MTGuiIcons.ICON_PERSONAL_TEAM_OFF,
                button -> sendPersonalTeamPacket(!this.lastPersonalTeamEnabled),
                Component.translatable("better_mine_team.gui.tooltip.personal_team")
        );

        // 5. 注册到 Screen (这样 Screen 就会自动处理点击和渲染)
        addRenderableWidget();

        // 6. 初始状态同步
        this.lastTeamName = "";
        checkAndUpdateState();
    }

    private void addRenderableWidget() {
        // 使用 Consumer 回调将按钮注册进 Screen
        addWidget(this.teamIcon);
        addWidget(this.teamPVPOn);
        addWidget(this.teamPVPOff);
        addWidget(this.personalTeamToggle);
        for (ImageButton button : teamSmallIcons.values()) {
            addWidget(button);
        }
    }

    // 辅助方法：处理泛型转换，因为 ImageButton 既是 GuiEventListener 又是 Renderable
    private void addWidget(GuiEventListener widget) {
        this.widgetAdder.accept(widget);
    }

    private void initSmallIcon(int guiLeft, int guiTop) {
        // 原版颜色数组
        List<String> teamColors = Arrays.stream(TeamManager.ORIGINAL_DYE_COLORS)
                .map(DyeColor::getName)
                .toList().reversed();

        int firstOff = BetterMineTeam.IS_CONFLUENCE_LOADED ? CONFLUENCE_OFFSET : 0;

        for (int i = 0; i < teamColors.size(); i++) {
            String newTeamColor = teamColors.get(i);
            int col = i / 8;
            int row = i % 8;

            int x = guiLeft - SMALL_ICON_SIZE - col * SMALL_ICON_SIZE - col * SMALL_ICON_SPACING + TEAM_BUTTON_X_OFFSET;
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
        boolean personalTeamsAvailable = ClientTeamUiState.isPersonalTeamsAvailable(player);
        boolean personalTeamEnabled = ClientTeamUiState.isPersonalTeamEnabled(player);

        if (Objects.equals(currentTeamName, lastTeamName)
                && currentPvPState == lastPvPState
                && personalTeamsAvailable == lastPersonalTeamsAvailable
                && personalTeamEnabled == lastPersonalTeamEnabled) {
            return;
        }

        this.lastTeamName = currentTeamName;
        this.lastPvPState = currentPvPState;
        this.lastPersonalTeamsAvailable = personalTeamsAvailable;
        this.lastPersonalTeamEnabled = personalTeamEnabled;

        updateButtonsState(team);
    }

    private void updateButtonsState(PlayerTeam team) {
        updatePersonalTeamButton();

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
        PacketDistributor.sendToServer(new TeamActionPayload(TeamActionPayload.ACTION_CHANGE_TEAM, colorName, false));
    }

    private void sendPvPPacket(boolean enablePvP) {
        PacketDistributor.sendToServer(new TeamActionPayload(TeamActionPayload.ACTION_SET_PVP, "", enablePvP));
    }

    private void sendPersonalTeamPacket(boolean enabled) {
        PacketDistributor.sendToServer(new TeamActionPayload(TeamActionPayload.ACTION_SET_PERSONAL_TEAM, "", enabled));
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

    private void updatePersonalTeamButton() {
        if (this.personalTeamToggle == null) return;

        this.personalTeamToggle.visible = this.lastPersonalTeamsAvailable;
        this.personalTeamToggle.active = this.lastPersonalTeamsAvailable;
        this.personalTeamToggle.setIcon(
                this.lastPersonalTeamEnabled
                        ? MTGuiIcons.ICON_PERSONAL_TEAM_ON
                        : MTGuiIcons.ICON_PERSONAL_TEAM_OFF
        );
    }

    private String getTextureColorName(PlayerTeam team) {
        if (team.getName().startsWith(TeamManager.TEAM_PREFIX)) {
            String colorName = team.getName().substring(TeamManager.TEAM_PREFIX.length());
            // 只验证原版颜色
            if (TeamManager.getOriginalColorByName(colorName, null) != null) {
                return colorName;
            }
        }
        ChatFormatting formatting = team.getColor();
        if (formatting != ChatFormatting.RESET) {
            String name = formatting.getName().toLowerCase();
            // 使用安全方法验证
            if (TeamManager.getOriginalColorByName(name, null) != null) return name;
        }
        return "white";
    }
    private static class PersonalTeamIconButton extends Button {

        private MTGuiIcons icon;
        private long lastPressTime = 0L;

        private PersonalTeamIconButton(
                int x,
                int y,
                MTGuiIcons icon,
                OnPress onPress,
                Component tooltip
        ) {
            super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
            this.icon = icon;
            this.setTooltip(Tooltip.create(tooltip));
        }

        public void setIcon(MTGuiIcons icon) {
            this.icon = icon;
        }

        @Override
        public void onPress() {
            super.onPress();
            this.lastPressTime = System.currentTimeMillis();
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            boolean hovered = mouseX >= getX() && mouseX < getX() + width
                    && mouseY >= getY() && mouseY < getY() + height;
            boolean pressed = System.currentTimeMillis() - this.lastPressTime < 120L;

            MTGuiIcons base = !this.active
                    ? MTGuiIcons.BUTTON_DISABLED
                    : pressed
                    ? MTGuiIcons.BUTTON_PRESSED
                    : hovered
                    ? MTGuiIcons.BUTTON_HOVER
                    : MTGuiIcons.BUTTON_NORMAL;

            base.render(gfx, getX(), getY());

            if (this.icon != null) {
                this.icon.render(gfx, getX() + 2, getY() + 2);
            }
        }
    }
}
