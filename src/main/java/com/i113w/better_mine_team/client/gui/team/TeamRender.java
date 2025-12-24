package com.i113w.better_mine_team.client.gui.team;

import com.google.common.collect.Maps;
import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.network.TeamActionPayload;
import com.i113w.better_mine_team.common.team.TeamManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.ChatFormatting;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TeamRender {
    private final EffectRenderingInventoryScreen<? extends AbstractContainerMenu> screen;

    private ImageButton teamIcon;
    private ImageButton teamPVPOn;
    private ImageButton teamPVPOff;
    private final Map<String, ImageButton> teamSmallIcons = Maps.newHashMap();

    // 缓存状态，用于检测变化
    private String lastTeamName = "";
    private boolean lastPvPState = false;

    public TeamRender(EffectRenderingInventoryScreen<? extends AbstractContainerMenu> screen) {
        this.screen = screen;
    }

    public void renderTeamIcon(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (teamIcon == null || teamPVPOff == null || teamPVPOn == null || teamSmallIcons.isEmpty()) {
            return;
        }

        // --- 核心优化：每帧检查状态更新 ---
        // 这确保了当你换队成功后，UI 会立即变成新的颜色，而不需要重开界面
        checkAndUpdateState();

        // 渲染主图标
        if (this.teamIcon.visible) {
            this.teamIcon.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // 渲染 PvP 按钮
        if (this.teamPVPOff.visible) this.teamPVPOff.render(guiGraphics, mouseX, mouseY, partialTick);
        if (this.teamPVPOn.visible) this.teamPVPOn.render(guiGraphics, mouseX, mouseY, partialTick);

        // 渲染颜色选择小图标
        renderTeamSmallIcon(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderTeamSmallIcon(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        for (ImageButton button : teamSmallIcons.values()) {
            if (button.visible) {
                button.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }

    public void initButton() {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) return;

        // 获取初始颜色，防止空指针，默认白色
        String teamColor = DyeColor.WHITE.getName();
        Scoreboard scoreboard = localPlayer.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(localPlayer.getScoreboardName());

        if (team != null) {
            // 修复：使用辅助方法获取颜色名，不再写空 if
            teamColor = getTextureColorName(team);
        }

        int iconSize = 16;
        int off = 6;

        // 1. 初始化主图标
        this.teamIcon = new ImageButton(screen.leftPos - iconSize, screen.topPos, iconSize, iconSize,
                createWidgetSprites("team/" + teamColor + "_team_icon"),
                button -> {
                    this.teamIcon.visible = false;
                    this.teamPVPOn.visible = false;
                    this.teamPVPOff.visible = false;
                    visibleTeamSmallIcon(true);
                });

        // 2. 初始化 PvP 按钮
        // PvP Off 按钮：点击开启 PvP
        this.teamPVPOff = new ImageButton(screen.leftPos - iconSize, screen.topPos + iconSize + off, iconSize, iconSize,
                createWidgetSprites("team/pvp/" + teamColor + "_pvp_off"),
                button -> sendPvPPacket(true));

        // PvP On 按钮：点击关闭 PvP
        this.teamPVPOn = new ImageButton(screen.leftPos - iconSize, screen.topPos + iconSize + off, iconSize, iconSize,
                createWidgetSprites("team/pvp/" + teamColor + "_pvp_on"),
                button -> sendPvPPacket(false));

        // 3. 初始化颜色选择板
        initSmallIcon();

        // 4. 强制刷新一次状态
        this.lastTeamName = ""; // 重置缓存以强制刷新
        checkAndUpdateState();

        addRenderableWidget();
    }

    private void initSmallIcon() {
        List<String> teamColors = Arrays.stream(DyeColor.values())
                .map(DyeColor::getName)
                .toList().reversed();

        int size = 8;
        // 如果有冲突模组，偏移一下位置 (这里假设原作者的意图)
        int firstOff = BetterMineTeam.IS_CONFLUENCE_LOADED ? 22 : 0;

        for (int i = 0; i < teamColors.size(); i++) {
            String newTeamColor = teamColors.get(i);
            int x = screen.leftPos - size - (i / 8) * size - (i / 8) * 2;
            int y = screen.topPos + (i % 8) * size + (i % 8) * 2;

            ImageButton teamSmallIconBtn = new ImageButton(x, y + firstOff, size, size,
                    createWidgetSprites("team/small/" + newTeamColor + "_team_small_icon"),
                    button -> {
                        sendChangeTeamPacket(newTeamColor);
                        // UI 交互优化：点击后先收起面板，等待服务器同步
                        visibleTeamSmallIcon(false);
                        this.teamIcon.visible = true;
                    });
            teamSmallIconBtn.visible = false;
            teamSmallIcons.put(newTeamColor, teamSmallIconBtn);
        }
    }

    public void addRenderableWidget() {
        screen.addRenderableWidget(this.teamIcon);
        screen.addRenderableWidget(this.teamPVPOn);
        screen.addRenderableWidget(this.teamPVPOff);
        for (ImageButton button : teamSmallIcons.values()) {
            screen.addRenderableWidget(button);
        }
    }

    /**
     * 每一帧检查计分板数据，如果发生变化（例如服务器处理了换队请求），
     * 则更新按钮的贴图和可见性。
     */
    private void checkAndUpdateState() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Scoreboard scoreboard = player.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());

        // 获取当前实际数据
        String currentTeamName = (team != null) ? team.getName() : "null";
        boolean currentPvPState = (team != null) && team.isAllowFriendlyFire();

        // 对比缓存，如果没有变化，直接返回（节省性能）
        if (Objects.equals(currentTeamName, lastTeamName) && currentPvPState == lastPvPState) {
            return;
        }

        // --- 数据变了，开始更新 UI ---
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

        // 1. 更新 PvP 按钮可见性
        boolean isPvPEnabled = team.isAllowFriendlyFire();
        if (this.teamIcon.visible) {
            this.teamPVPOn.visible = isPvPEnabled;
            this.teamPVPOff.visible = !isPvPEnabled;
        }

        // 2. 获取颜色名 (所有逻辑都收进这个方法里了)
        String colorName = getTextureColorName(team);

        // 3. 只调用一次设置贴图的方法
        setImageButtonSprites(this.teamIcon, "team/" + colorName + "_team_icon");
        setImageButtonSprites(this.teamPVPOn, "team/pvp/" + colorName + "_pvp_on");
        setImageButtonSprites(this.teamPVPOff, "team/pvp/" + colorName + "_pvp_off");
    }

    // --- 网络通信 ---

    private void sendChangeTeamPacket(String colorName) {
        PacketDistributor.sendToServer(new TeamActionPayload(0, colorName, false));
    }

    private void sendPvPPacket(boolean enablePvP) {
        PacketDistributor.sendToServer(new TeamActionPayload(1, "", enablePvP));
    }

    // --- 辅助方法 ---

    private void visibleTeamSmallIcon(boolean visible) {
        for (ImageButton button : teamSmallIcons.values()) {
            button.visible = visible;
        }
    }

    private void setImageButtonSprites(ImageButton button, String path) {
        // 更新按钮的材质
        ResourceLocation loc = BetterMineTeam.asResource(path);
        // 注意：这里假设 enabled 和 disabled 用同一张图，如果不是，需要传两个不同的 ResourceLocation
        button.sprites = new WidgetSprites(loc, loc);
    }

    private WidgetSprites createWidgetSprites(String path) {
        ResourceLocation loc = BetterMineTeam.asResource(path);
        return new WidgetSprites(loc, loc);
    }

    private String getTextureColorName(PlayerTeam team) {
        // 优先级 1: 模组原生队伍 (前缀匹配)
        if (team.getName().startsWith(TeamManager.TEAM_PREFIX)) {
            return team.getName().substring(TeamManager.TEAM_PREFIX.length());
        }

        // 优先级 2: 计分板显式颜色属性 (ChatFormatting)
        ChatFormatting formatting = team.getColor();
        if (formatting != ChatFormatting.RESET) {
            String name = formatting.getName().toLowerCase();
            // 映射 ChatFormatting 到 DyeColor
            if (name.equals("gold")) return "orange";
            if (name.equals("aqua")) return "light_blue";
            if (name.equals("light_purple")) return "magenta";
            if (name.equals("dark_purple")) return "purple";
            if (name.equals("dark_blue")) return "blue";
            if (name.equals("dark_green")) return "green";
            if (name.equals("dark_red")) return "red";
            if (name.equals("dark_aqua")) return "cyan";

            if (DyeColor.byName(name, null) != null) return name;
        }

        // 优先级 3: 检查队伍名字里是否包含颜色关键词 (用于 FTB Teams 等)
        String teamNameLower = team.getName().toLowerCase();
        for (DyeColor color : DyeColor.values()) {
            if (teamNameLower.contains(color.getName())) {
                return color.getName();
            }
        }

        // 优先级 4: 最后保底回退到白色
        return "white";
    }
}