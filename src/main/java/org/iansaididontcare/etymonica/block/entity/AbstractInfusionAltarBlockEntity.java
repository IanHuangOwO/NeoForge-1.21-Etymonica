package org.iansaididontcare.etymonica.block.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.iansaididontcare.etymonica.block.ModBlocks;
import org.iansaididontcare.etymonica.block.custom.AbstractInfusionAltarBlock;
import org.iansaididontcare.etymonica.block.entity.infusionaltar.AltarPartBlockEntity;
import org.iansaididontcare.etymonica.registry.infusion.api.InfusionAltarStats;
import org.iansaididontcare.etymonica.registry.infusion.data.InfusionAltarData;
import org.iansaididontcare.etymonica.screen.custom.InfusionAltarMenu;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractInfusionAltarBlockEntity extends BlockEntity implements MenuProvider {

    // --- Multiblock State ---
    private boolean isFormed = false;
    private int structureCheckCooldown = 0;
    private final List<BlockPos> partPositions = new ArrayList<>();

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

    public void tickServer(Level level, BlockPos pos, BlockState state) {
        if (recomputeCooldownTicks-- <= 0) {
            recomputeCooldownTicks = 20;
            recomputeStatsNow(level, state);
        }

        if (structureCheckCooldown-- <= 0) {
            structureCheckCooldown = 20;
            if (!isFormed) {
                checkStructureAndForm(level, pos, state);
            }
        }
    }

    private void checkStructureAndForm(Level level, BlockPos pos, BlockState state) {
        String tierId = getTierIdFromState(level, state);
        InfusionAltarStats stats = InfusionAltarData.getAltarTier(tierId);
        int r = stats.multiblockRadius();
        Identifier requiredBlockId = stats.multiblockBlock();
        
        List<BlockPos> ringPositions = new ArrayList<>();

        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                if (Math.round(Math.sqrt(i * i + j * j)) == r) {
                    ringPositions.add(pos.offset(i, 0, j)); // XZ
                    ringPositions.add(pos.offset(i, j, 0)); // XY
                    ringPositions.add(pos.offset(0, i, j)); // YZ
                }
            }
        }

        for (BlockPos p : ringPositions) {
            if (p.equals(pos)) continue;
            if (!isBlockCorrect(level, p, requiredBlockId)) return;
        }

        // All blocks correct, form the multiblock
        form(level, pos, state, ringPositions, requiredBlockId);
    }

    private void form(Level level, BlockPos pos, BlockState state, List<BlockPos> ringPositions, Identifier originalBlockId) {
        this.isFormed = true;
        this.partPositions.clear();

        for (BlockPos p : ringPositions) {
            if (p.equals(pos)) continue;
            
            level.setBlock(p, ModBlocks.ALTAR_PART_BLOCK.get().defaultBlockState(), 3);
            if (level.getBlockEntity(p) instanceof AltarPartBlockEntity part) {
                part.setMetadata(pos, originalBlockId);
                this.partPositions.add(p.immutable());
            }
        }

        // Feedback
        notifyStateChange(level, pos, state, true);
    }

    public void unform() {
        unform(false);
    }

    public void unform(boolean isRemovingMaster) {
        if (!isFormed || level == null || level.isClientSide()) return;

        this.isFormed = false;
        String tierId = getTierIdFromState(level, getBlockState());
        InfusionAltarStats stats = InfusionAltarData.getAltarTier(tierId);
        Block originalBlock = BuiltInRegistries.BLOCK.get(stats.multiblockBlock())
                .orElseThrow(() -> new IllegalStateException("Block not found: " + stats.multiblockBlock()))
                .value();

        for (BlockPos p : partPositions) {
            if (p.equals(worldPosition)) continue;

            if (level.getBlockState(p).is(ModBlocks.ALTAR_PART_BLOCK.get())) {
                if (level.getBlockEntity(p) instanceof AltarPartBlockEntity part) {
                    part.setReverting(true);
                }
                level.setBlock(p, originalBlock.defaultBlockState(), 3);
            }
        }
        
        this.partPositions.clear();

        // Only update the altar's blockstate if we aren't currently breaking the altar
        if (!isRemovingMaster) {
            notifyStateChange(level, worldPosition, getBlockState(), false);
        } else {
            // If removing, just play the sound and send message without level.setBlock
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1f, 1f);
        }
    }

    private void notifyStateChange(Level level, BlockPos pos, BlockState state, boolean formed) {
        if (state.hasProperty(AbstractInfusionAltarBlock.FORMED)) {
            level.setBlock(pos, state.setValue(AbstractInfusionAltarBlock.FORMED, formed), 3);
        }

        String msgKey = formed ? "message.etymonica.multiblock.formed" : "message.etymonica.multiblock.failed";
        Component msg = Component.translatable(msgKey).withStyle(formed ? ChatFormatting.GREEN : ChatFormatting.RED);
        
        AABB searchBox = new AABB(pos).inflate(8);
        level.getEntitiesOfClass(Player.class, searchBox, player -> player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64)
             .forEach(player -> player.displayClientMessage(msg, true));

        level.playSound(null, pos, formed ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1f, 1f);

        setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private boolean isBlockCorrect(Level level, BlockPos target, Identifier expectedId) {
        BlockState state = level.getBlockState(target);
        Identifier actualId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return actualId.equals(expectedId);
    }

    private void recomputeStatsNow(Level level, BlockState state) {
        String tierId = getTierIdFromState(level, state);
        long revision = InfusionAltarData.getRevision();
        boolean tierChanged = !tierId.equals(lastTierId);
        boolean dataChanged = revision != lastDataRevision;

        if (!(statsDirty || tierChanged || dataChanged)) return;

        lastTierId = tierId;
        lastDataRevision = revision;
        statsDirty = false;
    }

    private String getTierIdFromState(Level level, BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = id.getPath(); // e.g., "infusion_altar_tier1"
        if (path.startsWith("infusion_altar_")) {
            return path.substring("infusion_altar_".length()); // returns "tier1"
        }
        return "tier0";
    }


    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (isFormed) unform(true);
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
        output.store("infusion_altar.parts", BlockPos.CODEC.listOf(), partPositions);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        isFormed = input.getBooleanOr("infusion_altar.is_formed", false);
        inventory.deserialize(input);
        partPositions.clear();
        input.read("infusion_altar.parts", BlockPos.CODEC.listOf()).ifPresent(partPositions::addAll);
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

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new InfusionAltarMenu(containerId, playerInventory, this);
    }
}
