package org.iansaididontcare.etymonica.registry.experience_tree;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.iansaididontcare.etymonica.Etymonica;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ExperienceTreeStagesLoader {
    private static final Gson GSON = new Gson();
    private static final Identifier FILE_ID = Identifier.parse(Etymonica.MOD_ID + ":experience_tree/stages.json");

    private ExperienceTreeStagesLoader() {}

    public static void load(ResourceManager resourceManager) {
        List<ExperienceTreeData.ExperienceTreeStage> parsed = new ArrayList<>();

        Optional<Resource> resourceOpt = resourceManager.getResource(FILE_ID);
        if (resourceOpt.isEmpty()) {
            Etymonica.LOGGER.warn("Missing {}, using default stages.", FILE_ID);
            setDefaultStages();
            return;
        }

        try (var reader = new InputStreamReader(resourceOpt.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) root = new JsonObject();

            JsonArray stagesArr = root.getAsJsonArray("stages");
            if (stagesArr == null) {
                Etymonica.LOGGER.warn("No 'stages' array in {}", FILE_ID);
                setDefaultStages();
                return;
            }

            for (JsonElement e : stagesArr) {
                if (!e.isJsonObject()) continue;
                JsonObject s = e.getAsJsonObject();
                int threshold = s.has("threshold") ? s.get("threshold").getAsInt() : 0;
                String name = s.has("name") ? s.get("name").getAsString() : "unknown";
                parsed.add(new ExperienceTreeData.ExperienceTreeStage(threshold, name));
            }

            // Ensure they are sorted by threshold
            parsed.sort(Comparator.comparingInt(ExperienceTreeData.ExperienceTreeStage::threshold));
            ExperienceTreeData.setStages(parsed);
            Etymonica.LOGGER.info("Loaded {} experience tree stages.", parsed.size());
        } catch (Exception ex) {
            Etymonica.LOGGER.error("Failed to load {} (keeping previous values).", FILE_ID, ex);
        }
    }

    private static void setDefaultStages() {
        List<ExperienceTreeData.ExperienceTreeStage> defaults = new ArrayList<>();
        defaults.add(new ExperienceTreeData.ExperienceTreeStage(0, "seed"));
        defaults.add(new ExperienceTreeData.ExperienceTreeStage(1, "sprout"));
        defaults.add(new ExperienceTreeData.ExperienceTreeStage(15, "sapling"));
        defaults.add(new ExperienceTreeData.ExperienceTreeStage(30, "young_tree"));
        defaults.add(new ExperienceTreeData.ExperienceTreeStage(50, "tree"));
        defaults.add(new ExperienceTreeData.ExperienceTreeStage(100, "ancient_tree"));
        ExperienceTreeData.setStages(defaults);
    }
}
