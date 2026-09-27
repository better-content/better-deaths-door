package com.bettercontent.downedplayerrevival.network;

import com.bettercontent.downedplayerrevival.DamageLedger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Modes: own state, open target view, death recap, update target view. */
public record StateSyncPacket(BodyView view, int mode, DamageLedger.Summary damage) {
    public StateSyncPacket(BodyView view, int mode) { this(view, mode, DamageLedger.Summary.empty()); }
    public static void encode(StateSyncPacket packet, FriendlyByteBuf buf) {
        BodyView.write(packet.view, buf); buf.writeVarInt(packet.mode);
        buf.writeVarLong(packet.damage.hits()); buf.writeDouble(packet.damage.incoming());
        buf.writeDouble(packet.damage.mitigation()); buf.writeDouble(packet.damage.absorption());
        buf.writeDouble(packet.damage.applied()); buf.writeDouble(packet.damage.healthLost());
        buf.writeDouble(packet.damage.unknown());
    }
    public static StateSyncPacket decode(FriendlyByteBuf buf) {
        BodyView view = BodyView.read(buf); int mode = buf.readVarInt();
        return new StateSyncPacket(view, mode, new DamageLedger.Summary(buf.readVarLong(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble()));
    }
    public static void handle(StateSyncPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> com.bettercontent.downedplayerrevival.client.ClientRevivalState.accept(packet));
        context.get().setPacketHandled(true);
    }
}
