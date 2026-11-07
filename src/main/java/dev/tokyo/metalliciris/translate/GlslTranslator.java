package dev.tokyo.metalliciris.translate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates OptiFine/Iris-style shaderpack GLSL (legacy 110-150 or modern compatibility profile)
 * into the form Minecraft's post-effect system accepts: core GLSL where loose uniforms live in one
 * std140 block, samplers are named {@code <name>Sampler}, and outputs use explicit locations.
 *
 * <p>It targets the fullscreen programs (prepare, begin, deferred, composite, final). Geometry
 * programs (gbuffers, shadow) use inputs this does not provide and are out of scope here.
 */
public final class GlslTranslator {
    public enum Stage { VERTEX, FRAGMENT }

    /** A non-sampler uniform: {@code arraySize} is 0 for a plain value. */
    public record Uniform(String type, String name, int arraySize) {}

    /**
     * @param vertex vertex shader source
     * @param fragment fragment shader source
     * @param uniforms every loose uniform, in the order they appear in the uniform block
     * @param samplers sampler names as the pack wrote them (bind each as {@code <name>Sampler})
     * @param outputs the render target id each fragment output location writes to
     * @param warnings things that were not translated and may fail to compile
     */
    public record Program(String vertex, String fragment, List<Uniform> uniforms, List<String> samplers,
            List<Integer> outputs, List<String> warnings) {}

    public static final String UNIFORM_BLOCK = "IrisUniforms";
    private static final int MIN_VERSION = 330;

    private static final String VERTEX_POS =
            "vec4(vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)) * 2.0 - 1.0, 0.0, 1.0)";
    private static final String VERTEX_UV =
            "vec4(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2), 0.0, 1.0)";

    private static final Pattern VERSION = Pattern.compile("(?m)^[ \\t]*#version[ \\t]+(\\d+)[^\n]*$");
    private static final Pattern EXTENSION = Pattern.compile("(?m)^[ \\t]*#extension[^\n]*$");
    private static final Pattern TARGETS = Pattern.compile(
            "/\\*\\s*(?:DRAWBUFFERS|RENDERTARGETS)\\s*:\\s*([0-9A-Za-z,\\s]+?)\\s*\\*/");
    private static final Pattern UNIFORM_DECL = Pattern.compile(
            "\\buniform\\s+((?:(?:highp|mediump|lowp|flat|smooth)\\s+)*)([A-Za-z_]\\w*)\\s+([^;{}]+);");
    private static final Pattern DECLARATOR = Pattern.compile("^(\\w+)\\s*(?:\\[\\s*(\\w+)\\s*\\])?$");
    private static final Pattern FRAG_DATA = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*\\]");
    private static final Pattern TEX_COORD = Pattern.compile("\\bgl_TexCoord\\s*\\[\\s*(\\d+)\\s*\\]");
    private static final Pattern LAYOUT_OUT = Pattern.compile(
            "layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*(?:flat\\s+)?out\\b");
    private static final Pattern FRAG_IN = Pattern.compile(
            "(?m)^[ \\t]*(?:flat\\s+|smooth\\s+|noperspective\\s+)?in\\s+(?:highp\\s+|mediump\\s+|lowp\\s+)?(\\w+)\\s+(\\w+)\\s*;");

    private static final Map<String, String> FUNCTIONS = Map.ofEntries(
            Map.entry("texture1D", "texture"), Map.entry("texture2D", "texture"),
            Map.entry("texture3D", "texture"), Map.entry("textureCube", "texture"),
            Map.entry("texture2DLod", "textureLod"), Map.entry("texture3DLod", "textureLod"),
            Map.entry("textureCubeLod", "textureLod"), Map.entry("texture2DProj", "textureProj"),
            Map.entry("texture2DProjLod", "textureProjLod"), Map.entry("texture2DGrad", "textureGrad"));

    private GlslTranslator() {}

    /** One stage after the translations that do not depend on the other stage. */
    private static final class Parsed {
        Stage stage;
        int version = 110;
        String body;
        final List<Uniform> uniforms = new ArrayList<>();
        final List<String> samplers = new ArrayList<>();
        final List<Integer> targets = new ArrayList<>();
        final TreeSet<Integer> fragOutputs = new TreeSet<>();
        final TreeSet<Integer> texCoords = new TreeSet<>();
        final List<Integer> layoutOutputs = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
    }

    /**
     * Translates a vertex and fragment shader pair. A null vertex shader gets a generated
     * fullscreen-triangle one that feeds the fragment shader's inputs.
     */
    public static Program translateProgram(String vertexSource, String fragmentSource) {
        Parsed frag = analyze(fragmentSource, Stage.FRAGMENT);
        Parsed vert = vertexSource == null ? null : analyze(vertexSource, Stage.VERTEX);

        List<String> warnings = new ArrayList<>(frag.warnings);
        Map<String, Uniform> union = new LinkedHashMap<>();
        for (Parsed p : vert == null ? List.of(frag) : List.of(vert, frag)) {
            for (Uniform u : p.uniforms) {
                Uniform existing = union.putIfAbsent(u.name(), u);
                if (existing != null && !existing.equals(u)) {
                    warnings.add("uniform " + u.name() + " declared with different types; using " + existing.type());
                }
            }
        }
        if (vert != null) {
            warnings.addAll(vert.warnings);
        }
        Set<Integer> texCoords = new TreeSet<>(frag.texCoords);
        if (vert != null) {
            texCoords.addAll(vert.texCoords);
        }
        List<Uniform> uniforms = List.copyOf(union.values());
        int version = Math.max(MIN_VERSION, Math.max(frag.version, vert == null ? 0 : vert.version));

        String fragment = render(frag, version, uniforms, texCoords);
        String vertex = vert != null ? render(vert, version, uniforms, texCoords) : synthesizeVertex(fragment, version);

        List<Integer> outputs = new ArrayList<>(frag.targets);
        if (outputs.isEmpty()) {
            if (!frag.layoutOutputs.isEmpty()) {
                outputs.addAll(frag.layoutOutputs);
            } else {
                for (int i = 0; i <= (frag.fragOutputs.isEmpty() ? 0 : frag.fragOutputs.last()); i++) {
                    outputs.add(i);
                }
            }
        }
        return new Program(vertex, fragment, uniforms, List.copyOf(frag.samplers), List.copyOf(outputs),
                List.copyOf(warnings));
    }

    // ------------------------------------------------------------------ analysis

    private static Parsed analyze(String source, Stage stage) {
        Parsed p = new Parsed();
        p.stage = stage;
        String src = source.replace("\r\n", "\n").replace('\r', '\n');

        Matcher vm = VERSION.matcher(src);
        if (vm.find()) {
            p.version = Integer.parseInt(vm.group(1));
            src = src.substring(0, vm.start()) + src.substring(vm.end());
        }
        src = EXTENSION.matcher(src).replaceAll("");

        Matcher tm = TARGETS.matcher(src);
        if (tm.find()) {
            parseTargets(tm.group(1), p.targets);
        }
        src = stripComments(src);

        src = translateUniforms(src, p);

        for (Map.Entry<String, String> f : FUNCTIONS.entrySet()) {
            src = src.replaceAll("(?<![\\w.])" + f.getKey() + "(?=\\s*\\()", f.getValue());
        }
        if (Pattern.compile("(?<![\\w.])shadow2D(Lod)?\\s*\\(").matcher(src).find()) {
            p.warnings.add("shadow2D is not translated (shadow samplers return a float in core GLSL)");
        }
        src = src.replaceAll("\\bvarying\\b", stage == Stage.VERTEX ? "out" : "in");
        if (stage == Stage.VERTEX) {
            src = src.replaceAll("\\battribute\\b", "in");
            src = translateVertexBuiltins(src);
        } else {
            src = translateFragmentOutputs(src, p);
        }
        src = translateTexCoords(src, p);

        Matcher lo = LAYOUT_OUT.matcher(src);
        while (lo.find()) {
            p.layoutOutputs.add(Integer.parseInt(lo.group(1)));
        }
        p.body = src;
        return p;
    }

    private static void parseTargets(String text, List<Integer> out) {
        String t = text.trim();
        if (t.contains(",")) {
            for (String part : t.split("\\s*,\\s*")) {
                out.add(Integer.parseInt(part.trim()));
            }
        } else {
            for (char c : t.replaceAll("\\s", "").toCharArray()) {
                out.add(Character.digit(c, 16));
            }
        }
    }

    /** Removes comments but keeps newlines, so a commented-out declaration is never parsed. */
    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                while (i < s.length() && s.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < s.length() && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
                    if (s.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static String translateUniforms(String src, Parsed p) {
        Set<String> seenSamplers = new LinkedHashSet<>();
        Set<String> seenUniforms = new LinkedHashSet<>();
        Matcher m = UNIFORM_DECL.matcher(src);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String type = m.group(2);
            boolean sampler = type.startsWith("sampler") || type.startsWith("isampler") || type.startsWith("usampler");
            boolean plain = Std140Layout.supports(type);
            if (!sampler && !plain) {
                p.warnings.add("uniform of type " + type + " is not translated: " + m.group(3).trim());
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
                continue;
            }
            StringBuilder repl = new StringBuilder();
            for (String raw : splitTopLevel(m.group(3))) {
                String declarator = raw.contains("=") ? raw.substring(0, raw.indexOf('=')).trim() : raw.trim();
                Matcher dm = DECLARATOR.matcher(declarator);
                if (!dm.matches()) {
                    p.warnings.add("could not parse uniform declarator '" + raw.trim() + "'");
                    continue;
                }
                String name = dm.group(1);
                if (sampler) {
                    if (seenSamplers.add(name)) {
                        p.samplers.add(name);
                        repl.append("uniform ").append(type).append(' ').append(name).append("Sampler;\n")
                                .append("#define ").append(name).append(' ').append(name).append("Sampler\n");
                    }
                    continue;
                }
                int arraySize = 0;
                if (dm.group(2) != null) {
                    arraySize = resolveArraySize(dm.group(2), src);
                    if (arraySize <= 0) {
                        p.warnings.add("array size '" + dm.group(2) + "' of uniform " + name + " is not a constant");
                        continue;
                    }
                }
                if (seenUniforms.add(name)) {
                    p.uniforms.add(new Uniform(type, name, arraySize));
                }
            }
            m.appendReplacement(out, Matcher.quoteReplacement(repl.toString()));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static int resolveArraySize(String expr, String src) {
        if (expr.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(expr);
        }
        Matcher d = Pattern.compile("(?m)^[ \\t]*#define[ \\t]+" + Pattern.quote(expr) + "[ \\t]+(\\d+)\\s*$").matcher(src);
        return d.find() ? Integer.parseInt(d.group(1)) : -1;
    }

    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    private static String translateVertexBuiltins(String src) {
        src = src.replaceAll("\\bftransform\\s*\\(\\s*\\)", "(" + VERTEX_POS + ")");
        src = src.replaceAll("\\bgl_Vertex\\b", "(" + VERTEX_POS + ")");
        src = src.replaceAll("\\bgl_MultiTexCoord0\\b", "(" + VERTEX_UV + ")");
        src = src.replaceAll("\\bgl_MultiTexCoord[1-7]\\b", "vec4(0.0, 0.0, 0.0, 1.0)");
        src = src.replaceAll("\\bgl_(ModelViewProjection|ModelView|Projection)Matrix(Inverse|Transpose)?\\b", "mat4(1.0)");
        src = src.replaceAll("\\bgl_NormalMatrix\\b", "mat3(1.0)");
        src = src.replaceAll("\\bgl_Normal\\b", "vec3(0.0, 0.0, 1.0)");
        src = src.replaceAll("\\bgl_Color\\b", "vec4(1.0)");
        return src;
    }

    private static String translateFragmentOutputs(String src, Parsed p) {
        Matcher fd = FRAG_DATA.matcher(src);
        StringBuilder sb = new StringBuilder();
        while (fd.find()) {
            int index = Integer.parseInt(fd.group(1));
            p.fragOutputs.add(index);
            fd.appendReplacement(sb, "iris_FragData" + index);
        }
        fd.appendTail(sb);
        src = sb.toString();
        if (Pattern.compile("\\bgl_FragColor\\b").matcher(src).find()) {
            p.fragOutputs.add(0);
            src = src.replaceAll("\\bgl_FragColor\\b", "iris_FragData0");
        }
        return src;
    }

    private static String translateTexCoords(String src, Parsed p) {
        Matcher tc = TEX_COORD.matcher(src);
        StringBuilder sb = new StringBuilder();
        while (tc.find()) {
            int index = Integer.parseInt(tc.group(1));
            p.texCoords.add(index);
            tc.appendReplacement(sb, "iris_TexCoord" + index);
        }
        tc.appendTail(sb);
        return sb.toString();
    }

    // ------------------------------------------------------------------ output

    private static String render(Parsed p, int version, List<Uniform> uniforms, Set<Integer> texCoords) {
        StringBuilder sb = new StringBuilder();
        sb.append("#version ").append(version).append('\n');
        String io = p.stage == Stage.VERTEX ? "out" : "in";
        for (int i : texCoords) {
            sb.append(io).append(" vec4 iris_TexCoord").append(i).append(";\n");
        }
        if (p.stage == Stage.FRAGMENT) {
            for (int i : p.fragOutputs) {
                sb.append("layout(location = ").append(i).append(") out vec4 iris_FragData").append(i).append(";\n");
            }
        }
        if (!uniforms.isEmpty()) {
            sb.append("layout(std140) uniform ").append(UNIFORM_BLOCK).append(" {\n");
            for (Uniform u : uniforms) {
                sb.append("    ").append(u.type()).append(' ').append(u.name());
                if (u.arraySize() > 0) {
                    sb.append('[').append(u.arraySize()).append(']');
                }
                sb.append(";\n");
            }
            sb.append("};\n");
        }
        sb.append(p.body);
        if (sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        return sb.toString();
    }

    /** A fullscreen-triangle vertex shader that fills every input the fragment shader declares. */
    private static String synthesizeVertex(String fragment, int version) {
        StringBuilder decl = new StringBuilder();
        StringBuilder assign = new StringBuilder();
        boolean uvUsed = false;
        Matcher m = FRAG_IN.matcher(fragment);
        while (m.find()) {
            String type = m.group(1);
            String name = m.group(2);
            String flat = m.group().contains("flat") ? "flat " : "";
            decl.append(flat).append("out ").append(type).append(' ').append(name).append(";\n");
            if (!uvUsed && type.equals("vec2")) {
                uvUsed = true;
                assign.append("    ").append(name).append(" = uv;\n");
            } else if (type.equals("vec4") && name.startsWith("iris_TexCoord")) {
                assign.append("    ").append(name).append(" = vec4(uv, 0.0, 1.0);\n");
            } else {
                boolean intLike = type.startsWith("i") || type.startsWith("u") || type.startsWith("b");
                assign.append("    ").append(name).append(" = ").append(type).append(intLike ? "(0)" : "(0.0)").append(";\n");
            }
        }
        return "#version " + version + "\n" + decl
                + "void main() {\n"
                + "    vec2 uv = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));\n"
                + assign
                + "    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);\n"
                + "}\n";
    }
}
