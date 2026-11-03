package dev.tokyo.metalliciris.pack;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inlines {@code #include "file"} directives. A path starting with {@code /} is relative to the
 * pack's {@code shaders/} root; any other path is relative to the including file.
 */
public final class IncludeResolver {
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");
    private static final int MAX_DEPTH = 64;

    private final PackFiles files;

    public IncludeResolver(PackFiles files) {
        this.files = files;
    }

    /** Returns the file at {@code path} with every include expanded recursively. */
    public String resolve(String path) {
        return expand(path, new ArrayDeque<>());
    }

    private String expand(String path, Deque<String> stack) {
        if (stack.size() >= MAX_DEPTH) {
            throw new ShaderPackException("Includes nested more than " + MAX_DEPTH + " deep at " + path);
        }
        if (stack.contains(path)) {
            throw new ShaderPackException(
                    "Circular #include: " + String.join(" -> ", stack.reversed()) + " -> " + path);
        }
        String source = files.get(path);
        if (source == null) {
            String from = stack.isEmpty() ? "" : " (included from " + stack.peek() + ")";
            throw new ShaderPackException("Missing shader file " + path + from);
        }

        stack.push(path);
        StringBuilder out = new StringBuilder(source.length());
        String[] lines = source.replace("\r\n", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = INCLUDE.matcher(lines[i]);
            if (m.matches()) {
                out.append(expand(target(path, m.group(1)), stack));
            } else {
                out.append(lines[i]);
            }
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        stack.pop();
        return out.toString();
    }

    private static String target(String includingFile, String include) {
        String normalized;
        if (include.startsWith("/")) {
            normalized = Path.of(include.substring(1)).normalize().toString();
        } else {
            Path dir = Path.of(includingFile).getParent();
            Path base = dir == null ? Path.of("") : dir;
            normalized = base.resolve(include).normalize().toString();
        }
        normalized = normalized.replace('\\', '/');
        if (normalized.startsWith("..") || normalized.isEmpty()) {
            throw new ShaderPackException(
                    "#include \"" + include + "\" in " + includingFile + " escapes the shaders folder");
        }
        return normalized;
    }
}
