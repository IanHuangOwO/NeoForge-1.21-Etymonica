package org.iansaididontcare.etymonica.block.entity.infusionaltar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.iansaididontcare.etymonica.block.entity.AbstractInfusionAltarBlockEntity;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;

public class PedestalBlockEntity extends AbstractInfusionAltarBlockEntity {

    public PedestalBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.PEDESTAL_BE.get(), pos, blockState);

        // Simple way: Overwrite the inherited inventory with a hardcoded 1-item limit
        this.inventory = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if(level != null && !level.isClientSide()) {
                    level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                }
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
    }
}
