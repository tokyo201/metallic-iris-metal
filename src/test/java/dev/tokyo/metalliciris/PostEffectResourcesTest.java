package dev.tokyo.metalliciris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PostEffectResourcesTest {
    private static String read(String path) throws IOException {
        try (InputStream in = PostEffectResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    void postEffectJsonAndShaderAreShippedAndConsistent() throws IOException {
        String json = read("/assets/metallic_iris_metal/post_effect/test_grayscale.json");
        String fsh = read("/assets/metallic_iris_metal/shaders/post/grayscale.fsh");
        assertTrue(json.contains("metallic_iris_metal:post/grayscale"));
        assertTrue(json.contains("minecraft:core/screenquad"));
        // every uniform block the JSON configures must be declared by the shader
        assertTrue(json.contains("\"GrayConfig\"") && fsh.contains("uniform GrayConfig"));
        assertTrue(fsh.contains("uniform sampler2D InSampler"));
        assertTrue(fsh.startsWith("#version 330"));
        // the game rejects a pass with no "output" ("No key output"), so every pass needs one
        assertEquals(count(json, "\"vertex_shader\""), count(json, "\"output\""), "each pass needs an output");
        assertTrue(json.contains("\"output\": \"minecraft:main\""), "final pass must write to the main target");
    }
}
