package org.iansaididontcare.etymonica.block.custom.jar;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.iansaididontcare.etymonica.block.custom.AbstractJarBlock;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;
import org.iansaididontcare.etymonica.block.entity.jar.JarBlockEntity;
import org.iansaididontcare.etymonica.item.custom.jar.JarItem;
import org.iansaididontcare.etymonica.registry.jar.data.JarData;
import org.jetbrains.annotations.Nullable;

public class JarBlock extends AbstractJarBlock {
    public static final MapCodec<JarBlock> CODEC = simpleCodec(JarBlock::new);

    public JarBlock(Properties properties) {
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
        return new JarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;

        return createTickerHelper(type, ModBlockEntities.JAR_BE.get(),
                (lvl, p, st, be) -> ((JarBlockEntity) be).tickServer(lvl, p, st));
    }

    @Override
    protected boolean isJarEntity(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof JarBlockEntity;
    }

    @Override
    protected int getJarStoredMillibuckets(BlockEntity blockEntity) {
        return ((JarBlockEntity) blockEntity).getStoredMillibuckets();
    }

    @Override
    protected void setJarStoredMillibuckets(BlockEntity blockEntity, int amount) {
        ((JarBlockEntity) blockEntity).setStoredMillibuckets(amount);
    }

    @Override
    protected boolean tryJarExtract(BlockEntity blockEntity, Level level, BlockPos pos, BlockState state, int amount) {
        return ((JarBlockEntity) blockEntity).tryExtract(level, pos, state, amount);
    }

    @Override
    protected boolean tryJarInsert(BlockEntity blockEntity, Level level, BlockPos pos, BlockState state, int amount) {
        return ((JarBlockEntity) blockEntity).tryInsert(level, pos, state, amount);
    }

    @Override
    protected int getJarCapacityMillibuckets() {
        Identifier id = BuiltInRegistries.BLOCK.getKey(this);
        return JarData.getJarType(id).capacity();
    }

    @Override
    protected int getItemStoredMillibuckets(ItemStack stack) {
        return JarItem.getStoredMillibuckets(stack);
    }

    @Override
    protected void setItemStoredMillibuckets(ItemStack stack, int amount) {
        JarItem.setStoredMillibuckets(stack, amount);
    }
}
