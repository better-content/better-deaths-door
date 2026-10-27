package com.bettercontent.betterdeathsdoor.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Dormant raw-field surface for a FUTURE qualified DD-owned commit authority.
 * No callers/price/registration/activation in this spike. Not a public setter fallback.
 * Actual merged methods, actor binding and final-definition attestation remain required.
 */
@Mixin(FoodData.class)
public interface FoodDataRawAccess {
    @Accessor("saturationLevel") float revival$getRawSaturation();
    @Accessor("saturationLevel") void revival$setRawSaturation(float value);
    @Accessor("foodLevel") int revival$getRawFoodLevel();
    @Accessor("foodLevel") void revival$setRawFoodLevel(int value);
}
