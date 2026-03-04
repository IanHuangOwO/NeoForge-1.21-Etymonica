package org.iansaididontcare.etymonica.registry.infusion.api;

public record EnchantmentTierWeights(
    double common,
    double uncommon,
    double rare,
    double epic,
    double legendary,
    double mystic
) {
    public static final EnchantmentTierWeights DEFAULT = new EnchantmentTierWeights(0.6, 0.3, 0.1, 0.0, 0.0, 0.0);
}
