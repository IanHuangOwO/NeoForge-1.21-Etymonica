package org.iansaididontcare.etymonica.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.iansaididontcare.etymonica.Etymonica;

public final class ModBlockTags {
    private ModBlockTags() {}

    public static final TagKey<Block> ENCHANTING_TABLES =
            TagKey.create(Registries.BLOCK, Identifier.parse(Etymonica.MOD_ID + ":enchanting_tables"));

    public static final TagKey<Block> ENCHANTING_TABLE_MODIFIERS =
            TagKey.create(Registries.BLOCK, Identifier.parse(Etymonica.MOD_ID + ":enchanting_table_modifiers"));

    public static final TagKey<Block> ENCHANTING_TABLE_BOOKSHELVES =
            TagKey.create(Registries.BLOCK, Identifier.parse(Etymonica.MOD_ID + ":enchanting_table_bookshelves"));

    public static final TagKey<Block> INFUSION_ALTARS =
            TagKey.create(Registries.BLOCK, Identifier.parse(Etymonica.MOD_ID + ":infusion_altars"));

    public static final TagKey<Block> INFUSION_ALTAR_MODIFIERS =
            TagKey.create(Registries.BLOCK, Identifier.parse(Etymonica.MOD_ID + ":infusion_altar_modifiers"));

    public static final TagKey<Block> INFUSION_ALTAR_PEDESTALS =
            TagKey.create(Registries.BLOCK, Identifier.parse(Etymonica.MOD_ID + ":infusion_altar_pedestals"));
}
