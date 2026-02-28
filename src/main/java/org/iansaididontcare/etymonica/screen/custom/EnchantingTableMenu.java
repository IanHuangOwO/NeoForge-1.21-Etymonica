package org.iansaididontcare.etymonica.screen.custom;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.iansaididontcare.etymonica.block.entity.EnchantingTableBlockEntity;
import org.iansaididontcare.etymonica.block.entity.enchanting.EnchantingTableDataSlots;
import org.iansaididontcare.etymonica.screen.ModMenuTypes;

public class EnchantingTableMenu extends AbstractContainerMenu {
    public enum TableMode {
        IDLE,
        RELINK,
        ENCHANT;

        public static TableMode fromSyncedId(int id) {
            if (id < 0 || id >= values().length) return IDLE;
            return values()[id];
        }
    }

    private static final int SLOT_FUTURE_X = 8;
    private static final int SLOT_FUTURE_Y = 8;

    private static final int SLOT_ITEM_X = 80;
    private static final int SLOT_ITEM_Y = 35;

    private final EnchantingTableBlockEntity blockEntity;
    private final ContainerData data;

    public EnchantingTableMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(id, playerInv, getBlockEntity(playerInv, buf));
    }

    public EnchantingTableMenu(int id, Inventory playerInv, EnchantingTableBlockEntity be) {
        this(id, playerInv, be, be.getData());
    }

    public EnchantingTableMenu(int id, Inventory playerInv, EnchantingTableBlockEntity be, ContainerData data) {
        super(ModMenuTypes.ENCHANTING_TABLE_MENU.get(), id);
        this.blockEntity = be;
        this.data = data;

        addDataSlots(data);

        this.addSlot(new SlotItemHandler(be.itemHandler, EnchantingTableBlockEntity.SLOT_FUTURE, SLOT_FUTURE_X, SLOT_FUTURE_Y));
        this.addSlot(new SlotItemHandler(be.itemHandler, EnchantingTableBlockEntity.SLOT_ITEM, SLOT_ITEM_X, SLOT_ITEM_Y));

        addPlayerInventory(playerInv);
        addPlayerHotbar(playerInv);
    }

    private static final int TE_FIRST_SLOT_INDEX = 0;
    private static final int TE_SLOT_COUNT = 2;

    private static final int PLAYER_INVENTORY_ROW_COUNT = 3;
    private static final int PLAYER_INVENTORY_COLUMN_COUNT = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = PLAYER_INVENTORY_COLUMN_COUNT * PLAYER_INVENTORY_ROW_COUNT; // 27
    private static final int HOTBAR_SLOT_COUNT = 9;

    private static final int PLAYER_FIRST_SLOT_INDEX = TE_FIRST_SLOT_INDEX + TE_SLOT_COUNT; // 2
    private static final int PLAYER_SLOT_COUNT = PLAYER_INVENTORY_SLOT_COUNT + HOTBAR_SLOT_COUNT; // 36
    private static final int HOTBAR_FIRST_SLOT_INDEX = PLAYER_FIRST_SLOT_INDEX + PLAYER_INVENTORY_SLOT_COUNT; // 29

    @Override
    public ItemStack quickMoveStack(Player playerIn, int pIndex) {
        if (pIndex < 0 || pIndex >= slots.size()) return ItemStack.EMPTY;

        Slot sourceSlot = slots.get(pIndex);
        if (!sourceSlot.hasItem()) return ItemStack.EMPTY;

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        if (pIndex < TE_FIRST_SLOT_INDEX + TE_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, PLAYER_FIRST_SLOT_INDEX, PLAYER_FIRST_SLOT_INDEX + PLAYER_SLOT_COUNT, true)) {
                return ItemStack.EMPTY;
            }
        } else if (pIndex < PLAYER_FIRST_SLOT_INDEX + PLAYER_SLOT_COUNT) {
            // Shift-click from player inventory: try enchanting input slot only.
            if (sourceStack.isEnchantable()) {
                if (!moveItemStackTo(sourceStack,
                        EnchantingTableBlockEntity.SLOT_ITEM,
                        EnchantingTableBlockEntity.SLOT_ITEM + 1,
                        false)) {
                    return ItemStack.EMPTY;
                }
            } else if (pIndex < HOTBAR_FIRST_SLOT_INDEX) {
                if (!moveItemStackTo(sourceStack, HOTBAR_FIRST_SLOT_INDEX, HOTBAR_FIRST_SLOT_INDEX + HOTBAR_SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(sourceStack, PLAYER_FIRST_SLOT_INDEX, PLAYER_FIRST_SLOT_INDEX + PLAYER_INVENTORY_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        if (sourceStack.getCount() == copyOfSourceStack.getCount()) {
            return ItemStack.EMPTY;
        }

        sourceSlot.onTake(playerIn, sourceStack);
        return copyOfSourceStack;
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 84 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    private static EnchantingTableBlockEntity getBlockEntity(Inventory playerInv, FriendlyByteBuf buf) {
        var level = playerInv.player.level();
        var pos = buf.readBlockPos();
        var be = level.getBlockEntity(pos);
        if (!(be instanceof EnchantingTableBlockEntity table)) {
            throw new IllegalStateException("EnchantingTableBlockEntity not found at " + pos);
        }
        return table;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public int getProgress() { return data.get(EnchantingTableDataSlots.PROGRESS); }
    public int getMaxProgress() { return data.get(EnchantingTableDataSlots.MAX_PROGRESS); }

    public int getCurrentPower() { return data.get(EnchantingTableDataSlots.POWER); }
    public float getCurrentSpeed() { return data.get(EnchantingTableDataSlots.SPEED_MILLI) / 1000f; }
    public float getCurrentStability() { return data.get(EnchantingTableDataSlots.STABILITY_MILLI) / 1000f; }
    public float getCurrentEfficiency() { return data.get(EnchantingTableDataSlots.EFFICIENCY_MILLI) / 1000f; }

    public int getScanTotal() { return data.get(EnchantingTableDataSlots.SCAN_TOTAL); }
    public int getScanDone() { return data.get(EnchantingTableDataSlots.SCAN_DONE); }
    public int getScanLinked() { return data.get(EnchantingTableDataSlots.SCAN_LINKED); }

    public TableMode getMode() { return TableMode.fromSyncedId(data.get(EnchantingTableDataSlots.MODE)); }

    public int getEnchantPercent() {
        int total = Math.max(1, getMaxProgress());
        int done = Math.min(total, getProgress());
        return (int) Math.round(done * 100.0 / total);
    }

    public int getRelinkPercent() {
        int total = Math.max(1, getScanTotal());
        int done = Math.min(total, getScanDone());
        return (int) Math.round(done * 100.0 / total);
    }
}
