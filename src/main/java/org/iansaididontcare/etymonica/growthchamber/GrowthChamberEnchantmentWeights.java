package org.iansaididontcare.etymonica.growthchamber;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.iansaididontcare.etymonica.Etymonica;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class GrowthChamberEnchantmentWeights {
    private static final Gson GSON = new Gson();
    private static final Identifier FILE_ID = Identifier.parse(Etymonica.MOD_ID + ":growth_chamber/enchantment_weights.json");

    private static volatile int defaultWeight = 1;
    private static volatile Map<Identifier, Integer> weights = Map.of();

    private GrowthChamberEnchantmentWeights() {}

    public static int getDefaultWeight() {
        return defaultWeight;
    }

    public static int getWeight(Identifier enchantmentId) {
        return weights.getOrDefault(enchantmentId, defaultWeight);
    }

    public static void load(ResourceManager resourceManager) {
        int newDefault = 1;
        Map<Identifier, Integer> newWeights = new HashMap<>();

        Optional<Resource> resourceOpt = resourceManager.getResource(FILE_ID);
        if (resourceOpt.isEmpty()) {
            Etymonica.LOGGER.warn("Missing {}, using defaults.", FILE_ID);
            defaultWeight = newDefault;
            weights = Map.copyOf(newWeights);
            return;
        }

        try (var reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) root = new JsonObject();

            if (root.has("defaultWeight")) {
                newDefault = Math.max(0, root.get("defaultWeight").getAsInt());
            }

            if (root.has("weights") && root.get("weights").isJsonObject()) {
                JsonObject w = root.getAsJsonObject("weights");
                for (Map.Entry<String, JsonElement> e : w.entrySet()) {
                    Identifier id = Identifier.parse(e.getKey());
                    int val = Math.max(0, e.getValue().getAsInt());
                    newWeights.put(id, val);
                }
            }

            defaultWeight = newDefault;
            weights = Map.copyOf(newWeights);

            Etymonica.LOGGER.info("Loaded Growth Chamber enchantment weights: {} entries, default={}", weights.size(), defaultWeight);
        } catch (Exception ex) {
            Etymonica.LOGGER.error("Failed to load {} (keeping previous values).", FILE_ID, ex);
        }
    }
}
