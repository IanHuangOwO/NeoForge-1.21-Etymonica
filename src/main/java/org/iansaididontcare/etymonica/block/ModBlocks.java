package org.iansaididontcare.etymonica.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.block.custom.enchantingtable.EnchantingTableTier0Block;
import org.iansaididontcare.etymonica.block.custom.enchantingtable.EnchantingTableTier1Block;
import org.iansaididontcare.etymonica.block.custom.PedestalBlock;
import org.iansaididontcare.etymonica.block.custom.jar.ZombieBrainInAJarBlock;
import org.iansaididontcare.etymonica.fluid.ModFluids;
import org.iansaididontcare.etymonica.item.ModItems;
import org.iansaididontcare.etymonica.item.custom.BrainInAJarItem;

import java.util.function.Function;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Etymonica.MOD_ID);

    public static final DeferredBlock<Block> ORICHALCUM_BLOCK = registerBlock("orichalcum_block",
            (properties) -> new Block(properties
                    .strength(4f).requiresCorrectToolForDrops().sound(SoundType.COPPER)));

    public static final DeferredBlock<Block> PEDESTAL = registerBlock("pedestal",
            (properties) -> new PedestalBlock(properties.noOcclusion()));

    public static final DeferredBlock<Block> ENCHANTING_TABLE_TIER0 = registerBlock("enchanting_table_tier0",
            (properties) -> new EnchantingTableTier0Block(properties
                    .strength(3.5f)
                    .sound(SoundType.WOOD)));

    public static final DeferredBlock<Block> ENCHANTING_TABLE_TIER1 = registerBlock("enchanting_table_tier1",
            (properties) -> new EnchantingTableTier1Block(properties
                    .strength(4.0f)
                    .sound(SoundType.WOOD)));

    public static final DeferredBlock<Block> BRAIN_IN_A_JAR = registerBlock("brain_in_a_jar",
            properties -> new ZombieBrainInAJarBlock(properties
                    .strength(2.0f)
                    .sound(SoundType.GLASS)
                    .noOcclusion()));

    public static final DeferredBlock<LiquidBlock> LIQUID_EXPERIENCE_BLOCK = BLOCKS.registerBlock("liquid_experience_block",
            properties -> new LiquidBlock(ModFluids.LIQUID_EXPERIENCE.get(),
                    properties
                            .replaceable()
                            .noCollision()
                            .strength(100.0F)));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Function<BlockBehaviour.Properties, T> function) {
        DeferredBlock<T> toReturn = BLOCKS.registerBlock(name, function);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.registerItem(name, (properties) -> {
            if ("brain_in_a_jar".equals(name)) {
                return new BrainInAJarItem(block.get(), properties.useBlockDescriptionPrefix().stacksTo(1));
            }
            return new BlockItem(block.get(), properties.useBlockDescriptionPrefix());
        });
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
