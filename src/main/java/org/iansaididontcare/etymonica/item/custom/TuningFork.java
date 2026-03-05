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
import net.minecraft.world.level.block.state.BlockState;
import org.iansaididontcare.etymonica.block.entity.AbstractEnchantingTableBlockEntity;
import org.iansaididontcare.etymonica.block.entity.AbstractInfusionAltarBlockEntity;
import org.iansaididontcare.etymonica.registry.enchanting.EnchantingTableMessages;
import org.iansaididontcare.etymonica.registry.infusion.InfusionAltarMessages;
import org.iansaididontcare.etymonica.tag.ModBlockTags;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

public class TuningFork extends Item {
    private static final String KEY_DIM = "BoundDim";
    private static final String KEY_POS = "BoundPos";
    private static final String KEY_NAME = "BoundName";

    public TuningFork(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getBound(stack) != null;
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
        String nameStr = bound.name() != null ? " (" + bound.name() + ")" : "";
        tooltipAdder.accept(Component.translatable("tooltip.etymonica.tuning_fork.bound", p.getX(), p.getY(), p.getZ())
                .append(nameStr)
                .withStyle(ChatFormatting.GRAY));

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
            if (be == null) {
                tooltipAdder.accept(Component.translatable("tooltip.etymonica.tuning_fork.table_not_loaded")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        } catch (NoClassDefFoundError ignored) {}
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        BlockPos clickedPos = ctx.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);

        if (level.isClientSide()) return InteractionResult.SUCCESS;

        boolean isMachine = clickedState.is(ModBlockTags.ENCHANTING_TABLES) || clickedState.is(ModBlockTags.INFUSION_ALTARS);

        if (player != null && player.isCrouching() && isMachine) {
            Bound bound = getBound(stack);
            String hereDim = level.dimension().toString();
            boolean alreadyBoundToThisMachine = bound != null && bound.dimId().equals(hereDim) && bound.pos().equals(clickedPos);

            if (!alreadyBoundToThisMachine) {
                bind(stack, level, clickedPos);
                String blockName = clickedState.getBlock().getName().getString();
                player.displayClientMessage(Component.translatable("message.etymonica.tuning_fork.bound_with_name", clickedPos.toShortString(), blockName), true);
                return InteractionResult.SUCCESS;
            }

            BlockEntity be = level.getBlockEntity(clickedPos);
            if (be instanceof AbstractEnchantingTableBlockEntity table) {
                player.displayClientMessage(EnchantingTableMessages.action(table.beginOrCancelRelinkScan(player)), true);
            }
            return InteractionResult.SUCCESS;
        }

        boolean isSupport = clickedState.is(ModBlockTags.ENCHANTING_TABLE_MODIFIERS) 
                || clickedState.is(ModBlockTags.ENCHANTING_TABLE_BOOKSHELVES);

        if (!isSupport) return InteractionResult.PASS;

        Bound bound = getBound(stack);
        if (bound == null) {
            if (player != null) player.displayClientMessage(Component.translatable("message.etymonica.tuning_fork.not_bound"), true);
            return InteractionResult.SUCCESS;
        }

        if (!bound.dimId().equals(level.dimension().toString())) {
            if (player != null) player.displayClientMessage(Component.translatable("message.etymonica.tuning_fork.wrong_dimension"), true);
            return InteractionResult.SUCCESS;
        }

        BlockEntity targetBe = level.getBlockEntity(bound.pos());
        if (targetBe instanceof AbstractEnchantingTableBlockEntity table) {
            if (player != null) {
                player.displayClientMessage(EnchantingTableMessages.action(table.tryLinkBlock((net.minecraft.server.level.ServerLevel) level, clickedPos)), true);
            }
        } else {
            if (player != null) player.displayClientMessage(Component.translatable("message.etymonica.tuning_fork.bound_missing"), true);
            clearBind(stack);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
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
        tag.putString(KEY_NAME, level.getBlockState(pos).getBlock().getName().getString());
        setForkTag(stack, tag);
    }

    private static void clearBind(ItemStack stack) {
        CompoundTag tag = getForkTag(stack);
        if (tag == null) return;
        tag.remove(KEY_DIM);
        tag.remove(KEY_POS);
        tag.remove(KEY_NAME);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            setForkTag(stack, tag);
        }
    }

    private static @Nullable Bound getBound(ItemStack stack) {
        CompoundTag tag = getForkTag(stack);
        if (tag == null) return null;
        
        Optional<String> dimOpt = (Optional<String>) (Object) tag.getString(KEY_DIM);
        Optional<Long> posOpt = (Optional<Long>) (Object) tag.getLong(KEY_POS);
        Optional<String> nameOpt = (Optional<String>) (Object) tag.getString(KEY_NAME);
        
        if (dimOpt.isPresent() && posOpt.isPresent()) {
            return new Bound(dimOpt.get(), BlockPos.of(posOpt.get()), nameOpt.orElse(null));
        }
        return null;
    }

    private static @Nullable CompoundTag getForkTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : null;
    }

    private static CompoundTag getOrCreateForkTag(ItemStack stack) {
        CompoundTag tag = getForkTag(stack);
        return tag != null ? tag : new CompoundTag();
    }

    private static void setForkTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private record Bound(String dimId, BlockPos pos, @Nullable String name) {}
}
