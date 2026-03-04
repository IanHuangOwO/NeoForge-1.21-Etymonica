package org.iansaididontcare.etymonica.registry.enchantment.data;

import net.minecraft.resources.Identifier;
import org.iansaididontcare.etymonica.registry.enchantment.api.EnchantmentRarity;
import org.iansaididontcare.etymonica.registry.enchantment.api.EnchantmentWeightStats;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class EnchantmentData {
    private EnchantmentData() {}

    private static volatile Map<Identifier, EnchantmentWeightStats> WEIGHTS = Map.of();
    private static volatile Map<Identifier, EnchantmentRarity> RARITIES = Map.of();
    private static volatile Map<EnchantmentRarity, EnchantmentWeightStats> RARITY_DEFAULTS = Map.of();
    private static volatile EnchantmentWeightStats DEFAULT_WEIGHTS = EnchantmentWeightStats.DEFAULT;
    private static final AtomicLong REVISION = new AtomicLong(0L);

    public static EnchantmentWeightStats getEnchantmentWeight(Identifier enchantmentId) {
        EnchantmentWeightStats specific = WEIGHTS.get(enchantmentId);
        if (specific != null) return specific;

        EnchantmentRarity rarity = getEnchantmentRarity(enchantmentId);
        return RARITY_DEFAULTS.getOrDefault(rarity, DEFAULT_WEIGHTS);
    }

    public static EnchantmentRarity getEnchantmentRarity(Identifier enchantmentId) {
        return RARITIES.getOrDefault(enchantmentId, EnchantmentRarity.COMMON);
    }

    public static double getPower(Identifier enchantmentId) {
        return getEnchantmentWeight(enchantmentId).power();
    }

    public static double getDrainWeight(Identifier enchantmentId) {
        return getEnchantmentWeight(enchantmentId).drain();
    }

    public static long getRevision() {
        return REVISION.get();
    }

    public static void setEnchantmentWeights(Map<Identifier, EnchantmentWeightStats> weights, EnchantmentWeightStats defaultWeights) {
        WEIGHTS = Map.copyOf(weights);
        DEFAULT_WEIGHTS = defaultWeights != null ? defaultWeights : EnchantmentWeightStats.DEFAULT;
        REVISION.incrementAndGet();
    }

    public static void setEnchantmentRarities(Map<Identifier, EnchantmentRarity> rarities) {
        RARITIES = Map.copyOf(rarities);
        REVISION.incrementAndGet();
    }

    public static void setRarityDefaults(Map<EnchantmentRarity, EnchantmentWeightStats> defaults) {
        RARITY_DEFAULTS = Map.copyOf(defaults);
        REVISION.incrementAndGet();
    }
}
