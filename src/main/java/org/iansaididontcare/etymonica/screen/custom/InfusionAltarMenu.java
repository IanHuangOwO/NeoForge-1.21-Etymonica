package org.iansaididontcare.etymonica.screen.custom;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.iansaididontcare.etymonica.block.entity.AbstractInfusionAltarBlockEntity;
import org.iansaididontcare.etymonica.screen.ModMenuTypes;

public class InfusionAltarMenu extends AbstractContainerMenu {
    private static final int ALTAR_SLOT_X = 80;
    private static final int ALTAR_SLOT_Y = 35;

    private static final int TE_FIRST_SLOT_INDEX = 0;
    private static final int TE_SLOT_COUNT = 1;

    private static final int PLAYER_INVENTORY_ROW_COUNT = 3;
    private static final int PLAYER_INVENTORY_COLUMN_COUNT = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = PLAYER_INVENTORY_COLUMN_COUNT * PLAYER_INVENTORY_ROW_COUNT;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_FIRST_SLOT_INDEX = TE_FIRST_SLOT_INDEX + TE_SLOT_COUNT;
    private static final int PLAYER_SLOT_COUNT = PLAYER_INVENTORY_SLOT_COUNT + HOTBAR_SLOT_COUNT;
    private static final int HOTBAR_FIRST_SLOT_INDEX = PLAYER_FIRST_SLOT_INDEX + PLAYER_INVENTORY_SLOT_COUNT;

    private final AbstractInfusionAltarBlockEntity blockEntity;

    public InfusionAltarMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(id, playerInv, getBlockEntity(playerInv, buf));
    }

    public InfusionAltarMenu(int id, Inventory playerInv, AbstractInfusionAltarBlockEntity be) {
        super(ModMenuTypes.INFUSION_ALTAR_MENU.get(), id);
        this.blockEntity = be;

        this.addSlot(new SlotItemHandler(be.getInventory(), 0, ALTAR_SLOT_X, ALTAR_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return blockEntity.isFormed() && super.mayPlace(stack);
            }
        });

        addPlayerInventory(playerInv);
        addPlayerHotbar(playerInv);
    }

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
            if (blockEntity.isFormed()) {
                if (!moveItemStackTo(sourceStack, TE_FIRST_SLOT_INDEX, TE_FIRST_SLOT_INDEX + TE_SLOT_COUNT, false)) {
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

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private static AbstractInfusionAltarBlockEntity getBlockEntity(Inventory playerInv, FriendlyByteBuf buf) {
        var level = playerInv.player.level();
        var pos = buf.readBlockPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AbstractInfusionAltarBlockEntity altar)) {
            throw new IllegalStateException("InfusionAltarBlockEntity not found at " + pos);
        }
        return altar;
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
}
