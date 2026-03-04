package org.iansaididontcare.etymonica.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.iansaididontcare.etymonica.block.entity.infusionaltar.infuser.InfusionRelinkScanner;
import org.iansaididontcare.etymonica.registry.enchantment.api.EnchantmentRarity;
import org.iansaididontcare.etymonica.registry.enchantment.data.EnchantmentData;
import org.iansaididontcare.etymonica.registry.infusion.api.AltarActionResult;
import org.iansaididontcare.etymonica.registry.infusion.api.InfusionAltarModifierStats;
import org.iansaididontcare.etymonica.registry.infusion.api.InfusionAltarStats;
import org.iansaididontcare.etymonica.registry.infusion.data.InfusionAltarData;
import org.iansaididontcare.etymonica.tag.ModBlockTags;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractInfusionAltarBlockEntity extends BlockEntity {
    private static final int TICKS_PER_ITEM = 200;
    
    private enum Mode {
        IDLE,
        RELINK,
        INFUSE
    }

    private static final int SCAN_POSITIONS_PER_TICK = 1500;

    public final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if(level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };


    private Mode mode = Mode.IDLE;
    private int progress = 0;
    private int maxProgress = 0;
    private float rotation;

    private float currentSpeed = 0f;
    private float currentEfficiency = 0f;

    private int recomputeCooldownTicks = 0;
    private boolean statsDirty = true;
    private long lastDataRevision = -1L;
    private String lastTierId = "";

    private final Set<BlockPos> linkedModifiers = new HashSet<>();
    private final Set<BlockPos> linkedPedestals = new HashSet<>();
    
    // items queued to be processed
    private final List<ItemStack> resultsBuffer = new ArrayList<>();
    // completed items waiting to be popped at the end
    private final List<ItemStack> popBuffer = new ArrayList<>();
    
    private final Map<BlockPos, LinkedModifierContribution> modifierContributionCache = new HashMap<>();
    private float linkedSpeedTotal = 0f;
    private float linkedEfficiencyTotal = 0f;

    private int scanCap = 0;
    private @Nullable InfusionRelinkScanner relinkSession = null;
    private int scanTotalPositions = 0;
    private int scanScannedPositions = 0;
    private int scanLinkedCount = 0;
    private @Nullable UUID relinkRequester = null;

    private record LinkedModifierContribution(long fingerprint, float speed, float efficiency) {}

    public AbstractInfusionAltarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public float getRenderingRotation() {
        rotation += 0.5f;
        if(rotation >= 360) rotation = 0;
        return rotation;
    }

    public void tickServer(Level level, BlockPos pos, BlockState state) {
        if (mode == Mode.RELINK) {
            advanceRelinkScan((ServerLevel) level);
        }

        if (recomputeCooldownTicks-- <= 0) {
            recomputeCooldownTicks = 20;
            recomputeStatsNow(level, state);
        }

        if (mode == Mode.INFUSE) {
            progress++;
            if (progress >= maxProgress) {
                processOneItem(level);
                
                if (resultsBuffer.isEmpty()) {
                    spawnFinalResults(level);
                    mode = Mode.IDLE;
                    progress = 0;
                    maxProgress = 0;
                } else {
                    progress = 0;
                    maxProgress = (int) Math.round(TICKS_PER_ITEM / (1.0 + currentSpeed));
                }
            }
            setChanged();
        }
    }

    public AltarActionResult attemptStartInfusion(Player player) {
        if (level == null || level.isClientSide() || mode != Mode.IDLE) return AltarActionResult.INFUSE_BLOCKED;

        ItemStack bookStack = inventory.getStackInSlot(0);
        if (bookStack.isEmpty() || !bookStack.is(Items.BOOK)) return AltarActionResult.INFUSE_BLOCKED;

        String tierId = getTierIdFromState(level, getBlockState());
        InfusionAltarStats tierStats = InfusionAltarData.getAltarTier(tierId);

        int lapisAvailable = 0;
        for (BlockPos pPos : linkedPedestals) {
            BlockEntity be = level.getBlockEntity(pPos);
            if (be instanceof AbstractInfusionAltarBlockEntity pedestal) {
                ItemStack fuel = pedestal.inventory.getStackInSlot(0);
                if (!fuel.isEmpty() && fuel.is(Items.LAPIS_LAZULI)) {
                    lapisAvailable += fuel.getCount();
                }
            }
        }

        if (lapisAvailable <= 0) return AltarActionResult.INFUSE_BLOCKED;

        int processCount = Math.min(bookStack.getCount(), lapisAvailable);
        processCount = Math.min(processCount, tierStats.itemsPerInfusion());

        if (processCount <= 0) return AltarActionResult.INFUSE_BLOCKED;

        resultsBuffer.clear();
        popBuffer.clear();
        RandomSource random = level.random;
        var enchantRegistry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        for (int i = 0; i < processCount; i++) {
            EnchantmentRarity rolledRarity = rollRarity(tierStats, random);
            Optional<Identifier> rolledEnchantId = EnchantmentData.getRandomEnchantmentByRarity(rolledRarity, random);
            
            if (rolledEnchantId.isPresent()) {
                var enchantHolder = enchantRegistry.get(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, rolledEnchantId.get()));
                if (enchantHolder.isPresent()) {
                    ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
                    net.minecraft.world.item.enchantment.ItemEnchantments.Mutable mutable = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
                    mutable.set(enchantHolder.get(), 1);
                    net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(enchantedBook, mutable.toImmutable());
                    resultsBuffer.add(enchantedBook);
                }
            }
        }

        if (resultsBuffer.isEmpty()) return AltarActionResult.INFUSE_BLOCKED;

        progress = 0;
        maxProgress = (int) Math.round(TICKS_PER_ITEM / (1.0 + currentSpeed));
        mode = Mode.INFUSE;
        
        setChanged();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        return AltarActionResult.INFUSE_STARTED;
    }

    private EnchantmentRarity rollRarity(InfusionAltarStats stats, RandomSource random) {
        double roll = random.nextDouble();
        var w = stats.weights();
        if (roll < w.mystic()) return EnchantmentRarity.MYSTIC;
        roll -= w.mystic();
        if (roll < w.legendary()) return EnchantmentRarity.LEGENDARY;
        roll -= w.legendary();
        if (roll < w.epic()) return EnchantmentRarity.EPIC;
        roll -= w.epic();
        if (roll < w.rare()) return EnchantmentRarity.RARE;
        roll -= w.rare();
        if (roll < w.uncommon()) return EnchantmentRarity.UNCOMMON;
        return EnchantmentRarity.COMMON;
    }

    private void processOneItem(Level level) {
        if (resultsBuffer.isEmpty()) return;

        // Check if books are still in the Altar (but don't consume yet)
        ItemStack bookCheck = inventory.getStackInSlot(0);
        if (bookCheck.isEmpty() || !bookCheck.is(Items.BOOK)) {
            resultsBuffer.clear();
            return;
        }

        ItemStack result = resultsBuffer.remove(0);
        boolean paid = false;

        // Consume Lapis
        for (BlockPos pPos : linkedPedestals) {
            BlockEntity be = level.getBlockEntity(pPos);
            if (be instanceof AbstractInfusionAltarBlockEntity pedestal) {
                ItemStack fuel = pedestal.inventory.getStackInSlot(0);
                if (!fuel.isEmpty() && fuel.is(Items.LAPIS_LAZULI)) {
                    pedestal.inventory.extractItem(0, 1, false);
                    paid = true;
                    break;
                }
            }
        }

        // Add to final pop buffer
        popBuffer.add(paid ? result : new ItemStack(Items.BOOK));
    }

    private void spawnFinalResults(Level level) {
        // Bulk consume books from Altar based on how many were processed
        int countToConsume = popBuffer.size();
        if (countToConsume > 0) {
            inventory.extractItem(0, countToConsume, false);
        }

        for (ItemStack result : popBuffer) {
            ItemEntity entity = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, result);
            entity.setDeltaMovement(level.random.nextGaussian() * 0.05, 0.2 + level.random.nextDouble() * 0.2, level.random.nextGaussian() * 0.05);
            level.addFreshEntity(entity);
        }
        popBuffer.clear();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    // --- Stats & Linking Logic ---

    private void recomputeStatsNow(Level level, BlockState state) {
        String tierId = getTierIdFromState(level, state);
        InfusionAltarStats base = InfusionAltarData.getAltarTier(tierId);
        boolean tierChanged = !tierId.equals(lastTierId);
        long revision = InfusionAltarData.getRevision();
        boolean dataChanged = revision != lastDataRevision;
        boolean linkedChanged = reconcileLinkedContributions(level);

        if (!(statsDirty || tierChanged || dataChanged || linkedChanged)) return;

        lastTierId = tierId;
        lastDataRevision = revision;
        currentSpeed = (float) (base.speed() + linkedSpeedTotal);
        currentEfficiency = (float) (base.efficiency() + linkedEfficiencyTotal);
        statsDirty = false;
    }

    private boolean reconcileLinkedContributions(Level level) {
        boolean changed = false;
        Iterator<BlockPos> pedIt = linkedPedestals.iterator();
        while (pedIt.hasNext()) {
            if (!level.getBlockState(pedIt.next()).is(ModBlockTags.INFUSION_ALTAR_PEDESTALS)) {
                pedIt.remove();
                changed = true;
            }
        }
        Iterator<BlockPos> linkedIt = linkedModifiers.iterator();
        while (linkedIt.hasNext()) {
            BlockPos pos = linkedIt.next();
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModBlockTags.INFUSION_ALTAR_MODIFIERS)) {
                linkedIt.remove();
                removeModifierContribution(pos);
                changed = true;
                continue;
            }
            long fingerprint = Block.getId(state);
            LinkedModifierContribution previous = modifierContributionCache.get(pos);
            if (previous == null || previous.fingerprint() != fingerprint) {
                if (previous != null) subtractModifierContribution(previous);
                Identifier id = blockId(level, state.getBlock());
                InfusionAltarModifierStats m = InfusionAltarData.getModifier(id);
                LinkedModifierContribution next = new LinkedModifierContribution(fingerprint, (float) m.speed(), (float) m.efficiency());
                modifierContributionCache.put(pos, next);
                addModifierContribution(next);
                changed = true;
            }
        }
        Iterator<Map.Entry<BlockPos, LinkedModifierContribution>> cacheIt = modifierContributionCache.entrySet().iterator();
        while (cacheIt.hasNext()) {
            if (!linkedModifiers.contains(cacheIt.next().getKey())) {
                subtractModifierContribution(cacheIt.next().getValue());
                cacheIt.remove();
                changed = true;
            }
        }
        int totalCount = linkedModifiers.size() + linkedPedestals.size();
        if (scanLinkedCount != totalCount) {
            scanLinkedCount = totalCount;
            changed = true;
        }
        if (changed) statsDirty = true;
        return changed;
    }

    private void addModifierContribution(LinkedModifierContribution c) {
        linkedSpeedTotal += c.speed();
        linkedEfficiencyTotal += c.efficiency();
    }

    private void subtractModifierContribution(LinkedModifierContribution c) {
        linkedSpeedTotal -= c.speed();
        linkedEfficiencyTotal -= c.efficiency();
    }

    private void removeModifierContribution(BlockPos pos) {
        LinkedModifierContribution old = modifierContributionCache.remove(pos);
        if (old != null) subtractModifierContribution(old);
    }

    public AltarActionResult beginOrCancelRelinkScan(@Nullable Player requester) {
        if (level == null || level.isClientSide()) return AltarActionResult.RELINK_BLOCKED;
        if (mode == Mode.RELINK) {
            cancelRelinkScan((ServerLevel) level);
            return AltarActionResult.RELINK_CANCELLED;
        }
        String tierId = getTierIdFromState(level, getBlockState());
        InfusionAltarStats base = InfusionAltarData.getAltarTier(tierId);
        scanCap = 9999;
        relinkSession = new org.iansaididontcare.etymonica.block.entity.infusionaltar.infuser.InfusionRelinkScanner(base.linkRadius(), scanCap);
        linkedModifiers.clear();
        linkedPedestals.clear();
        scanLinkedCount = 0;
        modifierContributionCache.clear();
        linkedSpeedTotal = 0f;
        linkedEfficiencyTotal = 0f;
        if (base.linkRadius() <= 0) {
            mode = Mode.IDLE;
            relinkSession = null;
            return AltarActionResult.RELINK_BLOCKED;
        }
        scanTotalPositions = relinkSession.getTotalPositions();
        scanScannedPositions = relinkSession.getScannedPositions();
        mode = Mode.RELINK;
        relinkRequester = requester != null ? requester.getUUID() : null;
        setChanged();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        return AltarActionResult.RELINK_STARTED;
    }

    private void cancelRelinkScan(ServerLevel level) {
        mode = Mode.IDLE;
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
            if (target == null) break;
            scanScannedPositions = relinkSession.getScannedPositions();
            tryLinkedBlock(target);
        }
        if (relinkSession != null) {
            scanScannedPositions = relinkSession.getScannedPositions();
            if (!relinkSession.isActive()) {
                mode = Mode.IDLE;
                relinkSession = null;
                if (relinkRequester != null) {
                    Player requester = level.getPlayerByUUID(relinkRequester);
                    if (requester != null) {
                        requester.displayClientMessage(Component.translatable("message.etymonica.relink.completed", scanLinkedCount), true);
                    }
                }
                relinkRequester = null;
                statsDirty = true;
                setChanged();
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    }

    private void tryLinkedBlock(BlockPos target) {
        BlockState st = level.getBlockState(target);
        if (st.is(ModBlockTags.INFUSION_ALTAR_PEDESTALS)) {
            tryLinkedPedestal(target);
        } else if (st.is(ModBlockTags.INFUSION_ALTAR_MODIFIERS)) {
            tryLinkedModifier(target);
        }
    }

    public AltarActionResult tryManualLink(ServerLevel level, BlockPos targetPos) {
        BlockState st = level.getBlockState(targetPos);
        if (st.is(ModBlockTags.INFUSION_ALTAR_PEDESTALS)) {
            if (linkedPedestals.contains(targetPos)) {
                linkedPedestals.remove(targetPos);
                statsDirty = true;
                return AltarActionResult.MODIFIER_UNLINKED;
            } else if (tryLinkedPedestal(targetPos)) return AltarActionResult.MODIFIER_LINKED;
        } else if (st.is(ModBlockTags.INFUSION_ALTAR_MODIFIERS)) {
            if (linkedModifiers.contains(targetPos)) {
                linkedModifiers.remove(targetPos);
                removeModifierContribution(targetPos);
                statsDirty = true;
                return AltarActionResult.MODIFIER_UNLINKED;
            } else if (tryLinkedModifier(targetPos)) return AltarActionResult.MODIFIER_LINKED;
        }
        return AltarActionResult.LINK_BLOCKED_INVALID_BLOCK;
    }

    private boolean tryLinkedPedestal(BlockPos pedestalPos) {
        if (level == null || linkedPedestals.contains(pedestalPos)) return false;
        String tierId = getTierIdFromState(level, getBlockState());
        InfusionAltarStats tierStats = InfusionAltarData.getAltarTier(tierId);
        if (linkedPedestals.size() >= tierStats.maxLinkedPedestals()) return false;
        boolean added = linkedPedestals.add(pedestalPos.immutable());
        if (added) {
            scanLinkedCount = linkedModifiers.size() + linkedPedestals.size();
            statsDirty = true;
        }
        return added;
    }

    private boolean tryLinkedModifier(BlockPos modifierPos) {
        if (level == null || linkedModifiers.contains(modifierPos)) return false;
        BlockState state = level.getBlockState(modifierPos);
        Identifier id = blockId(level, state.getBlock());
        InfusionAltarModifierStats stats = InfusionAltarData.getModifier(id);
        if (stats.maxNum() > 0 && countModifiersOfType(state.getBlock()) >= stats.maxNum()) return false;
        boolean added = linkedModifiers.add(modifierPos.immutable());
        if (added) {
            scanLinkedCount = linkedModifiers.size() + linkedPedestals.size();
            statsDirty = true;
        }
        return added;
    }

    private int countModifiersOfType(Block block) {
        if (level == null) return 0;
        int count = 0;
        for (BlockPos pos : linkedModifiers) if (level.getBlockState(pos).is(block)) count++;
        return count;
    }

    protected final Identifier blockId(Level level, Block block) {
        return level.registryAccess().lookupOrThrow(Registries.BLOCK).getKey(block);
    }

    private String getTierIdFromState(Level level, BlockState state) {
        Identifier id = blockId(level, state.getBlock());
        String path = id.getPath();
        if (path.startsWith("infusion_altar_")) return path.substring("infusion_altar_".length());
        return "tier0";
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        SimpleContainer inv = new SimpleContainer(inventory.getSlots());
        for(int i = 0; i < inventory.getSlots(); i++) inv.setItem(i, inventory.getStackInSlot(i));
        if (this.level != null) Containers.dropContents(this.level, this.worldPosition, inv);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output);
        long[] linkedMod = linkedModifiers.stream().mapToLong(BlockPos::asLong).toArray();
        CompoundTag linkedModTag = new CompoundTag();
        linkedModTag.putLongArray("positions", linkedMod);
        output.store("infusion_altar.linked_modifiers", CompoundTag.CODEC, linkedModTag);
        long[] linkedPed = linkedPedestals.stream().mapToLong(BlockPos::asLong).toArray();
        CompoundTag linkedPedTag = new CompoundTag();
        linkedPedTag.putLongArray("positions", linkedPed);
        output.store("infusion_altar.linked_pedestals", CompoundTag.CODEC, linkedPedTag);
        
        output.store("infusion_altar.results_buffer", ItemStack.CODEC.listOf(), resultsBuffer);
        output.store("infusion_altar.pop_buffer", ItemStack.CODEC.listOf(), popBuffer);

        output.putInt("infusion_altar.progress", progress);
        output.putInt("infusion_altar.max_progress", maxProgress);
        output.putInt("infusion_altar.mode", mode.ordinal());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input);
        linkedModifiers.clear();
        input.read("infusion_altar.linked_modifiers", CompoundTag.CODEC).ifPresent(tag -> {
            long[] linked = tag.getLongArray("positions").orElse(new long[0]);
            for (long l : linked) linkedModifiers.add(BlockPos.of(l));
        });
        linkedPedestals.clear();
        input.read("infusion_altar.linked_pedestals", CompoundTag.CODEC).ifPresent(tag -> {
            long[] linked = tag.getLongArray("positions").orElse(new long[0]);
            for (long l : linked) linkedPedestals.add(BlockPos.of(l));
        });
        
        resultsBuffer.clear();
        input.read("infusion_altar.results_buffer", ItemStack.CODEC.listOf()).ifPresent(resultsBuffer::addAll);
        
        popBuffer.clear();
        input.read("infusion_altar.pop_buffer", ItemStack.CODEC.listOf()).ifPresent(popBuffer::addAll);

        progress = input.getIntOr("infusion_altar.progress", 0);
        maxProgress = input.getIntOr("infusion_altar.max_progress", TICKS_PER_ITEM);
        int modeIdx = input.getIntOr("infusion_altar.mode", 0);
        mode = Mode.values()[modeIdx % Mode.values().length];
        if (this.level != null && !this.level.isClientSide()) {
            if (mode == Mode.RELINK) mode = Mode.IDLE;
            relinkSession = null;
            statsDirty = true;
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        return saveWithoutMetadata(pRegistries);
    }
}
