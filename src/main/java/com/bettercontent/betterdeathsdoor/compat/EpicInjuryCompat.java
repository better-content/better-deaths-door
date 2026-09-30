package com.bettercontent.betterdeathsdoor.compat;

import com.bettercontent.betterdeathsdoor.RevivalManager;
import com.bettercontent.betterdeathsdoor.state.Region;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Loaded only by optional Epic Fight mixins. Never modifies shared weapon geometry. */
public final class EpicInjuryCompat {
    private EpicInjuryCompat() {}
    public static ItemStack usedItem(DamageSource source, ItemStack fallback) {
        return source instanceof yesman.epicfight.world.damagesource.EpicFightDamageSource epic ? epic.getUsedItem() : fallback;
    }
    public static double factor(LivingEntityPatch<?> patch) {
        if (!(patch.getOriginal() instanceof Player player)) return 1;
        if (player instanceof ServerPlayer server) return 1 - RevivalManager.snapshot(server).regionalReduction(Region.LEFT_ARM);
        Double factor = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> Client.factor(player));
        return factor == null ? 1 : factor;
    }
    public static OpenMatrix4f contract(LivingEntityPatch<?> patch, float partialTick) {
        OpenMatrix4f original = patch.getModelMatrix(partialTick);
        float factor = (float) factor(patch);
        if (factor >= 1) return original;
        float pivot = patch.getOriginal().getBbHeight() * .5f;
        return new OpenMatrix4f(original).translate(0, pivot * (1 - factor), 0).scale(factor, factor, factor);
    }
    private static final class Client {
        static double factor(Player player) {
            return com.bettercontent.betterdeathsdoor.client.ClientRevivalState.get(player.getUUID())
                .map(body -> 1 - body.region(Region.LEFT_ARM).reduction()).orElse(1.);
        }
    }
}
