package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.compat.EpicInjuryCompat;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import java.util.List;

@Pseudo
@Mixin(targets = "yesman.epicfight.api.animation.types.AttackAnimation$Phase", remap = false)
public abstract class EpicPhaseMixin {
    @Inject(method = "getCollidingEntities", at = @At("HEAD"), cancellable = true)
    private void revival$noDegenerateHits(LivingEntityPatch<?> patch, AttackAnimation animation, float previous, float elapsed, float speed, CallbackInfoReturnable<List<Entity>> result) {
        if (EpicInjuryCompat.factor(patch) <= 0) result.setReturnValue(List.of());
    }
}
