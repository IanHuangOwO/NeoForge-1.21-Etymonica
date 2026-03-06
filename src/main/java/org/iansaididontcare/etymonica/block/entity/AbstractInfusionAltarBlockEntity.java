package org.iansaididontcare.etymonica.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import org.iansaididontcare.etymonica.block.entity.infusionaltar.InfusionCoreBlockEntity;
import org.iansaididontcare.etymonica.registry.enchantment.api.EnchantmentRarity;
import org.iansaididontcare.etymonica.registry.enchantment.data.EnchantmentData;
import org.iansaididontcare.etymonica.registry.infusion.api.AltarActionResult;
import org.iansaididontcare.etymonica.registry.infusion.api.InfusionAltarStats;
import org.iansaididontcare.etymonica.registry.infusion.data.InfusionAltarData;
import org.jetbrains.annotations.Nullable;

import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.block.ModBlocks;
import org.iansaididontcare.etymonica.block.custom.AbstractInfusionAltarBlock;
import org.iansaididontcare.etymonica.block.custom.infusionaltar.InfusionCoreBlock;
import org.iansaididontcare.etymonica.registry.infusion.api.MultiblockStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class AbstractInfusionAltarBlockEntity extends BlockEntity {
    public static final int TICKS_PER_ITEM = 200;

    public enum Mode {
        IDLE,
        INFUSE
    }

    // --- Multiblock State ---
    private boolean isFormed = false;
    private int structureCheckCooldown = 0;

    public boolean isFormed() {
        return isFormed;
    }

    protected ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if(level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    // --- Enchantment State ---
    private Mode mode = Mode.IDLE;
    private int progress = 0;
    private int maxProgress = 0;
    private final List<ItemStack> resultsBuffer = new ArrayList<>();
    private final List<ItemStack> popBuffer = new ArrayList<>();

    public @Nullable InfusionCoreBlockEntity getCore() {
        if (!isFormed || level == null) return null;
        String tierId = getTierIdFromState(level, getBlockState());
        InfusionAltarStats stats = InfusionAltarData.getAltarTier(tierId);
        BlockPos corePos = worldPosition.above(stats.multiblockStructure().offsetY() + 1);
        BlockEntity be = level.getBlockEntity(corePos);
        return be instanceof InfusionCoreBlockEntity core ? core : null;
    }

    public ItemStackHandler getInventory() {
        return this.inventory;
    }

    private float renderingRotation;

    public float getRenderingRotation() {
        renderingRotation += 0.5f;
        if (renderingRotation >= 360) renderingRotation = 0;
        return renderingRotation;
    }

    private int recomputeCooldownTicks = 0;
    private boolean statsDirty = true;
    private long lastDataRevision = -1L;
    private String lastTierId = "";
    
    public AbstractInfusionAltarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- Interaction Logic ---
    public InteractionResult handleInteraction(Player player, InteractionHand hand, ItemStack stack) {
        if (level == null) return InteractionResult.PASS;
        
        // Only allow interactions if formed
        if (!isFormed) return InteractionResult.PASS;

        ItemStackHandler inv = getInventory();
        ItemStack inSlot = inv.getStackInSlot(0);

        // 1. If holding an item, try to insert it
        if (!stack.isEmpty()) {
            if (!level.isClientSide()) {
                ItemStack toInsert = stack.copyWithCount(1);
                ItemStack remainder = inv.insertItem(0, toInsert, false);

                if (remainder.isEmpty()) {
                    stack.shrink(1);
                    level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);
                }
            }
            return InteractionResult.SUCCESS;
        } 
        
        // 2. If hand is empty, try to extract the item
        else if (hand == InteractionHand.MAIN_HAND) {
            if (!inSlot.isEmpty()) {
                if (!level.isClientSide()) {
                    ItemStack extracted = inv.extractItem(0, inSlot.getCount(), false);
                    if (!extracted.isEmpty()) {
                        if (!player.getInventory().add(extracted)) {
                            player.drop(extracted, false);
                        }
                        level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1f);
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    private void processOneItem(Level level) {
        if (resultsBuffer.isEmpty()) return;
        InfusionCoreBlockEntity core = getCore();
        if (core == null) {
            resultsBuffer.clear();
            return;
        }

        ItemStack bookCheck = core.getInventory().getStackInSlot(0);
        if (bookCheck.isEmpty() || !bookCheck.is(Items.BOOK)) {
            resultsBuffer.clear();
            return;
        }

        ItemStack result = resultsBuffer.remove(0);
        popBuffer.add(result);
    }

    private void finishInfusion(Level level) {
        InfusionCoreBlockEntity core = getCore();
        if (core == null) return;

        int countToConsume = popBuffer.size();
        if (countToConsume > 0) {
            core.getInventory().extractItem(0, countToConsume, false);
        }

        BlockPos corePos = core.getBlockPos();
        for (ItemStack result : popBuffer) {
            ItemEntity entity = new ItemEntity(level, corePos.getX() + 0.5, corePos.getY() + 1.5, corePos.getZ() + 0.5, result);
            entity.setDeltaMovement(level.random.nextGaussian() * 0.05, 0.2 + level.random.nextDouble() * 0.2, level.random.nextGaussian() * 0.05);
            level.addFreshEntity(entity);
        }
        popBuffer.clear();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public AltarActionResult attemptStartInfusion(Player player) {
        if (level == null || level.isClientSide() || mode != Mode.IDLE || !isFormed) return AltarActionResult.INFUSE_BLOCKED;

        InfusionCoreBlockEntity core = getCore();
        if (core == null) return AltarActionResult.INFUSE_BLOCKED;

        ItemStack bookStack = core.getInventory().getStackInSlot(0);
        if (bookStack.isEmpty() || !bookStack.is(Items.BOOK)) return AltarActionResult.INFUSE_BLOCKED;

        String tierId = getTierIdFromState(level, getBlockState());
        InfusionAltarStats stats = InfusionAltarData.getAltarTier(tierId);
        int processCount = Math.min(bookStack.getCount(), stats.itemsPerInfusion());

        if (processCount <= 0) return AltarActionResult.INFUSE_BLOCKED;

        resultsBuffer.clear();
        popBuffer.clear();
        RandomSource random = level.random;
        var enchantRegistry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        for (int i = 0; i < processCount; i++) {
            EnchantmentRarity rolledRarity = rollRarity(stats, random);
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
        maxProgress = (int) Math.round(TICKS_PER_ITEM / (1.0 + stats.speed()));
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

    public boolean checkStructure() {
        if (level == null) return false;
        boolean formedNow = verifyStructure(level, worldPosition);
        setFormed(formedNow);
        return formedNow;
    }

    private boolean verifyStructure(Level level, BlockPos center) {
        String tierId = getTierIdFromState(level, getBlockState());
        InfusionAltarStats stats = InfusionAltarData.getAltarTier(tierId);
        MultiblockStructure struct = stats.multiblockStructure();

        BlockPos cubeBottomCenter = center.above(struct.offsetY());

        if (!checkLayer(level, cubeBottomCenter, struct.bottom())) return false;
        if (!checkLayer(level, cubeBottomCenter.above(), struct.middle())) return false;
        if (!checkLayer(level, cubeBottomCenter.above(2), struct.top())) return false;

        return true;
    }

    private boolean checkLayer(Level level, BlockPos layerCenter, List<List<String>> layerData) {
        for (int row = 0; row < 3; row++) {
            List<String> rowData = layerData.get(row);
            for (int col = 0; col < 3; col++) {
                String expectedBlockId = rowData.get(col);
                if (expectedBlockId.isEmpty()) continue;

                int dx = row - 1;
                int dz = col - 1;
                BlockPos targetPos = layerCenter.offset(dx, 0, dz);
                if (targetPos.equals(worldPosition)) continue;

                BlockState state = level.getBlockState(targetPos);
                Identifier expectedId = Identifier.parse(expectedBlockId);
                Identifier actualId = level.registryAccess().lookupOrThrow(Registries.BLOCK).getKey(state.getBlock());

                if (actualId == null || !actualId.equals(expectedId)) return false;
            }
        }
        return true;
    }

    private void setFormed(boolean formed) {
        if (this.isFormed != formed) {
            this.isFormed = formed;
            if (level != null && !level.isClientSide()) {
                String tierId = getTierIdFromState(level, getBlockState());
                InfusionAltarStats stats = InfusionAltarData.getAltarTier(tierId);
                BlockPos corePos = worldPosition.above(stats.multiblockStructure().offsetY() + 1);

                if (formed) {
                    // Place core
                    int tierInt = getTierInt(tierId);
                    BlockState coreState = ModBlocks.INFUSION_CORE.get().defaultBlockState().setValue(InfusionCoreBlock.TIER, tierInt);
                    level.setBlock(corePos, coreState, 3);
                    if (level.getBlockEntity(corePos) instanceof InfusionCoreBlockEntity core) {
                        core.setTierId(tierId);
                        core.resizeInventory(stats.itemsPerInfusion());
                    }
                } else {
                    // Remove core
                    if (level.getBlockState(corePos).is(ModBlocks.INFUSION_CORE.get())) {
                        level.destroyBlock(corePos, true);
                    }
                    mode = Mode.IDLE;
                    progress = 0;
                    resultsBuffer.clear();
                    popBuffer.clear();
                }

                BlockState state = level.getBlockState(worldPosition);
                if (state.hasProperty(AbstractInfusionAltarBlock.FORMED)) {
                    level.setBlock(worldPosition, state.setValue(AbstractInfusionAltarBlock.FORMED, formed), 3);
                }
                setChanged();
                level.sendBlockUpdated(worldPosition, state, state, 3);
            }
        }
    }

    private int getTierInt(String tierId) {
        return switch (tierId) {
            case "tier1" -> 1;
            case "tier2" -> 2;
            default -> 0;
        };
    }

    public void tickServer(Level level, BlockPos pos, BlockState state) {
        if (recomputeCooldownTicks-- <= 0) {
            recomputeCooldownTicks = 20;
            recomputeStatsNow(level, state);
        }

        if (isFormed && structureCheckCooldown-- <= 0) {
            structureCheckCooldown = 40;
            if (!verifyStructure(level, pos)) {
                setFormed(false);
            }
        }

        if (isFormed && mode == Mode.INFUSE) {
            progress++;
            if (progress >= maxProgress) {
                processOneItem(level);
                if (resultsBuffer.isEmpty()) {
                    finishInfusion(level);
                    mode = Mode.IDLE;
                    progress = 0;
                } else {
                    progress = 0;
                    String tierId = getTierIdFromState(level, state);
                    InfusionAltarStats stats = InfusionAltarData.getAltarTier(tierId);
                    maxProgress = (int) Math.round(TICKS_PER_ITEM / (1.0 + stats.speed()));
                }
            }
            setChanged();
        }
    }

    private void recomputeStatsNow(Level level, BlockState state) {
        String tierId = getTierIdFromState(level, state);
        InfusionAltarStats base = InfusionAltarData.getAltarTier(tierId);
        boolean tierChanged = !tierId.equals(lastTierId);
        long revision = InfusionAltarData.getRevision();
        boolean dataChanged = revision != lastDataRevision;

        if (!(statsDirty || tierChanged || dataChanged)) return;

        lastTierId = tierId;
        lastDataRevision = revision;
        statsDirty = false;

        InfusionCoreBlockEntity core = getCore();
        if (core != null) core.setTierId(tierId);
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
        if (isFormed) setFormed(false);
        SimpleContainer inv = new SimpleContainer(inventory.getSlots());
        for(int i = 0; i < inventory.getSlots(); i++) inv.setItem(i, inventory.getStackInSlot(i));
        if (this.level != null) Containers.dropContents(this.level, this.worldPosition, inv);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("infusion_altar.is_formed", isFormed);
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
        isFormed = input.getBooleanOr("infusion_altar.is_formed", false);
        inventory.deserialize(input);
        resultsBuffer.clear();
        input.read("infusion_altar.results_buffer", ItemStack.CODEC.listOf()).ifPresent(resultsBuffer::addAll);
        popBuffer.clear();
        input.read("infusion_altar.pop_buffer", ItemStack.CODEC.listOf()).ifPresent(popBuffer::addAll);
        progress = input.getIntOr("infusion_altar.progress", 0);
        maxProgress = input.getIntOr("infusion_altar.max_progress", 0);
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
