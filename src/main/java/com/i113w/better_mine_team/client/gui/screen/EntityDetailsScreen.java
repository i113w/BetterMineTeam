package com.i113w.better_mine_team.client.gui.screen;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.asset.MTGuiIcons;
import com.i113w.better_mine_team.common.menu.EntityDetailsMenu;
import com.i113w.better_mine_team.common.network.TeamManagementPayload;
import com.i113w.better_mine_team.common.team.TeamManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public class EntityDetailsScreen extends AbstractContainerScreen<EntityDetailsMenu> {

    private static final ResourceLocation BG_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BetterMineTeam.MODID, "textures/gui/entity_details.png"
    );

    private static final int TEXTURE_SIZE = 256;
    private static final int CONTENT_WIDTH = 248;
    private static final int CONTENT_HEIGHT = 220;

    private float xMouse;
    private float yMouse;

    private EditBox nameField;
    private boolean isRenaming = false;
    private IconButton followButton;

    // ── Aggressive level UI ──────────────────────────────────────────────────
    /**
     * Client-side cache of the target entity's aggressive level.
     * Updated by the server via {@link TeamManagementPayload#ACTION_GET_AGGRESSIVE_LEVEL}.
     */
    private int currentAggressiveLevel = 0;

    /**
     * The three aggressive level buttons (Passive / Guard / Aggressive).
     */
    @Nullable private IconButton aggressiveBtn0; // Passive
    @Nullable private IconButton aggressiveBtn1; // Guard
    @Nullable private IconButton aggressiveBtn2; // Aggressive
    // ─────────────────────────────────────────────────────────────────────────

    public EntityDetailsScreen(EntityDetailsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = CONTENT_WIDTH;
        this.imageHeight = CONTENT_HEIGHT;
        this.inventoryLabelX = 85;
        this.inventoryLabelY = 104 - 10;
        this.titleLabelX = 85;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        this.nameField = new EditBox(this.font,
                this.leftPos + 7, this.topPos + 115, 70, 12,
                Component.translatable("better_mine_team.gui.label.name"));
        this.nameField.setMaxLength(32);
        this.nameField.setBordered(true);
        this.nameField.setVisible(false);
        this.nameField.setValue(this.menu.getTargetEntity().getDisplayName().getString());
        this.addRenderableWidget(this.nameField);

        int btnX = this.leftPos + 7;
        int btnY = this.topPos + 130;
        int spacing = 22;

        // ── Row 1 ──
        // 传送按钮：单独向左向上偏移 1px
        IconButton teleportBtn = new IconButton(btnX, btnY, new ItemStack(Items.ENDER_PEARL), (btn) -> {
            sendAction(TeamManagementPayload.ACTION_TELEPORT, "");
        }, Component.translatable("better_mine_team.gui.tooltip.teleport"));
        teleportBtn.setIconOffset(-1, 0);
        this.addRenderableWidget(teleportBtn);

        // 重命名按钮 (Col 2)
        addItemButton(btnX + spacing, btnY, Items.NAME_TAG, (btn) -> {
            toggleRenameMode();
        }, "better_mine_team.gui.tooltip.rename");

        // 跟随按钮 (Col 3)
        this.followButton = new IconButton(
                btnX + spacing * 2, btnY,
                MTGuiIcons.ICON_FOLLOW_OFF,
                (btn) -> {
                    sendAction(TeamManagementPayload.ACTION_TOGGLE_FOLLOW, "");
                },
                Component.translatable("better_mine_team.gui.tooltip.follow")
        );
        this.addRenderableWidget(this.followButton);
        updateFollowButtonState();

        // ── Row 2: Aggressive Levels (Level 0, Level 1, Level 2) ──
        int aggrBtnY = btnY + spacing;

        this.aggressiveBtn0 = new IconButton(
                btnX, aggrBtnY, // Col 1
                MTGuiIcons.ICON_LEVEL_0,
                btn -> onAggressiveLevelChange(0),
                Component.translatable("better_mine_team.gui.tooltip.aggressive.level0")
        );
        this.aggressiveBtn1 = new IconButton(
                btnX + spacing, aggrBtnY, // Col 2
                MTGuiIcons.ICON_LEVEL_1,
                btn -> onAggressiveLevelChange(1),
                Component.translatable("better_mine_team.gui.tooltip.aggressive.level1")
        );
        this.aggressiveBtn2 = new IconButton(
                btnX + spacing * 2, aggrBtnY, // Col 3
                MTGuiIcons.ICON_LEVEL_2,
                btn -> onAggressiveLevelChange(2),
                Component.translatable("better_mine_team.gui.tooltip.aggressive.level2")
        );

        this.addRenderableWidget(this.aggressiveBtn0);
        this.addRenderableWidget(this.aggressiveBtn1);
        this.addRenderableWidget(this.aggressiveBtn2);

        // 向服务端请求当前 Aggressive 等级；服务端回包将调用 setAggressiveLevel()
        sendAction(TeamManagementPayload.ACTION_GET_AGGRESSIVE_LEVEL, "");
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void containerTick() {
        super.containerTick();
        updateFollowButtonState();
    }

    // ── Follow helpers ────────────────────────────────────────────────────────

    private void updateFollowButtonState() {
        if (this.menu.getTargetEntity() == null || this.followButton == null) return;
        boolean isFollowing = this.menu.getTargetEntity().getPersistentData().getBoolean("bmt_follow_enabled");
        this.followButton.setIcon(isFollowing ? MTGuiIcons.ICON_FOLLOW_ON : MTGuiIcons.ICON_FOLLOW_OFF);
    }

    // ── Aggressive level API (called from TeamManagementPayload.ClientHandler) ──

    /**
     * Updates the displayed aggressive level and refreshes button highlight states.
     * Called from the client-side packet handler when the server responds to
     * {@link TeamManagementPayload#ACTION_GET_AGGRESSIVE_LEVEL}.
     *
     * @param level 0 (Passive), 1 (Guard), 2 (Aggressive)
     */
    public void setAggressiveLevel(int level) {
        this.currentAggressiveLevel = Mth.clamp(level, 0, 2);
        refreshAggressiveButtonHighlights();
    }

    private void refreshAggressiveButtonHighlights() {
        if (aggressiveBtn0 == null) return; // called before init()
        aggressiveBtn0.setHighlighted(currentAggressiveLevel == 0);
        aggressiveBtn1.setHighlighted(currentAggressiveLevel == 1);
        aggressiveBtn2.setHighlighted(currentAggressiveLevel == 2);
    }

    private void onAggressiveLevelChange(int newLevel) {
        if (newLevel == currentAggressiveLevel) return;
        currentAggressiveLevel = newLevel;
        refreshAggressiveButtonHighlights();
        sendAction(TeamManagementPayload.ACTION_SET_AGGRESSIVE_LEVEL, String.valueOf(newLevel));
    }

    // ── Rename helpers ────────────────────────────────────────────────────────

    private void toggleRenameMode() {
        this.isRenaming = !this.isRenaming;
        this.nameField.setVisible(this.isRenaming);
        this.nameField.setFocused(this.isRenaming);
        if (this.isRenaming) {
            this.nameField.setValue(this.menu.getTargetEntity().getDisplayName().getString());
        } else {
            confirmRename();
        }
    }

    private void confirmRename() {
        String newName = this.nameField.getValue();
        if (!newName.isEmpty()) {
            sendAction(TeamManagementPayload.ACTION_RENAME, newName);
        }
        this.isRenaming = false;
        this.nameField.setVisible(false);
    }

    private void sendAction(int action, String data) {
        PacketDistributor.sendToServer(new TeamManagementPayload(
                action,
                this.menu.getTargetEntity().getId(),
                data
        ));
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.isRenaming) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                confirmRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.isRenaming = false;
                this.nameField.setVisible(false);
                return true;
            }
            if (this.nameField.isFocused()) {
                return this.nameField.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void addItemButton(int x, int y, Item item, Button.OnPress onPress, String tooltipKey) {
        this.addRenderableWidget(new IconButton(x, y, new ItemStack(item), onPress, Component.translatable(tooltipKey)));
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.xMouse = mouseX;
        this.yMouse = mouseY;
        gfx.fill(0, 0, this.width, this.height, 0x50000000);
        super.render(gfx, mouseX, mouseY, partialTick);
        this.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        gfx.blit(BG_TEXTURE, this.leftPos, this.topPos, 0, 0,
                this.imageWidth, this.imageHeight, TEXTURE_SIZE, TEXTURE_SIZE);

        LivingEntity entity = this.menu.getTargetEntity();
        if (entity != null) {
            float height = entity.getBbHeight();
            int scale = height < 0.9 ? 45 : (height > 2.2 ? 18 : 30);
            int entityX = this.leftPos + 32;
            int entityY = this.topPos + 84;

            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 50);
            RenderSystem.enableDepthTest();

            renderEntityInInventoryFollowsMouse(gfx, entityX, entityY, scale,
                    (float) entityX - this.xMouse,
                    (float) (this.topPos + 40) - this.yMouse,
                    entity);

            RenderSystem.disableDepthTest();
            gfx.pose().popPose();
        }

        for (Slot slot : this.menu.slots) {
            if (slot instanceof EntityDetailsMenu.DisabledSlot) {
                int x = this.leftPos + slot.x;
                int y = this.topPos  + slot.y;
                gfx.fill(x, y, x + 16, y + 16, 0x508B8B8B);
                RenderSystem.enableBlend();
                MTGuiIcons.ICON_LOCKED_INVENTORY.render(gfx, x, y);
                RenderSystem.disableBlend();
            }
        }
    }

    @SuppressWarnings("all")
    private void renderEntityInInventoryFollowsMouse(GuiGraphics gfx, int x, int y, int scale,
                                                     float mouseX, float mouseY, LivingEntity entity) {
        float f  = (float) Math.atan(mouseX / 40.0F);
        float f1 = (float) Math.atan(mouseY / 40.0F);
        Quaternionf quaternionf  = (new Quaternionf()).rotateZ((float) Math.PI);
        Quaternionf quaternionf1 = (new Quaternionf()).rotateX(f1 * 20.0F * ((float) Math.PI / 180F));
        quaternionf.mul(quaternionf1);

        float yBodyRot = entity.yBodyRot;
        float yHeadRot = entity.yHeadRot;
        float xRot     = entity.getXRot();
        float yRotO    = entity.yRotO;
        float xRotO    = entity.xRotO;

        entity.yBodyRot = 180.0F + f * 20.0F;
        entity.setYRot(180.0F + f * 40.0F);
        entity.setXRot(-f1 * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yRotO    = entity.getYRot();
        entity.xRotO    = entity.getXRot();

        InventoryScreen.renderEntityInInventory(gfx, (float) x, (float) y, (float) scale,
                new Vector3f(0, 0, 0), quaternionf, quaternionf1, entity);

        entity.yBodyRot = yBodyRot;
        entity.setYRot(yHeadRot);
        entity.setXRot(xRot);
        entity.yHeadRot = yHeadRot;
        entity.yRotO    = yRotO;
        entity.xRotO    = xRotO;
    }

    // ── IconButton ────────────────────────────────────────────────────────────

    /**
     * Extends the basic icon button with:
     * <ul>
     *   <li>Dual icon source: {@link MTGuiIcons} atlas or {@link ItemStack}.</li>
     *   <li>Highlighted state: renders a gold (0xFFFFD700) 1-pixel border when active,
     *       used to indicate the currently selected Aggressive level.</li>
     * </ul>
     */
    private static class IconButton extends Button {

        @Nullable private MTGuiIcons  icon;
        @Nullable private ItemStack   itemStack;
        private boolean highlighted  = false;
        private long    lastPressTime = 0;

        // 允许自由偏移图标绘制位置
        private int iconOffsetX = 0;
        private int iconOffsetY = 0;

        protected IconButton(int x, int y, MTGuiIcons icon, OnPress onPress, Component tooltip) {
            super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
            this.icon      = icon;
            this.itemStack = null;
            this.setTooltip(Tooltip.create(tooltip));
        }

        /** Constructor B: ItemStack icon (item rendered at 16×16, offset +2). */
        protected IconButton(int x, int y, ItemStack itemStack, OnPress onPress, Component tooltip) {
            super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
            this.itemStack = itemStack;
            this.icon      = null;
            this.setTooltip(Tooltip.create(tooltip));
        }

        public IconButton setIconOffset(int offsetX, int offsetY) {
            this.iconOffsetX = offsetX;
            this.iconOffsetY = offsetY;
            return this;
        }

        public void setIcon(MTGuiIcons newIcon) {
            this.icon      = newIcon;
            this.itemStack = null;
        }

        /**
         * Marks this button as the "active" state.
         * When {@code true} a gold border is drawn around the button background.
         */
        public void setHighlighted(boolean highlighted) {
            this.highlighted = highlighted;
        }

        @Override
        public void onPress() {
            super.onPress();
            this.lastPressTime = System.currentTimeMillis();
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            boolean isPressed = System.currentTimeMillis() - lastPressTime < 1000;

            // Highlighted border (1px gold ring drawn behind the button base)
            if (highlighted) {
                gfx.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, 0xFFFFD700);
            }

            // Button base
            if (isPressed) {
                MTGuiIcons.BUTTON_PRESSED.render(gfx, getX(), getY());
            } else if (isHoveredOrFocused()) {
                MTGuiIcons.BUTTON_HOVER.render(gfx, getX(), getY());
            } else {
                MTGuiIcons.BUTTON_NORMAL.render(gfx, getX(), getY());
            }

            int renderX = getX() + 2 + iconOffsetX;
            int renderY = getY() + 2 + iconOffsetY;

            if (this.itemStack != null) {
                gfx.renderItem(this.itemStack, renderX, renderY);
            } else if (this.icon != null) {
                this.icon.render(gfx, renderX, renderY);
            }
        }
    }
}