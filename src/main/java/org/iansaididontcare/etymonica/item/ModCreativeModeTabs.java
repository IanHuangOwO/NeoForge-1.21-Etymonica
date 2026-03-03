package org.iansaididontcare.etymonica.item;

import org.iansaididontcare.etymonica.Etymonica;

import org.iansaididontcare.etymonica.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Etymonica.MOD_ID);

    public static final Supplier<CreativeModeTab> ETYMONICA_ITEMS_TAB = CREATIVE_MODE_TAB.register("etymonica_items_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.ETYMONICON.get()))
                    .title(Component.translatable("creativetab.etymonica.etymonica_items"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModItems.ETYMONICON);
                        output.accept(ModItems.RAW_ORICHALCUM);
                        output.accept(ModItems.ORICHALCUM_INGOT);
                        output.accept(ModItems.TUNING_FORK);
                        output.accept(ModItems.QUILL_TIER0);
                        output.accept(ModItems.LIQUID_EXPERIENCE_BUCKET);
                    }).build());

    public static final Supplier<CreativeModeTab> ETYMONICA_BLOCK_TAB = CREATIVE_MODE_TAB.register("etmonica_blocks_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.ORICHALCUM_BLOCK))
                    .withTabsBefore(Identifier.fromNamespaceAndPath(Etymonica.MOD_ID, "etymonica_items_tab"))
                    .title(Component.translatable("creativetab.etymonica.etymonica_blocks"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModBlocks.ORICHALCUM_BLOCK);
                        output.accept(ModBlocks.PEDESTAL);
                        output.accept(ModBlocks.ENCHANTING_TABLE_TIER0);
                        output.accept(ModBlocks.ENCHANTING_TABLE_TIER1);
                        output.accept(ModBlocks.INFUSION_ALTAR_TIER0);
                        output.accept(ModBlocks.BRAIN_IN_A_JAR);
                    }).build());

    public static void register(IEventBus eventBus) {CREATIVE_MODE_TAB.register(eventBus);}
}
