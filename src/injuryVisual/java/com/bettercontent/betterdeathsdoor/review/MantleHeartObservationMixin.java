package com.bettercontent.betterdeathsdoor.review;

import com.bettercontent.betterdeathsdoor.HeartVisualReview;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Review-only: observe the actual transformed renderer after drawing, before Forge cancellation. */
@Pseudo
@Mixin(targets = "slimeknights.mantle.client.ExtraHeartRenderHandler", remap = false, priority = 500)
public abstract class MantleHeartObservationMixin {
    @Shadow(remap = false) private int lastHealth;
    @Shadow(remap = false) private int displayHealth;
    @Shadow(remap = false) private long healthBlinkTime;

    @Inject(method = "renderHealthbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/client/event/RenderGuiOverlayEvent$Pre;setCanceled(Z)V"), remap = false)
    private void review$drawn(RenderGuiOverlayEvent.Pre event, CallbackInfo callback) {
        HeartVisualReview.observe("Mantle", lastHealth, displayHealth, healthBlinkTime);
    }
}
