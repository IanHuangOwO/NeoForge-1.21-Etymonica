package org.iansaididontcare.etymonica.registry.infusion.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.registry.infusion.api.EnchantmentTierWeights;
import org.iansaididontcare.etymonica.registry.infusion.api.InfusionAltarStats;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InfusionAltarTierLoader {
    private static final Gson GSON = new Gson();
    private static final Identifier FILE_ID = Identifier.parse(Etymonica.MOD_ID + ":infusion/altar_tiers.json");

    private InfusionAltarTierLoader() {}

    public static void load(ResourceManager resourceManager) {
        Map<String, InfusionAltarStats> parsed = new HashMap<>();

        Optional<Resource> resourceOpt = resourceManager.getResource(FILE_ID);
        if (resourceOpt.isEmpty()) {
            Etymonica.LOGGER.warn("Missing {}, using empty tier stats.", FILE_ID);
            InfusionAltarData.setAltarTiers(Map.of());
            return;
        }

        try (var reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) root = new JsonObject();

            JsonObject tiersObj = root.getAsJsonObject("altar_tiers");
            if (tiersObj == null) {
                Etymonica.LOGGER.warn("No 'altar_tiers' object in {}", FILE_ID);
                InfusionAltarData.setAltarTiers(Map.of());
                return;
            }

            for (Map.Entry<String, JsonElement> e : tiersObj.entrySet()) {
                String tierId = e.getKey();
                if (!e.getValue().isJsonObject()) continue;

                JsonObject t = e.getValue().getAsJsonObject();

                int itemsPerInfusion = getInt(t, "items_per_infusion", 1);
                double speed = getDouble(t, "speed", 0.1);
                double efficiency = getDouble(t, "efficiency", 0.0);
                int linkRadius = getInt(t, "link_radius", 4);
                int maxLinkedPedestals = getInt(t, "max_linked_pedestals", 16);

                EnchantmentTierWeights weights = EnchantmentTierWeights.DEFAULT;
                if (t.has("weights") && t.get("weights").isJsonObject()) {
                    JsonObject w = t.getAsJsonObject("weights");
                    weights = new EnchantmentTierWeights(
                        getDouble(w, "common", 0.5),
                        getDouble(w, "uncommon", 0.3),
                        getDouble(w, "rare", 0.15),
                        getDouble(w, "epic", 0.04),
                        getDouble(w, "legendary", 0.01),
                        getDouble(w, "mystic", 0.0)
                    );
                }

                int multiblockRadius = getInt(t, "multiblock_radius", 3);
                Identifier multiblockBlock = Identifier.parse(getString(t, "multiblock_block", "minecraft:gold_block"));

                parsed.put(tierId, new InfusionAltarStats(
                    itemsPerInfusion, speed, efficiency, linkRadius, maxLinkedPedestals, 
                    weights, multiblockRadius, multiblockBlock
                ));
            }

            InfusionAltarData.setAltarTiers(parsed);
            Etymonica.LOGGER.info("Loaded altar tiers: {}", parsed.keySet());
        } catch (Exception ex) {
            Etymonica.LOGGER.error("Failed to load {} (keeping previous values).", FILE_ID, ex);
        }
    }

    private static int getInt(JsonObject json, String key, int def) {
        return json.has(key) ? json.get(key).getAsInt() : def;
    }

    private static double getDouble(JsonObject json, String key, double def) {
        return json.has(key) ? json.get(key).getAsDouble() : def;
    }

    private static String getString(JsonObject json, String key, String def) {
        return json.has(key) ? json.get(key).getAsString() : def;
    }
}
