package org.iansaididontcare.etymonica.block;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.block.custom.GrowthChamberBlock;
import org.iansaididontcare.etymonica.block.custom.PedestalBlock;
import org.iansaididontcare.etymonica.item.ModItems;

import java.util.function.Function;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Etymonica.MOD_ID);

    public static final DeferredBlock<Block> ORICHALCUM_BLOCK = registerBlock("orichalcum_block",
            (properties) -> new Block(properties
                    .strength(4f).requiresCorrectToolForDrops().sound(SoundType.COPPER)));

    public static final DeferredBlock<Block> PEDESTAL = registerBlock("pedestal",
            (properties) -> new PedestalBlock(properties.noOcclusion()));

    public static final DeferredBlock<Block> GROWTH_CHAMBER = registerBlock("growth_chamber",
            GrowthChamberBlock::new);

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Function<BlockBehaviour.Properties, T> function) {
        DeferredBlock<T> toReturn = BLOCKS.registerBlock(name, function);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.registerItem(name, (properties) -> new BlockItem(block.get(), properties.useBlockDescriptionPrefix()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
