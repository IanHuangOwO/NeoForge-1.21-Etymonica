package org.iansaididontcare.etymonica.block.entity.infusionaltar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.iansaididontcare.etymonica.block.entity.AbstractInfusionAltarBlockEntity;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;

public class PedestalBlockEntity extends AbstractInfusionAltarBlockEntity {

    public PedestalBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.PEDESTAL_BE.get(), pos, blockState);
    }
}
