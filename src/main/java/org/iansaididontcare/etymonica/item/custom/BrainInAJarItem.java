package org.iansaididontcare.etymonica.item.custom;

import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import org.iansaididontcare.etymonica.block.entity.BrainInAJarBlockEntity;

import java.util.function.Consumer;

public class BrainInAJarItem extends BlockItem {
    public BrainInAJarItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltipAdder, flag);
        int stored = getStoredMillibuckets(stack);
        tooltipAdder.accept(Component.translatable(
                "tooltip.etymonica.brain_in_a_jar.storage",
                stored,
                BrainInAJarBlockEntity.CAPACITY_MILLIBUCKETS
        ));
    }

    public static int getStoredMillibuckets(ItemStack stack) {
        CompoundTag tag = getJarTag(stack);
        if (tag == null) {
            return 0;
        }
        return Math.max(0, Math.min(
                BrainInAJarBlockEntity.CAPACITY_MILLIBUCKETS,
                tag.getInt(BrainInAJarBlockEntity.STORED_MILLIBUCKETS_KEY).orElse(0)
        ));
    }

    public static void setStoredMillibuckets(ItemStack stack, int amount) {
        int clamped = Math.max(0, Math.min(BrainInAJarBlockEntity.CAPACITY_MILLIBUCKETS, amount));
        if (clamped <= 0) {
            CompoundTag existing = getJarTag(stack);
            if (existing == null) {
                return;
            }
            existing.remove(BrainInAJarBlockEntity.STORED_MILLIBUCKETS_KEY);
            if (existing.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_DATA);
            } else {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(existing));
            }
            return;
        }

        CompoundTag tag = getJarTag(stack);
        CompoundTag updated = tag != null ? tag : new CompoundTag();
        updated.putInt(BrainInAJarBlockEntity.STORED_MILLIBUCKETS_KEY, clamped);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(updated));
    }

    private static CompoundTag getJarTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }
}
