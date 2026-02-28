package org.iansaididontcare.etymonica.block.custom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.iansaididontcare.etymonica.block.entity.EnchantingTableBlockEntity;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;
import org.iansaididontcare.etymonica.enchanting.EnchantingTableMessages;
import org.iansaididontcare.etymonica.item.ModItems;
import org.iansaididontcare.etymonica.tag.ModItemTags;
import org.jetbrains.annotations.Nullable;

public class EnchantingTableBlock extends BaseEntityBlock {
    public static final MapCodec<EnchantingTableBlock> CODEC = simpleCodec(EnchantingTableBlock::new);

    public EnchantingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnchantingTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;

        return createTickerHelper(type, ModBlockEntities.ENCHANTING_TABLE_BE.get(),
                (lvl, p, st, be) -> ((EnchantingTableBlockEntity) be).tickServer(lvl, p, st));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof EnchantingTableBlockEntity table) {

                ItemStack held = player.getItemInHand(hand);
                boolean holdingFork = held.is(ModItems.TUNING_FORK.get());
                boolean holdingQuill = held.is(ModItemTags.QUILLS);

                if (player.isCrouching() && holdingFork) {
                    player.displayClientMessage(EnchantingTableMessages.action(table.beginOrCancelRelinkScan(player)), true);
                    return InteractionResult.SUCCESS;
                }

                if (player.isCrouching() && holdingQuill) {
                    if (table.itemHandler.getStackInSlot(EnchantingTableBlockEntity.SLOT_ITEM).isEmpty()) {
                        player.displayClientMessage(Component.translatable("message.etymonica.enchanting.missing_input"), true);
                        return InteractionResult.SUCCESS;
                    }

                    player.displayClientMessage(EnchantingTableMessages.action(table.requestStartEnchanting()), true);
                    return InteractionResult.SUCCESS;
                }

                ((ServerPlayer) player).openMenu(new SimpleMenuProvider(table, table.getDisplayName()), pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
