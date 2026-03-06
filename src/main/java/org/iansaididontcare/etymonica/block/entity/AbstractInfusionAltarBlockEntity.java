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
import org.iansaididontcare.etymonica.block.entity.infusionaltar.InfusionCoreBlockEntity;
import org.iansaididontcare.etymonica.block.entity.infusionaltar.infuser.InfusionRelinkScanner;
import org.iansaididontcare.etymonica.registry.infusion.api.AltarActionResult;
import org.iansaididontcare.etymonica.registry.infusion.api.InfusionAltarModifierStats;
import org.iansaididontcare.etymonica.registry.infusion.api.InfusionAltarStats;
import org.iansaididontcare.etymonica.registry.infusion.data.InfusionAltarData;
import org.iansaididontcare.etymonica.tag.ModBlockTags;
import org.jetbrains.annotations.Nullable;

import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.block.ModBlocks;
import org.iansaididontcare.etymonica.block.custom.AbstractInfusionAltarBlock;
import org.iansaididontcare.etymonica.registry.infusion.api.MultiblockStructure;
import java.util.List;
import java.util.Optional;

public abstract class AbstractInfusionAltarBlockEntity extends BlockEntity {
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

    public @Nullable InfusionCoreBlockEntity getCore() {
        if (!isFormed || level == null) return null;
        String tierId = getTierIdFromState(level, getBlockState());
        InfusionAltarStats stats = InfusionAltarData.getAltarTier(tierId);
        BlockPos corePos = worldPosition.above(stats.multiblockStructure().offsetY() + 1);
        BlockEntity be = level.getBlockEntity(corePos);
        return be instanceof InfusionCoreBlockEntity core ? core : null;
    }

    public ItemStackHandler getInventory() {
        InfusionCoreBlockEntity core = getCore();
        return core != null ? core.getInventory() : this.inventory;
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

    // --- Main server tick: periodic stat recompute and integrity check ---
    public void tickServer(Level level, BlockPos pos, BlockState state) {
        if (recomputeCooldownTicks-- <= 0) {
            recomputeCooldownTicks = 20;
            recomputeStatsNow(level, state);
        }

        // Periodic structure integrity check
        if (isFormed && structureCheckCooldown-- <= 0) {
            structureCheckCooldown = 40; // Check every 2 seconds
            if (!verifyStructure(level, pos)) {
                setFormed(false);
            }
        }
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

        // Check Bottom Layer (Relative Y = 0)
        if (!checkLayer(level, cubeBottomCenter, struct.bottom())) return false;
        // Check Middle Layer (Relative Y = 1)
        if (!checkLayer(level, cubeBottomCenter.above(), struct.middle())) return false;
        // Check Top Layer (Relative Y = 2)
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
                BlockState state = level.getBlockState(targetPos);
                
                Identifier expectedId = Identifier.parse(expectedBlockId);
                Identifier actualId = level.registryAccess().lookupOrThrow(Registries.BLOCK).getKey(state.getBlock());

                if (actualId == null) return false;

                if (!actualId.equals(expectedId)) {
                    Etymonica.LOGGER.info("Multiblock mismatch at {}: expected {}, found {}", targetPos, expectedId, actualId);
                    return false;
                }
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
                    // Place the core block in the center of the structure
                    level.setBlock(corePos, ModBlocks.INFUSION_CORE.get().defaultBlockState(), 3);
                    if (level.getBlockEntity(corePos) instanceof InfusionCoreBlockEntity core) {
                        core.setTierId(tierId);
                    }
                } else {
                    // Remove the core block if it exists
                    if (level.getBlockState(corePos).is(ModBlocks.INFUSION_CORE.get())) {
                        level.destroyBlock(corePos, true); // Drops items
                    }
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

    // --- Infusion lifecycle: redirected to the Core ---
    public AltarActionResult attemptStartInfusion(Player player) {
        InfusionCoreBlockEntity core = getCore();
        if (core == null) return AltarActionResult.INFUSE_BLOCKED;

        String tierId = getTierIdFromState(level, getBlockState());
        return core.attemptStartInfusion(player, tierId);
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
        statsDirty = false;

        // If formed, ensure core knows the tier
        InfusionCoreBlockEntity core = getCore();
        if (core != null) core.setTierId(tierId);
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

    // --- Block-entity lifecycle hooks ---
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (isFormed) setFormed(false);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("infusion_altar.is_formed", isFormed);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        isFormed = input.getBooleanOr("infusion_altar.is_formed", false);
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
