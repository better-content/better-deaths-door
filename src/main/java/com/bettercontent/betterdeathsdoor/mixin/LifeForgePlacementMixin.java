package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.records.PlacementTransactions;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ForgeHooks.class, remap = false)
public abstract class LifeForgePlacementMixin {
    @WrapMethod(method = "onPlaceItemIntoWorld", remap = false)
    private static InteractionResult life$committedPlacement(UseOnContext context, Operation<InteractionResult> original) {
        return PlacementTransactions.forge(context, () -> original.call(context));
    }
}
