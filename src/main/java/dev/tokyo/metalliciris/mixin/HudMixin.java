package dev.tokyo.metalliciris.mixin;

import dev.tokyo.metalliciris.HookStats;
import dev.tokyo.metalliciris.MetallicIrisMetal;
import dev.tokyo.metalliciris.ShaderManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Proof-of-hook. Runs at the start of every HUD draw. While shaders are on with an active pack
 * it draws a translucent orange tint over the whole screen, to show the render hook is live and
 * follows the settings. This is not shading yet.
 */
@Mixin(Hud.class)
public abstract class HudMixin {
    private static final int TINT_ARGB = 0x40FF8800;
    private static boolean metallicIris$logged;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"),
            require = 0)
    private void metallicIris$tint(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        HookStats.frameSeen();
        ShaderManager manager = MetallicIrisMetal.manager();
        if (manager == null || !manager.isShaderActive()) {
            return;
        }
        HookStats.frameTinted();
        var window = Minecraft.getInstance().getWindow();
        graphics.fill(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), TINT_ARGB);
        if (!metallicIris$logged) {
            metallicIris$logged = true;
            MetallicIrisMetal.LOG.info("Shader hook active: drew proof-of-hook tint for pack {}",
                    manager.settings().getActivePack());
        }
    }
}
