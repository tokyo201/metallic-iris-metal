package dev.tokyo.metalliciris.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tokyo.metalliciris.pack.PackFiles;
import dev.tokyo.metalliciris.pack.ShaderPack;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostChainGeneratorTest {
    @TempDir Path dir;

    @Test
    void ordersPassesAndHandlesFeedback() throws Exception {
        Path s = Files.createDirectories(dir.resolve("shaders"));
        Files.writeString(s.resolve("composite.fsh"),
                "/* DRAWBUFFERS:0 */\nuniform sampler2D colortex0;\nuniform float frameTimeCounter;\nvarying vec2 t;\n"
                + "void main(){ gl_FragData[0] = texture2D(colortex0, t); }");
        Files.writeString(s.resolve("final.fsh"),
                "uniform sampler2D gcolor;\nuniform sampler2D noisetex;\nvarying vec2 t;\n"
                + "void main(){ gl_FragColor = texture2D(gcolor, t); }");
        Files.writeString(s.resolve("deferred.fsh"),
                "uniform sampler2D depthtex0;\nvarying vec2 t;\nvoid main(){ gl_FragData[0] = vec4(1.0); }");
        ShaderPack pack = ShaderPack.load(PackFiles.fromDirectory(dir));

        PostChainGenerator.Chain c = PostChainGenerator.generate(pack);
        assertEquals(3, c.passes());
        assertTrue(c.shaders().containsKey("shaders/post/iris_composite.fsh"));
        assertTrue(c.json().indexOf("iris_deferred") < c.json().indexOf("iris_composite"));
        assertTrue(c.json().indexOf("iris_composite") < c.json().indexOf("iris_final"));
        assertTrue(c.json().contains("colortex0_swap"), "composite reads and writes colortex0");
        assertTrue(c.json().contains("\"use_depth_buffer\": true"));
        assertTrue(c.warnings().stream().anyMatch(w -> w.contains("noisetex")), c.warnings().toString());
        assertTrue(c.json().contains("frameTimeCounter"));
    }
}
