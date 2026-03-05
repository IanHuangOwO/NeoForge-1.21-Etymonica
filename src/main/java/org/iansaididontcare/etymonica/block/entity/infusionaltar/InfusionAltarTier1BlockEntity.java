package org.iansaididontcare.etymonica.block.entity.infusionaltar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.iansaididontcare.etymonica.block.entity.AbstractInfusionAltarBlockEntity;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;

public class InfusionAltarTier1BlockEntity extends AbstractInfusionAltarBlockEntity {

    public InfusionAltarTier1BlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFUSION_ALTAR_TIER1_BE.get(), pos, state);
    }
}
