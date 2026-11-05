package dev.tokyo.metalliciris.render;

import dev.tokyo.metalliciris.HookStats;
import dev.tokyo.metalliciris.MetallicIrisMetal;
import dev.tokyo.metalliciris.ShaderManager;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;

/**
 * Switches Minecraft's own post-effect system on while shaders are active. The effect is an
 * ordinary post_effect JSON plus fragment shader shipped in this mod's assets, so the game and its
 * graphics backend compile and run it exactly like a vanilla post effect.
 *
 * <p>This is step 1: a built-in grayscale effect that proves our shader code runs on the frame.
 */
public final class PostEffectController {
    public static final Identifier EFFECT = Identifier.fromNamespaceAndPath("metallic_iris_metal", "test_grayscale");
    private static final int MAX_FAILURES = 3;

    private static Method setter;
    private static boolean setterLooked;
    private static boolean attempted;
    private static boolean gaveUp;
    private static boolean loggedApplied;
    private static int failures;

    private PostEffectController() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PostEffectController::tick);
    }

    private static void tick(Minecraft mc) {
        ShaderManager manager = MetallicIrisMetal.manager();
        GameRenderer renderer = mc.gameRenderer;
        if (renderer == null) {
            return;
        }
        boolean want = manager != null && manager.isShaderActive() && manager.settings().isTestEffect()
                && mc.level != null;
        Identifier current = renderer.currentPostEffect();

        if (!want) {
            HookStats.setEffectState("off - " + whyOff(manager, mc));
            attempted = false;
            gaveUp = false;
            failures = 0;
            loggedApplied = false;
            if (EFFECT.equals(current)) {
                renderer.clearPostEffect();
                MetallicIrisMetal.say("post effect cleared");
            }
            return;
        }

        if (EFFECT.equals(current)) {
            attempted = false;
            failures = 0;
            HookStats.setEffectState("ACTIVE - game accepted it (if the screen is not gray, the result is not being shown)");
            if (!loggedApplied) {
                loggedApplied = true;
                MetallicIrisMetal.say("post effect ACTIVE: " + EFFECT);
            }
            return;
        }
        if (gaveUp) {
            return;
        }
        if (attempted && ++failures >= MAX_FAILURES) {
            gaveUp = true;
            HookStats.setEffectState("REJECTED - game dropped it " + MAX_FAILURES + " times (shader likely failed to compile)");
            MetallicIrisMetal.say("post effect " + EFFECT + " keeps getting dropped by the game; giving up until shaders are toggled");
            return;
        }
        HookStats.setEffectState("applying (attempt " + (failures + 1) + ")");
        apply(renderer);
    }

    private static String whyOff(ShaderManager manager, Minecraft mc) {
        if (manager == null) {
            return "mod not ready";
        }
        if (!manager.settings().isEnabled()) {
            return "Shaders is OFF";
        }
        if (manager.settings().getActivePack() == null) {
            return "no pack selected";
        }
        if (!manager.isShaderActive()) {
            return "selected pack is disabled";
        }
        if (!manager.settings().isTestEffect()) {
            return "Test effect is OFF";
        }
        return mc.level == null ? "not in a world" : "waiting";
    }

    private static void apply(GameRenderer renderer) {
        try {
            if (!setterLooked) {
                setterLooked = true;
                setter = GameRenderer.class.getDeclaredMethod("setPostEffect", Identifier.class);
                setter.setAccessible(true);
            }
            if (setter == null) {
                return;
            }
            attempted = true;
            setter.invoke(renderer, EFFECT);
        } catch (ReflectiveOperationException | RuntimeException e) {
            gaveUp = true;
            HookStats.setEffectState("ERROR - " + e);
            MetallicIrisMetal.say("could not apply post effect: " + e);
            MetallicIrisMetal.LOG.error("post effect failed", e);
        }
    }
}
