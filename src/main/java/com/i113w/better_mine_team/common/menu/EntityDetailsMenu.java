package com.i113w.better_mine_team.common.menu;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.config.BMTConfig;
import com.i113w.better_mine_team.common.registry.ModMenuTypes;
import com.i113w.better_mine_team.common.team.TeamPermissions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;

public class EntityDetailsMenu extends AbstractContainerMenu {

    private static final int ENTITY_GRID_SLOTS = 36;
    private static final int EQUIPMENT_MIRROR_SLOTS = 6;
    private static final int VILLAGER_CAPABILITY_SLOTS = 27;
    private static final int VILLAGER_NATIVE_SLOTS = 8;

    private static final int LAYOUT_STANDARD = 0;
    private static final int LAYOUT_EQUIPMENT_MIRROR = 1;
    private static final int LAYOUT_VILLAGER = 2;

    private static final Identifier EMPTY_HELMET_SLOT = Identifier.withDefaultNamespace("container/slot/helmet");
    private static final Identifier EMPTY_CHESTPLATE_SLOT = Identifier.withDefaultNamespace("container/slot/chestplate");
    private static final Identifier EMPTY_LEGGINGS_SLOT = Identifier.withDefaultNamespace("container/slot/leggings");
    private static final Identifier EMPTY_BOOTS_SLOT = Identifier.withDefaultNamespace("container/slot/boots");
    private static final Identifier EMPTY_MAINHAND_SLOT = Identifier.withDefaultNamespace("container/slot/sword");
    private static final Identifier EMPTY_OFFHAND_SLOT = Identifier.withDefaultNamespace("container/slot/shield");

    private final LivingEntity targetEntity;
    private final boolean clientSideMenu;

    public EntityDetailsMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, readMenuOpenData(playerInv, extraData), true);
    }

    public EntityDetailsMenu(int containerId, Inventory playerInv, LivingEntity entity) {
        this(containerId, playerInv, createMenuOpenData(entity), playerInv.player.level().isClientSide());
    }

    private EntityDetailsMenu(int containerId, Inventory playerInv, MenuOpenData openData, boolean clientSideMenu) {
        super(ModMenuTypes.ENTITY_DETAILS_MENU.get(), containerId);
        this.targetEntity = openData.entity();
        this.clientSideMenu = clientSideMenu;

        LivingEntity entity = this.targetEntity;
        if (entity == null) return;

        // === 1. 左侧面板：通用装备栏 (26.1.x GUI sprite atlas) ===
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.HEAD, 61, 18, EMPTY_HELMET_SLOT));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.CHEST, 61, 36, EMPTY_CHESTPLATE_SLOT));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.LEGS, 61, 54, EMPTY_LEGGINGS_SLOT));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.FEET, 61, 72, EMPTY_BOOTS_SLOT));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.MAINHAND, 8, 94, EMPTY_MAINHAND_SLOT));
        addSlot(new EntityEquipmentSlot(targetEntity, EquipmentSlot.OFFHAND, 26, 94, EMPTY_OFFHAND_SLOT));

        // === 2. 右侧面板：物品栏区域 ===
        int gridStartX = 85;
        int gridStartY = 18;

        layoutEntityInventory(
                entity,
                gridStartX,
                gridStartY,
                clientSideMenu,
                openData.layoutKind(),
                openData.visibleSlots()
        );

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
    private record MenuOpenData(LivingEntity entity, int layoutKind, int visibleSlots) {
    }

    public static void writeMenuOpenData(FriendlyByteBuf buf, LivingEntity entity) {
        MenuOpenData openData = createMenuOpenData(entity);
        buf.writeInt(entity.getId());
        buf.writeInt(openData.layoutKind());
        buf.writeInt(openData.visibleSlots());
    }

    private static MenuOpenData readMenuOpenData(Inventory playerInv, FriendlyByteBuf data) {
        int entityId = data.readInt();
        LivingEntity entity = getClientEntity(playerInv, entityId);

        // 兼容旧包：如果服务端仍只写 entityId，则退回旧推断逻辑。
        // 但正式修复必须让服务端写 layoutKind + visibleSlots。
        if (data.readableBytes() < Integer.BYTES * 2) {
            return createMenuOpenData(entity);
        }

        int layoutKind = sanitizeLayoutKind(data.readInt());
        int visibleSlots = data.readInt();
        visibleSlots = clampVisibleSlots(layoutKind, visibleSlots);

        return new MenuOpenData(entity, layoutKind, visibleSlots);
    }

    private static MenuOpenData createMenuOpenData(LivingEntity entity) {
        int layoutKind = resolveLayoutKind(entity);
        int visibleSlots = resolveVisibleSlots(entity, layoutKind);
        return new MenuOpenData(entity, layoutKind, visibleSlots);
    }

    private static int sanitizeLayoutKind(int layoutKind) {
        return switch (layoutKind) {
            case LAYOUT_EQUIPMENT_MIRROR, LAYOUT_VILLAGER -> layoutKind;
            default -> LAYOUT_STANDARD;
        };
    }

    private static int resolveLayoutKind(LivingEntity entity) {
        if (entity instanceof Villager) {
            return LAYOUT_VILLAGER;
        }

        IItemHandler entityInv = getUnifiedInventory(entity);

        if (!(entity instanceof InventoryCarrier) && entityInv.getSlots() <= EQUIPMENT_MIRROR_SLOTS) {
            return LAYOUT_EQUIPMENT_MIRROR;
        }

        return LAYOUT_STANDARD;
    }

    private static int resolveVisibleSlots(LivingEntity entity, int layoutKind) {
        return switch (layoutKind) {
            case LAYOUT_EQUIPMENT_MIRROR -> EQUIPMENT_MIRROR_SLOTS;
            case LAYOUT_VILLAGER -> clamp(getEntityCapabilityInventory(entity).getSlots(), 0, VILLAGER_CAPABILITY_SLOTS);
            default -> clamp(getUnifiedInventory(entity).getSlots(), 0, ENTITY_GRID_SLOTS);
        };
    }

    private static int clampVisibleSlots(int layoutKind, int visibleSlots) {
        return switch (layoutKind) {
            case LAYOUT_EQUIPMENT_MIRROR -> EQUIPMENT_MIRROR_SLOTS;
            case LAYOUT_VILLAGER -> clamp(visibleSlots, 0, VILLAGER_CAPABILITY_SLOTS);
            default -> clamp(visibleSlots, 0, ENTITY_GRID_SLOTS);
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 村民专用混合布局
     * 前3行：尝试显示 Capability (如果有)
     * 第4行：显示村民原生 8 格背包
     */
    private void layoutVillagerInventory(Villager villager, int startX, int startY, boolean clientLayout, int visibleCapabilitySlots) {
        IItemHandler capabilityHandler = clientLayout
                ? new ItemStackHandler(clamp(visibleCapabilitySlots, 0, VILLAGER_CAPABILITY_SLOTS))
                : getEntityCapabilityInventory(villager);

        Container villagerContainer = clientLayout
                ? new SimpleContainer(VILLAGER_NATIVE_SLOTS)
                : villager.getInventory();

        int actualCapabilitySlots = Math.min(capabilityHandler.getSlots(), VILLAGER_CAPABILITY_SLOTS);

        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 9; ++col) {
                int x = startX + col * 18;
                int y = startY + row * 18;

                if (row < 3) {
                    int slotIndex = col + row * 9;
                    if (slotIndex < actualCapabilitySlots) {
                        this.addSlot(new SlotItemHandler(capabilityHandler, slotIndex, x, y));
                    } else {
                        this.addSlot(new DisabledSlot(x, y));
                    }
                } else {
                    if (col < VILLAGER_NATIVE_SLOTS) {
                        this.addSlot(new VillagerSlot(villagerContainer, col, x, y));
                    } else {
                        this.addSlot(new DisabledSlot(x, y));
                    }
                }
            }
        }
    }

    private void layoutEntityInventory(
            LivingEntity entity,
            int startX,
            int startY,
            boolean clientLayout,
            int layoutKind,
            int visibleSlots
    ) {
        switch (layoutKind) {
            case LAYOUT_VILLAGER -> {
                if (entity instanceof Villager villager) {
                    layoutVillagerInventory(villager, startX, startY, clientLayout, visibleSlots);
                } else {
                    // 客户端极端情况下找不到村民实体时，仍按服务端布局创建 dummy slots，避免 slot 数量/顺序不一致。
                    layoutVillagerInventory(null, startX, startY, true, visibleSlots);
                }
            }
            case LAYOUT_EQUIPMENT_MIRROR -> layoutEquipmentMirrorInventory(entity, startX, startY);
            default -> {
                if (clientLayout) {
                    layoutStandardInventory(new ItemStackHandler(clamp(visibleSlots, 0, ENTITY_GRID_SLOTS)), startX, startY);
                } else {
                    layoutStandardInventory(getUnifiedInventory(entity), startX, startY);
                }
            }
        }
    }

    private boolean isEquipmentOnlyInventory(LivingEntity entity, IItemHandler entityInv) {
        // InventoryCarrier 有自己的原生背包，不应被当成“只有装备栏”的实体。
        if (entity instanceof InventoryCarrier) {
            return false;
        }

        // 26.1.x 下很多普通 LivingEntity 暴露出来的 ENTITY item capability
        // 只包含主手、副手和四件盔甲，也就是 6 格。
        // 这种情况下右侧前 6 格按原设计镜像装备栏，其余格子锁定。
        return entityInv.getSlots() <= EQUIPMENT_MIRROR_SLOTS;
    }

    private void layoutEquipmentMirrorInventory(LivingEntity entity, int startX, int startY) {
        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 9; ++col) {
                int displayIndex = col + row * 9;
                int x = startX + col * 18;
                int y = startY + row * 18;

                if (displayIndex < EQUIPMENT_MIRROR_SLOTS) {
                    EquipmentSlot slot = getMirrorEquipmentSlot(displayIndex);
                    this.addSlot(new EntityEquipmentSlot(entity, slot, x, y, getEmptyIconForEquipmentSlot(slot)));
                } else {
                    this.addSlot(new DisabledSlot(x, y));
                }
            }
        }
    }

    private EquipmentSlot getMirrorEquipmentSlot(int displayIndex) {
        return switch (displayIndex) {
            case 0 -> EquipmentSlot.MAINHAND;
            case 1 -> EquipmentSlot.OFFHAND;
            case 2 -> EquipmentSlot.HEAD;
            case 3 -> EquipmentSlot.CHEST;
            case 4 -> EquipmentSlot.LEGS;
            case 5 -> EquipmentSlot.FEET;
            default -> throw new IllegalArgumentException("Invalid equipment mirror slot index: " + displayIndex);
        };
    }

    private Identifier getEmptyIconForEquipmentSlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> EMPTY_MAINHAND_SLOT;
            case OFFHAND -> EMPTY_OFFHAND_SLOT;
            case HEAD -> EMPTY_HELMET_SLOT;
            case CHEST -> EMPTY_CHESTPLATE_SLOT;
            case LEGS -> EMPTY_LEGGINGS_SLOT;
            case FEET -> EMPTY_BOOTS_SLOT;
            default -> null;
        };
    }
    private void layoutStandardInventory(IItemHandler handler, int startX, int startY) {
        int actualSlots = Math.min(handler.getSlots(), ENTITY_GRID_SLOTS);
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

    private static IItemHandler getUnifiedInventory(LivingEntity entity) {
        IItemHandler capability = getEntityCapabilityInventory(entity);
        if (capability.getSlots() > 0) return capability;
        if (entity instanceof InventoryCarrier carrier) {
            return new InvWrapper(carrier.getInventory());
        }
        return new ItemStackHandler(0);
    }

    private static IItemHandler getEntityCapabilityInventory(LivingEntity entity) {
        var cap = entity.getCapability(Capabilities.Item.ENTITY);
        return cap == null ? new ItemStackHandler(0) : IItemHandler.of(cap);
    }

    private static LivingEntity getClientEntity(Inventory playerInv, int entityId) {
        try {
            if (playerInv.player == null || playerInv.player.level() == null) {
                return playerInv.player;
            }

            net.minecraft.world.entity.Entity entity = playerInv.player.level().getEntity(entityId);

            if (entity instanceof LivingEntity living && living.isAlive()) {
                return living;
            }

            return playerInv.player;
        } catch (Exception e) {
            BetterMineTeam.LOGGER.error("Error getting client entity for details menu", e);
            return playerInv.player;
        }
    }


    @Override

    public boolean stillValid(@NotNull Player player) {
        if (clientSideMenu || player.level().isClientSide()) {
            return true;
        }

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

        public EntityEquipmentSlot(LivingEntity entity, EquipmentSlot slot, int x, int y, Identifier emptyIcon) {
            super(new SimpleContainer(1), 0, x, y);
            this.entity = entity;
            this.slot = slot;
            // 绑定原版图标占位符
            if (emptyIcon != null) {
                this.setBackground(emptyIcon);
            }
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
        public void setChanged() {}

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
            if (current.isEmpty()) return ItemStack.EMPTY;
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
        public VillagerSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public void setChanged() {
            super.setChanged();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return true;
        }
    }
}
