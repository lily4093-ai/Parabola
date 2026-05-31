package net.beady.parabola.mixin;

import net.beady.parabola.render.ScopeRenderCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    // Fire just before renderLevel() is called inside render(), after extractCamera() has run.
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"
        )
    )
    private void beforeRenderLevel(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        ScopeRenderCapture.captureScope(Minecraft.getInstance(), deltaTracker);
    }
}
