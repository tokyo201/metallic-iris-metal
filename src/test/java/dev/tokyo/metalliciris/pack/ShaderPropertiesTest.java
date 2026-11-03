package dev.tokyo.metalliciris.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShaderPropertiesTest {
    @Test
    void parsesKeyValuesAndSkipsCommentsAndBlankLines() {
        ShaderProperties p = ShaderProperties.parse("""
                # a comment
                shadowMapResolution = 2048

                oldLighting=false
                """);
        assertEquals("2048", p.get("shadowMapResolution"));
        assertEquals(2048, p.getInt("shadowMapResolution", 0));
        assertEquals(false, p.getBoolean("oldLighting", true));
        assertEquals(2, p.asMap().size());
    }

    @Test
    void joinsBackslashContinuedLines() {
        ShaderProperties p = ShaderProperties.parse("screen=A B \\\n  C \\\r\n  D\nnext=1\n");
        assertEquals("A B  C  D", p.get("screen"));
        assertEquals("1", p.get("next"));
    }

    @Test
    void keepsEqualsSignsInsideValues() {
        ShaderProperties p = ShaderProperties.parse("blend.gbuffers_water=SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ZERO\nx=a=b");
        assertEquals("a=b", p.get("x"));
        assertTrue(p.get("blend.gbuffers_water").startsWith("SRC_ALPHA"));
    }

    @Test
    void fallsBackWhenMissingOrMalformed() {
        ShaderProperties p = ShaderProperties.parse("n=notanumber\nno equals here");
        assertEquals(7, p.getInt("n", 7));
        assertEquals(3, p.getInt("missing", 3));
        assertNull(p.get("no equals here"));
    }
}
