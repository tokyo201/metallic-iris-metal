package dev.tokyo.metalliciris.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class IncludeResolverTest {
    @Test
    void inlinesRelativeAndRootRelativeIncludes() {
        PackFiles files = PackFiles.of(Map.of(
                "composite.fsh", "#version 330\n#include \"/lib/common.glsl\"\nvoid main() {}",
                "lib/common.glsl", "#include \"util.glsl\"\nfloat a;",
                "lib/util.glsl", "float b;"));
        String out = new IncludeResolver(files).resolve("composite.fsh");
        assertEquals("#version 330\nfloat b;\nfloat a;\nvoid main() {}", out);
    }

    @Test
    void resolvesParentDirectoryIncludes() {
        PackFiles files = PackFiles.of(Map.of(
                "world-1/final.fsh", "#include \"../lib/x.glsl\"",
                "lib/x.glsl", "float x;"));
        assertEquals("float x;", new IncludeResolver(files).resolve("world-1/final.fsh"));
    }

    @Test
    void detectsCircularIncludes() {
        PackFiles files = PackFiles.of(Map.of(
                "a.fsh", "#include \"b.glsl\"",
                "b.glsl", "#include \"a.fsh\""));
        ShaderPackException e = assertThrows(ShaderPackException.class,
                () -> new IncludeResolver(files).resolve("a.fsh"));
        assertTrue(e.getMessage().contains("Circular"), e.getMessage());
    }

    @Test
    void reportsMissingIncludeWithItsParent() {
        PackFiles files = PackFiles.of(Map.of("a.fsh", "#include \"nope.glsl\""));
        ShaderPackException e = assertThrows(ShaderPackException.class,
                () -> new IncludeResolver(files).resolve("a.fsh"));
        assertTrue(e.getMessage().contains("nope.glsl") && e.getMessage().contains("a.fsh"), e.getMessage());
    }

    @Test
    void rejectsIncludesThatEscapeTheShadersFolder() {
        PackFiles files = PackFiles.of(Map.of("a.fsh", "#include \"../../etc/passwd\""));
        assertThrows(ShaderPackException.class, () -> new IncludeResolver(files).resolve("a.fsh"));
    }
}
