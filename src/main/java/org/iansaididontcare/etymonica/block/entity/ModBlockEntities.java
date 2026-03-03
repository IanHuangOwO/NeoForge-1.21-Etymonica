package org.iansaididontcare.etymonica.block.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.block.ModBlocks;
import org.iansaididontcare.etymonica.block.entity.enchantingtable.EnchantingTableTier0BlockEntity;
import org.iansaididontcare.etymonica.block.entity.enchantingtable.EnchantingTableTier1BlockEntity;
import org.iansaididontcare.etymonica.block.entity.jar.ZombieBrainInAJarBlockEntity;
import org.iansaididontcare.etymonica.block.entity.infusionaltar.InfusionAltarTier0BlockEntity;
import org.iansaididontcare.etymonica.block.entity.infusionaltar.PedestalBlockEntity;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Etymonica.MOD_ID);

    public static final Supplier<BlockEntityType<PedestalBlockEntity>> PEDESTAL_BE =
            BLOCK_ENTITIES.register("pedestal_be", () -> new BlockEntityType<>(
                    PedestalBlockEntity::new, ModBlocks.PEDESTAL.get()));

    public static final Supplier<BlockEntityType<EnchantingTableTier0BlockEntity>> ENCHANTING_TABLE_TIER0_BE =
            BLOCK_ENTITIES.register("enchanting_table_tier0_be", () -> new BlockEntityType<>(
                    EnchantingTableTier0BlockEntity::new,
                    ModBlocks.ENCHANTING_TABLE_TIER0.get()));

    public static final Supplier<BlockEntityType<EnchantingTableTier1BlockEntity>> ENCHANTING_TABLE_TIER1_BE =
            BLOCK_ENTITIES.register("enchanting_table_tier1_be", () -> new BlockEntityType<>(
                    EnchantingTableTier1BlockEntity::new,
                    ModBlocks.ENCHANTING_TABLE_TIER1.get()));

    public static final Supplier<BlockEntityType<InfusionAltarTier0BlockEntity>> INFUSION_ALTAR_TIER0_BE =
            BLOCK_ENTITIES.register("infusion_altar_tier0_be", () -> new BlockEntityType<>(
                    InfusionAltarTier0BlockEntity::new,
                    ModBlocks.INFUSION_ALTAR_TIER0.get()));

    public static final Supplier<BlockEntityType<ZombieBrainInAJarBlockEntity>> BRAIN_IN_A_JAR_BE =
            BLOCK_ENTITIES.register("brain_in_a_jar_be", () -> new BlockEntityType<>(
                    ZombieBrainInAJarBlockEntity::new,
                    ModBlocks.BRAIN_IN_A_JAR.get()));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
