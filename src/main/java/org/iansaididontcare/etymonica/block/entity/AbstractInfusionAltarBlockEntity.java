package org.iansaididontcare.etymonica.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
    // --- Core constants and machine modes ---
    public static final int TICKS_PER_ITEM = 200;
    
    public enum Mode {
        IDLE,
        INFUSE
    }

    // --- Inventory and runtime state shared by ticking and renderer ---
    protected ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            statsDirty = true;
            if(level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            if (level == null) return 1;
            String tierId = getTierIdFromState(level, getBlockState());
            int limit = InfusionAltarData.getAltarTier(tierId).itemsPerInfusion();
            return limit > 0 ? limit : 1;
        }
    };

    public ItemStackHandler getInventory() {
        return inventory;
    }

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

    // items queued to be processed
    private final List<ItemStack> resultsBuffer = new ArrayList<>();
    // completed items waiting to be popped at the end
    private final List<ItemStack> popBuffer = new ArrayList<>();
    
    public AbstractInfusionAltarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- Client-side visual rotation for the rendered items ---
    public float getRenderingRotation() {
        rotation += 0.5f;
        if(rotation >= 360) rotation = 0;
        return rotation;
    }

    // --- Main server tick: periodic stat recompute and infusion progress ---
    public void tickServer(Level level, BlockPos pos, BlockState state) {
        if (recomputeCooldownTicks-- <= 0) {
            recomputeCooldownTicks = 20;
            recomputeStatsNow(level, state);
        }

        if (mode == Mode.INFUSE) {
            progress++;
            if (progress >= maxProgress) {
                processOneItem(level);
                
                if (resultsBuffer.isEmpty()) {
                    finishInfusion(level);
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

    // --- Infusion lifecycle: start/stop flow, item calculation, and bulk payout ---
    public AltarActionResult attemptStartInfusion(Player player) {
        if (level == null || level.isClientSide() || mode != Mode.IDLE) return AltarActionResult.INFUSE_BLOCKED;

        ItemStack bookStack = inventory.getStackInSlot(0);
        if (bookStack.isEmpty() || !bookStack.is(Items.BOOK)) return AltarActionResult.INFUSE_BLOCKED;

        String tierId = getTierIdFromState(level, getBlockState());
        InfusionAltarStats tierStats = InfusionAltarData.getAltarTier(tierId);

        int processCount = Math.min(bookStack.getCount(), tierStats.itemsPerInfusion());

        if (processCount <= 0) {
            return AltarActionResult.INFUSE_BLOCKED;
        }

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

        // Check if books are still in the Altar
        ItemStack bookCheck = inventory.getStackInSlot(0);
        if (bookCheck.isEmpty() || !bookCheck.is(Items.BOOK)) {
            resultsBuffer.clear();
            return;
        }

        ItemStack result = resultsBuffer.remove(0);
        popBuffer.add(result);
    }

    private void finishInfusion(Level level) {
        // Bulk consume books from Altar based on how many were successfully processed
        int countToConsume = popBuffer.size();
        if (countToConsume > 0) {
            inventory.extractItem(0, countToConsume, false);
        }

        // Spawn all results at once
        for (ItemStack result : popBuffer) {
            ItemEntity entity = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, result);
            entity.setDeltaMovement(level.random.nextGaussian() * 0.05, 0.2 + level.random.nextDouble() * 0.2, level.random.nextGaussian() * 0.05);
            level.addFreshEntity(entity);
        }
        popBuffer.clear();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    // --- Stat refresh API and recompute pipeline ---
    private void recomputeStatsNow(Level level, BlockState state) {
        String tierId = getTierIdFromState(level, state);
        InfusionAltarStats base = InfusionAltarData.getAltarTier(tierId);
        boolean tierChanged = !tierId.equals(lastTierId);
        long revision = InfusionAltarData.getRevision();
        boolean dataChanged = revision != lastDataRevision;

        if (!(statsDirty || tierChanged || dataChanged)) return;

        lastTierId = tierId;
        lastDataRevision = revision;
        currentSpeed = (float) base.speed();
        currentEfficiency = (float) base.efficiency();
        statsDirty = false;
    }

    // --- Small registry and tier resolution helpers ---
    protected final Identifier blockId(Level level, Block block) {
        return level.registryAccess().lookupOrThrow(Registries.BLOCK).getKey(block);
    }

    private String getTierIdFromState(Level level, BlockState state) {
        Identifier id = blockId(level, state.getBlock());
        String path = id.getPath();
        if (path.startsWith("infusion_altar_")) return path.substring("infusion_altar_".length());
        return "tier0";
    }

    // --- Block-entity lifecycle hooks: drop inventory and persist runtime data ---
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
        
        resultsBuffer.clear();
        input.read("infusion_altar.results_buffer", ItemStack.CODEC.listOf()).ifPresent(resultsBuffer::addAll);
        
        popBuffer.clear();
        input.read("infusion_altar.pop_buffer", ItemStack.CODEC.listOf()).ifPresent(popBuffer::addAll);

        progress = input.getIntOr("infusion_altar.progress", 0);
        maxProgress = input.getIntOr("infusion_altar.max_progress", TICKS_PER_ITEM);
        int modeIdx = input.getIntOr("infusion_altar.mode", 0);
        mode = Mode.values()[modeIdx % Mode.values().length];
        if (this.level != null && !this.level.isClientSide()) {
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
