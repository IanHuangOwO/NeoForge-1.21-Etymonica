package org.iansaididontcare.etymonica.block.entity.infusionaltar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;
import org.jetbrains.annotations.Nullable;

public class InfusionCoreBlockEntity extends BlockEntity {
    private int maxStackSize = 1;

    private ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if(level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return maxStackSize;
        }
    };

    private String tierId = "tier0";

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
        setChanged();
        if(level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    public String getTierId() {
        return tierId;
    }

    public void resizeInventory(int size) {
        this.maxStackSize = size;
        
        // If current items exceed new limit, drop the excess
        ItemStack stack = inventory.getStackInSlot(0);
        if (stack.getCount() > size && level != null && !level.isClientSide()) {
            ItemStack drop = stack.split(stack.getCount() - size);
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), drop);
        }
        
        setChanged();
        if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    private float renderingRotation;

    public float getRenderingRotation() {
        renderingRotation += 0.5f;
        if(renderingRotation >= 360) renderingRotation = 0;
        return renderingRotation;
    }

    public InteractionResult handleInteraction(Player player, InteractionHand hand, ItemStack stack) {
        if (level == null) return InteractionResult.PASS;

        ItemStack inSlot = inventory.getStackInSlot(0);

        // 1. If holding an item, try to insert it
        if (!stack.isEmpty()) {
            if (!level.isClientSide()) {
                // Only try to insert 1 item at a time from the player's stack
                ItemStack toInsert = stack.copyWithCount(1);
                ItemStack remainder = inventory.insertItem(0, toInsert, false);

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
                    // Extract everything in the slot
                    ItemStack extracted = inventory.extractItem(0, inSlot.getCount(), false);
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
        output.putString("infusion_core.tier_id", tierId);
        output.putInt("infusion_core.max_stack", maxStackSize);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        maxStackSize = input.getIntOr("infusion_core.max_stack", 1);
        inventory.deserialize(input);
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
