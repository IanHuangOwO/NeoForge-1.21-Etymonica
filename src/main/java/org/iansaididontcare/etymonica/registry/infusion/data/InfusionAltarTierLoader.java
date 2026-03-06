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
import org.iansaididontcare.etymonica.registry.infusion.api.MultiblockStructure;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class InfusionAltarTierLoader {
    private static final Gson GSON = new Gson();
    private static final Identifier FILE_ID =
            Identifier.parse(Etymonica.MOD_ID + ":infusion/altar_tiers.json");

    private InfusionAltarTierLoader() {}

    public static void load(ResourceManager resourceManager) {
        Map<String, InfusionAltarStats> parsed = new HashMap<>();

        Optional<Resource> resourceOpt = resourceManager.getResource(FILE_ID);
        if (resourceOpt.isEmpty()) {
            Etymonica.LOGGER.warn("Missing {}, using default infusion altar stats.", FILE_ID);
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
                if (t.has("enchantment_weights") && t.get("enchantment_weights").isJsonObject()) {
                    JsonObject w = t.getAsJsonObject("enchantment_weights");
                    weights = new EnchantmentTierWeights(
                        getDouble(w, "common", 0.6),
                        getDouble(w, "uncommon", 0.3),
                        getDouble(w, "rare", 0.1),
                        getDouble(w, "epic", 0.0),
                        getDouble(w, "legendary", 0.0),
                        getDouble(w, "mystic", 0.0)
                    );
                }

                MultiblockStructure structure = MultiblockStructure.DEFAULT;
                if (t.has("multiblock_structure") && t.get("multiblock_structure").isJsonObject()) {
                    JsonObject s = t.getAsJsonObject("multiblock_structure");
                    structure = new MultiblockStructure(
                        getInt(s, "offset_y", 3),
                        parseLayer(s.getAsJsonArray("bottom")),
                        parseLayer(s.getAsJsonArray("middle")),
                        parseLayer(s.getAsJsonArray("top"))
                    );
                }

                parsed.put(tierId, new InfusionAltarStats(itemsPerInfusion, speed, efficiency, linkRadius, maxLinkedPedestals, weights, structure));
            }

            InfusionAltarData.setAltarTiers(parsed);
            Etymonica.LOGGER.info("Loaded {} infusion altar tiers from {}", parsed.size(), FILE_ID);
        } catch (Exception ex) {
            Etymonica.LOGGER.error("Failed to load {} (keeping previous values).", FILE_ID, ex);
        }
    }

    private static List<List<String>> parseLayer(com.google.gson.JsonArray array) {
        if (array == null) return MultiblockStructure.DEFAULT.bottom();
        List<List<String>> layer = new ArrayList<>();
        for (JsonElement rowElement : array) {
            if (rowElement.isJsonArray()) {
                List<String> row = new ArrayList<>();
                for (JsonElement cell : rowElement.getAsJsonArray()) {
                    row.add(cell.getAsString());
                }
                layer.add(row);
            }
        }
        return layer;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        JsonElement e = obj.get(key);
        return (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) ? e.getAsInt() : def;
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        JsonElement e = obj.get(key);
        return (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) ? e.getAsDouble() : def;
    }
}
