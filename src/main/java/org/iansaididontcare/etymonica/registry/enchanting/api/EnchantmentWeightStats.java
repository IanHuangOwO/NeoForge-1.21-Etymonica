package org.iansaididontcare.etymonica.registry.enchanting.api;

public record EnchantmentWeightStats(double accumulation, double drain) {
    public EnchantmentWeightStats {
        accumulation = Math.max(0.0d, accumulation);
        drain = Math.max(0.0d, drain);
    }

    public static final EnchantmentWeightStats DEFAULT = new EnchantmentWeightStats(1.0d, 1.0d);
}
