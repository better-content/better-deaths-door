package com.bettercontent.betterdeathsdoor.client;

import com.bettercontent.betterdeathsdoor.DamageLedger;
import com.bettercontent.betterdeathsdoor.network.*;
import com.bettercontent.betterdeathsdoor.state.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BodyViewCodecTest {
    @Test void allMaimTypesAndLargeCountsSurviveSyncAndRecap() {
        var regions = Arrays.stream(Region.values()).map(region -> {
            Map<MaimType, Integer> active = new EnumMap<>(MaimType.class);
            Map<MaimType, Integer> treated = new EnumMap<>(MaimType.class);
            for (MaimType type : MaimType.values()) {
                active.put(type, 100_000 + type.ordinal());
                treated.put(type, 700_000 + type.ordinal());
            }
            return new BodyView.RegionView(region, active, .137, treated);
        }).toList();
        var original = new BodyView(UUID.randomUUID(), "Teammate", 0, 37, true, 32, 12, 3600,
                .42, .75, 7.33, regions, true);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BodyView.write(original, buffer);
            assertEquals(original, BodyView.read(buffer));
            assertEquals(0, buffer.readableBytes());
            assertEquals("3 min", original.traumaLifetimeLabel());
        } finally { buffer.release(); }
        var recap = new StateSyncPacket(original, 2,
                new DamageLedger.Summary(27, 122.5, 30, 18, 60.5, 43.5, 14));
        var recapBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            StateSyncPacket.encode(recap, recapBuffer);
            assertEquals(recap, StateSyncPacket.decode(recapBuffer));
            assertEquals(0, recapBuffer.readableBytes());
        } finally { recapBuffer.release(); }
    }

    @Test void invalidCountsAreRejectedAndVisualSyncTracksEveryRegionAndType() {
        var regions = Arrays.stream(Region.values()).map(region -> {
            Map<MaimType, Integer> counts = new EnumMap<>(MaimType.class);
            counts.put(MaimType.values()[0], -1);
            return new BodyView.RegionView(region, counts, 0, Map.of());
        }).toList();
        var invalid = new BodyView(UUID.randomUUID(), "Review", 20, 20, false, 0, 0, 1200,
                0, .5, 2, regions, false);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BodyView.write(invalid, buffer);
            assertThrows(IllegalArgumentException.class, () -> BodyView.read(buffer));
        } finally { buffer.release(); }
        int[] counts = new int[Region.values().length * MaimType.values().length];
        counts[Region.RIGHT_LEG.ordinal() * MaimType.values().length + MaimType.values().length - 1] = 3;
        var visual = new MaimSyncPacket(UUID.randomUUID(), counts);
        var visualBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MaimSyncPacket.encode(visual, visualBuffer);
            var decoded = MaimSyncPacket.decode(visualBuffer);
            assertEquals(3, decoded.count(Region.RIGHT_LEG, MaimType.values()[MaimType.values().length - 1]));
            assertEquals(0, visualBuffer.readableBytes());
        } finally { visualBuffer.release(); }
    }
}
