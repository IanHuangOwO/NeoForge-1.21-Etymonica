package org.iansaididontcare.etymonica.block.entity.enchantingtable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;
import org.iansaididontcare.etymonica.block.entity.AbstractEnchantingTableBlockEntity;

public class EnchantingTableTier1BlockEntity extends AbstractEnchantingTableBlockEntity {
    public EnchantingTableTier1BlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENCHANTING_TABLE_TIER1_BE.get(), pos, state);
    }
}
