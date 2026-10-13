package com.bettercontent.betterdeathsdoor.mixin;

import com.bettercontent.betterdeathsdoor.records.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.*;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerStatsCounter.class)
public abstract class LifeNativeStatsMixin {
    @WrapMethod(method = "setValue")
    private void life$committedStat(Player player, Stat<?> stat, int requested, Operation<Void> original) {
        ServerStatsCounter counter = (ServerStatsCounter) (Object) this;
        int before = counter.getValue(stat);
        original.call(player, stat, requested);
        int after = counter.getValue(stat);
        if (!(player instanceof ServerPlayer serverPlayer) || after <= before) return;
        if (stat.getType() == Stats.BLOCK_MINED) LifeRecords.add(serverPlayer, LifeMetric.BLOCKS_MINED, (long) after - before);
        else if (stat.equals(Stats.CUSTOM.get(Stats.MOB_KILLS))) LifeRecords.add(serverPlayer, LifeMetric.MOB_KILLS, (long) after - before);
    }
}
