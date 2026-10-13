package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.records.PlacementTransactions;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class LifePlacementMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void life$completedPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> ci) {
        PlacementTransactions.placed(context, ci.getReturnValue());
    }
}
