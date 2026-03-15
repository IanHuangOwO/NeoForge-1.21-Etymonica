package org.iansaididontcare.etymonica.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.iansaididontcare.etymonica.item.ModItems;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractJarBlock extends BaseEntityBlock {
    private static final int BUCKET_MB = 1_000;
    private static final int BOTTLE_MB = 250;

    protected AbstractJarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!isJarEntity(blockEntity)) {
            return;
        }

        setJarStoredMillibuckets(blockEntity, getItemStoredMillibuckets(stack));
        blockEntity.setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);

        if (level.isClientSide() || player.isCreative()) {
            return;
        }

        ItemStack drop = new ItemStack(this.asItem());
        if (isJarEntity(blockEntity)) {
            setItemStoredMillibuckets(drop, getJarStoredMillibuckets(blockEntity));
        }

        ItemEntity itemEntity = new ItemEntity(level,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                drop);
        level.addFreshEntity(itemEntity);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!isJarEntity(blockEntity)) {
            return InteractionResult.PASS;
        }

        if (tryHandleContainerInteraction(stack, level, pos, state, player, hand, blockEntity)) {
            return InteractionResult.SUCCESS;
        }

        int stored = getJarStoredMillibuckets(blockEntity);
        int capacity = getJarCapacityMillibuckets();
        player.displayClientMessage(Component.translatable(getStorageMessageKey(), stored, capacity), true);
        return InteractionResult.SUCCESS;
    }

    private boolean tryHandleContainerInteraction(ItemStack stack, Level level, BlockPos pos, BlockState state,
                                                  Player player, InteractionHand hand, BlockEntity blockEntity) {
        if (stack.is(Items.BUCKET) && tryJarExtract(blockEntity, level, pos, state, BUCKET_MB)) {
            replaceOneHeldItem(player, hand, stack, new ItemStack(ModItems.LIQUID_EXPERIENCE_BUCKET.get()));
            return true;
        }

        if (stack.is(ModItems.LIQUID_EXPERIENCE_BUCKET.get()) && tryJarInsert(blockEntity, level, pos, state, BUCKET_MB)) {
            replaceOneHeldItem(player, hand, stack, new ItemStack(Items.BUCKET));
            return true;
        }

        if (stack.is(Items.GLASS_BOTTLE) && tryJarExtract(blockEntity, level, pos, state, BOTTLE_MB)) {
            replaceOneHeldItem(player, hand, stack, new ItemStack(Items.EXPERIENCE_BOTTLE));
            return true;
        }

        if (stack.is(Items.EXPERIENCE_BOTTLE) && tryJarInsert(blockEntity, level, pos, state, BOTTLE_MB)) {
            replaceOneHeldItem(player, hand, stack, new ItemStack(Items.GLASS_BOTTLE));
            return true;
        }

        return false;
    }

    private static void replaceOneHeldItem(Player player, InteractionHand hand, ItemStack held, ItemStack replacement) {
        if (player.getAbilities().instabuild) {
            if (!player.getInventory().contains(replacement)) {
                player.getInventory().add(replacement);
            }
            return;
        }

        held.shrink(1);
        if (held.isEmpty()) {
            player.setItemInHand(hand, replacement);
        } else if (!player.getInventory().add(replacement)) {
            player.drop(replacement, false);
        }
    }

    protected String getStorageMessageKey() {
        return "message.etymonica.jar.storage";
    }

    protected abstract boolean isJarEntity(@Nullable BlockEntity blockEntity);

    protected abstract int getJarStoredMillibuckets(BlockEntity blockEntity);

    protected abstract void setJarStoredMillibuckets(BlockEntity blockEntity, int amount);

    protected abstract boolean tryJarExtract(BlockEntity blockEntity, Level level, BlockPos pos, BlockState state, int amount);

    protected abstract boolean tryJarInsert(BlockEntity blockEntity, Level level, BlockPos pos, BlockState state, int amount);

    protected abstract int getJarCapacityMillibuckets();

    protected abstract int getItemStoredMillibuckets(ItemStack stack);

    protected abstract void setItemStoredMillibuckets(ItemStack stack, int amount);
}
