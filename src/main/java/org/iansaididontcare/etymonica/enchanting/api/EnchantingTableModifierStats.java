package org.iansaididontcare.etymonica.enchanting.api;

public record EnchantingTableModifierStats(float speed, float stability, float efficiency) {
    public EnchantingTableModifierStats {
        speed = EnchantingTableStats.clamp01(speed);
        stability = EnchantingTableStats.clamp01(stability);
        efficiency = EnchantingTableStats.clamp01(efficiency);
    }

    public static final EnchantingTableModifierStats ZERO = new EnchantingTableModifierStats(0f, 0f, 0f);
}
