package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.compat.EpicInjuryCompat;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Pseudo
@Mixin(targets = "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch", remap = false)
public abstract class EpicReachMixin {
    @Inject(method = "getReach", at = @At("RETURN"), cancellable = true)
    private void revival$advertiseReach(InteractionHand hand, CallbackInfoReturnable<Float> result) {
        result.setReturnValue((float) (result.getReturnValue() * EpicInjuryCompat.factor((LivingEntityPatch<?>) (Object) this)));
    }
}
