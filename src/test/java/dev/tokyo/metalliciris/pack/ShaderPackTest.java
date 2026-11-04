package dev.tokyo.metalliciris.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShaderPackTest {
    private static final Map<String, String> SAMPLE = Map.of(
            "shaders.properties", "shadowMapResolution=1024",
            "gbuffers_terrain.vsh", "#version 330\nvoid main() {}",
            "gbuffers_terrain.fsh", "#version 330\n#include \"lib/c.glsl\"\nvoid main() {}",
            "lib/c.glsl", "uniform sampler2D tex;",
            "composite.fsh", "#version 330\nvoid main() {}",
            "world-1/composite.fsh", "#version 330\n// nether",
            "shadow.csh", "#version 430\nlayout(local_size_x=8) in;\nvoid main() {}",
            "notes.txt", "ignored, not a program");

    @Test
    void discoversProgramsAndGroupsStages() {
        ShaderPack pack = ShaderPack.load(PackFiles.of(SAMPLE));
        assertEquals(4, pack.programs().size(), pack.programs().keySet().toString());

        ShaderPack.Program terrain = pack.program("gbuffers_terrain");
        assertNotNull(terrain.vertex());
        assertNotNull(terrain.fragment());
        assertNull(terrain.geometry());
        assertTrue(terrain.fragment().contains("uniform sampler2D tex;"), "include was expanded");
        assertFalse(terrain.fragment().contains("#include"));

        assertEquals(1024, pack.properties().getInt("shadowMapResolution", 0));
    }

    @Test
    void keepsDimensionOverridesSeparate() {
        ShaderPack pack = ShaderPack.load(PackFiles.of(SAMPLE));
        ShaderPack.Program overworld = pack.program("composite");
        ShaderPack.Program nether = pack.program("world-1/composite");
        assertEquals("", overworld.dimension());
        assertEquals("world-1", nether.dimension());
        assertEquals("composite", nether.name());
        assertTrue(nether.fragment().contains("// nether"));
    }

    @Test
    void flagsComputeOnlyPrograms() {
        ShaderPack pack = ShaderPack.load(PackFiles.of(SAMPLE));
        assertTrue(pack.program("shadow").isComputeOnly());
        assertFalse(pack.program("composite").isComputeOnly());
    }

    @Test
    void packWithoutPropertiesStillLoads() {
        ShaderPack pack = ShaderPack.load(PackFiles.of(Map.of("final.fsh", "void main() {}")));
        assertEquals(1, pack.programs().size());
        assertTrue(pack.properties().asMap().isEmpty());
    }

    @Test
    void loadsFromDirectory(@TempDir Path root) throws IOException {
        writeDirectory(root);
        ShaderPack pack = ShaderPack.loadDirectory(root);
        assertEquals(4, pack.programs().size());
        assertTrue(pack.program("gbuffers_terrain").fragment().contains("uniform sampler2D tex;"));
    }

    @Test
    void loadsFromZip(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("pack.zip");
        try (OutputStream os = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(os)) {
            for (Map.Entry<String, String> e : SAMPLE.entrySet()) {
                z.putNextEntry(new ZipEntry("shaders/" + e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
            z.putNextEntry(new ZipEntry("pack.png"));
            z.write(new byte[] {1, 2, 3});
            z.closeEntry();
        }
        ShaderPack pack = ShaderPack.loadZip(zip);
        assertEquals(4, pack.programs().size());
    }

    @Test
    void rejectsPacksWithoutShadersFolder(@TempDir Path root) {
        assertThrows(ShaderPackException.class, () -> ShaderPack.loadDirectory(root));
    }

    private static void writeDirectory(Path root) throws IOException {
        for (Map.Entry<String, String> e : SAMPLE.entrySet()) {
            Path p = root.resolve("shaders").resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue());
        }
    }

    @Test
    void acceptsPacksWrappedInOneFolder(@TempDir Path dir) throws IOException {
        Path wrapped = dir.resolve("Outer/Inner/shaders");
        Files.createDirectories(wrapped);
        Files.writeString(wrapped.resolve("final.fsh"), "void main() {}");
        assertTrue(PackFiles.looksLikePack(dir.resolve("Outer")));
        assertEquals(1, ShaderPack.loadDirectory(dir.resolve("Outer")).programs().size());

        Path zip = dir.resolve("wrapped.zip");
        try (OutputStream os = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(os)) {
            z.putNextEntry(new ZipEntry("MyPack/shaders/final.fsh"));
            z.write("void main() {}".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        assertTrue(PackFiles.looksLikePack(zip));
        assertEquals(1, ShaderPack.loadZip(zip).programs().size());
        assertFalse(PackFiles.looksLikePack(dir.resolve("missing.zip")));
    }
}
