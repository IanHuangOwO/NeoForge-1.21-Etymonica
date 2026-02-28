package org.iansaididontcare.etymonica.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.iansaididontcare.etymonica.enchanting.api.EnchantingTableModifierStats;
import org.iansaididontcare.etymonica.enchanting.api.EnchantingTableStats;
import org.iansaididontcare.etymonica.enchanting.data.EnchantingTableData;
import org.iansaididontcare.etymonica.screen.custom.EnchantingTableMenu;
import org.iansaididontcare.etymonica.tag.ModBlockTags;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class EnchantingTableBlockEntity extends BlockEntity implements MenuProvider {
    private static final int BASE_PROGRESS_TICKS = 640;

    public static final int SLOT_FUTURE = 0;
    public static final int SLOT_ITEM = 1;

    private static final int SCAN_POSITIONS_PER_TICK = 1500;

    public final ItemStackHandler itemHandler = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    private boolean running = false;
    private int progress = 0;
    private int maxProgress = BASE_PROGRESS_TICKS;

    // Cached effective stats
    private int currentPower = 0;
    private float currentSpeed = 0f;
    private float currentStability = 0f;
    private float currentEfficiency = 0f;

    private int recomputeCooldownTicks = 0;

    private final Set<BlockPos> linkedModifiers = new HashSet<>();

    // Scan state (server) + synced progress (via data)
    private boolean relinkInProgress = false;
    private int scanRadius = 0;
    private int scanMaxDistSq = 0;
    private int scanCap = 0;

    // cube iterator state
    private int scanX = 0;
    private int scanY = 0;
    private int scanZ = 0;

    private int scanTotalPositions = 0;   // cube total
    private int scanScannedPositions = 0; // cube scanned so far
    private int scanLinkedCount = 0;      // linked so far

    private @Nullable UUID relinkRequester = null;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> currentPower;
                case 3 -> (int) (currentSpeed * 1000f);
                case 4 -> (int) (currentStability * 1000f);
                case 5 -> (int) (currentEfficiency * 1000f);

                // scan progress for client UI
                case 6 -> relinkInProgress ? 1 : 0;
                case 7 -> scanTotalPositions;
                case 8 -> scanScannedPositions;
                case 9 -> scanLinkedCount;

                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> maxProgress = value;
                case 2 -> currentPower = value;
                case 3 -> currentSpeed = value / 1000f;
                case 4 -> currentStability = value / 1000f;
                case 5 -> currentEfficiency = value / 1000f;

                // client receives these from server; allow setting for display
                case 6 -> relinkInProgress = value != 0;
                case 7 -> scanTotalPositions = value;
                case 8 -> scanScannedPositions = value;
                case 9 -> scanLinkedCount = value;

                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 10;
        }
    };

    public ContainerData getData() {
        return data;
    }

    public EnchantingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENCHANTING_TABLE_BE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.etymonica.enchanting_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new EnchantingTableMenu(containerId, inv, this, this.data);
    }

    /** Shift-right-click: start scan; shift-right-click again: cancel scan. */
    public void beginOrCancelRelinkScan(@Nullable Player requester) {
        if (level == null || level.isClientSide()) return;

        if (relinkInProgress) {
            cancelRelinkScan((ServerLevel) level, requester);
            return;
        }

        String tierId = getTierIdFromState(level, getBlockState());
        EnchantingTableStats base = EnchantingTableData.getTier(tierId);

        scanRadius = base.linkRadius();
        scanCap = base.maxLinkedModifiers();
        scanMaxDistSq = scanRadius * scanRadius;

        linkedModifiers.clear();
        scanLinkedCount = 0;

        // iterate offsets from -r..r
        scanX = -scanRadius;
        scanY = -scanRadius;
        scanZ = -scanRadius;

        int side = (scanRadius * 2) + 1;
        scanTotalPositions = side * side * side;
        scanScannedPositions = 0;

        relinkInProgress = scanRadius > 0 && scanCap > 0;
        relinkRequester = requester != null ? requester.getUUID() : null;

        if (requester != null) {
            requester.displayClientMessage(Component.literal("Relink started..."), true);
        }

        setChanged();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    private void cancelRelinkScan(ServerLevel level, @Nullable Player requester) {
        relinkInProgress = false;
        relinkRequester = null;

        if (requester != null) {
            requester.displayClientMessage(Component.literal("Relink cancelled."), true);
        }

        setChanged();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    public void tickServer(Level level, BlockPos pos, BlockState state) {
        if (relinkInProgress) {
            advanceRelinkScan((ServerLevel) level);
        }

        if (recomputeCooldownTicks-- <= 0) {
            recomputeCooldownTicks = 20;
            recomputeStatsNow(level, pos, state);
        }

        if (!running) return;

        if (!hasValidInput()) {
            stopEnchanting();
            return;
        }

        progress++;
        setChanged(level, pos, state);

        if (progress >= maxProgress) {
            doEnchant(level);
            stopEnchanting();
        }
    }

    private void advanceRelinkScan(ServerLevel level) {
        int budget = SCAN_POSITIONS_PER_TICK;

        while (budget-- > 0 && relinkInProgress) {
            // finished cube
            if (scanX > scanRadius) {
                relinkInProgress = false;

                requestUpdateStatsNow();

                if (relinkRequester != null) {
                    Player p = level.getPlayerByUUID(relinkRequester);
                    if (p != null) {
                        p.displayClientMessage(
                                Component.literal("Relink complete: " + scanLinkedCount + " blocks linked."),
                                true
                        );
                    }
                }
                relinkRequester = null;

                setChanged();
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                return;
            }

            // capture current offsets for this step
            int dx = scanX;
            int dy = scanY;
            int dz = scanZ;

            BlockPos target = worldPosition.offset(dx, dy, dz);

            // advance iterator (z -> y -> x)
            scanZ++;
            if (scanZ > scanRadius) {
                scanZ = -scanRadius;
                scanY++;
                if (scanY > scanRadius) {
                    scanY = -scanRadius;
                    scanX++;
                }
            }

            scanScannedPositions++;

            if (dx == 0 && dy == 0 && dz == 0) continue;

            // sphere filter
            int d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > scanMaxDistSq) continue;

            if (scanLinkedCount >= scanCap) {
                // cap reached: finish early
                scanX = scanRadius + 1;
                continue;
            }

            BlockState st = level.getBlockState(target);
            if (!st.is(ModBlockTags.ENCHANTING_TABLE_MODIFIERS)) continue;

            if (linkedModifiers.add(target.immutable())) {
                scanLinkedCount++;
            }
        }

        // keep client UI updated occasionally (cheap enough)
        if ((scanScannedPositions & 0x3FFF) == 0) { // every ~16384 steps
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public void requestStart() {
        if (!hasValidInput()) return;
        running = true;
        progress = 0;
        setChanged();
    }

    public void requestUpdateStatsNow() {
        if (level == null || level.isClientSide()) return;
        recomputeCooldownTicks = 0;
        recomputeStatsNow(level, worldPosition, getBlockState());
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        setChanged();
    }

    private void stopEnchanting() {
        running = false;
        progress = 0;
        setChanged();
    }

    private boolean hasValidInput() {
        if (level == null || level.isClientSide()) return false;

        ItemStack stack = itemHandler.getStackInSlot(SLOT_ITEM);
        return !stack.isEmpty() && stack.isEnchantable();
    }

    private void doEnchant(Level level) {
        ItemStack stack = itemHandler.getStackInSlot(SLOT_ITEM);
        if (stack.isEmpty()) return;

        int power = currentPower;
        ItemStack enchanted = EnchantmentHelper.enchantItem(
                level.random,
                stack.copyWithCount(1),
                Math.max(0, power),
                level.registryAccess(),
                Optional.empty()
        );

        itemHandler.setStackInSlot(SLOT_ITEM, enchanted);
    }

    private void recomputeStatsNow(Level level, BlockPos origin, BlockState state) {
        String tierId = getTierIdFromState(level, state);
        EnchantingTableStats base = EnchantingTableData.getTier(tierId);

        EnchantingTableModifierStats modifiers = sumLinkedModifiers(level);

        currentSpeed = EnchantingTableStats.clamp01(base.speed() + modifiers.speed());
        currentStability = EnchantingTableStats.clamp01(base.stability() + modifiers.stability());
        currentEfficiency = EnchantingTableStats.clamp01(base.efficiency() + modifiers.efficiency());

        currentPower = Math.max(0, base.enchantingPowerCap());
        maxProgress = Math.max(1, Math.round(BASE_PROGRESS_TICKS * (1.0f - currentSpeed)));
    }

    private EnchantingTableModifierStats sumLinkedModifiers(Level level) {
        if (linkedModifiers.isEmpty()) return EnchantingTableModifierStats.ZERO;

        float speed = 0f;
        float stability = 0f;
        float efficiency = 0f;

        linkedModifiers.removeIf(p -> !level.getBlockState(p).is(ModBlockTags.ENCHANTING_TABLE_MODIFIERS));

        for (BlockPos pos : linkedModifiers) {
            BlockState st = level.getBlockState(pos);
            Identifier id = blockId(level, st.getBlock());
            EnchantingTableModifierStats add = EnchantingTableData.getModifier(id);

            speed += add.speed();
            stability += add.stability();
            efficiency += add.efficiency();
        }

        return new EnchantingTableModifierStats(speed, stability, efficiency);
    }

    public boolean isRelinkInProgress() {
        return relinkInProgress;
    }

    public int getLinkedModifiersCount() {
        return linkedModifiers.size();
    }

    public int getScanTotalPositions() {
        return scanTotalPositions;
    }

    public int getScanScannedPositions() {
        return scanScannedPositions;
    }

    public int getScanLinkedCount() {
        return scanLinkedCount;
    }

    public int getScanCap() {
        return scanCap;
    }

    private EnchantingTableStats getCurrentTierStats(ServerLevel level) {
        String tierId = getTierIdFromState(level, getBlockState());
        return EnchantingTableData.getTier(tierId);
    }

    public int getMaxLinkedModifiers(ServerLevel level) {
        return getCurrentTierStats(level).maxLinkedModifiers();
    }

    /** Manual add from tuning fork. Returns true if it was added. */
    public boolean tryLinkModifier(ServerLevel level, BlockPos modifierPos, @Nullable Player requester) {
        EnchantingTableStats stats = getCurrentTierStats(level);

        int cap = stats.maxLinkedModifiers();
        if (cap <= 0) {
            if (requester != null) requester.displayClientMessage(Component.literal("This table cannot link modifiers."), true);
            return false;
        }
        if (linkedModifiers.size() >= cap) {
            if (requester != null) requester.displayClientMessage(Component.literal("Linked modifier list is full."), true);
            return false;
        }

        int radius = stats.linkRadius();
        if (radius <= 0) {
            if (requester != null) requester.displayClientMessage(Component.literal("This table has no link radius."), true);
            return false;
        }

        int maxDistSq = radius * radius;
        if (modifierPos.distSqr(worldPosition) > maxDistSq) {
            if (requester != null) requester.displayClientMessage(Component.literal("This block is too far away from the table."), true);
            return false;
        }

        BlockState st = level.getBlockState(modifierPos);
        if (!st.is(ModBlockTags.ENCHANTING_TABLE_MODIFIERS)) {
            if (requester != null) requester.displayClientMessage(Component.literal("This block is not a valid modifier."), true);
            return false;
        }

        boolean added = linkedModifiers.add(modifierPos.immutable());
        if (added) {
            scanLinkedCount = linkedModifiers.size(); // keep UI counter consistent
            requestUpdateStatsNow();
        }

        return added;
    }

    private static String getTierIdFromState(Level level, BlockState state) {
        Identifier id = blockId(level, state.getBlock());
        String path = id.getPath();
        String prefix = "enchanting_table_";

        if (path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }
        return "tier0";
    }

    private static Identifier blockId(Level level, Block block) {
        try {
            return level.registryAccess().lookupOrThrow(Registries.BLOCK).getKey(block);
        } catch (Exception ignored) {
            return Identifier.parse("minecraft:air");
        }
    }

    public void drops() {
        SimpleContainer inv = new SimpleContainer(itemHandler.getSlots());
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            inv.setItem(i, itemHandler.getStackInSlot(i));
        }
        Containers.dropContents(this.level, this.worldPosition, inv);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        drops();
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        itemHandler.serialize(output);

        long[] linked = linkedModifiers.stream().mapToLong(BlockPos::asLong).toArray();
        CompoundTag linkedTag = new CompoundTag();
        linkedTag.putLongArray("positions", linked);
        output.store("enchanting_table.linked_modifiers", CompoundTag.CODEC, linkedTag);

        // Sync-friendly counters (small + cheap)
        output.putInt("enchanting_table.linked_count", linkedModifiers.size());
        output.putBoolean("enchanting_table.relinking", relinkInProgress);
        output.putInt("enchanting_table.scan_total", scanTotalPositions);
        output.putInt("enchanting_table.scan_done", scanScannedPositions);
        output.putInt("enchanting_table.scan_linked", scanLinkedCount);
        output.putInt("enchanting_table.scan_cap", scanCap);

        output.putInt("enchanting_table.progress", progress);
        output.putInt("enchanting_table.max_progress", maxProgress);
        output.putBoolean("enchanting_table.running", running);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        itemHandler.deserialize(input);

        linkedModifiers.clear();
        CompoundTag linkedTag = input.read("enchanting_table.linked_modifiers", CompoundTag.CODEC)
                .orElseGet(CompoundTag::new);
        long[] linked = linkedTag.getLongArray("positions").orElseGet(() -> new long[0]);
        for (long l : linked) {
            linkedModifiers.add(BlockPos.of(l));
        }

        // These are used for client display (tooltip/screen). Server will reset relink state below.
        relinkInProgress = input.getBooleanOr("enchanting_table.relinking", false);
        scanTotalPositions = input.getIntOr("enchanting_table.scan_total", 0);
        scanScannedPositions = input.getIntOr("enchanting_table.scan_done", 0);
        scanLinkedCount = input.getIntOr("enchanting_table.scan_linked", linkedModifiers.size());
        scanCap = input.getIntOr("enchanting_table.scan_cap", 0);

        progress = input.getIntOr("enchanting_table.progress", 0);
        maxProgress = input.getIntOr("enchanting_table.max_progress", BASE_PROGRESS_TICKS);
        running = input.getBooleanOr("enchanting_table.running", false);

        // Never persist an in-progress scan on the server across loads
        if (this.level != null && !this.level.isClientSide()) {
            relinkInProgress = false;
            relinkRequester = null;
            scanTotalPositions = 0;
            scanScannedPositions = 0;
            scanLinkedCount = linkedModifiers.size();
        }
    }
}