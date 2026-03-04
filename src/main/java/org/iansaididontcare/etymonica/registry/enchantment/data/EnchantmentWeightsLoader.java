package org.iansaididontcare.etymonica.registry.enchantment.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.registry.enchantment.api.EnchantmentRarity;
import org.iansaididontcare.etymonica.registry.enchantment.api.EnchantmentWeightStats;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

public final class EnchantmentWeightsLoader {
    private static final Gson GSON = new Gson();
    private static final Identifier FILE_ID =
            Identifier.parse(Etymonica.MOD_ID + ":enchantments/weights.json");

    private EnchantmentWeightsLoader() {}

    public static void load(ResourceManager resourceManager) {
        Map<EnchantmentRarity, EnchantmentWeightStats> rarityDefaults = new HashMap<>();
        Map<Identifier, Double> powerOverrides = new HashMap<>();
        Map<Identifier, Double> drainOverrides = new HashMap<>();
        Map<Identifier, EnchantmentRarity> rarityOverrides = new HashMap<>();

        Optional<Resource> resourceOpt = resourceManager.getResource(FILE_ID);
        if (resourceOpt.isEmpty()) {
            Etymonica.LOGGER.warn("Missing {}, using hardcoded defaults.", FILE_ID);
            // Fallback would happen in EnchantmentData if we don't set anything here
            return;
        }

        try (var reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) root = new JsonObject();

            // 1. Parse Rarity Defaults
            if (root.has("rarity_defaults") && root.get("rarity_defaults").isJsonObject()) {
                JsonObject defaultsObj = root.getAsJsonObject("rarity_defaults");
                for (Map.Entry<String, JsonElement> e : defaultsObj.entrySet()) {
                    try {
                        EnchantmentRarity rarity = EnchantmentRarity.valueOf(e.getKey().toUpperCase());
                        JsonObject stats = e.getValue().getAsJsonObject();
                        double power = getDouble(stats, "power", 1.0);
                        double drain = getDouble(stats, "drain", 1.0);
                        rarityDefaults.put(rarity, new EnchantmentWeightStats(power, drain, rarity));
                    } catch (Exception ex) {
                        Etymonica.LOGGER.warn("Failed to parse rarity default for {}: {}", e.getKey(), ex.getMessage());
                    }
                }
            }

            // 2. Parse Overrides
            if (root.has("overrides") && root.get("overrides").isJsonObject()) {
                JsonObject overridesObj = root.getAsJsonObject("overrides");
                
                ParsedChannel powerChannel = parseChannel(overridesObj.getAsJsonObject("power"));
                powerOverrides.putAll(powerChannel.weights());

                ParsedChannel drainChannel = parseChannel(overridesObj.getAsJsonObject("drain"));
                drainOverrides.putAll(drainChannel.weights());

                if (overridesObj.has("rarities") && overridesObj.get("rarities").isJsonObject()) {
                    JsonObject raritiesObj = overridesObj.getAsJsonObject("rarities");
                    for (Map.Entry<String, JsonElement> e : raritiesObj.entrySet()) {
                        try {
                            Identifier id = Identifier.parse(e.getKey());
                            rarityOverrides.put(id, EnchantmentRarity.valueOf(e.getValue().getAsString().toUpperCase()));
                        } catch (Exception ignored) {}
                    }
                }
            }

            // 3. Merge and set in EnchantmentData
            Map<Identifier, EnchantmentWeightStats> merged = new HashMap<>();
            
            // Collect all unique IDs that have overrides
            Set<Identifier> overrideIds = new HashSet<>();
            overrideIds.addAll(powerOverrides.keySet());
            overrideIds.addAll(drainOverrides.keySet());
            overrideIds.addAll(rarityOverrides.keySet());
            
            for (Identifier id : overrideIds) {
                EnchantmentRarity rarity = rarityOverrides.getOrDefault(id, EnchantmentData.getEnchantmentRarity(id));
                EnchantmentWeightStats def = rarityDefaults.getOrDefault(rarity, EnchantmentWeightStats.DEFAULT);
                
                double power = powerOverrides.getOrDefault(id, def.power());
                double drain = drainOverrides.getOrDefault(id, def.drain());
                
                merged.put(id, new EnchantmentWeightStats(power, drain, rarity));
            }

            EnchantmentData.setRarityDefaults(rarityDefaults);
            EnchantmentData.setEnchantmentWeights(merged, EnchantmentWeightStats.DEFAULT);
            
            Etymonica.LOGGER.info("Loaded {} enchantment rarity defaults and {} explicit overrides from {}", rarityDefaults.size(), merged.size(), FILE_ID);
        } catch (Exception ex) {
            Etymonica.LOGGER.error("Failed to load {} (keeping previous values).", FILE_ID, ex);
        }
    }

    private static ParsedChannel parseChannel(JsonObject channelObj) {
        Map<Identifier, Double> parsed = new HashMap<>();
        if (channelObj == null) {
            return new ParsedChannel(parsed);
        }

        for (Map.Entry<String, JsonElement> e : channelObj.entrySet()) {
            try {
                Identifier id = Identifier.parse(e.getKey());
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) {
                    parsed.put(id, e.getValue().getAsDouble());
                }
            } catch (Exception ignored) {}
        }
        return new ParsedChannel(parsed);
    }

    private record ParsedChannel(Map<Identifier, Double> weights) {}

    private static double getDouble(JsonObject obj, String key, double def) {
        JsonElement e = obj.get(key);
        return (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) ? e.getAsDouble() : def;
    }
}
