package org.iansaididontcare.etymonica.registry.infusion.data;

import net.minecraft.resources.Identifier;
import org.iansaididontcare.etymonica.registry.infusion.api.InfusionAltarModifierStats;
import org.iansaididontcare.etymonica.registry.infusion.api.InfusionAltarStats;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class InfusionAltarData {
    private InfusionAltarData() {}

    private static volatile Map<String, InfusionAltarStats> ALTAR_TIERS = Map.of();
    private static volatile Map<Identifier, InfusionAltarModifierStats> MODIFIERS = Map.of();
    private static final AtomicLong REVISION = new AtomicLong(0L);

    public static InfusionAltarStats getAltarTier(String tierId) {
        return ALTAR_TIERS.getOrDefault(tierId, InfusionAltarStats.DEFAULT);
    }

    public static InfusionAltarModifierStats getModifier(Identifier blockId) {
        return MODIFIERS.getOrDefault(blockId, InfusionAltarModifierStats.DEFAULT);
    }

    public static long getRevision() {
        return REVISION.get();
    }

    public static void setAltarTiers(Map<String, InfusionAltarStats> tiers) {
        ALTAR_TIERS = Map.copyOf(tiers);
        REVISION.incrementAndGet();
    }

    public static void setModifiers(Map<Identifier, InfusionAltarModifierStats> modifiers) {
        MODIFIERS = Map.copyOf(modifiers);
        REVISION.incrementAndGet();
    }
}
