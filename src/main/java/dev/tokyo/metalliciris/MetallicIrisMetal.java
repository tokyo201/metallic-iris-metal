package dev.tokyo.metalliciris;

import dev.tokyo.metalliciris.config.ShaderSettings;
import dev.tokyo.metalliciris.config.ShaderSettingsStore;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MetallicIrisMetal implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Metallic Iris Metal");

    private static Path settingsFile;
    private static ShaderManager manager;

    /**
     * Logs through the normal logger and also prints to stdout with a [MIM] prefix, because some
     * launchers drop log4j output but still capture stdout.
     */
    public static void say(String message) {
        LOG.info(message);
        System.out.println("[MIM] " + message);
    }

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

        say("init: build=hud-fabricapi-v3, mod version "
                + loader.getModContainer("metallic_iris_metal").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?")
                + ", fabric-rendering-v1 loaded=" + loader.isModLoaded("fabric-rendering-v1")
                + ", modmenu loaded=" + loader.isModLoaded("modmenu"));
        if (loader.isModLoaded("fabric-rendering-v1")) {
            try {
                dev.tokyo.metalliciris.hud.HudTint.register();
                say("HUD hook registered");
            } catch (Throwable t) {
                say("HUD hook FAILED to register: " + t);
                LOG.error("HUD hook registration failed", t);
            }
        } else {
            say("Fabric API (fabric-rendering-v1) not found: the render hook is disabled.");
        }

        boolean irisPresent = loader.isModLoaded("iris");
        LOG.info("Clean-room Metal shader compatibility prototype loaded. Iris present: {}. "
                        + "Shaders {}, active pack: {}. Renderer pipeline integration is not implemented yet.",
                irisPresent, settings.isEnabled() ? "enabled" : "disabled",
                settings.getActivePack() == null ? "none" : settings.getActivePack());
    }
}
