package org.iansaididontcare.etymonica.datagen;

import net.minecraft.resources.Identifier;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.block.ModBlocks;
import org.iansaididontcare.etymonica.item.ModItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NonNull;

import java.util.Set;
import java.util.stream.Stream;

public class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, Etymonica.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        /* ITEMS */
        itemModels.generateFlatItem(ModItems.ETYMONICON.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.RAW_ORICHALCUM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ORICHALCUM_INGOT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TUNING_FORK.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.QUILL_TIER0.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LIQUID_EXPERIENCE_BUCKET.get(), ModelTemplates.FLAT_ITEM);

        /* BLOCKS */
        blockModels.createTrivialCube(ModBlocks.ORICHALCUM_BLOCK.get());
        blockModels.createTrivialCube(ModBlocks.ENCHANTING_TABLE_TIER0.get());
        blockModels.createTrivialCube(ModBlocks.ENCHANTING_TABLE_TIER1.get());
    }

    private static final Set<Identifier> MANUAL_MODEL_BLOCKS = Set.of(
            ModBlocks.PEDESTAL.getId(),
            ModBlocks.BRAIN_IN_A_JAR.getId(),
            ModBlocks.LIQUID_EXPERIENCE_BLOCK.getId()
    );

    private static final Set<Identifier> MANUAL_MODEL_ITEMS = Set.of(
            ModBlocks.PEDESTAL.getId(),
            ModBlocks.BRAIN_IN_A_JAR.getId(),
            ModBlocks.LIQUID_EXPERIENCE_BLOCK.getId()
    );

    @Override
    protected @NonNull Stream<? extends Holder<Block>> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream()
                .filter(h -> !MANUAL_MODEL_BLOCKS.contains(h.getId()));
    }

    @Override
    protected @NonNull Stream<? extends Holder<Item>> getKnownItems() {
        return ModItems.ITEMS.getEntries().stream()
                .filter(h -> !MANUAL_MODEL_ITEMS.contains(h.getId()));
    }
}
