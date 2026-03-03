package org.iansaididontcare.etymonica.registry.jar.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.registry.jar.api.JarTypeStats;
import org.iansaididontcare.etymonica.registry.jar.api.ZombieJarStats;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class JarTypeLoader {
    private static final Gson GSON = new Gson();
    private static final Identifier FILE_ID =
            Identifier.parse(Etymonica.MOD_ID + ":jars/jar_types.json");

    private JarTypeLoader() {}

    public static void load(ResourceManager resourceManager) {
        Map<Identifier, JarTypeStats> parsed = new HashMap<>();

        Optional<Resource> resourceOpt = resourceManager.getResource(FILE_ID);
        if (resourceOpt.isEmpty()) {
            Etymonica.LOGGER.warn("Missing {}, using default jar stats.", FILE_ID);
            JarData.setJarTypes(Map.of());
            return;
        }

        try (var reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) root = new JsonObject();

            JsonObject jarsObj = root.getAsJsonObject("jars");
            if (jarsObj == null) {
                Etymonica.LOGGER.warn("No 'jars' object in {}", FILE_ID);
                JarData.setJarTypes(Map.of());
                return;
            }

            for (Map.Entry<String, JsonElement> e : jarsObj.entrySet()) {
                Identifier jarId;
                try {
                    jarId = Identifier.parse(e.getKey());
                } catch (Exception ignored) {
                    continue;
                }

                if (!e.getValue().isJsonObject()) continue;
                JsonObject j = e.getValue().getAsJsonObject();

                int capacity = getInt(j, "capacity", 8000);
                Optional<ZombieJarStats> zombieStats = Optional.empty();

                if (j.has("zombie_special") && j.get("zombie_special").isJsonObject()) {
                    JsonObject z = j.getAsJsonObject("zombie_special");
                    zombieStats = Optional.of(new ZombieJarStats(
                        getInt(z, "xp_to_mb", 10),
                        getInt(z, "xp_per_drain", 10),
                        getInt(z, "interval", 20),
                        getDouble(z, "radius", 1.0d)
                    ));
                }

                parsed.put(jarId, new JarTypeStats(capacity, zombieStats));
            }

            JarData.setJarTypes(parsed);
            Etymonica.LOGGER.info("Loaded {} jar types from {}", parsed.size(), FILE_ID);
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
