package org.iansaididontcare.etymonica.registry.infusion;

import net.minecraft.network.chat.Component;
import org.iansaididontcare.etymonica.registry.infusion.api.AltarActionResult;

public final class InfusionAltarMessages {
    private InfusionAltarMessages() {}

    public static Component action(AltarActionResult result) {
        return switch (result) {
            case INFUSE_STARTED -> Component.translatable("message.etymonica.infusing.started");
            case INFUSE_BLOCKED -> Component.translatable("message.etymonica.infusing.cannot_start");
            case RELINK_STARTED -> Component.translatable("message.etymonica.relink.started");
            case RELINK_CANCELLED -> Component.translatable("message.etymonica.relink.cancelled");
            case RELINK_BLOCKED -> Component.translatable("message.etymonica.relink.cannot_start");
            case MODIFIER_LINKED -> Component.translatable("message.etymonica.link_modifier.success");
            case MODIFIER_UNLINKED -> Component.translatable("message.etymonica.link_modifier.unlinked");
            case LINK_BLOCKED_NO_CAP -> Component.translatable("message.etymonica.link_modifier.no_capability");
            case LINK_BLOCKED_CAP_REACHED -> Component.translatable("message.etymonica.link_modifier.cap_reached");
            case LINK_BLOCKED_NO_RADIUS -> Component.translatable("message.etymonica.link_modifier.no_radius");
            case LINK_BLOCKED_TOO_FAR -> Component.translatable("message.etymonica.link_modifier.too_far");
            case LINK_BLOCKED_INVALID_BLOCK -> Component.translatable("message.etymonica.link_modifier.invalid_block");
            case LINK_BLOCKED_ALREADY_LINKED -> Component.translatable("message.etymonica.link_modifier.already_linked");
        };
    }
}
