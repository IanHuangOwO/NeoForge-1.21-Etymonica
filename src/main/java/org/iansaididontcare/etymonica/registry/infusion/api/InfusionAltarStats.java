package org.iansaididontcare.etymonica.registry.infusion.api;

import net.minecraft.resources.Identifier;

public record InfusionAltarStats(
    int itemsPerInfusion,
    double speed,
    double efficiency,
    int linkRadius,
    int maxLinkedPedestals,
    EnchantmentTierWeights weights,
    int multiblockRadius,
    Identifier multiblockBlock,
    int glassSphereRadius
) {
    public static final InfusionAltarStats DEFAULT = new InfusionAltarStats(
            1, 0.1, 0.0, 4, 16, 
            EnchantmentTierWeights.DEFAULT, 
            3, Identifier.parse("minecraft:gold_block"),
            0
    );
}
