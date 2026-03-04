package org.iansaididontcare.etymonica.registry.enchanting.api;

public record EnchantingTableModifierStats(float speed, float stability, float efficiency, int maxNum) {
    public EnchantingTableModifierStats {
        speed = EnchantingTableStats.clamp01(speed);
        stability = EnchantingTableStats.clamp01(stability);
        efficiency = EnchantingTableStats.clamp01(efficiency);
        if (maxNum < 0) maxNum = 0;
    }

    public static final EnchantingTableModifierStats ZERO = new EnchantingTableModifierStats(0f, 0f, 0f, 0);
}
