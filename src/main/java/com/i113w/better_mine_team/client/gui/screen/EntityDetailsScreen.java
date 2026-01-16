package com.i113w.better_mine_team.client.gui.screen;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.asset.MTGuiIcons;
import com.i113w.better_mine_team.common.menu.EntityDetailsMenu;
import com.i113w.better_mine_team.common.network.TeamManagementPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public class EntityDetailsScreen extends AbstractContainerScreen<EntityDetailsMenu> {

    private static final ResourceLocation BG_TEXTURE = new ResourceLocation(
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

    public EntityDetailsScreen(EntityDetailsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = CONTENT_WIDTH;
        this.imageHeight = CONTENT_HEIGHT;
        this.inventoryLabelX = 84;
        this.inventoryLabelY = 103 - 10;
        this.titleLabelX = 84;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        this.nameField = new EditBox(this.font, this.leftPos + 7, this.topPos + 115, 70, 12,
                Component.translatable("better_mine_team.gui.label.name"));
        this.nameField.setMaxLength(32);
        this.nameField.setBordered(true);
        this.nameField.setVisible(false);
        this.nameField.setValue(this.menu.getTargetEntity().getDisplayName().getString());
        this.addRenderableWidget(this.nameField);

        int btnX = this.leftPos + 7;
        int btnY = this.topPos + 130;
        int spacing = 22;

        // 使用辅助方法添加按钮，它会自动将 String key 转换为 Component
        addIconButton(btnX, btnY, MTGuiIcons.ICON_TELEPORT, (btn) -> {
            sendAction(TeamManagementPacket.ACTION_TELEPORT, "");
        }, "better_mine_team.gui.tooltip.teleport");

        addIconButton(btnX + spacing, btnY, MTGuiIcons.ICON_RENAME, (btn) -> {
            toggleRenameMode();
        }, "better_mine_team.gui.tooltip.rename");

        // [修复] 手动通过 new IconButton 创建时，必须显式调用 Component.translatable
        this.followButton = new IconButton(
                btnX,
                btnY + spacing,
                MTGuiIcons.ICON_FOLLOW_OFF,
                (btn) -> {
                    sendAction(TeamManagementPacket.ACTION_TOGGLE_FOLLOW, "");
                    // 移除旧的预测逻辑，等待服务器同步
                },
                Component.translatable("better_mine_team.gui.tooltip.follow") // 修复处：String -> Component
        );
        this.addRenderableWidget(this.followButton);
        updateFollowButtonState();

        addIconButton(btnX + spacing, btnY + spacing, MTGuiIcons.ICON_RTS, (btn) -> {
        }, "better_mine_team.gui.tooltip.rts");
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updateFollowButtonState();
    }

    private void updateFollowButtonState() {
        if (this.menu.getTargetEntity() == null) return;
        boolean isFollowing = this.menu.getTargetEntity().getPersistentData().getBoolean("bmt_follow_enabled");
        this.followButton.setIcon(isFollowing ? MTGuiIcons.ICON_FOLLOW_ON : MTGuiIcons.ICON_FOLLOW_OFF);
    }

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
            sendAction(TeamManagementPacket.ACTION_RENAME, newName);
        }
        this.isRenaming = false;
        this.nameField.setVisible(false);
    }

    private void sendAction(int action, String data) {
        com.i113w.better_mine_team.common.init.MTNetworkRegister.CHANNEL.sendToServer(
                new com.i113w.better_mine_team.common.network.TeamManagementPacket(
                        action,
                        this.menu.getTargetEntity().getId(),
                        data
                )
        );
    }

    // 辅助方法：将 String key 自动转为 Component 传入 IconButton
    private void addIconButton(int x, int y, MTGuiIcons icon, Button.OnPress onPress, String tooltipKey) {
        this.addRenderableWidget(new IconButton(x, y, icon, onPress, Component.translatable(tooltipKey)));
    }

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
            int scale;
            if (height < 0.9) {
                scale = 45;
            } else if (height > 2.2) {
                scale = 18;
            } else {
                scale = 30;
            }

            int entityX = this.leftPos + 32;
            int entityY = this.topPos + 84;

            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 50);
            RenderSystem.enableDepthTest();

            renderEntityInInventoryFollowsMouse(gfx,
                    entityX, entityY,
                    scale,
                    (float)entityX - this.xMouse,
                    (float)(this.topPos + 40) - this.yMouse,
                    entity);

            RenderSystem.disableDepthTest();
            gfx.pose().popPose();
        }

        for (Slot slot : this.menu.slots) {
            if (slot instanceof EntityDetailsMenu.DisabledSlot) {
                int x = this.leftPos + slot.x;
                int y = this.topPos + slot.y;
                gfx.fill(x, y, x + 16, y + 16, 0x508B8B8B);
                RenderSystem.enableBlend();
                MTGuiIcons.ICON_LOCKED_INVENTORY.render(gfx, x, y);
                RenderSystem.disableBlend();
            }
        }
    }

    @SuppressWarnings("all")
    private void renderEntityInInventoryFollowsMouse(GuiGraphics gfx, int x, int y, int scale, float mouseX, float mouseY, LivingEntity entity) {
        float f = (float)Math.atan((double)(mouseX / 40.0F));
        float f1 = (float)Math.atan((double)(mouseY / 40.0F));
        Quaternionf quaternionf = (new Quaternionf()).rotateZ((float)Math.PI);
        Quaternionf quaternionf1 = (new Quaternionf()).rotateX(f1 * 20.0F * ((float)Math.PI / 180F));
        quaternionf.mul(quaternionf1);
        float yBodyRot = entity.yBodyRot;
        float yHeadRot = entity.yHeadRot;
        float xRot = entity.getXRot();
        float yRotO = entity.yRotO;
        float xRotO = entity.xRotO;
        entity.yBodyRot = 180.0F + f * 20.0F;
        entity.setYRot(180.0F + f * 40.0F);
        entity.setXRot(-f1 * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yRotO = entity.getYRot();
        entity.xRotO = entity.getXRot();
        InventoryScreen.renderEntityInInventory(gfx, x, y, scale, quaternionf, quaternionf1, entity);        entity.yBodyRot = yBodyRot;
        entity.setYRot(yHeadRot);
        entity.setXRot(xRot);
        entity.yHeadRot = yHeadRot;
        entity.yRotO = yRotO;
        entity.xRotO = xRotO;
    }

    private static class IconButton extends Button {
        private MTGuiIcons icon;
        private long lastPressTime = 0;

        // 构造函数参数为 Component
        protected IconButton(int x, int y, MTGuiIcons icon, OnPress onPress, Component tooltip) {
            super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
            this.icon = icon;
            this.setTooltip(Tooltip.create(tooltip));
        }

        public void setIcon(MTGuiIcons newIcon) {
            this.icon = newIcon;
        }

        @Override
        public void onPress() {
            super.onPress();
            this.lastPressTime = System.currentTimeMillis();
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            boolean isPressed = System.currentTimeMillis() - lastPressTime < 1000;

            if (isPressed) {
                MTGuiIcons.BUTTON_PRESSED.render(gfx, this.getX(), this.getY());
            } else if (this.isHoveredOrFocused()) {
                MTGuiIcons.BUTTON_HOVER.render(gfx, this.getX(), this.getY());
            } else {
                MTGuiIcons.BUTTON_NORMAL.render(gfx, this.getX(), this.getY());
            }

            this.icon.render(gfx, this.getX() + 2, this.getY() + 2);
        }
    }
}