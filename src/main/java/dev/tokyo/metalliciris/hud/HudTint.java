package dev.tokyo.metalliciris.hud;

import dev.tokyo.metalliciris.HookStats;
import dev.tokyo.metalliciris.MetallicIrisMetal;
import dev.tokyo.metalliciris.ShaderManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

/**
 * Proof-of-hook, registered through Fabric API's HUD registry (no mixin). While shaders are on
 * it draws a translucent orange tint behind the HUD every frame, plus a small
 * status square in the top-left corner (green active, yellow on without a pack, red off) whenever the hook runs.
 * This is not shading yet.
 */
public final class HudTint {
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
                    boolean on = manager != null && manager.settings().isEnabled();
                    boolean active = manager != null && manager.isShaderActive();
                    int state = active ? 2 : on ? 1 : 0;
                    if (state != lastState) {
                        lastState = state;
                        MetallicIrisMetal.say("HUD hook: shaders " + (active ? "ACTIVE" : on ? "ON, no pack" : "OFF")
                                + (manager == null ? " (manager null)" : ", " + HookStats.describe(manager)));
                    }
                    // Status square, top-left: green = shaders on with a pack, yellow = on but no pack
                    // selected, red = shaders off.
                    graphics.fill(2, 2, 10, 10, active ? 0xFF00FF00 : on ? 0xFFFFFF00 : 0xFFFF0000);
                    if (on) {
                        HookStats.frameTinted();
                    }
                });
    }
}
