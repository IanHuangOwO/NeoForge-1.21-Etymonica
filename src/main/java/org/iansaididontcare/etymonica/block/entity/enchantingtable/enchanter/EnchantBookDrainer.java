package org.iansaididontcare.etymonica.block.entity.enchantingtable.enchanter;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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
import org.iansaididontcare.etymonica.registry.enchanting.data.EnchantingTableData;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class EnchantBookDrainer {
    private EnchantBookDrainer() {}

    private record BookshelfSlot(BlockPos pos, Container container, int slot) {}

    public static int computeDrainBudgetFromItem(Level level, ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        double sum = 0.0d;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            int levelValue = Math.max(0, entry.getIntValue());
            if (levelValue <= 0) continue;
            Identifier enchantmentId = getEnchantmentId(level, entry.getKey());
            double weight = EnchantingTableData.getDrainWeight(enchantmentId);
            sum += levelValue * Math.max(0.0d, weight);
        }
        return Math.max(0, (int) Math.round(sum));
    }

    public static int drainFromLinkedBookshelves(Level level, Set<BlockPos> linkedModifiers, TagKey<Block> bookshelfTag, int budget) {
        if (budget <= 0 || linkedModifiers.isEmpty()) return 0;

        List<BookshelfSlot> candidates = collectDrainingCandidates(level, linkedModifiers, bookshelfTag);
        if (candidates.isEmpty()) return 0;

        double remainingBudget = budget;
        int drained = 0;
        while (remainingBudget > 0.0d && !candidates.isEmpty()) {
            int index = level.random.nextInt(candidates.size());
            BookshelfSlot selected = candidates.get(index);
            double consumed = drainOneLevelFromBook(level, selected);
            if (consumed <= 0.0d) {
                candidates.remove(index);
                continue;
            }

            drained++;
            remainingBudget -= consumed;

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

    private static double drainOneLevelFromBook(Level level, BookshelfSlot target) {
        ItemStack stack = target.container().getItem(target.slot());
        if (stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) return 0.0d;

        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        List<Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>>> entries = new ArrayList<>();
        for (Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> entry : stored.entrySet()) {
            if (entry != null && entry.getIntValue() > 0) {
                entries.add(entry);
            }
        }
        if (entries.isEmpty()) return 0.0d;

        Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> picked =
                entries.get(level.random.nextInt(entries.size()));
        var pickedEnchantment = picked.getKey();
        double consumed = Math.max(0.0d, EnchantingTableData.getAccumulationWeight(getEnchantmentId(level, pickedEnchantment)));

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
        return consumed;
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

    private static Identifier getEnchantmentId(Level level, Holder<Enchantment> enchantment) {
        try {
            return level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getKey(enchantment.value());
        } catch (Exception ignored) {
            return Identifier.parse("minecraft:air");
        }
    }
}
