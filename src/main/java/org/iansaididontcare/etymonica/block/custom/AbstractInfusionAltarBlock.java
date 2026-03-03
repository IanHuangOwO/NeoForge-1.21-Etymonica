package org.iansaididontcare.etymonica.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.iansaididontcare.etymonica.block.entity.AbstractInfusionAltarBlockEntity;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractInfusionAltarBlock extends BaseEntityBlock {

    protected AbstractInfusionAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected abstract MapCodec<? extends BaseEntityBlock> codec();

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public abstract BlockEntity newBlockEntity(BlockPos pos, BlockState state);

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        if(level.getBlockEntity(pos) instanceof AbstractInfusionAltarBlockEntity altar) {
            ItemStack inSlot = altar.inventory.getStackInSlot(0);

            if (!stack.isEmpty()) {
                // Try to insert (Always 1)
                if (!level.isClientSide()) {
                    ItemStack toInsert = stack.copyWithCount(1);
                    ItemStack remainder = altar.inventory.insertItem(0, toInsert, false);

                    if (remainder.isEmpty()) {
                        stack.shrink(1);
                        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);
                    }
                }
                return InteractionResult.SUCCESS;
            } else if (hand == InteractionHand.MAIN_HAND && !inSlot.isEmpty()) {
                // Try to extract (empty hand)
                if (!level.isClientSide()) {
                    int amountToExtract = player.isCrouching() ? inSlot.getCount() : 1;
                    ItemStack extracted = altar.inventory.extractItem(0, amountToExtract, false);
                    if (!extracted.isEmpty()) {
                        player.setItemInHand(hand, extracted);
                        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1f);
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.SUCCESS;
    }
}
