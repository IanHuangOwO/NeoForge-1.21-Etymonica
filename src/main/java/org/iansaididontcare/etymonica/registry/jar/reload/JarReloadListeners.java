package org.iansaididontcare.etymonica.registry.jar.reload;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.registry.jar.data.JarTypeLoader;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@EventBusSubscriber(modid = Etymonica.MOD_ID)
public final class JarReloadListeners {
    private static final Identifier JAR_LISTENER_ID =
            Identifier.parse(Etymonica.MOD_ID + ":jar_types");

    private JarReloadListeners() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(JAR_LISTENER_ID, new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState state,
                                                  Executor backgroundExecutor,
                                                  PreparationBarrier barrier,
                                                  Executor gameExecutor) {
                return CompletableFuture
                        .supplyAsync(() -> {
                            JarTypeLoader.load(state.resourceManager());
                            return Unit.INSTANCE;
                        }, backgroundExecutor)
                        .thenCompose(barrier::wait)
                        .thenAcceptAsync(u -> { }, gameExecutor);
            }
        });
    }
}
