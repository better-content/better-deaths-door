package com.bettercontent.betterdeathsdoor.network;

import com.bettercontent.betterdeathsdoor.records.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LifeRecapPacketTest {
    @Test void boundedRoundTrip() {
        List<Double> values = Collections.nCopies(10, 10d), bests = Collections.nCopies(10, 1d);
        List<LifeRecap.Card> cards = new ArrayList<>();
        for (LifeMetric m : List.of(LifeMetric.CHECKS_SURVIVED, LifeMetric.DOOR_ENTRIES, LifeMetric.INJURIES_TREATED))
            cards.add(new LifeRecap.Card(m, 10, 1, List.of(LifeRecap.Badge.BEST, LifeRecap.Badge.MILESTONE), 3));
        LifeRecap recap = new LifeRecap(1, UUID.randomUUID(), false, true, values, bests, cards);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            LifeRecapPacket.encode(new LifeRecapPacket(recap), buf);
            assertTrue(buf.readableBytes() < 256, "Packet must remain fixed and bounded");
            assertEquals(recap, LifeRecapPacket.decode(buf).recap()); assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }
    @Test void rejectVersionAndUnboundedCardsBeforeAllocating() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(2); assertThrows(IllegalArgumentException.class, () -> LifeRecapPacket.decode(buf));
            buf.clear(); header(buf, 0); buf.writeVarInt(4);
            assertThrows(IllegalArgumentException.class, () -> LifeRecapPacket.decode(buf));
        } finally { buf.release(); }
    }
    @Test void rejectNanAndBadEnums() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            header(buf, Double.NaN); buf.writeVarInt(0);
            assertThrows(IllegalArgumentException.class, () -> LifeRecapPacket.decode(buf));
            buf.clear(); header(buf, 0); buf.writeVarInt(1); buf.writeVarInt(10);
            assertThrows(IllegalArgumentException.class, () -> LifeRecapPacket.decode(buf));
        } finally { buf.release(); }
    }
    private static void header(FriendlyByteBuf buf, double value) {
        buf.writeVarInt(1); buf.writeUUID(UUID.randomUUID()); buf.writeBoolean(false); buf.writeBoolean(true);
        for (int i = 0; i < 20; i++) buf.writeDouble(value);
    }
}
