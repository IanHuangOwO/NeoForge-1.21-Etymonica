package org.iansaididontcare.etymonica.events;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.iansaididontcare.etymonica.item.custom.BrainInAJarItem;
import org.iansaididontcare.etymonica.registry.jar.data.JarData;

public final class ModEvents {
    private static final int MB_PER_XP_POINT = 20;
    private static final int XP_PER_DRAIN = 10;
    private static final int DRAIN_INTERVAL_TICKS = 20;

    private ModEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.register(ModEvents.class);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        if (player.tickCount % DRAIN_INTERVAL_TICKS != 0) {
            return;
        }

        if (player.experienceLevel <= 0 && player.experienceProgress <= 0.0F) {
            return;
        }

        int containerSize = player.getInventory().getContainerSize();
        for (int slot = 0; slot < containerSize; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof BrainInAJarItem)) {
                continue;
            }

            int capacity = getCapacity(stack);
            int stored = BrainInAJarItem.getStoredMillibuckets(stack);
            int spaceMb = capacity - stored;
            if (spaceMb <= 0) {
                continue;
            }

            // Ceil division allows topping off the last partial 20 mB slice (with minor XP loss).
            int xpNeededToFill = (spaceMb + MB_PER_XP_POINT - 1) / MB_PER_XP_POINT;
            int xpToDrain = Math.min(XP_PER_DRAIN, xpNeededToFill);
            int drained = 0;
            while (drained < xpToDrain && (player.experienceLevel > 0 || player.experienceProgress > 0.0F)) {
                player.giveExperiencePoints(-1);
                drained++;
            }

            if (drained > 0) {
                BrainInAJarItem.setStoredMillibuckets(stack, stored + (drained * MB_PER_XP_POINT));
                return;
            }
        }
    }

    private static int getCapacity(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return 8000;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
        return JarData.getJarType(id).capacity();
    }
}
