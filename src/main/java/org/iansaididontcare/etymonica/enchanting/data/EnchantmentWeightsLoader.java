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
        Map<Identifier, Double> accumulationWeights = new HashMap<>();
        Map<Identifier, Double> drainWeights = new HashMap<>();

        Optional<Resource> resourceOpt = resourceManager.getResource(FILE_ID);
        if (resourceOpt.isEmpty()) {
            Etymonica.LOGGER.warn("Missing {}, using default enchantment weights.", FILE_ID);
            EnchantingTableData.setEnchantmentWeights(Map.of(), 1.0d, Map.of(), 1.0d);
            return;
        }

        try (var reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) root = new JsonObject();

            ParsedWeights accumulation = parseChannel(root.getAsJsonObject("accumulation"), root);
            ParsedWeights drain = parseChannel(root.getAsJsonObject("drain"), root);

            accumulationWeights.putAll(accumulation.weights());
            drainWeights.putAll(drain.weights());

            EnchantingTableData.setEnchantmentWeights(
                    accumulationWeights,
                    accumulation.defaultWeight(),
                    drainWeights,
                    drain.defaultWeight()
            );
            Etymonica.LOGGER.info(
                    "Loaded enchantment weights: accumulation={} (default={}), drain={} (default={})",
                    accumulationWeights.size(),
                    accumulation.defaultWeight(),
                    drainWeights.size(),
                    drain.defaultWeight()
            );
        } catch (Exception ex) {
            Etymonica.LOGGER.error("Failed to load {} (keeping previous values).", FILE_ID, ex);
        }
    }

    private static ParsedWeights parseChannel(JsonObject channelObj, JsonObject legacyRoot) {
        if (channelObj == null) {
            return parseLegacy(legacyRoot);
        }
        double defaultWeight = getDouble(channelObj, "defaultWeight", 1.0d);
        Map<Identifier, Double> parsed = parseWeightsObject(channelObj.getAsJsonObject("weights"), defaultWeight);
        return new ParsedWeights(parsed, defaultWeight);
    }

    private static ParsedWeights parseLegacy(JsonObject root) {
        double defaultWeight = getDouble(root, "defaultWeight", 1.0d);
        Map<Identifier, Double> parsed = parseWeightsObject(root.getAsJsonObject("weights"), defaultWeight);
        return new ParsedWeights(parsed, defaultWeight);
    }

    private static Map<Identifier, Double> parseWeightsObject(JsonObject weightsObj, double defaultWeight) {
        Map<Identifier, Double> parsed = new HashMap<>();
        if (weightsObj == null) {
            return parsed;
        }

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
        return parsed;
    }

    private record ParsedWeights(Map<Identifier, Double> weights, double defaultWeight) {}

    private static double getDouble(JsonObject obj, String key, double def) {
        JsonElement e = obj.get(key);
        return (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) ? e.getAsDouble() : def;
    }
}
