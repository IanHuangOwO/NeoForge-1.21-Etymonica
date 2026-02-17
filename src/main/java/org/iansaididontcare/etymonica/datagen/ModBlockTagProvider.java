package org.iansaididontcare.etymonica.datagen;

import org.iansaididontcare.etymonica.Etymonica;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Etymonica.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
    }
}