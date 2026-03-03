package org.iansaididontcare.etymonica.block.custom.infusionaltar;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.iansaididontcare.etymonica.block.custom.AbstractInfusionAltarBlock;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;
import org.iansaididontcare.etymonica.block.entity.infusionaltar.InfusionAltarTier0BlockEntity;
import org.jetbrains.annotations.Nullable;

public class InfusionAltarTier0Block extends AbstractInfusionAltarBlock {
    public static final MapCodec<InfusionAltarTier0Block> CODEC = simpleCodec(InfusionAltarTier0Block::new);

    public InfusionAltarTier0Block(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends InfusionAltarTier0Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfusionAltarTier0BlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;

        return createTickerHelper(type, ModBlockEntities.INFUSION_ALTAR_TIER0_BE.get(),
                (lvl, p, st, be) -> ((InfusionAltarTier0BlockEntity) be).tickServer(lvl, p, st));
    }
}
