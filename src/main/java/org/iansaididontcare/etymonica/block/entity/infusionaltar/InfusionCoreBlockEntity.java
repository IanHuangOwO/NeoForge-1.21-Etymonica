package org.iansaididontcare.etymonica.block.entity.infusionaltar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;
import org.iansaididontcare.etymonica.registry.enchantment.api.EnchantmentRarity;
import org.iansaididontcare.etymonica.registry.enchantment.data.EnchantmentData;
import org.iansaididontcare.etymonica.registry.infusion.api.AltarActionResult;
import org.iansaididontcare.etymonica.registry.infusion.api.InfusionAltarStats;
import org.iansaididontcare.etymonica.registry.infusion.data.InfusionAltarData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InfusionCoreBlockEntity extends BlockEntity {
    public static final int TICKS_PER_ITEM = 200;

    public enum Mode {
        IDLE,
        INFUSE
    }

    private final ItemStackHandler inventory = new ItemStackHandler(1) {
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
    private String tierId = "tier0";

    private final List<ItemStack> resultsBuffer = new ArrayList<>();
    private final List<ItemStack> popBuffer = new ArrayList<>();

    // --- Animation Fields ---
    public int time;
    public float flip;
    public float oFlip;
    public float flipT;
    public float flipA;
    public float open;
    public float oOpen;
    public float rot;
    public float oRot;
    public float tRot;
    private static final RandomSource RANDOM = RandomSource.create();

    public InfusionCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFUSION_CORE_BE.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public void setTierId(String tierId) {
        this.tierId = tierId;
    }

    private float renderingRotation;

    public float getRenderingRotation() {
        renderingRotation += 0.5f;
        if(renderingRotation >= 360) renderingRotation = 0;
        return renderingRotation;
    }

    public void tickClient() {
        this.oOpen = this.open;
        this.oRot = this.rot;
        Player player = this.level.getNearestPlayer((double)this.worldPosition.getX() + 0.5, (double)this.worldPosition.getY() + 0.5, (double)this.worldPosition.getZ() + 0.5, 3.0, false);
        if (player != null) {
            double d0 = player.getX() - ((double)this.worldPosition.getX() + 0.5);
            double d1 = player.getZ() - ((double)this.worldPosition.getZ() + 0.5);
            this.tRot = (float)Mth.atan2(d1, d0);
            this.open += 0.1F;
            if (this.open < 0.5F || RANDOM.nextInt(40) == 0) {
                float f1 = this.flipT;

                do {
                    this.flipT += (float)(RANDOM.nextInt(4) - RANDOM.nextInt(4));
                } while(f1 == this.flipT);
            }
        } else {
            this.tRot += 0.02F;
            this.open -= 0.1F;
        }

        while(this.rot >= (float)Math.PI) {
            this.rot -= ((float)Math.PI * 2F);
        }

        while(this.rot < -(float)Math.PI) {
            this.rot += ((float)Math.PI * 2F);
        }

        while(this.tRot >= (float)Math.PI) {
            this.tRot -= ((float)Math.PI * 2F);
        }

        while(this.tRot < -(float)Math.PI) {
            this.tRot += ((float)Math.PI * 2F);
        }

        float f2;
        for(f2 = this.tRot - this.rot; f2 >= (float)Math.PI; f2 -= ((float)Math.PI * 2F)) {
        }

        while(f2 < -(float)Math.PI) {
            f2 += ((float)Math.PI * 2F);
        }

        this.rot += f2 * 0.4F;
        this.open = Mth.clamp(this.open, 0.0F, 1.0F);
        ++this.time;
        this.oFlip = this.flip;
        float f = (this.flipT - this.flip) * 0.4F;
        float f3 = 0.2F;
        f = Mth.clamp(f, -0.2F, 0.2F);
        this.flipA += (f - this.flipA) * 0.9F;
        this.flip += this.flipA;
    }

    public void tickServer(Level level, BlockPos pos, BlockState state) {
        if (mode == Mode.INFUSE) {
            progress++;
            if (progress >= maxProgress) {
                processOneItem(level);
                if (resultsBuffer.isEmpty()) {
                    finishInfusion(level);
                    mode = Mode.IDLE;
                    progress = 0;
                } else {
                    progress = 0;
                    InfusionAltarStats stats = InfusionAltarData.getAltarTier(tierId);
                    maxProgress = (int) Math.round(TICKS_PER_ITEM / (1.0 + stats.speed()));
                }
            }
            setChanged();
        }
    }

    public AltarActionResult attemptStartInfusion(Player player, String currentTierId) {
        this.tierId = currentTierId;
        if (level == null || level.isClientSide() || mode != Mode.IDLE) return AltarActionResult.INFUSE_BLOCKED;

        ItemStack bookStack = inventory.getStackInSlot(0);
        if (bookStack.isEmpty() || !bookStack.is(Items.BOOK)) return AltarActionResult.INFUSE_BLOCKED;

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

    private void processOneItem(Level level) {
        if (resultsBuffer.isEmpty()) return;
        ItemStack bookCheck = inventory.getStackInSlot(0);
        if (bookCheck.isEmpty() || !bookCheck.is(Items.BOOK)) {
            resultsBuffer.clear();
            return;
        }
        ItemStack result = resultsBuffer.remove(0);
        popBuffer.add(result);
    }

    private void finishInfusion(Level level) {
        int countToConsume = popBuffer.size();
        if (countToConsume > 0) {
            inventory.extractItem(0, countToConsume, false);
        }
        for (ItemStack result : popBuffer) {
            ItemEntity entity = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, result);
            entity.setDeltaMovement(level.random.nextGaussian() * 0.05, 0.2 + level.random.nextDouble() * 0.2, level.random.nextGaussian() * 0.05);
            level.addFreshEntity(entity);
        }
        popBuffer.clear();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
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
        output.store("infusion_core.results_buffer", ItemStack.CODEC.listOf(), resultsBuffer);
        output.store("infusion_core.pop_buffer", ItemStack.CODEC.listOf(), popBuffer);
        output.putInt("infusion_core.progress", progress);
        output.putInt("infusion_core.max_progress", maxProgress);
        output.putInt("infusion_core.mode", mode.ordinal());
        output.putString("infusion_core.tier_id", tierId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.deserialize(input);
        resultsBuffer.clear();
        input.read("infusion_core.results_buffer", ItemStack.CODEC.listOf()).ifPresent(resultsBuffer::addAll);
        popBuffer.clear();
        input.read("infusion_core.pop_buffer", ItemStack.CODEC.listOf()).ifPresent(popBuffer::addAll);
        progress = input.getIntOr("infusion_core.progress", 0);
        maxProgress = input.getIntOr("infusion_core.max_progress", TICKS_PER_ITEM);
        int modeIdx = input.getIntOr("infusion_core.mode", 0);
        mode = Mode.values()[modeIdx % Mode.values().length];
        tierId = input.getStringOr("infusion_core.tier_id", "tier0");
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
