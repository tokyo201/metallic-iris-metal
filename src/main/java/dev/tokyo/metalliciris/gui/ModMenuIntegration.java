package dev.tokyo.metalliciris.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Registers the shader settings screen as this mod's config screen in Mod Menu. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ShaderSettingsScreen::new;
    }
}
