package com.bettercontent.betterdeathsdoor.review;

import com.bettercontent.betterdeathsdoor.heartreview.HeartVisualReview;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts the actual engine getter inside the review wrapper, including its delegated mixin chain. */
@Mixin(LivingEntity.class)
public abstract class HealthReadObservationMixin {
    @Inject(method = "getHealth", at = @At("HEAD"), require = 1)
    private void review$engineRead(CallbackInfoReturnable<Float> result) { HeartVisualReview.engineHealthRead(); }
}
