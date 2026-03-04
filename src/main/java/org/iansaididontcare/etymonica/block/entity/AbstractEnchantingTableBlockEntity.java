package org.iansaididontcare.etymonica.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.iansaididontcare.etymonica.block.entity.enchantingtable.enchanter.EnchantBookDrainer;
import org.iansaididontcare.etymonica.block.entity.enchantingtable.enchanter.EnchantPowerCalculator;
import org.iansaididontcare.etymonica.block.entity.enchantingtable.enchanter.EnchantRelinkScanner;
import org.iansaididontcare.etymonica.block.entity.enchantingtable.enchanter.EnchantingTableDataSlots;
import org.iansaididontcare.etymonica.registry.enchanting.api.EnchantingTableModifierStats;
import org.iansaididontcare.etymonica.registry.enchanting.api.EnchantingTableStats;
import org.iansaididontcare.etymonica.registry.enchanting.data.EnchantingTableData;
import org.iansaididontcare.etymonica.screen.custom.EnchantingTableMenu;
import org.iansaididontcare.etymonica.tag.ModBlockTags;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.iansaididontcare.etymonica.registry.enchanting.api.TableActionResult;

public abstract class AbstractEnchantingTableBlockEntity extends BlockEntity implements MenuProvider {
    // Core constants and machine modes.
    private static final int BASE_PROGRESS_TICKS = 640;
    private enum Mode {
        IDLE,
        RELINK,
        ENCHANT
    }

    public static final int SLOT_FUTURE = 0;
    public static final int SLOT_ITEM = 1;

    private static final int SCAN_POSITIONS_PER_TICK = 1500;

    // Inventory and runtime state shared by ticking, menu, and renderer.
    public final ItemStackHandler itemHandler = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    private Mode mode = Mode.IDLE;
    private int progress = 0;
    private int maxProgress = BASE_PROGRESS_TICKS;
    private float renderRotation = 0f;

    // Cached effective stats
    private int currentPower = 0;
    private float currentSpeed = 0f;
    private float currentStability = 0f;
    private float currentEfficiency = 0f;

    private int recomputeCooldownTicks = 0;
    private boolean statsDirty = true;
    private long lastDataRevision = -1L;
    private String lastTierId = "";

    private final Set<BlockPos> linkedModifiers = new HashSet<>();
    private final Set<BlockPos> linkedBookshelves = new HashSet<>();
    
    private final Map<BlockPos, LinkedModifierContribution> modifierContributionCache = new HashMap<>();
    private float linkedSpeedTotal = 0f;
    private float linkedStabilityTotal = 0f;
    private float linkedEfficiencyTotal = 0f;
    private int linkedPowerTotal = 0;

    // Scan state (server) + synced progress (via data)
    private int scanCap = 0;
    private @Nullable EnchantRelinkScanner relinkSession = null;

    private int scanTotalPositions = 0;   // cube total
    private int scanScannedPositions = 0; // cube scanned so far
    private int scanLinkedCount = 0;      // linked so far (modifiers + bookshelves)
    private @Nullable UUID relinkRequester = null;

    private record LinkedModifierContribution(long fingerprint, float speed, float stability, float efficiency) {}

    // Data slot bridge used by menu/screen to read synced machine state.
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case EnchantingTableDataSlots.PROGRESS -> progress;
                case EnchantingTableDataSlots.MAX_PROGRESS -> maxProgress;
                case EnchantingTableDataSlots.POWER -> currentPower;
                case EnchantingTableDataSlots.SPEED_MILLI -> (int) (currentSpeed * 1000f);
                case EnchantingTableDataSlots.STABILITY_MILLI -> (int) (currentStability * 1000f);
                case EnchantingTableDataSlots.EFFICIENCY_MILLI -> (int) (currentEfficiency * 1000f);

                // scan progress for client UI
                case EnchantingTableDataSlots.RELINK_FLAG -> mode == Mode.RELINK ? 1 : 0;
                case EnchantingTableDataSlots.SCAN_TOTAL -> scanTotalPositions;
                case EnchantingTableDataSlots.SCAN_DONE -> scanScannedPositions;
                case EnchantingTableDataSlots.SCAN_LINKED -> scanLinkedCount;

                // enchanting running flag for client UI
                case EnchantingTableDataSlots.ENCHANT_FLAG -> mode == Mode.ENCHANT ? 1 : 0;
                // canonical machine mode for UI/menu logic
                case EnchantingTableDataSlots.MODE -> mode.ordinal();

                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case EnchantingTableDataSlots.PROGRESS -> progress = value;
                case EnchantingTableDataSlots.MAX_PROGRESS -> maxProgress = value;
                case EnchantingTableDataSlots.POWER -> currentPower = value;
                case EnchantingTableDataSlots.SPEED_MILLI -> currentSpeed = value / 1000f;
                case EnchantingTableDataSlots.STABILITY_MILLI -> currentStability = value / 1000f;
                case EnchantingTableDataSlots.EFFICIENCY_MILLI -> currentEfficiency = value / 1000f;

                // client receives these from server; allow setting for display
                case EnchantingTableDataSlots.RELINK_FLAG -> {
                    if (value != 0) {
                        mode = Mode.RELINK;
                    } else if (mode == Mode.RELINK) {
                        mode = Mode.IDLE;
                    }
                }
                case EnchantingTableDataSlots.SCAN_TOTAL -> scanTotalPositions = value;
                case EnchantingTableDataSlots.SCAN_DONE -> scanScannedPositions = value;
                case EnchantingTableDataSlots.SCAN_LINKED -> scanLinkedCount = value;

                case EnchantingTableDataSlots.ENCHANT_FLAG -> {
                    if (value != 0) {
                        mode = Mode.ENCHANT;
                    } else if (mode == Mode.ENCHANT) {
                        mode = Mode.IDLE;
                    }
                }
                case EnchantingTableDataSlots.MODE -> {
                    if (value < 0 || value >= Mode.values().length) {
                        mode = Mode.IDLE;
                    } else {
                        mode = Mode.values()[value];
                    }
                }

                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return EnchantingTableDataSlots.COUNT;
        }
    };

    public ContainerData getData() {
        return data;
    }

    // Client-side visual rotation for the rendered top item.
    public float getRenderingRotation() {
        renderRotation += 0.5f;
        if (renderRotation >= 360f) {
            renderRotation = 0f;
        }
        return renderRotation;
    }

    public AbstractEnchantingTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // Menu provider entry points.
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.etymonica.enchanting_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player player) {
        return new EnchantingTableMenu(containerId, inv, this, this.data);
    }

    // Main server tick: relink progression, periodic stat recompute, and enchant progress.
    public void tickServer(Level level, BlockPos pos, BlockState state) {
        if (mode == Mode.RELINK) {
            advanceRelinkScan((ServerLevel) level);
        }

        if (recomputeCooldownTicks-- <= 0) {
            recomputeCooldownTicks = 20;
            recomputeStatsNow(level, state);
        }

        if (mode != Mode.ENCHANT) return;

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

    // Enchant lifecycle: start/stop flow, input validation, and final enchant application.
    public TableActionResult requestStartEnchanting() {
        if (mode != Mode.IDLE) return TableActionResult.ENCHANT_BLOCKED;
        if (!hasValidInput()) return TableActionResult.ENCHANT_BLOCKED;
        mode = Mode.ENCHANT;
        progress = 0;
        setChanged();
        return TableActionResult.ENCHANT_STARTED;
    }

    private void stopEnchanting() {
        mode = Mode.IDLE;
        progress = 0;
        setChanged();
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

        int drainBudget = EnchantBookDrainer.computeDrainBudgetFromItem(level, enchanted);
        if (drainBudget > 0) {
            int drained = EnchantBookDrainer.drainFromLinkedBookshelves(
                    level,
                    linkedBookshelves,
                    ModBlockTags.ENCHANTING_TABLE_BOOKSHELVES,
                    drainBudget
            );
            if (drained > 0) {
                markStatsDirty();
                recomputeCooldownTicks = 0;
            }
        }
    }

    private boolean hasValidInput() {
        if (level == null || level.isClientSide()) return false;

        ItemStack stack = itemHandler.getStackInSlot(SLOT_ITEM);
        return !stack.isEmpty() && stack.isEnchantable();
    }

    // Relink lifecycle: scan start/cancel and incremental linking across ticks.
    public TableActionResult beginOrCancelRelinkScan(@Nullable Player requester) {
        if (level == null || level.isClientSide()) return TableActionResult.RELINK_BLOCKED;

        if (mode == Mode.RELINK) {
            cancelRelinkScan((ServerLevel) level);
            return TableActionResult.RELINK_CANCELLED;
        }
        if (mode == Mode.ENCHANT) {
            return TableActionResult.RELINK_BLOCKED;
        }

        String tierId = getTierIdFromState(level, getBlockState());
        EnchantingTableStats base = EnchantingTableData.getTier(tierId);

        int scanRadius = base.linkRadius();
        // No global modifier cap anymore, just use a very large number for the scanner
        scanCap = 9999;
        relinkSession = new EnchantRelinkScanner(scanRadius, scanCap);

        linkedModifiers.clear();
        linkedBookshelves.clear();
        scanLinkedCount = 0;
        resetContributionCache();

        if (scanRadius <= 0 || scanCap <= 0) {
            mode = Mode.IDLE;
            relinkSession = null;
            relinkRequester = null;
            scanTotalPositions = 0;
            scanScannedPositions = 0;
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            return TableActionResult.RELINK_BLOCKED;
        }

        scanTotalPositions = relinkSession.getTotalPositions();
        scanScannedPositions = relinkSession.getScannedPositions();

        mode = Mode.RELINK;
        relinkRequester = requester != null ? requester.getUUID() : null;

        setChanged();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        return TableActionResult.RELINK_STARTED;
    }

    private void cancelRelinkScan(ServerLevel level) {
        mode = Mode.IDLE;
        if (relinkSession != null) {
            scanScannedPositions = relinkSession.getScannedPositions();
        }
        relinkSession = null;
        relinkRequester = null;

        setChanged();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    private void advanceRelinkScan(ServerLevel level) {
        if (relinkSession == null || !relinkSession.isActive()) {
            mode = Mode.IDLE;
            relinkSession = null;
            return;
        }

        int budget = SCAN_POSITIONS_PER_TICK;

        while (budget-- > 0 && mode == Mode.RELINK) {
            BlockPos target = relinkSession.nextCandidate(worldPosition, scanLinkedCount);
            scanScannedPositions = relinkSession.getScannedPositions();
            if (target == null) break;
            
            tryLinkedBlock(target);
        }

        scanTotalPositions = relinkSession.getTotalPositions();
        scanScannedPositions = relinkSession.getScannedPositions();
        scanCap = relinkSession.getCap();

        if (!relinkSession.isActive()) {
            mode = Mode.IDLE;
            relinkSession = null;
            if (relinkRequester != null) {
                Player requester = level.getPlayerByUUID(relinkRequester);
                if (requester != null) {
                    requester.displayClientMessage(
                            Component.translatable("message.etymonica.relink.completed", scanLinkedCount),
                            true
                    );
                }
            }
            relinkRequester = null;
            requestUpdateStatsNow();
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            return;
        }

        // keep client UI updated occasionally (cheap enough)
        if ((scanScannedPositions & 0x3FFF) == 0) { // every ~16384 steps
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    private void tryLinkedBlock(BlockPos target) {
        BlockState st = level.getBlockState(target);
        if (st.is(ModBlockTags.ENCHANTING_TABLE_BOOKSHELVES)) {
            tryLinkedBookshelf(target);
        } else if (st.is(ModBlockTags.ENCHANTING_TABLE_MODIFIERS)) {
            tryLinkedModifier(target);
        }
    }

    // Stat refresh API and recompute pipeline.
    public void requestUpdateStatsNow() {
        if (level == null || level.isClientSide()) return;
        markStatsDirty();
        recomputeCooldownTicks = 0;
        recomputeStatsNow(level, getBlockState());
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        setChanged();
    }

    private void recomputeStatsNow(Level level, BlockState state) {
        String tierId = getTierIdFromState(level, state);
        EnchantingTableStats base = EnchantingTableData.getTier(tierId);
        boolean tierChanged = !tierId.equals(lastTierId);
        long revision = EnchantingTableData.getRevision();
        boolean dataChanged = revision != lastDataRevision;
        boolean linkedChanged = reconcileLinkedContributions(level);

        if (!(statsDirty || tierChanged || dataChanged || linkedChanged)) return;

        lastTierId = tierId;
        lastDataRevision = revision;

        currentSpeed = EnchantingTableStats.clamp01(base.speed() + linkedSpeedTotal);
        currentStability = EnchantingTableStats.clamp01(base.stability() + linkedStabilityTotal);
        currentEfficiency = EnchantingTableStats.clamp01(base.efficiency() + linkedEfficiencyTotal);

        // Power is only from bookshelves
        linkedPowerTotal = EnchantPowerCalculator.computeTotalLinkedPower(level, linkedBookshelves);
        currentPower = Math.max(0, Math.min(linkedPowerTotal, base.enchantingPowerCap()));
        
        scanCap = 9999;
        maxProgress = Math.max(1, Math.round(BASE_PROGRESS_TICKS * (1.0f - currentSpeed)));
        statsDirty = false;
    }

    // Incremental cache reconciliation for linked modifier contributions.
    private boolean reconcileLinkedContributions(Level level) {
        boolean changed = false;

        // Reconcile Bookshelves
        Iterator<BlockPos> shelfIt = linkedBookshelves.iterator();
        while (shelfIt.hasNext()) {
            BlockPos pos = shelfIt.next();
            if (!level.getBlockState(pos).is(ModBlockTags.ENCHANTING_TABLE_BOOKSHELVES)) {
                shelfIt.remove();
                changed = true;
            }
        }

        // Reconcile Modifiers
        Iterator<BlockPos> linkedIt = linkedModifiers.iterator();
        while (linkedIt.hasNext()) {
            BlockPos pos = linkedIt.next();
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModBlockTags.ENCHANTING_TABLE_MODIFIERS)) {
                linkedIt.remove();
                removeModifierContribution(pos);
                changed = true;
                continue;
            }

            long fingerprint = computeModifierFingerprint(level, pos, state);
            LinkedModifierContribution previous = modifierContributionCache.get(pos);
            if (previous == null || previous.fingerprint() != fingerprint) {
                if (previous != null) subtractModifierContribution(previous);
                LinkedModifierContribution next = computeModifierContribution(level, pos, state, fingerprint);
                modifierContributionCache.put(pos, next);
                addModifierContribution(next);
                changed = true;
            }
        }

        Iterator<Map.Entry<BlockPos, LinkedModifierContribution>> cacheIt = modifierContributionCache.entrySet().iterator();
        while (cacheIt.hasNext()) {
            Map.Entry<BlockPos, LinkedModifierContribution> entry = cacheIt.next();
            if (!linkedModifiers.contains(entry.getKey())) {
                subtractModifierContribution(entry.getValue());
                cacheIt.remove();
                changed = true;
            }
        }

        int totalCount = linkedModifiers.size() + linkedBookshelves.size();
        if (scanLinkedCount != totalCount) {
            scanLinkedCount = totalCount;
            changed = true;
        }

        if (changed) markStatsDirty();
        return changed;
    }

    private long computeModifierFingerprint(Level level, BlockPos pos, BlockState state) {
        long hash = 1469598103934665603L;
        hash = mix(hash, Block.getId(state));

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return hash;
        hash = mix(hash, be.getClass().getName().hashCode());

        if (!(be instanceof net.minecraft.world.Container container)) return hash;
        int size = container.getContainerSize();
        hash = mix(hash, size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                hash = mix(hash, 1);
                continue;
            }
            hash = mix(hash, Item.getId(stack.getItem()));
            hash = mix(hash, stack.getCount());

            ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (Object2IntMap.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> e : stored.entrySet()) {
                hash = mix(hash, e.getKey().hashCode());
                hash = mix(hash, e.getIntValue());
            }
        }
        return hash;
    }

    private static long mix(long current, int value) {
        return (current ^ (long) value) * 1099511628211L;
    }

    private LinkedModifierContribution computeModifierContribution(Level level, BlockPos pos, BlockState state, long fingerprint) {
        Identifier id = blockId(level, state.getBlock());
        EnchantingTableModifierStats modifier = EnchantingTableData.getModifier(id);
        return new LinkedModifierContribution(fingerprint, modifier.speed(), modifier.stability(), modifier.efficiency());
    }

    private void addModifierContribution(LinkedModifierContribution c) {
        linkedSpeedTotal += c.speed();
        linkedStabilityTotal += c.stability();
        linkedEfficiencyTotal += c.efficiency();
    }

    private void subtractModifierContribution(LinkedModifierContribution c) {
        linkedSpeedTotal -= c.speed();
        linkedStabilityTotal -= c.stability();
        linkedEfficiencyTotal -= c.efficiency();
    }

    private void removeModifierContribution(BlockPos pos) {
        LinkedModifierContribution old = modifierContributionCache.remove(pos);
        if (old != null) subtractModifierContribution(old);
    }

    private void resetContributionCache() {
        modifierContributionCache.clear();
        linkedSpeedTotal = 0f;
        linkedStabilityTotal = 0f;
        linkedEfficiencyTotal = 0f;
        linkedPowerTotal = 0;
        markStatsDirty();
    }

    private void markStatsDirty() {
        statsDirty = true;
    }

    // Manual tuning-fork link API and local link-set helpers.
    private EnchantingTableStats getCurrentTierStats(ServerLevel level) {
        String tierId = getTierIdFromState(level, getBlockState());
        return EnchantingTableData.getTier(tierId);
    }

    public TableActionResult tryLinkBlock(ServerLevel level, BlockPos targetPos) {
        BlockState st = level.getBlockState(targetPos);
        if (st.is(ModBlockTags.ENCHANTING_TABLE_BOOKSHELVES)) {
            return tryLinkBookshelf(level, targetPos);
        } else if (st.is(ModBlockTags.ENCHANTING_TABLE_MODIFIERS)) {
            return tryLinkModifier(level, targetPos);
        }
        return TableActionResult.LINK_BLOCKED_INVALID_BLOCK;
    }

    private TableActionResult tryLinkBookshelf(ServerLevel level, BlockPos shelfPos) {
        EnchantingTableStats stats = getCurrentTierStats(level);
        if (linkedBookshelves.size() >= stats.maxLinkedBookshelves() && !linkedBookshelves.contains(shelfPos)) {
            return TableActionResult.LINK_BLOCKED_CAP_REACHED;
        }
        
        int radius = stats.linkRadius();
        if (shelfPos.distSqr(worldPosition) > radius * radius) {
            return TableActionResult.LINK_BLOCKED_TOO_FAR;
        }

        if (tryLinkedBookshelf(shelfPos)) {
            requestUpdateStatsNow();
            return TableActionResult.MODIFIER_LINKED;
        }

        if (linkedBookshelves.remove(shelfPos)) {
            scanLinkedCount = linkedModifiers.size() + linkedBookshelves.size();
            requestUpdateStatsNow();
            return TableActionResult.MODIFIER_UNLINKED;
        }

        return TableActionResult.LINK_BLOCKED_ALREADY_LINKED;
    }

    private TableActionResult tryLinkModifier(ServerLevel level, BlockPos modifierPos) {
        EnchantingTableStats stats = getCurrentTierStats(level);

        int radius = stats.linkRadius();
        if (modifierPos.distSqr(worldPosition) > radius * radius) {
            return TableActionResult.LINK_BLOCKED_TOO_FAR;
        }

        if (tryLinkedModifier(modifierPos)) {
            requestUpdateStatsNow();
            return TableActionResult.MODIFIER_LINKED;
        }

        if (linkedModifiers.remove(modifierPos)) {
            removeModifierContribution(modifierPos);
            scanLinkedCount = linkedModifiers.size() + linkedBookshelves.size();
            requestUpdateStatsNow();
            return TableActionResult.MODIFIER_UNLINKED;
        }

        return TableActionResult.LINK_BLOCKED_ALREADY_LINKED;
    }

    private boolean tryLinkedBookshelf(BlockPos shelfPos) {
        if (level == null) return false;
        if (linkedBookshelves.contains(shelfPos)) return false;

        String tierId = getTierIdFromState(level, getBlockState());
        EnchantingTableStats tierStats = EnchantingTableData.getTier(tierId);
        if (linkedBookshelves.size() >= tierStats.maxLinkedBookshelves()) {
            return false;
        }

        boolean added = linkedBookshelves.add(shelfPos.immutable());
        if (added) {
            scanLinkedCount = linkedModifiers.size() + linkedBookshelves.size();
            markStatsDirty();
        }
        return added;
    }

    private boolean tryLinkedModifier(BlockPos modifierPos) {
        if (level == null) return false;
        if (linkedModifiers.contains(modifierPos)) return false;

        BlockState state = level.getBlockState(modifierPos);
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        EnchantingTableModifierStats modifierStats = EnchantingTableData.getModifier(id);

        if (modifierStats.maxNum() > 0) {
            int currentCount = countModifiersOfType(state.getBlock());
            if (currentCount >= modifierStats.maxNum()) {
                return false;
            }
        }

        boolean added = linkedModifiers.add(modifierPos.immutable());
        if (added) {
            scanLinkedCount = linkedModifiers.size() + linkedBookshelves.size();
            markStatsDirty();
        }
        return added;
    }

    private int countModifiersOfType(Block block) {
        if (level == null) return 0;
        int count = 0;
        for (BlockPos pos : linkedModifiers) {
            if (level.getBlockState(pos).is(block)) {
                count++;
            }
        }
        return count;
    }

    // Small registry and tier resolution helpers.
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
        return level.registryAccess().lookupOrThrow(Registries.BLOCK).getKey(block);
    }

    // Block-entity lifecycle hooks: drop inventory and persist runtime data.
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

        long[] linkedMod = linkedModifiers.stream().mapToLong(BlockPos::asLong).toArray();
        CompoundTag linkedModTag = new CompoundTag();
        linkedModTag.putLongArray("positions", linkedMod);
        output.store("enchanting_table.linked_modifiers", CompoundTag.CODEC, linkedModTag);

        long[] linkedShelf = linkedBookshelves.stream().mapToLong(BlockPos::asLong).toArray();
        CompoundTag linkedShelfTag = new CompoundTag();
        linkedShelfTag.putLongArray("positions", linkedShelf);
        output.store("enchanting_table.linked_bookshelves", CompoundTag.CODEC, linkedShelfTag);

        // Sync-friendly counters (small + cheap)
        output.putInt("enchanting_table.linked_count", linkedModifiers.size() + linkedBookshelves.size());
        output.putBoolean("enchanting_table.relinking", mode == Mode.RELINK);
        output.putInt("enchanting_table.scan_total", scanTotalPositions);
        output.putInt("enchanting_table.scan_done", scanScannedPositions);
        output.putInt("enchanting_table.scan_linked", scanLinkedCount);
        output.putInt("enchanting_table.scan_cap", scanCap);

        output.putInt("enchanting_table.progress", progress);
        output.putInt("enchanting_table.max_progress", maxProgress);
        output.putBoolean("enchanting_table.running", mode == Mode.ENCHANT);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        itemHandler.deserialize(input);

        linkedModifiers.clear();
        input.read("enchanting_table.linked_modifiers", CompoundTag.CODEC).ifPresent(tag -> {
            long[] linked = tag.getLongArray("positions").orElse(new long[0]);
            for (long l : linked) linkedModifiers.add(BlockPos.of(l));
        });

        linkedBookshelves.clear();
        input.read("enchanting_table.linked_bookshelves", CompoundTag.CODEC).ifPresent(tag -> {
            long[] linked = tag.getLongArray("positions").orElse(new long[0]);
            for (long l : linked) linkedBookshelves.add(BlockPos.of(l));
        });
        
        resetContributionCache();

        // These are used for client display (tooltip/screen). Server will reset relink state below.
        boolean relink = input.getBooleanOr("enchanting_table.relinking", false);
        scanTotalPositions = input.getIntOr("enchanting_table.scan_total", 0);
        scanScannedPositions = input.getIntOr("enchanting_table.scan_done", 0);
        scanLinkedCount = input.getIntOr("enchanting_table.scan_linked", linkedModifiers.size() + linkedBookshelves.size());
        scanCap = input.getIntOr("enchanting_table.scan_cap", 0);

        progress = input.getIntOr("enchanting_table.progress", 0);
        maxProgress = input.getIntOr("enchanting_table.max_progress", BASE_PROGRESS_TICKS);
        boolean running = input.getBooleanOr("enchanting_table.running", false);
        mode = running ? Mode.ENCHANT : (relink ? Mode.RELINK : Mode.IDLE);

        // Never persist an in-progress scan on the server across loads
        if (this.level != null && !this.level.isClientSide()) {
            if (mode == Mode.RELINK) mode = Mode.IDLE;
            relinkSession = null;
            relinkRequester = null;
            scanTotalPositions = 0;
            scanScannedPositions = 0;
            scanLinkedCount = linkedModifiers.size() + linkedBookshelves.size();
            resetContributionCache();
        }
    }
}
