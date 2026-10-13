package com.bettercontent.betterdeathsdoor.records;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import java.util.*;
import java.util.function.Supplier;

/** BlockItem outcome is staged until Forge's entire snapshot/event/rollback transaction returns. */
public final class PlacementTransactions {
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final class Frame { final UseOnContext context; boolean placed; Frame(UseOnContext c) { context = c; } }
    private PlacementTransactions() {}
    public static InteractionResult forge(UseOnContext context, Supplier<InteractionResult> action) {
        Deque<Frame> stack = FRAMES.get(); Frame frame = new Frame(context); stack.push(frame);
        try {
            InteractionResult outcome = action.get();
            if (outcome.consumesAction() && frame.placed && context.getPlayer() instanceof ServerPlayer player)
                LifeRecords.add(player, LifeMetric.BLOCKS_PLACED, 1);
            return outcome;
        } finally { stack.pop(); if (stack.isEmpty()) FRAMES.remove(); }
    }
    public static void placed(UseOnContext context, InteractionResult outcome) {
        if (!outcome.consumesAction()) return;
        Frame frame = FRAMES.get().peek();
        if (frame != null && frame.context.getPlayer() == context.getPlayer()) frame.placed = true;
        else if (context.getPlayer() instanceof ServerPlayer player) LifeRecords.add(player, LifeMetric.BLOCKS_PLACED, 1);
        if (frame == null) FRAMES.remove();
    }
}
