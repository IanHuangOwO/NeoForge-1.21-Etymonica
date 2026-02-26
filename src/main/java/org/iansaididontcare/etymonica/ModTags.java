package org.iansaididontcare.etymonica;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    private ModTags() {}

    public static final class Blocks {
        private Blocks() {}

        public static final TagKey<Block> GROWTH_CHAMBER_POWER_SOURCES =
                TagKey.create(Registries.BLOCK, Identifier.parse(Etymonica.MOD_ID + ":growth_chamber_power_sources"));
    }
}
