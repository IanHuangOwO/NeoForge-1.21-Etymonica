package org.iansaididontcare.etymonica.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.iansaididontcare.etymonica.block.entity.EnchantingTableBlockEntity;
import org.iansaididontcare.etymonica.enchanting.EnchantingTableMessages;
import org.iansaididontcare.etymonica.tag.ModBlockTags;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class TuningFork extends Item {
    private static final String KEY_DIM = "BoundDim";
    private static final String KEY_POS = "BoundPos";

    public TuningFork(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = getForkTag(stack);
        return tag != null && tag.contains(KEY_POS);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay tooltipDisplay,
            Consumer<Component> tooltipAdder,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltipAdder, flag);

        tooltipAdder.accept(Component.translatable("tooltip.etymonica.tuning_fork.usage_header").withStyle(ChatFormatting.GOLD));
        tooltipAdder.accept(Component.translatable("tooltip.etymonica.tuning_fork.usage.bind").withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.translatable("tooltip.etymonica.tuning_fork.usage.link").withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.translatable("tooltip.etymonica.tuning_fork.usage.clear").withStyle(ChatFormatting.GRAY));

        Bound bound = getBound(stack);
        if (bound == null) {
            tooltipAdder.accept(Component.translatable("tooltip.etymonica.tuning_fork.unbound").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        BlockPos p = bound.pos();
        tooltipAdder.accept(Component.translatable("tooltip.etymonica.tuning_fork.bound", p.getX(), p.getY(), p.getZ())
                .withStyle(ChatFormatting.GRAY));

        // Client-only: read bound table status for relink progress
        try {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;

            Level level = player.level();
            String hereDim = level.dimension().toString();
            if (!bound.dimId().equals(hereDim)) {
                tooltipAdder.accept(Component.translatable("tooltip.etymonica.tuning_fork.different_dimension")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            BlockEntity be = level.getBlockEntity(bound.pos());
            if (!(be instanceof EnchantingTableBlockEntity table)) {
                tooltipAdder.accept(Component.translatable("tooltip.etymonica.tuning_fork.table_not_loaded")
                        .withStyle(ChatFormatting.DARK_GRAY));
                return;
            }

        } catch (NoClassDefFoundError ignored) {
            // Dedicated server safety: client classes are not present there.
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        BlockPos clickedPos = ctx.getClickedPos();

        // Client: let server do the logic, but "consume" the click to feel responsive
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        // Shift + right-click enchanting table => bind AND start/cancel relink scan
        if (player != null && player.isCrouching() && level.getBlockState(clickedPos).is(ModBlockTags.ENCHANTING_TABLES)) {
            Bound bound = getBound(stack);

            String hereDim = level.dimension().toString();
            boolean alreadyBoundToThisTable =
                    bound != null
                            && bound.dimId().equals(hereDim)
                            && bound.pos().equals(clickedPos);

            if (!alreadyBoundToThisTable) {
                bind(stack, level, clickedPos);
                player.displayClientMessage(Component.translatable("message.etymonica.tuning_fork.bound", clickedPos.toShortString()), true);
                return InteractionResult.SUCCESS;
            }

            // Already bound to this table -> toggle relink (start or cancel)
            BlockEntity be = level.getBlockEntity(clickedPos);
            if (be instanceof EnchantingTableBlockEntity table) {
                player.displayClientMessage(EnchantingTableMessages.action(table.beginOrCancelRelinkScan(player)), true);
            } else {
                player.displayClientMessage(Component.translatable("message.etymonica.tuning_fork.table_missing"), true);
            }
            return InteractionResult.SUCCESS;
        }

        // Right-click modifier block => manually add to bound table (if bound)
        if (!level.getBlockState(clickedPos).is(ModBlockTags.ENCHANTING_TABLE_MODIFIERS)) {
            return InteractionResult.PASS;
        }

        Bound bound = getBound(stack);
        if (bound == null) {
            if (player != null) player.displayClientMessage(Component.translatable("message.etymonica.tuning_fork.not_bound"), true);
            return InteractionResult.SUCCESS;
        }

        // dimension check (stored as string)
        String hereDim = level.dimension().toString();
        if (!bound.dimId().equals(hereDim)) {
            if (player != null) player.displayClientMessage(Component.translatable("message.etymonica.tuning_fork.wrong_dimension"), true);
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(bound.pos());
        if (!(be instanceof EnchantingTableBlockEntity table)) {
            if (player != null) player.displayClientMessage(Component.translatable("message.etymonica.tuning_fork.bound_missing"), true);
            clearBind(stack);
            return InteractionResult.SUCCESS;
        }

        if (player != null) {
            player.displayClientMessage(
                    EnchantingTableMessages.action(table.tryLinkModifier((net.minecraft.server.level.ServerLevel) level, clickedPos)),
                    true
            );
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Shift + right-click air => clear bind
        if (!level.isClientSide() && player.isCrouching()) {
            clearBind(stack);
            player.displayClientMessage(Component.translatable("message.etymonica.tuning_fork.unbound"), true);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private static void bind(ItemStack stack, Level level, BlockPos pos) {
        CompoundTag tag = getOrCreateForkTag(stack);
        tag.putString(KEY_DIM, level.dimension().toString());
        tag.putLong(KEY_POS, pos.asLong());
        setForkTag(stack, tag);
    }

    private static void clearBind(ItemStack stack) {
        CompoundTag tag = getForkTag(stack);
        if (tag == null) return;

        tag.remove(KEY_DIM);
        tag.remove(KEY_POS);

        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            setForkTag(stack, tag);
        }
    }

    private static @Nullable Bound getBound(ItemStack stack) {
        CompoundTag tag = getForkTag(stack);
        if (tag == null) return null;

        var dimOpt = tag.getString(KEY_DIM);
        var posOpt = tag.getLong(KEY_POS);

        if (dimOpt.isEmpty() || posOpt.isEmpty()) return null;

        String dimStr = dimOpt.get();
        BlockPos pos = BlockPos.of(posOpt.get());

        return new Bound(dimStr, pos);
    }

    private static @Nullable CompoundTag getForkTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        return data.copyTag();
    }

    private static CompoundTag getOrCreateForkTag(ItemStack stack) {
        CompoundTag existing = getForkTag(stack);
        return existing != null ? existing : new CompoundTag();
    }

    private static void setForkTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private record Bound(String dimId, BlockPos pos) {}
}
