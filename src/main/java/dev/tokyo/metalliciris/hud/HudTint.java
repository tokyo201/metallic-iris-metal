package dev.tokyo.metalliciris.hud;

import dev.tokyo.metalliciris.HookStats;
import dev.tokyo.metalliciris.MetallicIrisMetal;
import dev.tokyo.metalliciris.ShaderManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Proof-of-hook, registered through Fabric API's HUD registry (no mixin). While shaders are on
 * with an active pack it draws a translucent orange tint behind the HUD every frame.
 * This is not shading yet.
 */
public final class HudTint {
    private static final int TINT_ARGB = 0x40FF8800;
    private static boolean logged;

    private HudTint() {}

    public static void register() {
        HudElementRegistry.addFirst(
                Identifier.fromNamespaceAndPath("metallic_iris_metal", "shader_tint"),
                (graphics, delta) -> {
                    HookStats.frameSeen();
                    ShaderManager manager = MetallicIrisMetal.manager();
                    if (manager == null || !manager.isShaderActive()) {
                        return;
                    }
                    HookStats.frameTinted();
                    var window = Minecraft.getInstance().getWindow();
                    graphics.fill(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), TINT_ARGB);
                    if (!logged) {
                        logged = true;
                        MetallicIrisMetal.LOG.info("Shader hook active: drew proof-of-hook tint for pack {}",
                                manager.settings().getActivePack());
                    }
                });
    }
}
