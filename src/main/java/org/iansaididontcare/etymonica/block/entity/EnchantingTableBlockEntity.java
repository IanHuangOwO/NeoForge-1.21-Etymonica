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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.iansaididontcare.etymonica.block.entity.enchanting.EnchantBookDrainer;
import org.iansaididontcare.etymonica.block.entity.enchanting.EnchantPowerCalculator;
import org.iansaididontcare.etymonica.block.entity.enchanting.EnchantRelinkScanner;
import org.iansaididontcare.etymonica.block.entity.enchanting.EnchantingTableDataSlots;
import org.iansaididontcare.etymonica.enchanting.api.EnchantingTableModifierStats;
import org.iansaididontcare.etymonica.enchanting.api.EnchantingTableStats;
import org.iansaididontcare.etymonica.enchanting.data.EnchantingTableData;
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

public class EnchantingTableBlockEntity extends BlockEntity implements MenuProvider {
    private static final int BASE_PROGRESS_TICKS = 640;
    private enum Mode {
        IDLE,
        RELINK,
        ENCHANT
    }
    public enum TableActionResult {
        ENCHANT_STARTED,
        ENCHANT_BLOCKED,
        RELINK_STARTED,
        RELINK_CANCELLED,
        RELINK_BLOCKED,
        MODIFIER_LINKED,
        MODIFIER_UNLINKED,
        LINK_BLOCKED_NO_CAP,
        LINK_BLOCKED_CAP_REACHED,
        LINK_BLOCKED_NO_RADIUS,
        LINK_BLOCKED_TOO_FAR,
        LINK_BLOCKED_INVALID_BLOCK,
        LINK_BLOCKED_ALREADY_LINKED
    }

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
    private final Map<BlockPos, LinkedContribution> contributionCache = new HashMap<>();
    private float linkedSpeedTotal = 0f;
    private float linkedStabilityTotal = 0f;
    private float linkedEfficiencyTotal = 0f;
    private int linkedPowerTotal = 0;

    // Scan state (server) + synced progress (via data)
    private int scanCap = 0;
    private @Nullable EnchantRelinkScanner relinkSession = null;

    private int scanTotalPositions = 0;   // cube total
    private int scanScannedPositions = 0; // cube scanned so far
    private int scanLinkedCount = 0;      // linked so far
    private @Nullable UUID relinkRequester = null;

    private record LinkedContribution(long fingerprint, float speed, float stability, float efficiency, int power) {}
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

    public float getRenderingRotation() {
        renderRotation += 0.5f;
        if (renderRotation >= 360f) {
            renderRotation = 0f;
        }
        return renderRotation;
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
        scanCap = base.maxLinkedModifiers();
        relinkSession = new EnchantRelinkScanner(scanRadius, scanCap);

        linkedModifiers.clear();
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
            BlockState st = level.getBlockState(target);
            if (!st.is(ModBlockTags.ENCHANTING_TABLE_MODIFIERS)) continue;

            tryLinkedModifier(target);
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

    public TableActionResult requestStartEnchanting() {
        if (mode != Mode.IDLE) return TableActionResult.ENCHANT_BLOCKED;
        if (!hasValidInput()) return TableActionResult.ENCHANT_BLOCKED;
        mode = Mode.ENCHANT;
        progress = 0;
        setChanged();
        return TableActionResult.ENCHANT_STARTED;
    }

    public void requestUpdateStatsNow() {
        if (level == null || level.isClientSide()) return;
        markStatsDirty();
        recomputeCooldownTicks = 0;
        recomputeStatsNow(level, getBlockState());
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        setChanged();
    }

    private void stopEnchanting() {
        mode = Mode.IDLE;
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

        int drainBudget = EnchantBookDrainer.computeDrainBudgetFromItem(enchanted);
        if (drainBudget > 0) {
            int drained = EnchantBookDrainer.drainFromLinkedBookshelves(
                    level,
                    linkedModifiers,
                    ModBlockTags.ENCHANTING_TABLE_BOOKSHELVES,
                    drainBudget
            );
            if (drained > 0) {
                markStatsDirty();
                recomputeCooldownTicks = 0;
            }
        }
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

        currentPower = Math.max(0, Math.min(linkedPowerTotal, base.enchantingPowerCap()));
        scanCap = Math.max(0, base.maxLinkedModifiers());
        maxProgress = Math.max(1, Math.round(BASE_PROGRESS_TICKS * (1.0f - currentSpeed)));
        statsDirty = false;
    }

    private boolean reconcileLinkedContributions(Level level) {
        boolean changed = false;

        Iterator<BlockPos> linkedIt = linkedModifiers.iterator();
        while (linkedIt.hasNext()) {
            BlockPos pos = linkedIt.next();
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModBlockTags.ENCHANTING_TABLE_MODIFIERS)) {
                linkedIt.remove();
                removeContribution(pos);
                changed = true;
                continue;
            }

            long fingerprint = computeFingerprint(level, pos, state);
            LinkedContribution previous = contributionCache.get(pos);
            if (previous == null || previous.fingerprint() != fingerprint) {
                if (previous != null) subtractContribution(previous);
                LinkedContribution next = computeContribution(level, pos, state, fingerprint);
                contributionCache.put(pos, next);
                addContribution(next);
                changed = true;
            }
        }

        Iterator<Map.Entry<BlockPos, LinkedContribution>> cacheIt = contributionCache.entrySet().iterator();
        while (cacheIt.hasNext()) {
            Map.Entry<BlockPos, LinkedContribution> entry = cacheIt.next();
            if (!linkedModifiers.contains(entry.getKey())) {
                subtractContribution(entry.getValue());
                cacheIt.remove();
                changed = true;
            }
        }

        if (scanLinkedCount != linkedModifiers.size()) {
            scanLinkedCount = linkedModifiers.size();
            changed = true;
        }

        if (changed) markStatsDirty();
        return changed;
    }

    private long computeFingerprint(Level level, BlockPos pos, BlockState state) {
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

    private LinkedContribution computeContribution(Level level, BlockPos pos, BlockState state, long fingerprint) {
        Identifier id = blockId(level, state.getBlock());
        EnchantingTableModifierStats modifier = EnchantingTableData.getModifier(id);
        int power = EnchantPowerCalculator.computeBookshelfPowerForBlock(level, pos);
        return new LinkedContribution(fingerprint, modifier.speed(), modifier.stability(), modifier.efficiency(), power);
    }

    private void addContribution(LinkedContribution c) {
        linkedSpeedTotal += c.speed();
        linkedStabilityTotal += c.stability();
        linkedEfficiencyTotal += c.efficiency();
        linkedPowerTotal += c.power();
    }

    private void subtractContribution(LinkedContribution c) {
        linkedSpeedTotal -= c.speed();
        linkedStabilityTotal -= c.stability();
        linkedEfficiencyTotal -= c.efficiency();
        linkedPowerTotal -= c.power();
        if (linkedPowerTotal < 0) linkedPowerTotal = 0;
    }

    private void removeContribution(BlockPos pos) {
        LinkedContribution old = contributionCache.remove(pos);
        if (old != null) subtractContribution(old);
    }

    private void resetContributionCache() {
        contributionCache.clear();
        linkedSpeedTotal = 0f;
        linkedStabilityTotal = 0f;
        linkedEfficiencyTotal = 0f;
        linkedPowerTotal = 0;
        markStatsDirty();
    }

    private void markStatsDirty() {
        statsDirty = true;
    }

    private EnchantingTableStats getCurrentTierStats(ServerLevel level) {
        String tierId = getTierIdFromState(level, getBlockState());
        return EnchantingTableData.getTier(tierId);
    }

    /** Manual add from tuning fork. */
    public TableActionResult tryLinkModifier(ServerLevel level, BlockPos modifierPos) {
        EnchantingTableStats stats = getCurrentTierStats(level);

        int cap = stats.maxLinkedModifiers();
        if (cap <= 0) {
            return TableActionResult.LINK_BLOCKED_NO_CAP;
        }
        if (linkedModifiers.size() >= cap) {
            return TableActionResult.LINK_BLOCKED_CAP_REACHED;
        }

        int radius = stats.linkRadius();
        if (radius <= 0) {
            return TableActionResult.LINK_BLOCKED_NO_RADIUS;
        }

        int maxDistSq = radius * radius;
        if (modifierPos.distSqr(worldPosition) > maxDistSq) {
            return TableActionResult.LINK_BLOCKED_TOO_FAR;
        }

        BlockState st = level.getBlockState(modifierPos);
        if (!st.is(ModBlockTags.ENCHANTING_TABLE_MODIFIERS)) {
            return TableActionResult.LINK_BLOCKED_INVALID_BLOCK;
        }

        if (tryLinkedModifier(modifierPos)) {
            requestUpdateStatsNow();
            return TableActionResult.MODIFIER_LINKED;
        }

        if (tryUnlinkModifier(modifierPos)) {
            requestUpdateStatsNow();
            return TableActionResult.MODIFIER_UNLINKED;
        }

        return TableActionResult.LINK_BLOCKED_ALREADY_LINKED;
    }

    private boolean tryLinkedModifier(BlockPos modifierPos) {
        boolean added = linkedModifiers.add(modifierPos.immutable());
        if (added) {
            scanLinkedCount = linkedModifiers.size();
            markStatsDirty();
        }
        return added;
    }

    private boolean tryUnlinkModifier(BlockPos modifierPos) {
        boolean removed = linkedModifiers.remove(modifierPos);
        if (removed) {
            scanLinkedCount = linkedModifiers.size();
            markStatsDirty();
        }
        return removed;
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
        return level.registryAccess().lookupOrThrow(Registries.BLOCK).getKey(block);
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
        CompoundTag linkedTag = input.read("enchanting_table.linked_modifiers", CompoundTag.CODEC)
                .orElseGet(CompoundTag::new);
        long[] linked = linkedTag.getLongArray("positions").orElseGet(() -> new long[0]);
        for (long l : linked) {
            linkedModifiers.add(BlockPos.of(l));
        }
        resetContributionCache();

        // These are used for client display (tooltip/screen). Server will reset relink state below.
        boolean relink = input.getBooleanOr("enchanting_table.relinking", false);
        scanTotalPositions = input.getIntOr("enchanting_table.scan_total", 0);
        scanScannedPositions = input.getIntOr("enchanting_table.scan_done", 0);
        scanLinkedCount = input.getIntOr("enchanting_table.scan_linked", linkedModifiers.size());
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
            scanLinkedCount = linkedModifiers.size();
            resetContributionCache();
        }
    }
}
