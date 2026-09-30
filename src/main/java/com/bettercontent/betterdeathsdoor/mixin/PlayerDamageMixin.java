package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.RevivalManager;
import com.bettercontent.betterdeathsdoor.DamageLedger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after shields, hurt immunity, armor and resistance, without replacing the damage event. */
@Mixin(Player.class)
public abstract class PlayerDamageMixin {
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void revival$beginDamageRecord(DamageSource source, float amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) DamageLedger.begin(player, amount);
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/ForgeHooks;onLivingHurt(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F", remap = false))
    private float revival$admittedDamage(LivingEntity entity, DamageSource source, float incoming) {
        float admitted = ForgeHooks.onLivingHurt(entity, source, incoming);
        if (entity instanceof ServerPlayer player) DamageLedger.admitted(player, admitted);
        return admitted;
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/ForgeHooks;onLivingDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F", remap = false))
    private float revival$acceptedDamage(LivingEntity entity, DamageSource source, float unabsorbed,
                                         DamageSource enclosingSource, float mitigated) {
        float result = ForgeHooks.onLivingDamage(entity, source, unabsorbed);
        if (entity instanceof ServerPlayer player) DamageLedger.damaged(player, unabsorbed, result);
        if (entity instanceof ServerPlayer player && mitigated > 0 && (result > 0 || unabsorbed == 0)) {
            RevivalManager.acceptedHit(player, source);
        }
        return result;
    }

    @Redirect(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
    private void revival$applyHealth(Player player, float proposed, DamageSource source, float amount) {
        if (player instanceof ServerPlayer serverPlayer) RevivalManager.applyDamageHealth(serverPlayer, source, proposed);
        else player.setHealth(proposed);
    }

    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void revival$refreshAfterHit(DamageSource source, float amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            DamageLedger.finish(player);
            RevivalManager.afterDamage(player);
        }
    }
}
