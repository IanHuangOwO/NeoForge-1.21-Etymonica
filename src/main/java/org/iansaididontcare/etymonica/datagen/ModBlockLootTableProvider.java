package org.iansaididontcare.etymonica.datagen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.common.Mod;
import org.iansaididontcare.etymonica.block.ModBlocks;
import org.iansaididontcare.etymonica.item.ModItems;

import java.util.Set;

public class ModBlockLootTableProvider extends BlockLootSubProvider {
    protected ModBlockLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        dropSelf(ModBlocks.ORICHALCUM_BLOCK.get());
        dropSelf(ModBlocks.PEDESTAL.get());
        dropSelf(ModBlocks.ENCHANTING_TABLE_TIER0.get());
        dropSelf(ModBlocks.ENCHANTING_TABLE_TIER1.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(Holder::value)::iterator;
    }
}
