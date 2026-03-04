package org.iansaididontcare.etymonica.registry.infusion.api;

public record InfusionAltarStats(
    int itemsPerInfusion,
    double speed,
    double efficiency,
    EnchantmentTierWeights weights
) {
    public static final InfusionAltarStats DEFAULT = new InfusionAltarStats(1, 0.1, 0.0, EnchantmentTierWeights.DEFAULT);
}
