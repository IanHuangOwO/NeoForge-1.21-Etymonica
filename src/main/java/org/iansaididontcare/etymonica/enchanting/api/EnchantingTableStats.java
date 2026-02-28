package org.iansaididontcare.etymonica.enchanting.api;

public record EnchantingTableStats(
        int enchantingPowerCap,
        int linkRadius,
        int maxTierEnchantment,
        int maxLinkedModifiers,
        float speed,
        float stability,
        float efficiency
) {
    public EnchantingTableStats {
        enchantingPowerCap = Math.max(0, enchantingPowerCap);
        linkRadius = Math.max(0, linkRadius);
        maxTierEnchantment = Math.max(0, maxTierEnchantment);

        // 0 means "no linking allowed"; you can choose a different default if you prefer
        maxLinkedModifiers = Math.max(0, maxLinkedModifiers);

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
