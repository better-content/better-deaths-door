package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.RevivalManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerDeathMixin {
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void revival$onlyOneDeath(DamageSource source, CallbackInfo ci) {
        if (RevivalManager.isFinalized((ServerPlayer) (Object) this)) ci.cancel();
    }

    @Redirect(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/ForgeHooks;onLivingDeath(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)Z", remap = false))
    private boolean revival$confirmedDeath(LivingEntity entity, DamageSource source) {
        boolean canceled = ForgeHooks.onLivingDeath(entity, source);
        if (!canceled) RevivalManager.finalDeath((ServerPlayer) entity, source);
        else RevivalManager.canceledDeath((ServerPlayer) entity);
        return canceled;
    }
}
