package org.iansaididontcare.etymonica.block.custom.enchantingtable;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.iansaididontcare.etymonica.block.custom.AbstractEnchantingTableBlock;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;
import org.iansaididontcare.etymonica.block.entity.AbstractEnchantingTableBlockEntity;
import org.iansaididontcare.etymonica.block.entity.enchantingtable.EnchantingTableTier1BlockEntity;
import org.jetbrains.annotations.Nullable;

public class EnchantingTableTier1Block extends AbstractEnchantingTableBlock {
    public static final MapCodec<EnchantingTableTier1Block> CODEC = simpleCodec(EnchantingTableTier1Block::new);

    public EnchantingTableTier1Block(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnchantingTableTier1BlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;

        return createTickerHelper(type, ModBlockEntities.ENCHANTING_TABLE_TIER1_BE.get(),
                (lvl, p, st, be) -> ((AbstractEnchantingTableBlockEntity) be).tickServer(lvl, p, st));
    }
}
