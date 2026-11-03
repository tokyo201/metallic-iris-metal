package dev.tokyo.metalliciris;

import dev.tokyo.metalliciris.config.ShaderSettings;
import dev.tokyo.metalliciris.config.ShaderSettingsStore;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MetallicIrisMetal implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("Metallic Iris Metal");

    private static Path settingsFile;
    private static ShaderManager manager;

    /** The shader manager, available once the client has initialised. */
    public static ShaderManager manager() {
        return manager;
    }

    /** Writes the current settings to disk. Call after changing them. */
    public static void saveSettings() {
        ShaderSettingsStore.save(settingsFile, manager.settings());
    }

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        settingsFile = loader.getConfigDir().resolve("metallic_iris_metal.properties");
        ShaderSettings settings = ShaderSettingsStore.load(settingsFile);
        manager = new ShaderManager(loader.getGameDir().resolve("shaderpacks"), settings);

        boolean irisPresent = loader.isModLoaded("iris");
        LOG.info("Clean-room Metal shader compatibility prototype loaded. Iris present: {}. "
                        + "Shaders {}, active pack: {}. Renderer pipeline integration is not implemented yet.",
                irisPresent, settings.isEnabled() ? "enabled" : "disabled",
                settings.getActivePack() == null ? "none" : settings.getActivePack());
    }
}
