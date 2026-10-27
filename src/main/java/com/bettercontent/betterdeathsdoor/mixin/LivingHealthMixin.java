package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.RevivalManager;
import com.bettercontent.betterdeathsdoor.nativecommit.NativeStoreScope;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Native primitive-write boundaries; no competing HEAD priority or early-return heal cancellation. */
@Mixin(LivingEntity.class)
public abstract class LivingHealthMixin {
    @SuppressWarnings("unchecked")
    @Redirect(method = "setHealth", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/syncher/SynchedEntityData;set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V"),
            require = 1, allow = 1)
    private void revival$writeHealth(SynchedEntityData data, EntityDataAccessor<?> key, Object nativeValue,
                                     float requested) {
        // This exact call is DATA_HEALTH_ID with the native Mth.clamp result. The delegate
        // remains the real setter, including sync callbacks/exceptions, inside try/finally.
        var healthKey = (EntityDataAccessor<Float>) key;
        var entity = (LivingEntity) (Object) this;
        NativeStoreScope.GLOBAL.rememberHealthKey(healthKey);
        RevivalManager.writeHealth(entity, requested, (Float) nativeValue, value -> {
            try (var ignored = NativeStoreScope.GLOBAL.openDelegate(entity, data, healthKey)) {
                data.set(healthKey, value);
            }
        });
    }

    @Redirect(method = "heal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"), require = 1, allow = 1)
    private void revival$writeEarnedHeal(LivingEntity entity, float requested, float amount) {
        // Appended enclosing argument is LIVE slot 1: ForgeEventFactory.onLivingHeal has
        // already returned and stored the canceled/modified amount there on pinned 47.4.22.
        RevivalManager.writeHeal(entity, requested, amount);
    }
}
