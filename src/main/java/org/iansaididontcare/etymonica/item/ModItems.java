package org.iansaididontcare.etymonica.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.fluid.ModFluids;
import org.iansaididontcare.etymonica.item.custom.ExperienceTreeItem;
import org.iansaididontcare.etymonica.item.custom.Quill;
import org.iansaididontcare.etymonica.item.custom.TuningFork;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Etymonica.MOD_ID);

    public static final DeferredItem<Item> EXPERIENCE_TREE = ITEMS.register("experience_tree",
            registryName -> new ExperienceTreeItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName)).stacksTo(1)));

    public static final DeferredItem<Item> ETYMONICON = ITEMS.register("etymonicon",
            registryName -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final DeferredItem<Item> RAW_ORICHALCUM = ITEMS.register("raw_orichalcum",
            registryName -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final DeferredItem<Item> ORICHALCUM_INGOT = ITEMS.register("orichalcum_ingot",
            registryName -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName))));

    public static final DeferredItem<Item> TUNING_FORK = ITEMS.register("tuning_fork",
            registryName -> new TuningFork(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName)).stacksTo(1)));

    public static final DeferredItem<Item> QUILL_TIER0 = ITEMS.register("quill_tier0",
            registryName -> new Quill(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, registryName)).stacksTo(1)));

    public static final DeferredItem<Item> LIQUID_EXPERIENCE_BUCKET = ITEMS.register("liquid_experience_bucket",
            registryName -> new BucketItem(ModFluids.LIQUID_EXPERIENCE.get(),
                    new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, registryName))
                            .stacksTo(1)
                            .craftRemainder(Items.BUCKET)));

    public static void register(IEventBus eventBus) {ITEMS.register(eventBus);}
}
