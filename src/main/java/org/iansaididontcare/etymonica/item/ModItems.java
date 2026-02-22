package org.iansaididontcare.etymonica.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.iansaididontcare.etymonica.Etymonica;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Etymonica.MOD_ID);

    public static final DeferredItem<Item> ETYMONICON = ITEMS.register("etymonicon",
            registryName -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final DeferredItem<Item> RAW_ORICHALCUM = ITEMS.register("raw_orichalcum",
            registryName -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final DeferredItem<Item> ORICHALCUM_INGOT = ITEMS.register("orichalcum_ingot",
            registryName -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static void register(IEventBus eventBus) {ITEMS.register(eventBus);}
}
