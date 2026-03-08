package org.iansaididontcare.etymonica.registry.experience_tree;

import java.util.Collections;
import java.util.List;

public final class ExperienceTreeData {
    private static List<ExperienceTreeStage> stages = Collections.emptyList();

    public static void setStages(List<ExperienceTreeStage> newStages) {
        stages = newStages;
    }

    public static List<ExperienceTreeStage> getStages() {
        return stages;
    }

    public record ExperienceTreeStage(int threshold, String stageName) {}
}
