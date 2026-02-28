package org.iansaididontcare.etymonica.block.entity.enchanting;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class EnchantBookDrainer {
    private EnchantBookDrainer() {}

    private record BookshelfSlot(BlockPos pos, Container container, int slot) {}

    public static int computeDrainBudgetFromItem(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int sum = 0;
        for (Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> entry : enchantments.entrySet()) {
            sum += Math.max(0, entry.getIntValue());
        }
        return Math.max(0, sum);
    }

    public static int drainFromLinkedBookshelves(Level level, Set<BlockPos> linkedModifiers, TagKey<Block> bookshelfTag, int budget) {
        if (budget <= 0 || linkedModifiers.isEmpty()) return 0;

        List<BookshelfSlot> candidates = collectDrainingCandidates(level, linkedModifiers, bookshelfTag);
        if (candidates.isEmpty()) return 0;

        int drained = 0;
        while (budget > 0 && !candidates.isEmpty()) {
            int index = level.random.nextInt(candidates.size());
            BookshelfSlot selected = candidates.get(index);
            if (!drainOneLevelFromBook(level, selected)) {
                candidates.remove(index);
                continue;
            }

            drained++;
            budget--;

            ItemStack now = selected.container().getItem(selected.slot());
            if (now.isEmpty() || !now.is(Items.ENCHANTED_BOOK)) {
                candidates.remove(index);
            }
        }
        return drained;
    }

    private static List<BookshelfSlot> collectDrainingCandidates(Level level, Set<BlockPos> linkedModifiers, TagKey<Block> bookshelfTag) {
        List<BookshelfSlot> out = new ArrayList<>();
        for (BlockPos pos : linkedModifiers) {
            BlockState st = level.getBlockState(pos);
            if (!st.is(bookshelfTag)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container container)) continue;

            int size = container.getContainerSize();
            for (int i = 0; i < size; i++) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) continue;
                ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
                if (!stored.entrySet().isEmpty()) {
                    out.add(new BookshelfSlot(pos, container, i));
                }
            }
        }
        return out;
    }

    private static boolean drainOneLevelFromBook(Level level, BookshelfSlot target) {
        ItemStack stack = target.container().getItem(target.slot());
        if (stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) return false;

        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        List<Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>>> entries = new ArrayList<>();
        for (Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> entry : stored.entrySet()) {
            if (entry != null && entry.getIntValue() > 0) {
                entries.add(entry);
            }
        }
        if (entries.isEmpty()) return false;

        Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> picked =
                entries.get(level.random.nextInt(entries.size()));
        var pickedEnchantment = picked.getKey();

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> entry : entries) {
            int value = entry.getIntValue();
            if (entry.getKey().equals(pickedEnchantment)) {
                value -= 1;
            }
            if (value > 0) {
                mutable.set(entry.getKey(), value);
            }
        }

        ItemEnchantments updated = mutable.toImmutable();
        if (updated.entrySet().isEmpty()) {
            target.container().setItem(target.slot(), new ItemStack(Items.BOOK, stack.getCount()));
        } else {
            stack.set(DataComponents.STORED_ENCHANTMENTS, updated);
            target.container().setItem(target.slot(), stack);
        }

        notifyBookshelfChanged(level, target.pos());
        return true;
    }

    private static void notifyBookshelfChanged(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            be.setChanged();
            if (!level.isClientSide()) {
                BlockState st = level.getBlockState(pos);
                level.sendBlockUpdated(pos, st, st, 3);
            }
        }
    }
}
