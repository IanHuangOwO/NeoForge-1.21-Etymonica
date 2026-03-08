package org.iansaididontcare.etymonica.registry.experience_tree;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.iansaididontcare.etymonica.Etymonica;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@EventBusSubscriber(modid = Etymonica.MOD_ID)
public final class ExperienceTreeReloadListeners {
    private static final Identifier TREE_LISTENER_ID = Identifier.parse(Etymonica.MOD_ID + ":experience_tree");

    private ExperienceTreeReloadListeners() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(TREE_LISTENER_ID, new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState state,
                                                  Executor backgroundExecutor,
                                                  PreparationBarrier barrier,
                                                  Executor gameExecutor) {
                return CompletableFuture
                        .supplyAsync(() -> {
                            ExperienceTreeStagesLoader.load(state.resourceManager());
                            return Unit.INSTANCE;
                        }, backgroundExecutor)
                        .thenCompose(barrier::wait)
                        .thenAcceptAsync(u -> { }, gameExecutor);
            }
        });
    }
}
