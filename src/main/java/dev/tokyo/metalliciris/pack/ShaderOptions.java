package dev.tokyo.metalliciris.pack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the options a pack declares and rewrites their {@code #define} lines. */
public final class ShaderOptions {
    private static final Pattern DEFINE = Pattern.compile(
            "^(\\s*)(//\\s*)?#define\\s+([A-Za-z_][A-Za-z0-9_]*)"
                    + "(?:[ \\t]+([^/\\s][^/]*?))?[ \\t]*(?://(.*))?$");
    private static final Pattern ALLOWED = Pattern.compile("\\[([^\\]]*)\\]");

    private ShaderOptions() {}

    private record Define(String indent, boolean commented, String name, String value, String trailing) {}

    /** Parses one line; returns {@code null} if it is not a user-adjustable define. */
    private static Define parse(String line) {
        Matcher m = DEFINE.matcher(line);
        if (!m.matches()) {
            return null;
        }
        boolean commented = m.group(2) != null;
        String value = m.group(4);
        if (commented && value != null) {
            return null; // an ordinary comment that happens to mention #define
        }
        return new Define(m.group(1), commented, m.group(3), value, m.group(5));
    }

    /** Discovers every option in the pack, keyed by name (first declaration wins). */
    public static Map<String, ShaderOption> discover(PackFiles files) {
        Map<String, ShaderOption> out = new LinkedHashMap<>();
        for (String path : files.paths()) {
            if (path.equals("shaders.properties") || path.endsWith(".properties") || path.endsWith(".txt")) {
                continue;
            }
            for (String line : files.get(path).replace("\r\n", "\n").split("\n", -1)) {
                Define d = parse(line);
                if (d == null || out.containsKey(d.name())) {
                    continue;
                }
                if (d.value() == null) {
                    out.put(d.name(), new ShaderOption(d.name(), ShaderOption.Type.BOOLEAN,
                            d.commented() ? "false" : "true", List.of("true", "false"), path));
                } else {
                    out.put(d.name(), new ShaderOption(d.name(), ShaderOption.Type.VALUE,
                            d.value(), allowedValues(d.trailing()), path));
                }
            }
        }
        return out;
    }

    private static List<String> allowedValues(String trailingComment) {
        if (trailingComment == null) {
            return List.of();
        }
        Matcher m = ALLOWED.matcher(trailingComment);
        if (!m.find() || m.group(1).isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String v : m.group(1).trim().split("\\s+")) {
            values.add(v);
        }
        return List.copyOf(values);
    }

    /**
     * Returns a copy of the pack where each overridden option's define line is rewritten.
     * Boolean overrides ({@code "true"}/{@code "false"}) toggle the comment; any other value
     * replaces the define's value. Options that are not present are ignored.
     */
    public static PackFiles apply(PackFiles files, Map<String, String> overrides) {
        if (overrides.isEmpty()) {
            return files;
        }
        Map<String, String> out = new TreeMap<>();
        for (String path : files.paths()) {
            String source = files.get(path);
            out.put(path, isCode(path) ? rewrite(source, overrides) : source);
        }
        return PackFiles.of(out);
    }

    private static boolean isCode(String path) {
        return !(path.endsWith(".properties") || path.endsWith(".txt"));
    }

    private static String rewrite(String source, Map<String, String> overrides) {
        String[] lines = source.replace("\r\n", "\n").split("\n", -1);
        StringBuilder sb = new StringBuilder(source.length());
        for (int i = 0; i < lines.length; i++) {
            Define d = parse(lines[i]);
            String override = d == null ? null : overrides.get(d.name());
            sb.append(override == null ? lines[i] : rebuild(d, override, lines[i]));
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String rebuild(Define d, String override, String original) {
        boolean isBoolean = d.value() == null;
        boolean boolOverride = override.equals("true") || override.equals("false");
        String tail = d.trailing() == null ? "" : " //" + d.trailing();
        if (isBoolean) {
            if (!boolOverride) {
                return original; // a value can't be applied to a bare define
            }
            return d.indent() + (override.equals("true") ? "" : "//") + "#define " + d.name() + tail;
        }
        return d.indent() + "#define " + d.name() + " " + override + tail;
    }
}
