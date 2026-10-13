package com.bettercontent.betterdeathsdoor.review;

import com.bettercontent.betterdeathsdoor.client.DeathPresentationSession;
import com.bettercontent.betterdeathsdoor.client.DeathRecapOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Nonshipping access to view state only. No mouse, keyboard, scrolling or server-state mutation. */
@Mixin(value = DeathRecapOverlay.class, remap = false)
public interface DeathPresentationObservation {
    @Accessor("SESSION")
    static DeathPresentationSession lifeReviewSession() { throw new AssertionError("Mixin accessor missing"); }
}
