package org.iansaididontcare.etymonica.enchanting.reload;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.enchanting.data.EnchantmentWeightsLoader;
import org.iansaididontcare.etymonica.enchanting.data.EnchantingTableModifiersLoader;
import org.iansaididontcare.etymonica.enchanting.data.EnchantingTableTiersLoader;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@EventBusSubscriber(modid = Etymonica.MOD_ID)
public final class EnchantingTableReloadListeners {
    private static final Identifier TIERS_LISTENER_ID =
            Identifier.parse(Etymonica.MOD_ID + ":enchanting_table_tiers");

    private static final Identifier MODIFIERS_LISTENER_ID =
            Identifier.parse(Etymonica.MOD_ID + ":enchanting_table_modifiers");
    private static final Identifier ENCHANTMENT_WEIGHTS_LISTENER_ID =
            Identifier.parse(Etymonica.MOD_ID + ":enchantment_weights");

    private EnchantingTableReloadListeners() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(TIERS_LISTENER_ID, new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState state,
                                                  Executor backgroundExecutor,
                                                  PreparationBarrier barrier,
                                                  Executor gameExecutor) {
                return CompletableFuture
                        .supplyAsync(() -> {
                            EnchantingTableTiersLoader.load(state.resourceManager());
                            return Unit.INSTANCE;
                        }, backgroundExecutor)
                        .thenCompose(barrier::wait)
                        .thenAcceptAsync(u -> { }, gameExecutor);
            }
        });

        event.addListener(MODIFIERS_LISTENER_ID, new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState state,
                                                  Executor backgroundExecutor,
                                                  PreparationBarrier barrier,
                                                  Executor gameExecutor) {
                return CompletableFuture
                        .supplyAsync(() -> {
                            EnchantingTableModifiersLoader.load(state.resourceManager());
                            return Unit.INSTANCE;
                        }, backgroundExecutor)
                        .thenCompose(barrier::wait)
                        .thenAcceptAsync(u -> { }, gameExecutor);
            }
        });

        event.addListener(ENCHANTMENT_WEIGHTS_LISTENER_ID, new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(SharedState state,
                                                  Executor backgroundExecutor,
                                                  PreparationBarrier barrier,
                                                  Executor gameExecutor) {
                return CompletableFuture
                        .supplyAsync(() -> {
                            EnchantmentWeightsLoader.load(state.resourceManager());
                            return Unit.INSTANCE;
                        }, backgroundExecutor)
                        .thenCompose(barrier::wait)
                        .thenAcceptAsync(u -> { }, gameExecutor);
            }
        });
    }
}
