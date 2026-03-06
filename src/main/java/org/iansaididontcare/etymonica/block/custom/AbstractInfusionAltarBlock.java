package org.iansaididontcare.etymonica.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.iansaididontcare.etymonica.block.entity.AbstractInfusionAltarBlockEntity;
import org.iansaididontcare.etymonica.item.ModItems;
import org.iansaididontcare.etymonica.tag.ModItemTags;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractInfusionAltarBlock extends BaseEntityBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    protected AbstractInfusionAltarBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
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
            ItemStack inSlot = altar.getInventory().getStackInSlot(0);

            // 1. If holding an item, try to insert it
            if (!stack.isEmpty()) {
                if (!level.isClientSide()) {
                    ItemStack toInsert = stack.copyWithCount(1);
                    ItemStack remainder = altar.getInventory().insertItem(0, toInsert, false);

                    if (remainder.isEmpty()) {
                        stack.shrink(1);
                        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 2f);
                    }
                }
                return InteractionResult.SUCCESS;
            } 
            
            // 2. If hand is empty, try to extract the item
            else if (hand == InteractionHand.MAIN_HAND) {
                if (!inSlot.isEmpty()) {
                    if (!level.isClientSide()) {
                        ItemStack extracted = altar.getInventory().extractItem(0, inSlot.getCount(), false);
                        if (!extracted.isEmpty()) {
                            if (!player.getInventory().add(extracted)) {
                                player.drop(extracted, false);
                            }
                            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1f, 1f);
                        }
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return InteractionResult.SUCCESS;
    }
}
