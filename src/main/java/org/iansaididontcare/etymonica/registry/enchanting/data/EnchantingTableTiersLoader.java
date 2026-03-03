package org.iansaididontcare.etymonica.registry.enchanting.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.registry.enchanting.api.EnchantingTableStats;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class EnchantingTableTiersLoader {
    private static final Gson GSON = new Gson();
    private static final Identifier FILE_ID =
            Identifier.parse(Etymonica.MOD_ID + ":enchanting_table/table_tiers.json");

    private EnchantingTableTiersLoader() {}

    public static void load(ResourceManager resourceManager) {
        Map<String, EnchantingTableStats> parsed = new HashMap<>();

        Optional<Resource> resourceOpt = resourceManager.getResource(FILE_ID);
        if (resourceOpt.isEmpty()) {
            Etymonica.LOGGER.warn("Missing {}, using empty tier stats.", FILE_ID);
            EnchantingTableData.setTiers(Map.of());
            return;
        }

        try (var reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) root = new JsonObject();

            JsonObject tiersObj = root.getAsJsonObject("table_tiers");
            if (tiersObj == null) {
                Etymonica.LOGGER.warn("No 'table_tiers' object in {}", FILE_ID);
                EnchantingTableData.setTiers(Map.of());
                return;
            }

            for (Map.Entry<String, JsonElement> e : tiersObj.entrySet()) {
                String tierId = e.getKey();
                if (!e.getValue().isJsonObject()) continue;

                JsonObject t = e.getValue().getAsJsonObject();

                int powerCap = getInt(t, "enchanting_power_cap", 0);
                int linkRadius = getInt(t, "link_radius", 0);
                int maxTierEnchant = getInt(t, "max_tier_enchantment", 0);
                int maxLinkedModifiers = getInt(t, "max_linked_modifiers", 128);

                float speed = getFloat(t, "speed", 0f);
                float stability = getFloat(t, "stability", 0f);
                float efficiency = getFloat(t, "efficiency", 0f);

                parsed.put(tierId, new EnchantingTableStats(
                        powerCap, linkRadius, maxTierEnchant, maxLinkedModifiers,
                        speed, stability, efficiency
                ));
            }

            EnchantingTableData.setTiers(parsed);
            Etymonica.LOGGER.info("Loaded enchanting table tiers: {}", parsed.keySet());
        } catch (Exception ex) {
            Etymonica.LOGGER.error("Failed to load {} (keeping previous values).", FILE_ID, ex);
        }
    }

    private static int getInt(JsonObject obj, String key, int def) {
        JsonElement e = obj.get(key);
        return (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) ? e.getAsInt() : def;
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        JsonElement e = obj.get(key);
        return (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) ? e.getAsFloat() : def;
    }
}
