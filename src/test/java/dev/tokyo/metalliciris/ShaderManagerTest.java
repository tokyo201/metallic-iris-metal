package dev.tokyo.metalliciris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tokyo.metalliciris.config.ShaderSettings;
import dev.tokyo.metalliciris.pack.ShaderPack;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShaderManagerTest {
    private static final String OPTS =
            "#define SHADOWS\n//#define BLOOM\n#define QUALITY 2 // [1 2 3]\n";

    @TempDir Path packs;
    ShaderSettings settings;
    ShaderManager manager;

    @BeforeEach
    void setUp() throws IOException {
        settings = new ShaderSettings();
        manager = new ShaderManager(packs, settings);

        Path shaders = packs.resolve("Cool/shaders");
        Files.createDirectories(shaders.resolve("lib"));
        Files.writeString(shaders.resolve("composite.fsh"), "#include \"lib/opts.glsl\"\nvoid main() {}");
        Files.writeString(shaders.resolve("lib/opts.glsl"), OPTS);

        Files.createDirectories(packs.resolve("NotAPack")); // no shaders/ folder
        Files.writeString(packs.resolve("readme.txt"), "ignored");
        try (OutputStream os = Files.newOutputStream(packs.resolve("Zipped.zip"));
                ZipOutputStream z = new ZipOutputStream(os)) {
            z.putNextEntry(new ZipEntry("shaders/final.fsh"));
            z.write("void main() {}".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
    }

    @Test
    void listsFolderAndZipPacksOnly() {
        assertEquals(List.of("Cool", "Zipped.zip"), manager.installedPacks());
    }

    @Test
    void missingPacksFolderIsEmpty(@TempDir Path other) {
        assertTrue(new ShaderManager(other.resolve("nope"), settings).installedPacks().isEmpty());
    }

    @Test
    void disabledPacksAreHiddenAndCannotBeActivated() {
        settings.setPackEnabled("Zipped.zip", false);
        assertEquals(List.of("Cool"), manager.enabledPacks());
        assertFalse(manager.activate("Zipped.zip"));
        assertFalse(manager.activate("Missing"));
        assertTrue(manager.activate("Cool"));
        assertEquals("Cool", settings.getActivePack());
    }

    @Test
    void nothingLoadsUntilEnabledAndActive() {
        assertTrue(manager.loadActive().isEmpty());
        settings.setEnabled(true);
        assertTrue(manager.loadActive().isEmpty(), "no active pack yet");
        manager.activate("Cool");
        assertTrue(manager.loadActive().isPresent());
        settings.setEnabled(false);
        assertTrue(manager.loadActive().isEmpty(), "master switch off");
    }

    @Test
    void loadedPackReflectsOptionOverrides() {
        settings.setEnabled(true);
        manager.activate("Cool");
        manager.setOption("Cool", "BLOOM", "true");
        manager.setOption("Cool", "QUALITY", "3");
        settings.setOption("Cool", "SHADOWS", "false");

        ShaderPack pack = manager.loadActive().orElseThrow();
        String fsh = pack.program("composite").fragment();
        assertTrue(fsh.contains("\n#define BLOOM\n"), fsh);
        assertTrue(fsh.contains("#define QUALITY 3 // [1 2 3]"), fsh);
        assertTrue(fsh.contains("//#define SHADOWS"), fsh);
    }

    @Test
    void rejectsUnknownOptionsAndBadValues() {
        assertThrows(IllegalArgumentException.class, () -> manager.setOption("Cool", "NOPE", "1"));
        assertThrows(IllegalArgumentException.class, () -> manager.setOption("Cool", "QUALITY", "9"));
        assertThrows(IllegalArgumentException.class, () -> manager.setOption("Cool", "BLOOM", "maybe"));
        assertTrue(settings.optionOverrides("Cool").isEmpty(), "nothing saved on rejection");
    }

    @Test
    void listsDeclaredOptions() {
        assertEquals(List.of("SHADOWS", "BLOOM", "QUALITY"),
                manager.options("Cool").stream().map(o -> o.name()).toList());
    }
}
