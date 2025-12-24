package com.i113w.better_mine_team.common.menu;

import com.i113w.better_mine_team.common.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;

public class EntityDetailsMenu extends AbstractContainerMenu {

    private final LivingEntity targetEntity;

    // 客户端构造器
    public EntityDetailsMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, getClientEntity(playerInv, extraData));
    }

    // 服务端构造器
    public EntityDetailsMenu(int containerId, Inventory playerInv, LivingEntity entity) {
        super(ModMenuTypes.ENTITY_DETAILS_MENU.get(), containerId);
        this.targetEntity = entity;

        if (entity == null) return;

        // === 左侧面板 (Left Panel) ===
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.HEAD, 60, 17));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.CHEST, 60, 35));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.LEGS, 60, 53));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.FEET, 60, 71));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.MAINHAND, 7, 93));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.OFFHAND, 25, 93));

        // === 右侧面板 (Right Panel) ===
        int gridStartX = 84;
        int gridStartY = 17;

        // 【核心修改】针对村民使用原生 Container 访问
        if (entity instanceof Villager villager) {
            layoutVillagerInventory(villager, gridStartX, gridStartY);
        } else {
            // 其他生物继续使用 Capability
            IItemHandler entityInv = getUnifiedInventory(entity);
            layoutStandardInventory(entityInv, gridStartX, gridStartY);
        }

        // 玩家背包
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 84 + col * 18, 103 + row * 18));
            }
        }
        // 玩家快捷栏
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 84 + col * 18, 161));
        }
    }

    /**
     * 村民专用布局：直接使用原生 Slot 和 SimpleContainer
     */
    private void layoutVillagerInventory(Villager villager, int startX, int startY) {
        // 获取原生容器 (SimpleContainer)
        SimpleContainer container = villager.getInventory();

        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 9; ++col) {
                int x = startX + col * 18;
                int y = startY + row * 18;

                // 只有第4行前8格是有效的
                if (row == 3 && col < 8) {
                    // 使用自定义的 VillagerSlot 来确保数据保存
                    this.addSlot(new VillagerSlot(villager, container, col, x, y));
                } else {
                    this.addSlot(new DisabledSlot(x, y));
                }
            }
        }
    }

    private void layoutStandardInventory(IItemHandler handler, int startX, int startY) {
        int actualSlots = handler.getSlots();
        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 9; ++col) {
                int displayIndex = col + row * 9;
                int x = startX + col * 18;
                int y = startY + row * 18;

                if (displayIndex < actualSlots) {
                    this.addSlot(new SlotItemHandler(handler, displayIndex, x, y));
                } else {
                    this.addSlot(new DisabledSlot(x, y));
                }
            }
        }
    }

    private IItemHandler getUnifiedInventory(LivingEntity entity) {
        var cap = entity.getCapability(Capabilities.ItemHandler.ENTITY);
        if (cap != null) return cap;
        if (entity instanceof InventoryCarrier carrier) {
            return new InvWrapper(carrier.getInventory());
        }
        return new ItemStackHandler(0);
    }

    private static LivingEntity getClientEntity(Inventory playerInv, FriendlyByteBuf data) {
        int entityId = data.readInt();
        net.minecraft.world.entity.Entity entity = playerInv.player.level().getEntity(entityId);
        if (entity instanceof LivingEntity living) return living;
        return playerInv.player;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return targetEntity != null && targetEntity.isAlive();
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    public LivingEntity getTargetEntity() {
        return targetEntity;
    }

    // --- 内部类 ---

    public static class DisabledSlot extends Slot {
        public DisabledSlot(int x, int y) { super(new SimpleContainer(1), 0, x, y); }
        @Override public boolean mayPlace(@NotNull ItemStack stack) { return false; }
        @Override public boolean mayPickup(@NotNull Player player) { return false; }
        @Override public boolean isActive() { return true; }
        @Override public void set(@NotNull ItemStack stack) {}
        @Override public @NotNull ItemStack getItem() { return ItemStack.EMPTY; }
    }

    public static class EntityEquipmentSlot extends Slot {
        private final LivingEntity entity;
        private final EquipmentSlot slot;
        public EntityEquipmentSlot(LivingEntity entity, EquipmentSlot slot, int x, int y) {
            super(new SimpleContainer(1), 0, x, y);
            this.entity = entity;
            this.slot = slot;
        }
        @Override public @NotNull ItemStack getItem() { return entity.getItemBySlot(slot); }
        @Override public void set(@NotNull ItemStack stack) { entity.setItemSlot(slot, stack); setChanged(); }
        @Override public void setChanged() {}
        @Override public boolean mayPlace(@NotNull ItemStack stack) { return true; }
        @Override public int getMaxStackSize() { return 1; }
    }

    /**
     * 【新增】村民专用 Slot
     * 作用：在物品变动时强制标记村民实体为 Dirty，确保数据保存。
     */
    public static class VillagerSlot extends Slot {
        // 移除未使用的 villager 字段，因为我们不需要调用它的方法了
        // private final Villager villager;

        public VillagerSlot(Villager villager, Container container, int slot, int x, int y) {
            super(container, slot, x, y);
            // this.villager = villager;
        }

        @Override
        public void setChanged() {
            // 调用父类方法，它会执行 container.setChanged()
            // 这对于 SimpleContainer 来说已经足够触发更新了
            super.setChanged();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // 允许玩家放入任何物品
            return true;
        }
    }

}