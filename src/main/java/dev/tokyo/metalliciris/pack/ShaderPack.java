package dev.tokyo.metalliciris.pack;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed shaderpack: its properties plus every shader program it defines, with includes
 * already expanded. Programs are keyed by name, prefixed with the dimension folder when the
 * file lives in one (for example {@code composite} or {@code world-1/composite}).
 */
public final class ShaderPack {
    /** A program is a set of stage sources sharing a name, e.g. composite.vsh + composite.fsh. */
    public record Program(
            String key,
            String dimension,
            String name,
            String vertex,
            String geometry,
            String fragment,
            String compute) {

        public boolean isComputeOnly() {
            return compute != null && vertex == null && fragment == null;
        }
    }

    private static final Pattern PROGRAM_FILE = Pattern.compile(
            "^(?:(world-?\\d+)/)?"
                    + "((?:gbuffers_[a-z_]+)|(?:shadow(?:_[a-z]+)?)|(?:prepare\\d*)|(?:begin\\d*)"
                    + "|(?:deferred\\d*)|(?:composite\\d*)|(?:final))"
                    + "\\.(vsh|fsh|gsh|csh)$");

    private final ShaderProperties properties;
    private final Map<String, Program> programs;

    private ShaderPack(ShaderProperties properties, Map<String, Program> programs) {
        this.properties = properties;
        this.programs = Collections.unmodifiableMap(programs);
    }

    public static ShaderPack loadDirectory(Path packRoot) {
        return load(PackFiles.fromDirectory(packRoot));
    }

    public static ShaderPack loadZip(Path zip) {
        return load(PackFiles.fromZip(zip));
    }

    public static ShaderPack load(PackFiles files) {
        String props = files.get("shaders.properties");
        ShaderProperties properties = props == null ? ShaderProperties.EMPTY : ShaderProperties.parse(props);
        IncludeResolver resolver = new IncludeResolver(files);

        // key -> [vertex, geometry, fragment, compute] source paths
        Map<String, String[]> stages = new TreeMap<>();
        Map<String, String[]> keyParts = new TreeMap<>(); // key -> [dimension, name]
        for (String path : files.paths()) {
            Matcher m = PROGRAM_FILE.matcher(path);
            if (!m.matches()) {
                continue;
            }
            String dimension = m.group(1) == null ? "" : m.group(1);
            String name = m.group(2);
            String key = dimension.isEmpty() ? name : dimension + "/" + name;
            String[] slots = stages.computeIfAbsent(key, k -> new String[4]);
            keyParts.put(key, new String[] {dimension, name});
            switch (m.group(3)) {
                case "vsh" -> slots[0] = path;
                case "gsh" -> slots[1] = path;
                case "fsh" -> slots[2] = path;
                default -> slots[3] = path;
            }
        }

        Map<String, Program> programs = new TreeMap<>();
        for (Map.Entry<String, String[]> e : stages.entrySet()) {
            String[] s = e.getValue();
            String[] parts = keyParts.get(e.getKey());
            programs.put(e.getKey(), new Program(
                    e.getKey(), parts[0], parts[1],
                    s[0] == null ? null : resolver.resolve(s[0]),
                    s[1] == null ? null : resolver.resolve(s[1]),
                    s[2] == null ? null : resolver.resolve(s[2]),
                    s[3] == null ? null : resolver.resolve(s[3])));
        }
        return new ShaderPack(properties, programs);
    }

    public ShaderProperties properties() {
        return properties;
    }

    public Map<String, Program> programs() {
        return programs;
    }

    /** Returns the program for the key, or {@code null} if the pack does not define it. */
    public Program program(String key) {
        return programs.get(key);
    }
}
