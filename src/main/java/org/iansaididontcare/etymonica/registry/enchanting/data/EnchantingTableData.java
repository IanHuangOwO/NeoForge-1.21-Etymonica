package org.iansaididontcare.etymonica.registry.enchanting.data;

import net.minecraft.resources.Identifier;
import org.iansaididontcare.etymonica.registry.enchanting.api.EnchantingTableStats;
import org.iansaididontcare.etymonica.registry.enchanting.api.EnchantingTableModifierStats;
import org.iansaididontcare.etymonica.registry.enchanting.api.EnchantmentWeightStats;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class EnchantingTableData {
    private EnchantingTableData() {}

    private static volatile Map<String, EnchantingTableStats> TIERS = Map.of();
    private static volatile Map<Identifier, EnchantingTableModifierStats> MODIFIERS = Map.of();
    private static volatile Map<Identifier, EnchantmentWeightStats> WEIGHTS = Map.of();
    private static volatile EnchantmentWeightStats DEFAULT_WEIGHTS = EnchantmentWeightStats.DEFAULT;
    private static final AtomicLong REVISION = new AtomicLong(0L);

    public static EnchantingTableStats getTier(String tierId) {
        return TIERS.getOrDefault(tierId, new EnchantingTableStats(0, 0, 0, 0, 0f, 0f, 0f));
    }

    public static EnchantingTableModifierStats getModifier(Identifier blockId) {
        return MODIFIERS.getOrDefault(blockId, EnchantingTableModifierStats.ZERO);
    }

    public static EnchantmentWeightStats getEnchantmentWeight(Identifier enchantmentId) {
        return WEIGHTS.getOrDefault(enchantmentId, DEFAULT_WEIGHTS);
    }

    public static double getAccumulationWeight(Identifier enchantmentId) {
        return getEnchantmentWeight(enchantmentId).accumulation();
    }

    public static double getDrainWeight(Identifier enchantmentId) {
        return getEnchantmentWeight(enchantmentId).drain();
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

    public static void setEnchantmentWeights(Map<Identifier, EnchantmentWeightStats> weights, EnchantmentWeightStats defaultWeights) {
        WEIGHTS = Map.copyOf(weights);
        DEFAULT_WEIGHTS = defaultWeights != null ? defaultWeights : EnchantmentWeightStats.DEFAULT;
        REVISION.incrementAndGet();
    }
}
