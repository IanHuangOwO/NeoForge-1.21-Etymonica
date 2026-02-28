package org.iansaididontcare.etymonica.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.iansaididontcare.etymonica.Etymonica;

public final class ModItemTags {
    private ModItemTags() {}

    public static final TagKey<Item> QUILLS =
            TagKey.create(Registries.ITEM, Identifier.parse(Etymonica.MOD_ID + ":quills"));
}
