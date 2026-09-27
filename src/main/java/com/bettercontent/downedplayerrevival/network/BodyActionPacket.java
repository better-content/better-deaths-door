package com.bettercontent.downedplayerrevival.network;

import com.bettercontent.downedplayerrevival.RevivalManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record BodyActionPacket(UUID subject, int action) {
    public static final int OPEN_MEND = 0, START_TREATMENT = 1, CANCEL_TREATMENT = 2, CLOSE = 3;
    public static BodyActionPacket open(UUID subject) { return new BodyActionPacket(subject, OPEN_MEND); }
    public boolean validShape() { return subject != null && action >= OPEN_MEND && action <= CLOSE; }
    public static void encode(BodyActionPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.subject); buf.writeVarInt(packet.action);
    }
    public static BodyActionPacket decode(FriendlyByteBuf buf) {
        return new BodyActionPacket(buf.readUUID(), buf.readVarInt());
    }
    public static void handle(BodyActionPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var viewer = context.get().getSender();
            if (viewer == null || !packet.validShape()) return;
            if (packet.action == CLOSE || packet.action == CANCEL_TREATMENT) {
                if (!RevivalNetwork.isViewing(viewer, packet.subject)) return;
                if (packet.action == CLOSE) RevivalManager.closeBody(viewer);
                else RevivalManager.cancelTreatment(viewer, "Treatment canceled");
                return;
            }
            var subject = viewer.server.getPlayerList().getPlayer(packet.subject);
            if (subject == null || !RevivalNetwork.canInspect(viewer, subject)) return;
            if (packet.action == OPEN_MEND) RevivalManager.openBody(viewer, subject);
            else RevivalManager.startTreatment(viewer, subject);
        });
        context.get().setPacketHandled(true);
    }
}
