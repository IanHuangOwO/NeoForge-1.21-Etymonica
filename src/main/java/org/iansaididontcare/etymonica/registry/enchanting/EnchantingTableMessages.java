package org.iansaididontcare.etymonica.registry.enchanting;

import net.minecraft.network.chat.Component;
import org.iansaididontcare.etymonica.registry.enchanting.api.TableActionResult;

public final class EnchantingTableMessages {
    private EnchantingTableMessages() {}

    public static Component action(TableActionResult result) {
        return switch (result) {
            case ENCHANT_STARTED -> Component.translatable("message.etymonica.enchanting.started");
            case ENCHANT_BLOCKED -> Component.translatable("message.etymonica.enchanting.cannot_start");
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
