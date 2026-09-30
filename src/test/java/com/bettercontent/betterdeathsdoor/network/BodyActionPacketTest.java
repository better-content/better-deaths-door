package com.bettercontent.betterdeathsdoor.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BodyActionPacketTest {
    private static final UUID SUBJECT = UUID.randomUUID();
    @Test void mendActionsHaveOnlySubjectAndAction() {
        for (int action = BodyActionPacket.OPEN_MEND; action <= BodyActionPacket.CLOSE; action++) {
            var packet = new BodyActionPacket(SUBJECT, action);
            assertTrue(packet.validShape());
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                BodyActionPacket.encode(packet, buffer);
                assertEquals(packet, BodyActionPacket.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
        assertFalse(new BodyActionPacket(SUBJECT, -1).validShape());
        assertFalse(new BodyActionPacket(SUBJECT, BodyActionPacket.CLOSE + 1).validShape());
    }
}
