package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.compat.EpicInjuryCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Pseudo
@Mixin(targets = {"yesman.epicfight.api.collider.Collider", "yesman.epicfight.api.collider.MultiCollider"}, remap = false)
public abstract class EpicColliderMixin {
    @Redirect(method = "updateAndSelectCollideEntity", at = @At(value = "INVOKE", target = "Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;getModelMatrix(F)Lyesman/epicfight/api/utils/math/OpenMatrix4f;"))
    private OpenMatrix4f revival$contractReach(LivingEntityPatch<?> patch, float partialTick) {
        return EpicInjuryCompat.contract(patch, partialTick);
    }
}
