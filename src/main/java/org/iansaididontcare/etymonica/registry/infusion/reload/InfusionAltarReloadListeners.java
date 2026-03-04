package org.iansaididontcare.etymonica.registry.infusion.reload;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.registry.infusion.data.InfusionAltarTierLoader;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@EventBusSubscriber(modid = Etymonica.MOD_ID)
public final class InfusionAltarReloadListeners {
    private static final Identifier INFUSION_LISTENER_ID =
            Identifier.parse(Etymonica.MOD_ID + ":infusion_altar_tiers");

    private InfusionAltarReloadListeners() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(INFUSION_LISTENER_ID, new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState state,
                                                  Executor backgroundExecutor,
                                                  PreparationBarrier barrier,
                                                  Executor gameExecutor) {
                return CompletableFuture
                        .supplyAsync(() -> {
                            InfusionAltarTierLoader.load(state.resourceManager());
                            return Unit.INSTANCE;
                        }, backgroundExecutor)
                        .thenCompose(barrier::wait)
                        .thenAcceptAsync(u -> { }, gameExecutor);
            }
        });
    }
}
