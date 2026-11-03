package dev.tokyo.metalliciris.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShaderOptionsTest {
    private static final String OPTS = String.join("\n",
            "#define SHADOWS",
            "//#define BLOOM",
            "#define QUALITY 2 // [1 2 3] render quality",
            "#define F(x) x*2",
            "// #define NOTE explained here",
            "#define NO_ALLOWED_LIST 5",
            "");

    private static PackFiles pack() {
        return PackFiles.of(Map.of("lib/opts.glsl", OPTS, "shaders.properties", "#define IGNORED"));
    }

    @Test
    void discoversBooleansValuesAndAllowedLists() {
        Map<String, ShaderOption> found = ShaderOptions.discover(pack());
        assertEquals(List.of("SHADOWS", "BLOOM", "QUALITY", "NO_ALLOWED_LIST"), List.copyOf(found.keySet()));

        assertEquals(ShaderOption.Type.BOOLEAN, found.get("SHADOWS").type());
        assertEquals("true", found.get("SHADOWS").defaultValue());
        assertEquals("false", found.get("BLOOM").defaultValue());

        ShaderOption quality = found.get("QUALITY");
        assertEquals(ShaderOption.Type.VALUE, quality.type());
        assertEquals("2", quality.defaultValue());
        assertEquals(List.of("1", "2", "3"), quality.allowedValues());
        assertTrue(found.get("NO_ALLOWED_LIST").allowedValues().isEmpty());
    }

    @Test
    void acceptsOnlyDeclaredValues() {
        Map<String, ShaderOption> found = ShaderOptions.discover(pack());
        assertTrue(found.get("QUALITY").accepts("3"));
        assertFalse(found.get("QUALITY").accepts("9"));
        assertTrue(found.get("NO_ALLOWED_LIST").accepts("anything"));
        assertTrue(found.get("BLOOM").accepts("true"));
        assertFalse(found.get("BLOOM").accepts("yes"));
        assertFalse(found.get("BLOOM").accepts(null));
    }

    @Test
    void appliesBooleanAndValueOverrides() {
        PackFiles out = ShaderOptions.apply(pack(),
                Map.of("BLOOM", "true", "SHADOWS", "false", "QUALITY", "3"));
        String text = out.get("lib/opts.glsl");
        assertTrue(text.contains("\n#define BLOOM\n"), text);
        assertTrue(text.contains("//#define SHADOWS\n"), text);
        assertTrue(text.contains("#define QUALITY 3 // [1 2 3] render quality"), text);
        // untouched lines survive exactly
        assertTrue(text.contains("#define F(x) x*2"));
        assertTrue(text.contains("// #define NOTE explained here"));
    }

    @Test
    void noOverridesReturnsSamePack() {
        PackFiles files = pack();
        assertSame(files, ShaderOptions.apply(files, Map.of()));
    }

    @Test
    void valueCannotBeAppliedToBareDefine() {
        PackFiles out = ShaderOptions.apply(pack(), Map.of("SHADOWS", "7"));
        assertTrue(out.get("lib/opts.glsl").contains("#define SHADOWS\n"));
    }
}
