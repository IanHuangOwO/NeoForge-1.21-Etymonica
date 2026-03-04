package org.iansaididontcare.etymonica.registry.infusion.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.registry.infusion.api.InfusionAltarModifierStats;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InfusionAltarModifierLoader {
    private static final Gson GSON = new Gson();
    private static final Identifier FILE_ID =
            Identifier.parse(Etymonica.MOD_ID + ":infusion/altar_modifiers.json");

    private InfusionAltarModifierLoader() {}

    public static void load(ResourceManager resourceManager) {
        Map<Identifier, InfusionAltarModifierStats> parsed = new HashMap<>();

        Optional<Resource> resourceOpt = resourceManager.getResource(FILE_ID);
        if (resourceOpt.isEmpty()) {
            Etymonica.LOGGER.warn("Missing {}, using empty infusion altar modifiers.", FILE_ID);
            InfusionAltarData.setModifiers(Map.of());
            return;
        }

        try (var reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) root = new JsonObject();

            JsonObject modifiersObj = root.getAsJsonObject("modifiers");
            if (modifiersObj == null) {
                Etymonica.LOGGER.warn("No 'modifiers' object in {}", FILE_ID);
                InfusionAltarData.setModifiers(Map.of());
                return;
            }

            for (Map.Entry<String, JsonElement> e : modifiersObj.entrySet()) {
                try {
                    Identifier blockId = Identifier.parse(e.getKey());
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject m = e.getValue().getAsJsonObject();

                    double speed = getDouble(m, "speed", 0.0);
                    double efficiency = getDouble(m, "efficiency", 0.0);
                    int maxNum = getInt(m, "max_num", 0);

                    parsed.put(blockId, new InfusionAltarModifierStats(speed, efficiency, maxNum));
                } catch (Exception ex) {
                    Etymonica.LOGGER.warn("Failed to parse infusion altar modifier {}: {}", e.getKey(), ex.getMessage());
                }
            }

            InfusionAltarData.setModifiers(parsed);
            Etymonica.LOGGER.info("Loaded {} infusion altar modifiers from {}", parsed.size(), FILE_ID);
        } catch (Exception ex) {
            Etymonica.LOGGER.error("Failed to load {} (keeping previous values).", FILE_ID, ex);
        }
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
