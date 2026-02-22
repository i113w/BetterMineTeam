package com.i113w.better_mine_team.common.menu;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
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
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.InvWrapper;


public class EntityDetailsMenu extends AbstractContainerMenu {

    private final LivingEntity targetEntity;

    public EntityDetailsMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, getClientEntityAndSync(playerInv, extraData));
    }

    public EntityDetailsMenu(int containerId, Inventory playerInv, LivingEntity entity) {
        super(ModMenuTypes.ENTITY_DETAILS_MENU.get(), containerId);
        this.targetEntity = entity;

        if (entity == null) return;

        // === 1. 左侧面板：通用装备栏 (所有生物都有) ===
        // 直接操作实体装备槽，不依赖 Capability，确保能给村民穿装备
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.HEAD, 60, 17));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.CHEST, 60, 35));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.LEGS, 60, 53));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.FEET, 60, 71));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.MAINHAND, 7, 93));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.OFFHAND, 25, 93));

        // === 2. 右侧面板：物品栏区域 ===
        int gridStartX = 84;
        int gridStartY = 17;

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
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 84 + col * 18, 103 + row * 18));
            }
        }
        // === 4. 玩家快捷栏 ===
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, 84 + col * 18, 161));
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
                    if (col < 8) {
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
        // [1.20.1 修改] 使用 LazyOptional 模式
        return entity.getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElseGet(() -> {
                    // 回退逻辑：检查是否是 InventoryCarrier
                    if (entity instanceof InventoryCarrier carrier) {
                        return new InvWrapper(carrier.getInventory());
                    }
                    // 最终回退：空 Handler
                    return new ItemStackHandler(0);
                });
    }

    // 提取实体并即时同步服务端传来的 Follow 状态
    private static LivingEntity getClientEntityAndSync(Inventory playerInv, FriendlyByteBuf data) {
        try {
            int entityId = data.readInt();
            boolean serverFollowState = data.readBoolean(); // 读出搭便车传过来的 Boolean

            // 1. 基础空值检查
            if (playerInv.player == null || playerInv.player.level() == null) {
                return playerInv.player;
            }

            // 2. 获取实体
            net.minecraft.world.entity.Entity entity = playerInv.player.level().getEntity(entityId);

            // 3. 验证实体有效性 (必须是 LivingEntity 且 活着)
            if (entity instanceof LivingEntity living && living.isAlive()) {
                // 将服务端的真实状态强制赋予客户端实体，一劳永逸解决图标显示不同步
                living.getPersistentData().putBoolean("bmt_follow_enabled", serverFollowState);
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
        // 检查：实体存在 + 活着 + 未被移除 + 在配置距离内
        return targetEntity != null
                && targetEntity.isAlive()
                && !targetEntity.isRemoved()
                && targetEntity.distanceToSqr(player) < BMTConfig.getRemoteInventoryRangeSqr();
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

    public static class VillagerSlot extends Slot {
        public VillagerSlot(Villager villager, Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }
        @Override public void setChanged() { super.setChanged(); }
        @Override public boolean mayPlace(ItemStack stack) { return true; }
    }
}