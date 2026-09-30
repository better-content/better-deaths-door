package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.RevivalManager;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingHealthMixin {
    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float revival$healingLock(float requested) {
        return RevivalManager.filterHealthWrite((LivingEntity) (Object) this, requested);
    }
    @Inject(method = "setHealth", at = @At("RETURN"))
    private void revival$afterHealth(float requested, CallbackInfo ci) {
        RevivalManager.afterHealthWrite((LivingEntity) (Object) this);
    }
    @Inject(method = "heal", at = @At("HEAD"))
    private void revival$beginHeal(float amount, CallbackInfo ci) { RevivalManager.beginHeal(); }
    @Inject(method = "heal", at = @At("RETURN"))
    private void revival$endHeal(float amount, CallbackInfo ci) { RevivalManager.endHeal(); }
}
