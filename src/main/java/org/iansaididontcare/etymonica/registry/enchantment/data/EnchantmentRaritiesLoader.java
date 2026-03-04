package org.iansaididontcare.etymonica.registry.enchantment.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.iansaididontcare.etymonica.Etymonica;
import org.iansaididontcare.etymonica.registry.enchantment.api.EnchantmentRarity;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class EnchantmentRaritiesLoader {
    private static final Gson GSON = new Gson();
    private static final Identifier FILE_ID =
            Identifier.parse(Etymonica.MOD_ID + ":enchantments/rarities.json");

    private EnchantmentRaritiesLoader() {}

    public static void load(ResourceManager resourceManager) {
        Map<Identifier, EnchantmentRarity> rarities = new HashMap<>();

        Optional<Resource> resourceOpt = resourceManager.getResource(FILE_ID);
        if (resourceOpt.isEmpty()) {
            Etymonica.LOGGER.warn("Missing {}, using default enchantment rarities.", FILE_ID);
            EnchantmentData.setEnchantmentRarities(Map.of());
            return;
        }

        try (var reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null) {
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    try {
                        Identifier id = Identifier.parse(e.getKey());
                        EnchantmentRarity rarity = EnchantmentRarity.valueOf(e.getValue().getAsString().toUpperCase());
                        rarities.put(id, rarity);
                    } catch (Exception ex) {
                        Etymonica.LOGGER.warn("Failed to parse rarity for {}: {}", e.getKey(), ex.getMessage());
                    }
                }
            }
            EnchantmentData.setEnchantmentRarities(rarities);
            Etymonica.LOGGER.info("Loaded {} enchantment rarities from {}", rarities.size(), FILE_ID);
        } catch (Exception ex) {
            Etymonica.LOGGER.error("Failed to load {} (keeping previous values).", FILE_ID, ex);
        }
    }
}
