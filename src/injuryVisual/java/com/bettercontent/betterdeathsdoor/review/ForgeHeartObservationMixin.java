package com.bettercontent.betterdeathsdoor.review;

import com.bettercontent.betterdeathsdoor.heartreview.HeartVisualReview;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Review-only observation of the arguments actually passed to the native heart drawer. */
@Mixin(Gui.class)
public abstract class ForgeHeartObservationMixin {
    @Inject(method = "renderHearts", at = @At("HEAD"))
    private void review$drawn(GuiGraphics graphics, Player player, int x, int y, int spacing, int regen,
            float maximum, int current, int remembered, int absorption, boolean blink, CallbackInfo callback) {
        HeartVisualReview.negativeState();
        HeartVisualReview.observe("Forge", current, remembered, blink ? 1 : 0);
    }
}
