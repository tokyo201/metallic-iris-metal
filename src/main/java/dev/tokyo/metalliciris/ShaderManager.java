package dev.tokyo.metalliciris;

import dev.tokyo.metalliciris.config.ShaderSettings;
import dev.tokyo.metalliciris.pack.PackFiles;
import dev.tokyo.metalliciris.pack.ShaderOption;
import dev.tokyo.metalliciris.pack.ShaderOptions;
import dev.tokyo.metalliciris.pack.ShaderPack;
import dev.tokyo.metalliciris.pack.ShaderPackException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Applies {@link ShaderSettings} to the packs found in a {@code shaderpacks} folder: lists them,
 * switches them on and off, validates option changes, and loads the active pack with the user's
 * option values applied.
 */
public final class ShaderManager {
    private final Path packsDir;
    private final ShaderSettings settings;

    public ShaderManager(Path packsDir, ShaderSettings settings) {
        this.packsDir = packsDir;
        this.settings = settings;
    }

    /** The folder scanned for packs. */
    public Path packsDir() {
        return packsDir;
    }

    public ShaderSettings settings() {
        return settings;
    }

    /** Every pack in the folder: directories with a {@code shaders/} folder, and {@code .zip} files. */
    public List<String> installedPacks() {
        if (!Files.isDirectory(packsDir)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> children = Files.list(packsDir)) {
            for (Path p : (Iterable<Path>) children::iterator) {
                String name = p.getFileName().toString();
                boolean zipName = Files.isRegularFile(p) && name.toLowerCase(Locale.ROOT).endsWith(".zip");
                if ((Files.isDirectory(p) || zipName) && PackFiles.looksLikePack(p)) {
                    names.add(name);
                }
            }
        } catch (IOException e) {
            throw new ShaderPackException("Could not list " + packsDir, e);
        }
        Collections.sort(names);
        return names;
    }

    /**
     * Cheap per-frame check: shaders are switched on and a pack is selected and not disabled.
     * Looks only at the settings (no disk access), so it is safe to call every frame.
     */
    public boolean isShaderActive() {
        String pack = settings.getActivePack();
        return settings.isEnabled() && pack != null && settings.isPackEnabled(pack);
    }

    /** One line describing what is in the packs folder, for diagnosing "no packs found". */
    public String describeFolder() {
        if (!Files.isDirectory(packsDir)) {
            return "packs folder " + packsDir + " does not exist";
        }
        try (Stream<Path> kids = Files.list(packsDir)) {
            List<String> parts = new ArrayList<>();
            for (Path k : (Iterable<Path>) kids.sorted()::iterator) {
                parts.add(k.getFileName() + (Files.isDirectory(k) ? "/" : "")
                        + (PackFiles.looksLikePack(k) ? "[pack]" : "[not a pack]"));
                if (parts.size() >= 15) {
                    parts.add("...");
                    break;
                }
            }
            return "packs folder " + packsDir + " contains " + (parts.isEmpty() ? "nothing" : parts);
        } catch (IOException e) {
            return "packs folder " + packsDir + " unreadable: " + e;
        }
    }

    /** Installed packs that have not been disabled. */
    public List<String> enabledPacks() {
        return installedPacks().stream().filter(settings::isPackEnabled).toList();
    }

    /** Makes {@code pack} the active pack. Returns false if it is not installed or is disabled. */
    public boolean activate(String pack) {
        if (!installedPacks().contains(pack) || !settings.isPackEnabled(pack)) {
            return false;
        }
        settings.setActivePack(pack);
        return true;
    }

    /** The options a pack declares, in the order found. */
    public List<ShaderOption> options(String pack) {
        return List.copyOf(ShaderOptions.discover(read(pack)).values());
    }

    /**
     * Saves an option override after checking the pack declares the option and accepts the value.
     *
     * @throws IllegalArgumentException if the option is unknown or the value is not allowed
     */
    public void setOption(String pack, String option, String value) {
        ShaderOption declared = ShaderOptions.discover(read(pack)).get(option);
        if (declared == null) {
            throw new IllegalArgumentException(pack + " has no option " + option);
        }
        if (!declared.accepts(value)) {
            throw new IllegalArgumentException(
                    "'" + value + "' is not valid for " + option + " (allowed: " + declared.allowedValues() + ")");
        }
        settings.setOption(pack, option, value);
    }

    /**
     * Loads the active pack with option overrides applied, or returns empty when shaders are
     * switched off, no pack is selected, or the selected pack is missing or disabled.
     */
    public Optional<ShaderPack> loadActive() {
        String pack = settings.getActivePack();
        if (!settings.isEnabled() || pack == null || !settings.isPackEnabled(pack)
                || !installedPacks().contains(pack)) {
            return Optional.empty();
        }
        Map<String, String> overrides = settings.optionOverrides(pack);
        return Optional.of(ShaderPack.load(ShaderOptions.apply(read(pack), overrides)));
    }

    private PackFiles read(String pack) {
        Path path = packsDir.resolve(pack);
        if (!path.normalize().startsWith(packsDir.normalize())) {
            throw new ShaderPackException("Invalid pack name: " + pack);
        }
        return Files.isDirectory(path) ? PackFiles.fromDirectory(path) : PackFiles.fromZip(path);
    }
}
