package org.iansaididontcare.etymonica.fluid;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.block.ModBlocks;
import org.iansaididontcare.etymonica.item.ModItems;

public class ModFluids {
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, Etymonica.MOD_ID);

    public static final DeferredHolder<Fluid, FlowingFluid> LIQUID_EXPERIENCE;
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_LIQUID_EXPERIENCE;
    public static final BaseFlowingFluid.Properties LIQUID_EXPERIENCE_PROPERTIES;

    static {
        LIQUID_EXPERIENCE_PROPERTIES =
                new BaseFlowingFluid.Properties(
                        NeoForgeMod.LAVA_TYPE::value,
                        ModFluids::still,
                        ModFluids::flowing
                )
                        .bucket(ModItems.LIQUID_EXPERIENCE_BUCKET)
                        .block(ModBlocks.LIQUID_EXPERIENCE_BLOCK)
                        .slopeFindDistance(2)
                        .levelDecreasePerBlock(2);

        LIQUID_EXPERIENCE =
                FLUIDS.register("liquid_experience", () -> new BaseFlowingFluid.Source(LIQUID_EXPERIENCE_PROPERTIES));

        FLOWING_LIQUID_EXPERIENCE =
                FLUIDS.register("flowing_liquid_experience", () -> new BaseFlowingFluid.Flowing(LIQUID_EXPERIENCE_PROPERTIES));
    }

    public static void register(IEventBus eventBus) {
        FLUIDS.register(eventBus);
    }

    private static FlowingFluid still() {
        return LIQUID_EXPERIENCE.value();
    }

    private static FlowingFluid flowing() {
        return FLOWING_LIQUID_EXPERIENCE.value();
    }
}
