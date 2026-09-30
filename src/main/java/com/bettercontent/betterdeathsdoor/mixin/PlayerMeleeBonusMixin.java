package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.RevivalManager;
import com.bettercontent.betterdeathsdoor.state.Region;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Attack damage attributes already carry impairment; only additive vanilla bonuses remain here. */
@Mixin(Player.class)
public abstract class PlayerMeleeBonusMixin {
    private float revival$armFactor() {
        return (Object) this instanceof ServerPlayer player
            ? (float) (1 - RevivalManager.snapshot(player).regionalReduction(Region.LEFT_ARM)) : 1;
    }

    @Redirect(method = "attack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getDamageBonus(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/MobType;)F"), require = 2)
    private float revival$enchantmentDamage(ItemStack weapon, MobType targetType) {
        return EnchantmentHelper.getDamageBonus(weapon, targetType) * revival$armFactor();
    }

    @ModifyArg(method = "attack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 1)
    private float revival$sweepingFloor(float damage) {
        // Vanilla computes 1 + sweepingRatio * already-impaired primary damage.
        return Math.max(0, damage - (1 - revival$armFactor()));
    }
}
