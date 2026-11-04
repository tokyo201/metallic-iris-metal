package dev.tokyo.metalliciris.hud;

import dev.tokyo.metalliciris.HookStats;
import dev.tokyo.metalliciris.MetallicIrisMetal;
import dev.tokyo.metalliciris.ShaderManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Proof-of-hook, registered through Fabric API's HUD registry (no mixin). While shaders are on
 * with an active pack it draws a translucent orange tint behind the HUD every frame, plus a small
 * status square in the top-left corner (green when active, red when not) whenever the hook runs.
 * This is not shading yet.
 */
public final class HudTint {
    private static final int TINT_ARGB = 0x70FF8800;
    private static boolean logged;
    private static int lastState = -1;

    private HudTint() {}

    public static void register() {
        HudElementRegistry.addFirst(
                Identifier.fromNamespaceAndPath("metallic_iris_metal", "shader_tint"),
                (graphics, delta) -> {
                    if (HookStats.hudFrames() == 0) {
                        MetallicIrisMetal.say("HUD hook: first frame drawn");
                    }
                    HookStats.frameSeen();
                    ShaderManager manager = MetallicIrisMetal.manager();
                    boolean active = manager != null && manager.isShaderActive();
                    int state = active ? 1 : 0;
                    if (state != lastState) {
                        lastState = state;
                        MetallicIrisMetal.say("HUD hook: shaders " + (active ? "ACTIVE" : "inactive")
                                + (manager == null ? " (manager null)" : ", " + HookStats.describe(manager)));
                    }
                    // Status square, top-left: green = shaders active, red = hook runs but shaders inactive.
                    graphics.fill(2, 2, 10, 10, active ? 0xFF00FF00 : 0xFFFF0000);
                    if (!active) {
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
