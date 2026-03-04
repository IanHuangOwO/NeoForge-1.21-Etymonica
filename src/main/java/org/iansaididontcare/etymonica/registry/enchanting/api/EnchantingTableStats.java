package org.iansaididontcare.etymonica.registry.enchanting.api;

public record EnchantingTableStats(
        int enchantingPowerCap,
        int linkRadius,
        int maxTierEnchantment,
        int maxLinkedBookshelves,
        float speed,
        float stability,
        float efficiency
) {
    public EnchantingTableStats {
        enchantingPowerCap = Math.max(0, enchantingPowerCap);
        linkRadius = Math.max(0, linkRadius);
        maxTierEnchantment = Math.max(0, maxTierEnchantment);

        maxLinkedBookshelves = Math.max(0, maxLinkedBookshelves);

        speed = clamp01(speed);
        stability = clamp01(stability);
        efficiency = clamp01(efficiency);
    }

    public static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
