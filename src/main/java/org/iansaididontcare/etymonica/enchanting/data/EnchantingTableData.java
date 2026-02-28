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
    private static volatile Map<Identifier, Double> ACCUMULATION_WEIGHTS = Map.of();
    private static volatile double DEFAULT_ACCUMULATION_WEIGHT = 1.0d;
    private static volatile Map<Identifier, Double> DRAIN_WEIGHTS = Map.of();
    private static volatile double DEFAULT_DRAIN_WEIGHT = 1.0d;
    private static final AtomicLong REVISION = new AtomicLong(0L);

    public static EnchantingTableStats getTier(String tierId) {
        return TIERS.getOrDefault(tierId, new EnchantingTableStats(0, 0, 0, 0, 0f, 0f, 0f));
    }

    public static EnchantingTableModifierStats getModifier(Identifier blockId) {
        return MODIFIERS.getOrDefault(blockId, EnchantingTableModifierStats.ZERO);
    }

    public static double getAccumulationWeight(Identifier enchantmentId) {
        return ACCUMULATION_WEIGHTS.getOrDefault(enchantmentId, DEFAULT_ACCUMULATION_WEIGHT);
    }

    public static double getDrainWeight(Identifier enchantmentId) {
        return DRAIN_WEIGHTS.getOrDefault(enchantmentId, DEFAULT_DRAIN_WEIGHT);
    }

    public static double getEnchantmentWeight(Identifier enchantmentId) {
        return getAccumulationWeight(enchantmentId);
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

    public static void setEnchantmentWeights(
            Map<Identifier, Double> accumulationWeights, double defaultAccumulationWeight,
            Map<Identifier, Double> drainWeights, double defaultDrainWeight
    ) {
        ACCUMULATION_WEIGHTS = Map.copyOf(accumulationWeights);
        DEFAULT_ACCUMULATION_WEIGHT = Math.max(0.0d, defaultAccumulationWeight);
        DRAIN_WEIGHTS = Map.copyOf(drainWeights);
        DEFAULT_DRAIN_WEIGHT = Math.max(0.0d, defaultDrainWeight);
        REVISION.incrementAndGet();
    }

    public static void setEnchantmentWeights(Map<Identifier, Double> weights, double defaultWeight) {
        setEnchantmentWeights(weights, defaultWeight, weights, defaultWeight);
    }
}
