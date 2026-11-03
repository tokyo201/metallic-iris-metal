package dev.tokyo.metalliciris.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShaderSettingsStoreTest {
    @Test
    void missingFileGivesDefaults(@TempDir Path dir) {
        ShaderSettings s = ShaderSettingsStore.load(dir.resolve("nope.properties"));
        assertFalse(s.isEnabled());
        assertNull(s.getActivePack());
        assertTrue(s.disabledPacks().isEmpty());
    }

    @Test
    void roundTripsEverything(@TempDir Path dir) {
        Path file = dir.resolve("sub/settings.properties");
        ShaderSettings s = new ShaderSettings();
        s.setEnabled(true);
        s.setActivePack("My Pack.zip");
        s.setPackEnabled("Old.zip", false);
        s.setOption("My Pack.zip", "QUALITY", "3");
        s.setOption("My Pack.zip", "BLOOM", "true");

        ShaderSettingsStore.save(file, s);
        ShaderSettings r = ShaderSettingsStore.load(file);

        assertTrue(r.isEnabled());
        assertEquals("My Pack.zip", r.getActivePack());
        assertFalse(r.isPackEnabled("Old.zip"));
        assertTrue(r.isPackEnabled("My Pack.zip"));
        assertEquals(Map.of("QUALITY", "3", "BLOOM", "true"), r.optionOverrides("My Pack.zip"));
    }

    @Test
    void disablingActivePackDeselectsIt() {
        ShaderSettings s = new ShaderSettings();
        s.setActivePack("A");
        s.setPackEnabled("A", false);
        assertNull(s.getActivePack());
        s.setPackEnabled("A", true);
        assertTrue(s.isPackEnabled("A"));
    }

    @Test
    void handEditedFileCannotKeepDisabledPackActive(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("s.properties");
        Files.writeString(file, "enabled=true\nactivePack=A\npack.A.enabled=false\n");
        ShaderSettings s = ShaderSettingsStore.load(file);
        assertNull(s.getActivePack());
    }

    @Test
    void resetOptionsRemovesOverrides() {
        ShaderSettings s = new ShaderSettings();
        s.setOption("P", "A", "1");
        s.setOption("P", "B", "2");
        s.resetOption("P", "A");
        assertEquals(Map.of("B", "2"), s.optionOverrides("P"));
        s.resetPackOptions("P");
        assertTrue(s.optionOverrides("P").isEmpty());
        assertTrue(s.packsWithOverrides().isEmpty());
    }
}
