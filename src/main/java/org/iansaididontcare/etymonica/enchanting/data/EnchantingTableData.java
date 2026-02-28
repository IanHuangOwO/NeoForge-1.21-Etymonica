package org.iansaididontcare.etymonica.enchanting.data;

import net.minecraft.resources.Identifier;
import org.iansaididontcare.etymonica.enchanting.api.EnchantingTableStats;
import org.iansaididontcare.etymonica.enchanting.api.EnchantingTableModifierStats;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class EnchantingTableData {
    private EnchantingTableData() {}

    private static volatile Map<String, EnchantingTableStats> TIERS = Map.of();
    private static volatile Map<Identifier, EnchantingTableModifierStats> MODIFIERS = Map.of();
    private static volatile Map<Identifier, Double> ENCHANTMENT_WEIGHTS = Map.of();
    private static volatile double DEFAULT_ENCHANTMENT_WEIGHT = 1.0d;
    private static final AtomicLong REVISION = new AtomicLong(0L);

    public static EnchantingTableStats getTier(String tierId) {
        return TIERS.getOrDefault(tierId, new EnchantingTableStats(0, 0, 0, 0, 0f, 0f, 0f));
    }

    public static EnchantingTableModifierStats getModifier(Identifier blockId) {
        return MODIFIERS.getOrDefault(blockId, EnchantingTableModifierStats.ZERO);
    }

    public static double getEnchantmentWeight(Identifier enchantmentId) {
        return ENCHANTMENT_WEIGHTS.getOrDefault(enchantmentId, DEFAULT_ENCHANTMENT_WEIGHT);
    }

    public static long getRevision() {
        return REVISION.get();
    }

    public static void setTiers(Map<String, EnchantingTableStats> tiers) {
        TIERS = Map.copyOf(tiers);
        REVISION.incrementAndGet();
    }

    public static void setModifiers(Map<Identifier, EnchantingTableModifierStats> modifiers) {
        MODIFIERS = Map.copyOf(modifiers);
        REVISION.incrementAndGet();
    }

    public static void setEnchantmentWeights(Map<Identifier, Double> weights, double defaultWeight) {
        ENCHANTMENT_WEIGHTS = Map.copyOf(weights);
        DEFAULT_ENCHANTMENT_WEIGHT = Math.max(0.0d, defaultWeight);
        REVISION.incrementAndGet();
    }
}
