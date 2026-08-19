package dev.obsidian.compat;

import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

public final class ConflictDetector {
    private static final String[] HARD_CONFLICTS = {
            "sodium",
            "vulkanmod",
            "iris",
            "immediatelyfast",
            "entityculling",
            "moreculling"
    };

    private ConflictDetector() {}

    public static List<String> findLoadedConflicts() {
        FabricLoader loader = FabricLoader.getInstance();
        List<String> found = new ArrayList<>();
        for (String modId : HARD_CONFLICTS) {
            if (loader.isModLoaded(modId)) {
                found.add(modId);
            }
        }
        return List.copyOf(found);
    }
}
