package org.iansaididontcare.etymonica.registry.enchantment.api;

public record EnchantmentWeightStats(double power, double drain, EnchantmentRarity rarity) {
    public EnchantmentWeightStats {
        power = Math.max(0.0d, power);
        drain = Math.max(0.0d, drain);
        if (rarity == null) rarity = EnchantmentRarity.COMMON;
    }

    public static final EnchantmentWeightStats DEFAULT = new EnchantmentWeightStats(1.0d, 1.0d, EnchantmentRarity.COMMON);
}
