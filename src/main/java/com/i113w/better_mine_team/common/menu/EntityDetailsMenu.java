package com.i113w.better_mine_team.common.menu;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.registry.ModMenuTypes;
import com.i113w.better_mine_team.common.team.TeamPermissions;
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

    public EntityDetailsMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, getClientEntity(playerInv, extraData));
    }

    public EntityDetailsMenu(int containerId, Inventory playerInv, LivingEntity entity) {
        super(ModMenuTypes.ENTITY_DETAILS_MENU.get(), containerId);
        this.targetEntity = entity;

        if (entity == null) return;

        // === 1. 左侧面板：通用装备栏 (所有生物都有) ===
        // 直接操作实体装备槽，不依赖 Capability，确保能给村民穿装备
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.HEAD, 61, 18));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.CHEST, 61, 36));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.LEGS, 61, 54));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.FEET, 61, 72));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.MAINHAND, 8, 94));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.OFFHAND, 26, 94));

        // === 2. 右侧面板：物品栏区域 ===
        int gridStartX = 85;
        int gridStartY = 18;

        if (entity instanceof Villager villager) {
            // 村民逻辑：混合布局
            layoutVillagerInventory(villager, gridStartX, gridStartY);
        } else {
            // 其他生物逻辑：标准 Capability 布局
            IItemHandler entityInv = getUnifiedInventory(entity);
            layoutStandardInventory(entityInv, gridStartX, gridStartY);
        }

        // === 3. 玩家背包 ===
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 85 + col * 18, 104 + row * 18));
            }
        }
        // === 4. 玩家快捷栏 ===
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 85 + col * 18, 162));
        }
    }

    /**
     * 村民专用混合布局
     * 前3行：尝试显示 Capability (如果有)
     * 第4行：显示村民原生 8 格背包
     */
    private void layoutVillagerInventory(Villager villager, int startX, int startY) {
        // 获取 Capability (用于前3行)
        IItemHandler capabilityHandler = getUnifiedInventory(villager);
        // 获取原生 Inventory (用于第4行)
        SimpleContainer villagerContainer = villager.getInventory();

        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 9; ++col) {
                int x = startX + col * 18;
                int y = startY + row * 18;

                // === 前3行 (Row 0-2)：显示 Capability ===
                if (row < 3) {
                    int slotIndex = col + row * 9;
                    // 如果 Capability 有足够的槽位，就显示
                    if (slotIndex < capabilityHandler.getSlots()) {
                        this.addSlot(new SlotItemHandler(capabilityHandler, slotIndex, x, y));
                    } else {
                        this.addSlot(new DisabledSlot(x, y));
                    }
                }
                // === 第4行 (Row 3)：显示村民原生背包 ===
                else {
                    if (col < 8) { // 村民背包只有 8 格
                        this.addSlot(new VillagerSlot(villager, villagerContainer, col, x, y));
                    } else {
                        this.addSlot(new DisabledSlot(x, y));
                    }
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
        try {
            int entityId = data.readInt();

            // 1. 基础空值检查
            if (playerInv.player == null || playerInv.player.level() == null) {
                return playerInv.player; // 安全回退
            }

            // 2. 获取实体
            net.minecraft.world.entity.Entity entity = playerInv.player.level().getEntity(entityId);

            // 3. 验证实体有效性 (必须是 LivingEntity 且 活着)
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return living;
            }

            // 4. 如果找不到或已死亡，回退到玩家自己，防止 GUI 崩溃
            return playerInv.player;

        } catch (Exception e) {
            BetterMineTeam.LOGGER.error("Error getting client entity for details menu", e);
            return playerInv.player;
        }
    }


    @Override

    public boolean stillValid(@NotNull Player player) {
        boolean baseValid = targetEntity != null
                && targetEntity.isAlive()
                && !targetEntity.isRemoved()
                && targetEntity.distanceToSqr(player) < BMTConfig.getRemoteInventoryRangeSqr();

        if (!baseValid) return false;

        // 持续检验：防止后台切换配置时发生非法截留
        if (BMTConfig.isEntityDetailsScreenBlacklisted(targetEntity.getType())) {
            // 没有特权强制返回 false
            if (!TeamPermissions.hasOverridePermission(player)) {
                return false;
            }
        }

        return true;
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

        @Override
        public @NotNull ItemStack getItem() {
            return entity.getItemBySlot(slot);
        }

        @Override
        public void set(@NotNull ItemStack stack) {
            entity.setItemSlot(slot, stack);
            setChanged();
        }

        @Override
        public void setChanged() {
            // 这里可以留空，因为 set 方法里直接操作了实体
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return true;
        }

        // 允许玩家拾取（取出）
        @Override
        public boolean mayPickup(@NotNull Player player) {
            return true;
        }

        // 拦截取出逻辑，使其从实体的身上剥离物品，而不是从虚拟的 SimpleContainer 中取
        @Override
        public @NotNull ItemStack remove(int amount) {
            ItemStack current = this.getItem();
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            // 分离出被玩家拿走的数量
            ItemStack split = current.split(amount);
            // 无论剩余多少（哪怕是空），都重新 set 回实体身上，触发原版数据同步
            this.set(current);
            return split;
        }

        // 允许主副手放入多件物品
        @Override
        public int getMaxStackSize() {
            return (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) ? 64 : 1;
        }
    }

    public static class VillagerSlot extends Slot {
        public VillagerSlot(Villager villager, net.minecraft.world.Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }
        @Override public void setChanged() { super.setChanged(); }
        @Override public boolean mayPlace(ItemStack stack) { return true; }
    }
}
