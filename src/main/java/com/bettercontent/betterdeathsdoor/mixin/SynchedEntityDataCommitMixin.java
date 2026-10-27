package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.nativecommit.NativeStoreScope;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Both pinned native DataItem.setValue callsites. No cancellation or payment here. */
@Mixin(SynchedEntityData.class)
public abstract class SynchedEntityDataCommitMixin {
    @Shadow @Final private Entity entity;

    @Redirect(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/syncher/SynchedEntityData$DataItem;setValue(Ljava/lang/Object;)V"),
            require = 1, allow = 1)
    private <T> void revival$bindSet(SynchedEntityData.DataItem<T> item, T value,
                                    EntityDataAccessor<T> key, T enclosingValue, boolean force) {
        if (entity == null || key == null || item == null) { item.setValue(value); return; }
        var scopes = NativeStoreScope.GLOBAL;
        boolean serverHealth = entity instanceof ServerPlayer && (!scopes.healthKeyKnown() || scopes.isHealthKey(key));
        try (var ignored = scopes.openItem(entity, this, key, item, serverHealth, NativeStoreScope.Route.SET)) {
            item.setValue(value); // The REAL method, including any earlier hooks/exceptions.
        }
    }

    @Redirect(method = "assignValue(Lnet/minecraft/network/syncher/SynchedEntityData$DataItem;Lnet/minecraft/network/syncher/SynchedEntityData$DataValue;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/syncher/SynchedEntityData$DataItem;setValue(Ljava/lang/Object;)V"),
            require = 1, allow = 1)
    private <T> void revival$bindAssignment(SynchedEntityData.DataItem<T> item, T value) {
        if (item == null) { item.setValue(value); return; }
        var scopes = NativeStoreScope.GLOBAL;
        var key = item.getAccessor();
        if (entity == null || key == null) { item.setValue(value); return; }
        boolean serverHealth = entity instanceof ServerPlayer && (!scopes.healthKeyKnown() || scopes.isHealthKey(key));
        try (var ignored = scopes.openItem(entity, this, key, item, serverHealth, NativeStoreScope.Route.ASSIGN)) {
            item.setValue(value);
        }
    }
}
