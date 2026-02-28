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
import org.iansaididontcare.etymonica.screen.ModMenuTypes;

public class EnchantingTableMenu extends AbstractContainerMenu {
    public static final int BUTTON_START = 0;

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

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide()) return true;

        if (id == BUTTON_START) {
            blockEntity.requestStart();
            return true;
        }
        return false;
    }

    public int getProgress() { return data.get(0); }
    public int getMaxProgress() { return data.get(1); }

    public int getCurrentPower() { return data.get(2); }
    public float getCurrentSpeed() { return data.get(3) / 1000f; }
    public float getCurrentStability() { return data.get(4) / 1000f; }
    public float getCurrentEfficiency() { return data.get(5) / 1000f; }

    public boolean isRelinkInProgress() { return data.get(6) != 0; }
    public int getScanTotal() { return data.get(7); }
    public int getScanDone() { return data.get(8); }
    public int getScanLinked() { return data.get(9); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}