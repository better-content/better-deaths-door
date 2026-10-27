package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.nativecommit.NativeCommitCore;
import com.bettercontent.betterdeathsdoor.nativecommit.NativeStoreScope;
import net.minecraft.network.syncher.SynchedEntityData;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Source spike: one REAL field assignment to the actual PUTFIELD receiver.
 * No provider => original object/value semantics, no health/saturation correction.
 * These merged raw methods are NOT certified authority until final-definition proof.
 */
@Mixin(SynchedEntityData.DataItem.class)
public abstract class HealthDataItemCommitMixin implements NativeStoreScope.RawItem {
    @Shadow private Object value;

    @Override @Unique
    public final Object revival$rawDataValue() { return value; }
    @Override @Unique
    public final void revival$storeRawDataValue(Object proposed) { value = proposed; }

    @Redirect(method = "setValue(Ljava/lang/Object;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/network/syncher/SynchedEntityData$DataItem;value:Ljava/lang/Object;",
                    opcode = Opcodes.PUTFIELD), require = 1, allow = 1)
    private void revival$beforeActualStore(SynchedEntityData.DataItem<?> receiver, Object proposed) {
        var raw = (NativeStoreScope.RawItem) (Object) receiver;
        NativeCommitCore.GLOBAL.beforeStore(receiver, raw.revival$rawDataValue(), proposed);
        raw.revival$storeRawDataValue(proposed); // Exactly one native PUTFIELD, no public setter recursion.
    }
}
