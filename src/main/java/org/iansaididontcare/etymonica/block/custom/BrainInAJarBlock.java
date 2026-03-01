package org.iansaididontcare.etymonica.block.custom;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.iansaididontcare.etymonica.block.entity.BrainInAJarBlockEntity;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;
import org.iansaididontcare.etymonica.item.ModItems;
import org.iansaididontcare.etymonica.item.custom.BrainInAJarItem;
import org.jetbrains.annotations.Nullable;

public class BrainInAJarBlock extends BaseEntityBlock {
    public static final MapCodec<BrainInAJarBlock> CODEC = simpleCodec(BrainInAJarBlock::new);
    private static final int BUCKET_MB = 1_000;
    private static final int BOTTLE_MB = 250;

    public BrainInAJarBlock(Properties properties) {
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
        return new BrainInAJarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;

        return createTickerHelper(type, ModBlockEntities.BRAIN_IN_A_JAR_BE.get(),
                (lvl, p, st, be) -> ((BrainInAJarBlockEntity) be).tickServer(lvl, p, st));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BrainInAJarBlockEntity jar) {
            jar.setStoredMillibuckets(BrainInAJarItem.getStoredMillibuckets(stack));
            jar.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);

        if (level.isClientSide() || player.isCreative()) {
            return;
        }

        ItemStack drop = new ItemStack(this.asItem());
        if (blockEntity instanceof BrainInAJarBlockEntity jar) {
            BrainInAJarItem.setStoredMillibuckets(drop, jar.getStoredMillibuckets());
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

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BrainInAJarBlockEntity jar)) {
            return InteractionResult.PASS;
        }

        if (tryHandleContainerInteraction(stack, level, pos, state, player, hand, jar)) {
            return InteractionResult.SUCCESS;
        }

        int stored = jar.getStoredMillibuckets();
        int capacity = BrainInAJarBlockEntity.CAPACITY_MILLIBUCKETS;
        Component msg = Component.translatable("message.etymonica.brain_in_a_jar.storage", stored, capacity);
        player.displayClientMessage(msg, true);

        return InteractionResult.SUCCESS;
    }

    private static boolean tryHandleContainerInteraction(ItemStack stack, Level level, BlockPos pos, BlockState state,
                                                         Player player, InteractionHand hand, BrainInAJarBlockEntity jar) {
        if (stack.is(Items.BUCKET) && jar.tryExtract(level, pos, state, BUCKET_MB)) {
            replaceOneHeldItem(player, hand, stack, new ItemStack(ModItems.LIQUID_EXPERIENCE_BUCKET.get()));
            return true;
        }

        if (stack.is(ModItems.LIQUID_EXPERIENCE_BUCKET.get()) && jar.tryInsert(level, pos, state, BUCKET_MB)) {
            replaceOneHeldItem(player, hand, stack, new ItemStack(Items.BUCKET));
            return true;
        }

        if (stack.is(Items.GLASS_BOTTLE) && jar.tryExtract(level, pos, state, BOTTLE_MB)) {
            replaceOneHeldItem(player, hand, stack, new ItemStack(Items.EXPERIENCE_BOTTLE));
            return true;
        }

        if (stack.is(Items.EXPERIENCE_BOTTLE) && jar.tryInsert(level, pos, state, BOTTLE_MB)) {
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
}
