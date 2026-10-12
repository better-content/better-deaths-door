package com.bettercontent.betterdeathsdoor.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TreatmentStatusPacketTest {
    @Test void subjectAndProgressRoundTripWithoutTrailingBytes() {
        var original = new TreatmentStatusPacket(UUID.randomUUID(), .75f, "Applying treatment");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            TreatmentStatusPacket.encode(original, buffer);
            assertEquals(original, TreatmentStatusPacket.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
