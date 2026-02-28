package org.iansaididontcare.etymonica.block.entity.enchanting;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.iansaididontcare.etymonica.enchanting.data.EnchantingTableData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class EnchantPowerCalculator {
    private EnchantPowerCalculator() {}

    public static int computeBookshelfPower(Level level, Set<BlockPos> linkedModifiers) {
        if (linkedModifiers.isEmpty()) return 0;

        Map<Holder<Enchantment>, Integer> levelsByType = new HashMap<>();

        for (BlockPos pos : linkedModifiers) {
            if (!(level.getBlockEntity(pos) instanceof Container container)) continue;

            int size = container.getContainerSize();
            for (int i = 0; i < size; i++) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) continue;

                ItemEnchantments enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
                for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                    if (entry == null) continue;
                    int levelValue = Math.max(0, entry.getIntValue());
                    if (levelValue <= 0) continue;
                    levelsByType.merge(entry.getKey(), levelValue, Integer::sum);
                }
            }
        }

        int power = 0;
        for (Map.Entry<Holder<Enchantment>, Integer> entry : levelsByType.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int levelSum = Math.max(0, entry.getValue());
            if (levelSum <= 0) continue;

            Identifier enchantmentId;
            try {
                enchantmentId = level.registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getKey(enchantment.value());
            } catch (Exception ignored) {
                enchantmentId = Identifier.parse("minecraft:air");
            }
            double weight = EnchantingTableData.getEnchantmentWeight(enchantmentId);

            power += (int) Math.round(levelSum * Math.max(0.0d, weight));
        }
        return Math.max(0, power);
    }
}
