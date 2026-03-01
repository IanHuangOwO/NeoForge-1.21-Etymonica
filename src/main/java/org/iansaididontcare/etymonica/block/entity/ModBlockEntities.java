package org.iansaididontcare.etymonica.block.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.block.ModBlocks;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Etymonica.MOD_ID);

    public static final Supplier<BlockEntityType<PedestalBlockEntity>> PEDESTAL_BE =
            BLOCK_ENTITIES.register("pedestal_be", () -> new BlockEntityType<>(
                    PedestalBlockEntity::new, ModBlocks.PEDESTAL.get()));

    public static final Supplier<BlockEntityType<EnchantingTableBlockEntity>> ENCHANTING_TABLE_BE =
            BLOCK_ENTITIES.register("enchanting_table_be", () -> new BlockEntityType<>(
                    EnchantingTableBlockEntity::new,
                    ModBlocks.ENCHANTING_TABLE_TIER0.get(),
                    ModBlocks.ENCHANTING_TABLE_TIER1.get()));

    public static final Supplier<BlockEntityType<BrainInAJarBlockEntity>> BRAIN_IN_A_JAR_BE =
            BLOCK_ENTITIES.register("brain_in_a_jar_be", () -> new BlockEntityType<>(
                    BrainInAJarBlockEntity::new,
                    ModBlocks.BRAIN_IN_A_JAR.get()));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
