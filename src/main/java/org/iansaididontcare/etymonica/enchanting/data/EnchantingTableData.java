package org.iansaididontcare.etymonica.enchanting.data;

import net.minecraft.resources.Identifier;
import org.iansaididontcare.etymonica.enchanting.api.EnchantingTableStats;
import org.iansaididontcare.etymonica.enchanting.api.EnchantingTableModifierStats;

import java.util.Map;

public final class EnchantingTableData {
    private EnchantingTableData() {}

    private static volatile Map<String, EnchantingTableStats> TIERS = Map.of();
    private static volatile Map<Identifier, EnchantingTableModifierStats> MODIFIERS = Map.of();

    public static EnchantingTableStats getTier(String tierId) {
        return TIERS.getOrDefault(tierId, new EnchantingTableStats(0, 0, 0, 0, 0f, 0f, 0f));
    }

    public static EnchantingTableModifierStats getModifier(Identifier blockId) {
        return MODIFIERS.getOrDefault(blockId, EnchantingTableModifierStats.ZERO);
    }

    public static void setTiers(Map<String, EnchantingTableStats> tiers) {
        TIERS = Map.copyOf(tiers);
    }

    public static void setModifiers(Map<Identifier, EnchantingTableModifierStats> modifiers) {
        MODIFIERS = Map.copyOf(modifiers);
    }
}
