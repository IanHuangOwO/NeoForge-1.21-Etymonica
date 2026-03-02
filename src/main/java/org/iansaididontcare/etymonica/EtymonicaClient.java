package org.iansaididontcare.etymonica;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.iansaididontcare.etymonica.block.entity.ModBlockEntities;
import org.iansaididontcare.etymonica.block.entity.renderer.EnchantingTableBlockEntityRenderer;
import org.iansaididontcare.etymonica.block.entity.renderer.PedestalBlockEntityRenderer;
import org.iansaididontcare.etymonica.fluid.ModFluids;
import org.iansaididontcare.etymonica.screen.ModMenuTypes;
import org.iansaididontcare.etymonica.screen.custom.EnchantingTableScreen;
import org.iansaididontcare.etymonica.screen.custom.PedestalScreen;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Etymonica.MOD_ID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = Etymonica.MOD_ID, value = Dist.CLIENT)
public class EtymonicaClient {
    public EtymonicaClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModFluids.LIQUID_EXPERIENCE.get(), ChunkSectionLayer.TRANSLUCENT);
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_LIQUID_EXPERIENCE.get(), ChunkSectionLayer.TRANSLUCENT);
        });

        Etymonica.LOGGER.info("HELLO FROM CLIENT SETUP");
        Etymonica.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    public static void registerBER(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PEDESTAL_BE.get(), PedestalBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ENCHANTING_TABLE_TIER0_BE.get(), EnchantingTableBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ENCHANTING_TABLE_TIER1_BE.get(), EnchantingTableBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.PEDESTAL_MENU.get(), PedestalScreen::new);
        event.register(ModMenuTypes.ENCHANTING_TABLE_MENU.get(), EnchantingTableScreen::new);
    }
}
