package org.iansaididontcare.etymonica.registry.infusion.api;

public record InfusionAltarModifierStats(double speed, double efficiency, int maxNum) {
    public static final InfusionAltarModifierStats DEFAULT = new InfusionAltarModifierStats(0.0, 0.0, 0);
}
