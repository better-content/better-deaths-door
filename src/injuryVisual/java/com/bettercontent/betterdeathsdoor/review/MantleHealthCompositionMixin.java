package com.bettercontent.betterdeathsdoor.review;

import com.bettercontent.betterdeathsdoor.heartreview.HeartVisualReview;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** A second composable health wrapper; no alteration of renderer values or gameplay. */
@Pseudo
@Mixin(targets = "slimeknights.mantle.client.ExtraHeartRenderHandler", remap = false, priority = 500)
public abstract class MantleHealthCompositionMixin {
    @WrapOperation(method = "renderHealthbar(Lnet/minecraftforge/client/event/RenderGuiOverlayEvent$Pre;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getHealth()F", remap = true),
            remap = false, require = 1, expect = 1, allow = 1)
    private float review$compose(Player player, Operation<Float> original) {
        HeartVisualReview.negativeState();
        HeartVisualReview.beginHealthOperation();
        try { return original.call(player); }
        finally { HeartVisualReview.endHealthOperation(); }
    }
}
