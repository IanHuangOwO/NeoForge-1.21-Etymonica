package org.iansaididontcare.etymonica.block.entity;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import org.iansaididontcare.etymonica.ModTags;
import org.iansaididontcare.etymonica.growthchamber.GrowthChamberEnchantmentWeights;
import org.iansaididontcare.etymonica.screen.custom.GrowthChamberMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GrowthChamberBlockEntity extends BlockEntity implements MenuProvider {
    public final ItemStackHandler itemHandler = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            if (slot == OUTPUT_SLOT) {
                return 1;
            }
            return super.getStackLimit(slot, stack);
        }
    };

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;

    private static final int SCAN_RADIUS = 5;

    // Mapping tuning:
    private static final int BASELINE_POWER = 30;
    private static final int LOG2_SCALE = 2; // 2 feels good; increase if you want faster scaling

    protected final ContainerData data;
    private int progress = 0;
    private int maxProgress = 72;

    public GrowthChamberBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.GROWTH_CHAMBER_BE.get(), pos, blockState);
        data = new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> GrowthChamberBlockEntity.this.progress;
                    case 1 -> GrowthChamberBlockEntity.this.maxProgress;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int value) {
                switch (i) {
                    case 0 -> GrowthChamberBlockEntity.this.progress = value;
                    case 1 -> GrowthChamberBlockEntity.this.maxProgress = value;
                }
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.etymonica.growth_chamber");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return new GrowthChamberMenu(i, inventory, this, this.data);
    }

    public void drops() {
        SimpleContainer inventory = new SimpleContainer(itemHandler.getSlots());
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            inventory.setItem(i, itemHandler.getStackInSlot(i));
        }

        Containers.dropContents(this.level, this.worldPosition, inventory);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        drops();
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        itemHandler.serialize(output);
        output.putInt("growth_chamber.progress", progress);
        output.putInt("growth_chamber.max_progress", maxProgress);

        super.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        itemHandler.deserialize(input);
        progress = input.getIntOr("growth_chamber.progress", 0);
        maxProgress = input.getIntOr("growth_chamber.max_progress", 0);
    }

    public void tick(Level level, BlockPos blockPos, BlockState blockState) {
        if (hasValidInput()) {
            increaseCraftingProgress();
            setChanged(level, blockPos, blockState);

            if (hasCraftingFinished()) {
                enchantAndMoveToOutput();
                resetProgress();
            }
        } else {
            resetProgress();
        }
    }

    private boolean hasValidInput() {
        if (this.level == null || this.level.isClientSide()) {
            return false;
        }

        ItemStack input = itemHandler.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty()) {
            return false;
        }

        if (!itemHandler.getStackInSlot(OUTPUT_SLOT).isEmpty()) {
            return false;
        }

        return input.isEnchantable();
    }

    private void enchantAndMoveToOutput() {
        if (this.level == null) return;

        // Consume exactly 1 item from input
        ItemStack toEnchant = itemHandler.extractItem(INPUT_SLOT, 1, false);
        if (toEnchant.isEmpty()) return;

        int rawPower = computeRawPowerFromNearbyBooks(this.level, this.worldPosition);
        int effectivePower = mapRawPowerToEffective(rawPower);

        ItemStack enchanted = EnchantmentHelper.enchantItem(
                this.level.random,
                toEnchant,
                effectivePower,
                this.level.registryAccess(),
                Optional.empty() // allow treasure/curses as vanilla does
        );

        // Drain after we know what we produced
        int drainCost = computeDrainCostFromOutput(this.level, enchanted);
        drainNearbyBooks(this.level, this.worldPosition, drainCost);

        itemHandler.setStackInSlot(OUTPUT_SLOT, enchanted);
    }

    private static int mapRawPowerToEffective(int rawPower) {
        if (rawPower <= BASELINE_POWER) return Math.max(0, rawPower);

        int extra = rawPower - BASELINE_POWER;
        int log2 = 31 - Integer.numberOfLeadingZeros(extra); // floor(log2(extra))
        int mappedExtra = (log2 + 1) * LOG2_SCALE; // +1 keeps growth visible for small extra values
        return BASELINE_POWER + mappedExtra;
    }

    private static int computeRawPowerFromNearbyBooks(Level level, BlockPos origin) {
        int power = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                origin.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS))) {

            BlockState state = level.getBlockState(pos);
            if (!state.is(ModTags.Blocks.GROWTH_CHAMBER_POWER_SOURCES)) continue;

            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof ChiseledBookShelfBlockEntity shelf)) continue;

            for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                ItemStack stack = shelf.getItem(slot);
                if (!stack.is(Items.ENCHANTED_BOOK)) continue;

                ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
                for (var entry : stored.entrySet()) {
                    Holder<Enchantment> ench = entry.getKey();
                    int levelOnBook = entry.getIntValue();

                    Identifier id = enchantmentId(level.registryAccess(), ench);
                    int weight = GrowthChamberEnchantmentWeights.getWeight(id);

                    power += weight * Math.max(0, levelOnBook);
                }
            }
        }

        return power;
    }

    private static int computeDrainCostFromOutput(Level level, ItemStack output) {
        int cost = 0;

        ItemEnchantments enchants = output.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            Holder<Enchantment> ench = entry.getKey();
            int lvl = entry.getIntValue();

            Identifier id = enchantmentId(level.registryAccess(), ench);
            int weight = GrowthChamberEnchantmentWeights.getWeight(id);

            cost += weight * Math.max(0, lvl);
        }

        return cost;
    }

    private static void drainNearbyBooks(Level level, BlockPos origin, int cost) {
        if (cost <= 0) return;

        List<ChiseledBookShelfBlockEntity> shelves = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                origin.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS))) {

            BlockState state = level.getBlockState(pos);
            if (!state.is(ModTags.Blocks.GROWTH_CHAMBER_POWER_SOURCES)) continue;

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ChiseledBookShelfBlockEntity shelf) {
                shelves.add(shelf);
            }
        }

        if (shelves.isEmpty()) return;

        int remaining = cost;
        while (remaining > 0) {
            ChiseledBookShelfBlockEntity shelf = shelves.get(level.random.nextInt(shelves.size()));

            List<Integer> enchantedSlots = new ArrayList<>();
            for (int i = 0; i < shelf.getContainerSize(); i++) {
                if (shelf.getItem(i).is(Items.ENCHANTED_BOOK)) {
                    enchantedSlots.add(i);
                }
            }
            if (enchantedSlots.isEmpty()) break;

            int slot = enchantedSlots.get(level.random.nextInt(enchantedSlots.size()));
            ItemStack book = shelf.getItem(slot);

            ItemStack drained = drainOneStoredEnchantmentLevelOrConvert(level, book);
            if (drained.isEmpty()) {
                break; // avoid infinite loop if something went sideways
            }

            shelf.setItem(slot, drained);
            shelf.setChanged();

            remaining--;
        }
    }

    private static ItemStack drainOneStoredEnchantmentLevelOrConvert(Level level, ItemStack enchantedBook) {
        if (!enchantedBook.is(Items.ENCHANTED_BOOK)) return ItemStack.EMPTY;

        ItemEnchantments stored = enchantedBook.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (stored.isEmpty()) return enchantedBook;

        var entries = new ArrayList<>(stored.entrySet());
        var picked = entries.get(level.random.nextInt(entries.size()));

        Holder<Enchantment> pickedEnchant = picked.getKey();
        int pickedLevel = picked.getIntValue();
        int newPickedLevel = pickedLevel - 1;

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(stored);
        if (newPickedLevel > 0) {
            mutable.set(pickedEnchant, newPickedLevel);
        } else {
            mutable.set(pickedEnchant, 0);
        }

        ItemEnchantments updated = mutable.toImmutable();
        if (updated.isEmpty()) {
            return new ItemStack(Items.BOOK, enchantedBook.getCount());
        }

        enchantedBook.set(DataComponents.STORED_ENCHANTMENTS, updated);
        return enchantedBook;
    }

    private static Identifier enchantmentId(RegistryAccess registryAccess, Holder<Enchantment> enchantment) {
        // Resolve to the actual registry key/id in this world's registry access.
        // If something goes wrong, fall back to "minecraft:unknown".
        try {
            return registryAccess.lookupOrThrow(Registries.ENCHANTMENT).getKey(enchantment.value());
        } catch (Exception ignored) {
            return Identifier.parse("minecraft:unknown");
        }
    }

    private void resetProgress() {
        progress = 0;
        maxProgress = 72;
    }

    private boolean hasCraftingFinished() {
        return this.progress >= this.maxProgress;
    }

    private void increaseCraftingProgress() {
        progress++;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        return saveWithoutMetadata(pRegistries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}