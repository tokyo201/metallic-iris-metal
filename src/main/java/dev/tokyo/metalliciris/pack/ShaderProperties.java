package dev.tokyo.metalliciris.pack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parser for a pack's {@code shaders.properties}: {@code key=value} lines, {@code #} comment
 * lines, and a trailing backslash to continue a value on the next line.
 */
public final class ShaderProperties {
    public static final ShaderProperties EMPTY = new ShaderProperties(Map.of());

    private final Map<String, String> values;

    private ShaderProperties(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static ShaderProperties parse(String source) {
        Map<String, String> out = new LinkedHashMap<>();
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder pending = null;
        for (String raw : lines) {
            String line = raw;
            if (pending == null) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                pending = new StringBuilder();
                line = trimmed;
            } else {
                line = line.strip();
            }
            boolean continued = line.endsWith("\\");
            if (continued) {
                line = line.substring(0, line.length() - 1);
            }
            pending.append(line);
            if (!continued) {
                put(out, pending.toString());
                pending = null;
            } else {
                pending.append(' ');
            }
        }
        if (pending != null) {
            put(out, pending.toString());
        }
        return new ShaderProperties(out);
    }

    private static void put(Map<String, String> out, String entry) {
        int eq = entry.indexOf('=');
        if (eq <= 0) {
            return; // not a key=value line
        }
        String key = entry.substring(0, eq).strip();
        String value = entry.substring(eq + 1).strip();
        if (!key.isEmpty()) {
            out.put(key, value);
        }
    }

    public String get(String key) {
        return values.get(key);
    }

    public boolean getBoolean(String key, boolean fallback) {
        String v = values.get(key);
        return v == null ? fallback : v.equalsIgnoreCase("true");
    }

    public int getInt(String key, int fallback) {
        String v = values.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public Map<String, String> asMap() {
        return values;
    }
}
