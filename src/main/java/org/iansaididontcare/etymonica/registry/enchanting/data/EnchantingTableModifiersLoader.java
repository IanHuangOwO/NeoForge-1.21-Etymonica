package org.iansaididontcare.etymonica.registry.enchanting.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.registry.enchanting.api.EnchantingTableModifierStats;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class EnchantingTableModifiersLoader {
    private static final Gson GSON = new Gson();
    private static final Identifier FILE_ID =
            Identifier.parse(Etymonica.MOD_ID + ":enchanting_table/table_modifiers.json");

    private EnchantingTableModifiersLoader() {}

    public static void load(ResourceManager resourceManager) {
        Map<Identifier, EnchantingTableModifierStats> parsed = new HashMap<>();

        Optional<Resource> resourceOpt = resourceManager.getResource(FILE_ID);
        if (resourceOpt.isEmpty()) {
            Etymonica.LOGGER.warn("Missing {}, using empty modifiers.", FILE_ID);
            EnchantingTableData.setModifiers(Map.of());
            return;
        }

        try (var reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) root = new JsonObject();

            JsonObject modifiersObj = root.getAsJsonObject("modifiers");
            if (modifiersObj == null) {
                Etymonica.LOGGER.warn("No 'modifiers' object in {}", FILE_ID);
                EnchantingTableData.setModifiers(Map.of());
                return;
            }

            for (Map.Entry<String, JsonElement> e : modifiersObj.entrySet()) {
                Identifier blockId;
                try {
                    blockId = Identifier.parse(e.getKey());
                } catch (Exception ignored) {
                    continue;
                }

                if (!e.getValue().isJsonObject()) continue;
                JsonObject m = e.getValue().getAsJsonObject();

                float speed = getFloat(m, "speed", 0f);
                float stability = getFloat(m, "stability", 0f);
                float efficiency = getFloat(m, "efficiency", 0f);

                parsed.put(blockId, new EnchantingTableModifierStats(speed, stability, efficiency));
            }

            EnchantingTableData.setModifiers(parsed);
            Etymonica.LOGGER.info("Loaded enchanting table modifiers: {} entries", parsed.size());
        } catch (Exception ex) {
            Etymonica.LOGGER.error("Failed to load {} (keeping previous values).", FILE_ID, ex);
        }
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        JsonElement e = obj.get(key);
        return (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) ? e.getAsFloat() : def;
    }
}
