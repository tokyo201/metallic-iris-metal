package dev.tokyo.metalliciris.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Map;

/**
 * Reads and writes {@link ShaderSettings} as a {@code .properties} file:
 *
 * <pre>
 * enabled=true
 * activePack=MyPack.zip
 * pack.OtherPack.enabled=false
 * option.MyPack.zip.SHADOW_QUALITY=2
 * </pre>
 */
public final class ShaderSettingsStore {
    private static final String PACK_PREFIX = "pack.";
    private static final String PACK_SUFFIX = ".enabled";
    private static final String OPTION_PREFIX = "option.";

    private ShaderSettingsStore() {}

    /** Loads settings, returning defaults if the file is missing or unreadable. */
    public static ShaderSettings load(Path file) {
        ShaderSettings settings = new ShaderSettings();
        if (!Files.isRegularFile(file)) {
            return settings;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException | IllegalArgumentException e) {
            return new ShaderSettings();
        }

        settings.setEnabled(Boolean.parseBoolean(props.getProperty("enabled", "false")));
        settings.setTestEffect(Boolean.parseBoolean(props.getProperty("testEffect", "false")));
        String active = props.getProperty("activePack");
        settings.setActivePack(active == null || active.isBlank() ? null : active);

        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            if (key.startsWith(PACK_PREFIX) && key.endsWith(PACK_SUFFIX)
                    && key.length() > PACK_PREFIX.length() + PACK_SUFFIX.length()) {
                String pack = key.substring(PACK_PREFIX.length(), key.length() - PACK_SUFFIX.length());
                settings.setPackEnabled(pack, Boolean.parseBoolean(value));
            } else if (key.startsWith(OPTION_PREFIX)) {
                // Option names are GLSL identifiers (no dots), so split at the last dot.
                String rest = key.substring(OPTION_PREFIX.length());
                int dot = rest.lastIndexOf('.');
                if (dot > 0 && dot < rest.length() - 1) {
                    settings.setOption(rest.substring(0, dot), rest.substring(dot + 1), value);
                }
            }
        }
        // A pack listed as disabled was already deselected by setPackEnabled(false) above,
        // so a hand-edited file can't leave a disabled pack active.
        return settings;
    }

    /** Writes settings atomically (temp file then move). */
    public static void save(Path file, ShaderSettings settings) {
        Map<String, String> entries = new TreeMap<>();
        entries.put("enabled", Boolean.toString(settings.isEnabled()));
        entries.put("testEffect", Boolean.toString(settings.isTestEffect()));
        if (settings.getActivePack() != null) {
            entries.put("activePack", settings.getActivePack());
        }
        for (String pack : settings.disabledPacks()) {
            entries.put(PACK_PREFIX + pack + PACK_SUFFIX, "false");
        }
        for (String pack : settings.packsWithOverrides()) {
            for (Map.Entry<String, String> e : settings.optionOverrides(pack).entrySet()) {
                entries.put(OPTION_PREFIX + pack + "." + e.getKey(), e.getValue());
            }
        }
        Properties props = new Properties();
        props.putAll(entries);

        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, "shader-settings", ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                props.store(out, "Metallic Iris Metal shader settings");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save shader settings to " + file, e);
        }
    }
}
