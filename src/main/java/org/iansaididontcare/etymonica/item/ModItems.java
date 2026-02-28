package org.iansaididontcare.etymonica.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.item.custom.QuillTier0;
import org.iansaididontcare.etymonica.item.custom.TuningFork;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Etymonica.MOD_ID);

    public static final DeferredItem<Item> ETYMONICON = ITEMS.register("etymonicon",
            registryName -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final DeferredItem<Item> RAW_ORICHALCUM = ITEMS.register("raw_orichalcum",
            registryName -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final DeferredItem<Item> ORICHALCUM_INGOT = ITEMS.register("orichalcum_ingot",
            registryName -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final DeferredItem<Item> TUNING_FORK = ITEMS.register("tuning_fork",
            registryName -> new TuningFork(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final DeferredItem<Item> QUILL_TIER0 = ITEMS.register("quill_tier0",
            registryName -> new QuillTier0(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static void register(IEventBus eventBus) {ITEMS.register(eventBus);}
}
