package org.iansaididontcare.etymonica.block.custom.jar;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.iansaididontcare.etymonica.block.custom.AbstractJarBlock;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;
import org.iansaididontcare.etymonica.block.entity.jar.ZombieBrainInAJarBlockEntity;
import org.iansaididontcare.etymonica.item.custom.BrainInAJarItem;
import org.jetbrains.annotations.Nullable;

public class ZombieBrainInAJarBlock extends AbstractJarBlock {
    public static final MapCodec<ZombieBrainInAJarBlock> CODEC = simpleCodec(ZombieBrainInAJarBlock::new);

    public ZombieBrainInAJarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends AbstractJarBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZombieBrainInAJarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;

        return createTickerHelper(type, ModBlockEntities.BRAIN_IN_A_JAR_BE.get(),
                (lvl, p, st, be) -> ((ZombieBrainInAJarBlockEntity) be).tickServer(lvl, p, st));
    }

    @Override
    protected boolean isJarEntity(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof ZombieBrainInAJarBlockEntity;
    }

    @Override
    protected int getJarStoredMillibuckets(BlockEntity blockEntity) {
        return ((ZombieBrainInAJarBlockEntity) blockEntity).getStoredMillibuckets();
    }

    @Override
    protected void setJarStoredMillibuckets(BlockEntity blockEntity, int amount) {
        ((ZombieBrainInAJarBlockEntity) blockEntity).setStoredMillibuckets(amount);
    }

    @Override
    protected boolean tryJarExtract(BlockEntity blockEntity, Level level, BlockPos pos, BlockState state, int amount) {
        return ((ZombieBrainInAJarBlockEntity) blockEntity).tryExtract(level, pos, state, amount);
    }

    @Override
    protected boolean tryJarInsert(BlockEntity blockEntity, Level level, BlockPos pos, BlockState state, int amount) {
        return ((ZombieBrainInAJarBlockEntity) blockEntity).tryInsert(level, pos, state, amount);
    }

    @Override
    protected int getJarCapacityMillibuckets() {
        return ZombieBrainInAJarBlockEntity.CAPACITY_MILLIBUCKETS;
    }

    @Override
    protected int getItemStoredMillibuckets(ItemStack stack) {
        return BrainInAJarItem.getStoredMillibuckets(stack);
    }

    @Override
    protected void setItemStoredMillibuckets(ItemStack stack, int amount) {
        BrainInAJarItem.setStoredMillibuckets(stack, amount);
    }
}
