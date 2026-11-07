package dev.tokyo.metalliciris.translate;

import dev.tokyo.metalliciris.pack.ShaderPack;
import dev.tokyo.metalliciris.translate.GlslTranslator.Uniform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds a Blaze3D post-effect chain (JSON plus translated shader files) from a pack's
 * prepare/deferred/composite/final programs.
 *
 * <p>Vanilla passes have exactly one output, so a pass that writes several colortex targets only
 * keeps the first; this is reported as a warning.
 */
public final class PostChainGenerator {
    public static final String NAMESPACE = "metallic_iris_metal";
    private static final Pattern ORDER = Pattern.compile("^(prepare|begin|deferred|composite)(\\d*)$");
    private static final Pattern COLORTEX = Pattern.compile("colortex(\\d+)");

    /**
     * @param json the post_effect JSON
     * @param shaders resource path (under assets/NAMESPACE/) to shader source
     * @param uniforms the loose uniforms of every pass (each pass has its own block with these values)
     * @param passes number of translated shaderpack passes
     */
    public record Chain(String json, Map<String, String> shaders, List<Uniform> uniforms, int passes,
            List<String> warnings) {}

    private static final Map<String, Integer> LEGACY = Map.of(
            "gcolor", 0, "gdepth", 1, "gnormal", 2, "composite", 3,
            "gaux1", 4, "gaux2", 5, "gaux3", 6, "gaux4", 7);

    private PostChainGenerator() {}

    public static Chain generate(ShaderPack pack) {
        List<String> warnings = new ArrayList<>();
        Map<String, String> shaders = new LinkedHashMap<>();
        Map<String, Uniform> allUniforms = new LinkedHashMap<>();
        List<String> passJson = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            targets.add("colortex" + i);
            targets.add("colortex" + i + "_swap");
        }

        passJson.add(blit("minecraft:main", "colortex0", false));

        int count = 0;
        for (ShaderPack.Program prog : ordered(pack)) {
            if (prog.fragment() == null) {
                continue;
            }
            GlslTranslator.Program t = GlslTranslator.translateProgram(prog.vertex(), prog.fragment());
            String name = prog.name();
            for (String w : t.warnings()) {
                warnings.add(name + ": " + w);
            }
            for (Uniform u : t.uniforms()) {
                allUniforms.putIfAbsent(u.name(), u);
            }
            shaders.put("shaders/post/iris_" + name + ".fsh", t.fragment());
            shaders.put("shaders/post/iris_" + name + ".vsh", t.vertex());

            boolean last = name.equals("final");
            int out = t.outputs().isEmpty() ? 0 : t.outputs().get(0);
            if (t.outputs().size() > 1) {
                warnings.add(name + ": writes " + t.outputs().size() + " targets, only colortex" + out + " is kept");
            }

            List<String> inputs = new ArrayList<>();
            List<String> reads = new ArrayList<>();
            for (String sampler : t.samplers()) {
                String target = resolve(sampler);
                if (target == null) {
                    warnings.add(name + ": sampler " + sampler + " is not available, reads black");
                    continue;
                }
                reads.add(target);
                inputs.add("{\"sampler_name\": \"" + sampler + "\", \"target\": \"" + target + "\""
                        + (sampler.startsWith("depthtex") ? ", \"use_depth_buffer\": true" : "") + "}");
            }
            String outTarget = last ? "minecraft:main" : "colortex" + out;
            boolean feedback = reads.contains(outTarget);
            String writeTo = feedback ? outTarget + "_swap" : outTarget;
            passJson.add("{\"vertex_shader\": \"" + NAMESPACE + ":post/iris_" + name + "\", "
                    + "\"fragment_shader\": \"" + NAMESPACE + ":post/iris_" + name + "\", "
                    + "\"inputs\": [" + String.join(", ", inputs) + "], \"output\": \"" + writeTo + "\""
                    + uniformsJson(t.uniforms()) + "}");
            if (feedback) {
                passJson.add(blit(writeTo, outTarget, false));
            }
            count++;
        }

        StringBuilder json = new StringBuilder("{\"targets\": {");
        for (int i = 0; i < targets.size(); i++) {
            json.append(i > 0 ? ", " : "").append('"').append(targets.get(i)).append("\": {}");
        }
        json.append("}, \"passes\": [").append(String.join(", ", passJson)).append("]}");
        return new Chain(json.toString(), shaders, List.copyOf(allUniforms.values()), count, warnings);
    }

    private static List<ShaderPack.Program> ordered(ShaderPack pack) {
        record Keyed(int stage, int index, ShaderPack.Program program) {}
        List<Keyed> list = new ArrayList<>();
        for (ShaderPack.Program p : pack.programs().values()) {
            if (p.dimension() != null && !p.dimension().isEmpty()) {
                continue;
            }
            String n = p.name();
            if (n.equals("final")) {
                list.add(new Keyed(9, 0, p));
                continue;
            }
            var m = ORDER.matcher(n);
            if (m.matches()) {
                int stage = switch (m.group(1)) {
                    case "begin" -> 0;
                    case "prepare" -> 1;
                    case "deferred" -> 2;
                    default -> 3;
                };
                list.add(new Keyed(stage, m.group(2).isEmpty() ? 0 : Integer.parseInt(m.group(2)), p));
            }
        }
        list.sort((a, b) -> a.stage != b.stage ? Integer.compare(a.stage, b.stage) : Integer.compare(a.index, b.index));
        return list.stream().map(Keyed::program).toList();
    }

    /** The target a pack sampler reads, or null if it has no equivalent. */
    static String resolve(String sampler) {
        var m = COLORTEX.matcher(sampler);
        if (m.matches()) {
            int i = Integer.parseInt(m.group(1));
            return i < 8 ? sampler : null;
        }
        if (sampler.startsWith("depthtex")) {
            return "minecraft:main";
        }
        Integer legacy = LEGACY.get(sampler);
        return legacy == null ? null : "colortex" + legacy;
    }

    private static String blit(String from, String to, boolean unused) {
        return "{\"vertex_shader\": \"minecraft:core/screenquad\", \"fragment_shader\": \"minecraft:post/blit\", "
                + "\"inputs\": [{\"sampler_name\": \"In\", \"target\": \"" + from + "\"}], \"output\": \"" + to + "\", "
                + "\"uniforms\": {\"BlitConfig\": [{\"name\": \"ColorModulate\", \"type\": \"vec4\", "
                + "\"value\": [1.0, 1.0, 1.0, 1.0]}]}}";
    }

    private static String uniformsJson(List<Uniform> uniforms) {
        if (uniforms.isEmpty()) {
            return "";
        }
        List<String> items = new ArrayList<>();
        for (Uniform u : uniforms) {
            String type = switch (u.type()) {
                case "mat4" -> "matrix4x4";
                case "float", "int", "vec2", "vec3", "vec4", "ivec3" -> u.type();
                default -> null;
            };
            if (type == null || u.arraySize() > 0) {
                continue;
            }
            items.add("{\"name\": \"" + u.name() + "\", \"type\": \"" + type + "\", \"value\": " + zero(u.type()) + "}");
        }
        return ", \"uniforms\": {\"" + GlslTranslator.UNIFORM_BLOCK + "\": [" + String.join(", ", items) + "]}";
    }

    private static String zero(String type) {
        return switch (type) {
            case "float" -> "0.0";
            case "int" -> "0";
            case "vec2" -> "[0.0, 0.0]";
            case "vec3" -> "[0.0, 0.0, 0.0]";
            case "vec4" -> "[0.0, 0.0, 0.0, 0.0]";
            case "ivec3" -> "[0, 0, 0]";
            default -> "[1.0,0.0,0.0,0.0, 0.0,1.0,0.0,0.0, 0.0,0.0,1.0,0.0, 0.0,0.0,0.0,1.0]";
        };
    }
}
