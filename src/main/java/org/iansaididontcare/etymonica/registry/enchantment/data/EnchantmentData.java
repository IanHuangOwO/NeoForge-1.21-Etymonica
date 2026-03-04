package org.iansaididontcare.etymonica.registry.enchantment.data;

import net.minecraft.resources.Identifier;
import org.iansaididontcare.etymonica.registry.enchantment.api.EnchantmentRarity;
import org.iansaididontcare.etymonica.registry.enchantment.api.EnchantmentWeightStats;

import java.util.concurrent.atomic.AtomicLong;

public final class EnchantmentData {
    private EnchantmentData() {}

    private static volatile java.util.Map<Identifier, EnchantmentWeightStats> WEIGHTS = java.util.Map.of();
    private static volatile java.util.Map<Identifier, EnchantmentRarity> RARITIES = java.util.Map.of();
    private static volatile java.util.Map<EnchantmentRarity, java.util.List<Identifier>> BY_RARITY = java.util.Map.of();
    private static volatile java.util.Map<EnchantmentRarity, EnchantmentWeightStats> RARITY_DEFAULTS = java.util.Map.of();
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

    public static java.util.List<Identifier> getEnchantmentsByRarity(EnchantmentRarity rarity) {
        return BY_RARITY.getOrDefault(rarity, java.util.List.of());
    }

    public static java.util.Optional<Identifier> getRandomEnchantmentByRarity(EnchantmentRarity rarity, net.minecraft.util.RandomSource random) {
        java.util.List<Identifier> list = getEnchantmentsByRarity(rarity);
        if (list.isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(list.get(random.nextInt(list.size())));
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

    public static void setEnchantmentWeights(java.util.Map<Identifier, EnchantmentWeightStats> weights, EnchantmentWeightStats defaultWeights) {
        WEIGHTS = java.util.Map.copyOf(weights);
        DEFAULT_WEIGHTS = defaultWeights != null ? defaultWeights : EnchantmentWeightStats.DEFAULT;
        REVISION.incrementAndGet();
    }

    public static void setEnchantmentRarities(java.util.Map<Identifier, EnchantmentRarity> rarities) {
        RARITIES = java.util.Map.copyOf(rarities);
        
        java.util.Map<EnchantmentRarity, java.util.List<Identifier>> byRarity = new java.util.EnumMap<>(EnchantmentRarity.class);
        for (EnchantmentRarity r : EnchantmentRarity.values()) {
            byRarity.put(r, new java.util.ArrayList<>());
        }
        for (java.util.Map.Entry<Identifier, EnchantmentRarity> entry : rarities.entrySet()) {
            byRarity.get(entry.getValue()).add(entry.getKey());
        }
        
        java.util.Map<EnchantmentRarity, java.util.List<Identifier>> finalMap = new java.util.EnumMap<>(EnchantmentRarity.class);
        byRarity.forEach((r, list) -> finalMap.put(r, java.util.List.copyOf(list)));
        BY_RARITY = java.util.Map.copyOf(finalMap);
        
        REVISION.incrementAndGet();
    }

    public static void setRarityDefaults(java.util.Map<EnchantmentRarity, EnchantmentWeightStats> defaults) {
        RARITY_DEFAULTS = java.util.Map.copyOf(defaults);
        REVISION.incrementAndGet();
    }
}
