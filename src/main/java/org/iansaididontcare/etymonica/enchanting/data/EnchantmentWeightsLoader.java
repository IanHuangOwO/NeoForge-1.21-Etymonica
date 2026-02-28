package org.iansaididontcare.etymonica.enchanting.data;

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

public final class EnchantmentWeightsLoader {
    private static final Gson GSON = new Gson();
    private static final Identifier FILE_ID =
            Identifier.parse(Etymonica.MOD_ID + ":enchanting_table/enchantment_weights.json");

    private EnchantmentWeightsLoader() {}

    public static void load(ResourceManager resourceManager) {
        Map<Identifier, Double> parsed = new HashMap<>();

        Optional<Resource> resourceOpt = resourceManager.getResource(FILE_ID);
        if (resourceOpt.isEmpty()) {
            Etymonica.LOGGER.warn("Missing {}, using default enchantment weights.", FILE_ID);
            EnchantingTableData.setEnchantmentWeights(Map.of(), 1.0d);
            return;
        }

        try (var reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) root = new JsonObject();

            double defaultWeight = getDouble(root, "defaultWeight", 1.0d);

            JsonObject weightsObj = root.getAsJsonObject("weights");
            if (weightsObj != null) {
                for (Map.Entry<String, JsonElement> e : weightsObj.entrySet()) {
                    Identifier enchantmentId;
                    try {
                        enchantmentId = Identifier.parse(e.getKey());
                    } catch (Exception ignored) {
                        continue;
                    }

                    double weight = getDouble(weightsObj, e.getKey(), defaultWeight);
                    parsed.put(enchantmentId, Math.max(0.0d, weight));
                }
            }

            EnchantingTableData.setEnchantmentWeights(parsed, defaultWeight);
            Etymonica.LOGGER.info("Loaded enchantment weights: {} entries (default={})", parsed.size(), defaultWeight);
        } catch (Exception ex) {
            Etymonica.LOGGER.error("Failed to load {} (keeping previous values).", FILE_ID, ex);
        }
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        JsonElement e = obj.get(key);
        return (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) ? e.getAsDouble() : def;
    }
}
