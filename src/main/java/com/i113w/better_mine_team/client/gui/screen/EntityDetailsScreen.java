package com.i113w.better_mine_team.client.gui.screen;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.client.gui.asset.MTGuiIcons;
import com.i113w.better_mine_team.common.menu.EntityDetailsMenu;
import com.i113w.better_mine_team.common.network.TeamManagementPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public class EntityDetailsScreen extends AbstractContainerScreen<EntityDetailsMenu> {

    private static final ResourceLocation BG_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BetterMineTeam.MODID, "textures/gui/entity_details.png"
    );

    // 宽屏布局参数
    private static final int TEXTURE_SIZE = 256;
    private static final int CONTENT_WIDTH = 248;
    private static final int CONTENT_HEIGHT = 220;

    private float xMouse;
    private float yMouse;

    // 组件
    private EditBox nameField;
    private boolean isRenaming = false;
    private IconButton followButton;

    public EntityDetailsScreen(EntityDetailsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = CONTENT_WIDTH;
        this.imageHeight = CONTENT_HEIGHT;
        // 调整标题和物品栏文字位置
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

        // === 1. 初始化重命名输入框 ===
        // 放在左侧按钮区域上方，或者覆盖在标题位置
        // 这里放在左侧按钮区上方 (x=7, y=115 是按钮区，我们放上面一点)
        this.nameField = new EditBox(this.font, this.leftPos + 7, this.topPos + 115, 70, 12, Component.literal("Name"));
        this.nameField.setMaxLength(32);
        this.nameField.setBordered(true);
        this.nameField.setVisible(false); // 默认隐藏
        this.nameField.setValue(this.menu.getTargetEntity().getDisplayName().getString());
        this.addRenderableWidget(this.nameField);

        // === 2. 初始化功能按钮 ===
        // 按钮区域起始: x=7, y=130 (输入框下面)
        int btnX = this.leftPos + 7;
        int btnY = this.topPos + 130;
        int spacing = 22;

        // [A] 传送 (Teleport)
        addIconButton(btnX, btnY, MTGuiIcons.ICON_TELEPORT, (btn) -> {
            sendAction(TeamManagementPayload.ACTION_TELEPORT, "");
        }, "Teleport Entity");

        // [B] 重命名 (Rename)
        addIconButton(btnX + spacing, btnY, MTGuiIcons.ICON_RENAME, (btn) -> {
            toggleRenameMode();
        }, "Rename Entity");

        // [C] 跟随 (Follow) - 需要动态图标
        this.followButton = new IconButton(btnX, btnY + spacing, MTGuiIcons.ICON_FOLLOW_OFF, (btn) -> {
            sendAction(TeamManagementPayload.ACTION_TOGGLE_FOLLOW, "");
            // 客户端预判更新图标 (实际状态由服务端同步)
            boolean current = this.menu.getTargetEntity().getPersistentData().getBoolean("bmt_follow_enabled");
            this.menu.getTargetEntity().getPersistentData().putBoolean("bmt_follow_enabled", !current);
            updateFollowButtonState();
        }, "Toggle Follow");
        this.addRenderableWidget(this.followButton);
        updateFollowButtonState(); // 初始化状态

        // [D] RTS 模式 (占位)
        addIconButton(btnX + spacing, btnY + spacing, MTGuiIcons.ICON_RTS, (btn) -> {
            // RTS 逻辑预留
        }, "RTS Mode (Coming Soon)");
    }

    private void updateFollowButtonState() {
        boolean isFollowing = this.menu.getTargetEntity().getPersistentData().getBoolean("bmt_follow_enabled");
        this.followButton.setIcon(isFollowing ? MTGuiIcons.ICON_FOLLOW_ON : MTGuiIcons.ICON_FOLLOW_OFF);
    }

    private void toggleRenameMode() {
        this.isRenaming = !this.isRenaming;
        this.nameField.setVisible(this.isRenaming);
        this.nameField.setFocused(this.isRenaming);
        if (this.isRenaming) {
            // 进入重命名模式：更新输入框内容为当前名字
            this.nameField.setValue(this.menu.getTargetEntity().getDisplayName().getString());
        } else {
            // 退出重命名模式：发送改名包
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

    private void addIconButton(int x, int y, MTGuiIcons icon, Button.OnPress onPress, String tooltip) {
        this.addRenderableWidget(new IconButton(x, y, icon, onPress, tooltip));
    }

    // --- 键盘交互 (防止按E退出) ---
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.isRenaming) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                confirmRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                // 取消重命名，但不关闭界面
                this.isRenaming = false;
                this.nameField.setVisible(false);
                return true;
            }
            // 如果输入框聚焦，拦截所有按键，防止触发 E 关闭界面
            if (this.nameField.isFocused()) {
                return this.nameField.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // --- 渲染逻辑 ---

    //@Override
    //public void renderBackground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // 留空，阻断原版模糊
    //}

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.xMouse = mouseX;
        this.yMouse = mouseY;

        // 1. 变暗背景
        gfx.fill(0, 0, this.width, this.height, 0x50000000);

        // 2. 绘制 GUI
        super.render(gfx, mouseX, mouseY, partialTick);

        // 3. 绘制 Tooltip
        this.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // A. 背景纹理
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        gfx.blit(BG_TEXTURE, this.leftPos, this.topPos, 0, 0,
                this.imageWidth, this.imageHeight, TEXTURE_SIZE, TEXTURE_SIZE);

        // B. 3D 实体渲染 (阈值缩放)
        LivingEntity entity = this.menu.getTargetEntity();
        if (entity != null) {
            float height = entity.getBbHeight();
            int scale;

            // 阈值缩放逻辑
            if (height < 0.9) { // 像鸡、兔子这么小的
                scale = 45; // 放大
            } else if (height > 2.2) { // 像末影人这么高的
                scale = 18; // 缩小
            } else {
                // 正常体型 (1.8m 左右)
                scale = 30;
            }

            // 渲染框位置: x=7, y=17, w=51, h=72
            // 中心 x = 7 + 25 = 32
            // 底部 y = 17 + 72 - 5 = 84
            int entityX = this.leftPos + 32;
            int entityY = this.topPos + 84;

            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 50); // Z轴抬高
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

        // C. 锁定图标渲染
        for (Slot slot : this.menu.slots) {
            if (slot instanceof EntityDetailsMenu.DisabledSlot) {
                int x = this.leftPos + slot.x;
                int y = this.topPos + slot.y;

                // 灰色遮罩
                gfx.fill(x, y, x + 16, y + 16, 0x508B8B8B);

                // 锁图标 (16x16)
                // 槽位是 18x18 (包含边框)，物品区域是 16x16
                // Slot.x/y 指向的是物品区域左上角
                // 所以直接画在 x, y 即可居中
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
        InventoryScreen.renderEntityInInventory(gfx, (float)x, (float)y, (float)scale, new Vector3f(0,0,0), quaternionf, quaternionf1, entity);
        entity.yBodyRot = yBodyRot;
        entity.setYRot(yHeadRot);
        entity.setXRot(xRot);
        entity.yHeadRot = yHeadRot;
        entity.yRotO = yRotO;
        entity.xRotO = xRotO;
    }

    // --- 自定义按钮 (支持 Pressed 状态保持) ---
    private static class IconButton extends Button {
        private MTGuiIcons icon;
        private long lastPressTime = 0; // 记录最后一次点击的时间

        protected IconButton(int x, int y, MTGuiIcons icon, OnPress onPress, String tooltip) {
            super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
            this.icon = icon;
            this.setTooltip(Tooltip.create(Component.literal(tooltip)));
        }

        public void setIcon(MTGuiIcons newIcon) {
            this.icon = newIcon;
        }

        @Override
        public void onPress() {
            super.onPress();
            this.lastPressTime = System.currentTimeMillis(); // 记录点击时间
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            boolean isPressed = System.currentTimeMillis() - lastPressTime < 1000; // 1000ms = 20 ticks

            // 1. 绘制底座
            if (isPressed) {
                MTGuiIcons.BUTTON_PRESSED.render(gfx, this.getX(), this.getY());
            } else if (this.isHoveredOrFocused()) {
                MTGuiIcons.BUTTON_HOVER.render(gfx, this.getX(), this.getY());
            } else {
                MTGuiIcons.BUTTON_NORMAL.render(gfx, this.getX(), this.getY());
            }

            // 2. 绘制图标 (居中偏移 2px)
            this.icon.render(gfx, this.getX() + 2, this.getY() + 2);
        }
    }
}