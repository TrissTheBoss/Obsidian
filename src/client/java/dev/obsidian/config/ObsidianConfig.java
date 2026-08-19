package dev.obsidian.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record ObsidianConfig(
        boolean strictConflictCheck,
        boolean experimentalFeatures,
        boolean verboseCapabilityLog) {

    private static final String FILE_NAME = "obsidian.properties";

    public static ObsidianConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties props = defaults();

        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Obsidian could not read " + path, e);
            }
        } else {
            try {
                Files.createDirectories(path.getParent());
                try (OutputStream out = Files.newOutputStream(path)) {
                    props.store(out, "Obsidian Phase 0 bootstrap settings");
                }
            } catch (IOException e) {
                throw new IllegalStateException("Obsidian could not create " + path, e);
            }
        }

        return new ObsidianConfig(
                bool(props, "strictConflictCheck", true),
                bool(props, "experimentalFeatures", false),
                bool(props, "verboseCapabilityLog", true));
    }

    private static Properties defaults() {
        Properties p = new Properties();
        p.setProperty("strictConflictCheck", "true");
        p.setProperty("experimentalFeatures", "false");
        p.setProperty("verboseCapabilityLog", "true");
        return p;
    }

    private static boolean bool(Properties props, String key, boolean fallback) {
        String value = props.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }
}
