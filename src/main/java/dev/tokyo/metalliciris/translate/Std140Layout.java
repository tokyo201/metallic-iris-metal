package dev.tokyo.metalliciris.translate;

import dev.tokyo.metalliciris.translate.GlslTranslator.Uniform;
import java.util.ArrayList;
import java.util.List;

/** Computes std140 byte offsets for a list of uniforms, so a uniform buffer can be filled to match. */
public final class Std140Layout {
    public record Entry(String name, String type, int arraySize, int offset, int size) {}

    private final List<Entry> entries;
    private final int totalSize;

    private Std140Layout(List<Entry> entries, int totalSize) {
        this.entries = List.copyOf(entries);
        this.totalSize = totalSize;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Total buffer size in bytes, rounded up to 16. */
    public int totalSize() {
        return totalSize;
    }

    public Entry find(String name) {
        for (Entry e : entries) {
            if (e.name().equals(name)) {
                return e;
            }
        }
        return null;
    }

    public static boolean supports(String type) {
        return baseSize(type) > 0;
    }

    public static Std140Layout compute(List<Uniform> uniforms) {
        List<Entry> out = new ArrayList<>();
        int cursor = 0;
        for (Uniform u : uniforms) {
            int align = baseAlign(u.type());
            int size = baseSize(u.type());
            if (size <= 0) {
                throw new IllegalArgumentException("Unsupported uniform type for std140: " + u.type());
            }
            int total;
            if (u.arraySize() > 0) {
                align = roundUp(align, 16);
                int stride = roundUp(size, 16);
                total = stride * u.arraySize();
            } else {
                total = size;
            }
            int offset = roundUp(cursor, align);
            out.add(new Entry(u.name(), u.type(), u.arraySize(), offset, total));
            cursor = offset + total;
        }
        return new Std140Layout(out, roundUp(cursor, 16));
    }

    private static int roundUp(int v, int to) {
        return (v + to - 1) / to * to;
    }

    /** Size in bytes of one value of the type (a matrix is its columns, each padded to 16). */
    private static int baseSize(String type) {
        return switch (type) {
            case "float", "int", "uint", "bool" -> 4;
            case "vec2", "ivec2", "uvec2", "bvec2" -> 8;
            case "vec3", "ivec3", "uvec3", "bvec3" -> 12;
            case "vec4", "ivec4", "uvec4", "bvec4" -> 16;
            case "mat2" -> 32;
            case "mat3" -> 48;
            case "mat4" -> 64;
            default -> 0;
        };
    }

    private static int baseAlign(String type) {
        return switch (type) {
            case "float", "int", "uint", "bool" -> 4;
            case "vec2", "ivec2", "uvec2", "bvec2" -> 8;
            default -> 16;
        };
    }
}
