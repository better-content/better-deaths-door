package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.records.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class LifeNativePlayerMixin {
    @WrapMethod(method = "eat")
    private ItemStack life$completedFood(Level level, ItemStack stack, Operation<ItemStack> original) {
        Player player = (Player) (Object) this;
        int before = player.getFoodData().getFoodLevel();
        boolean food = stack.isEdible();
        ItemStack result = original.call(level, stack);
        if (player instanceof ServerPlayer serverPlayer && food)
            LifeRecords.add(serverPlayer, LifeMetric.HUNGER_RESTORED, Math.max(0, player.getFoodData().getFoodLevel() - before));
        return result;
    }
    @Inject(method = {"giveExperiencePoints", "giveExperienceLevels"}, at = @At("RETURN"))
    private void life$levelChanged(int amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) LifeRecords.observeLevel(player);
    }
}
