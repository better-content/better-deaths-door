package com.bettercontent.downedplayerrevival.network;

import com.bettercontent.downedplayerrevival.state.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Only active counts are sent to clients rendering another player's model. */
public record MaimSyncPacket(UUID playerId, int[] counts) {
    public MaimSyncPacket { counts = counts.clone(); }
    public static MaimSyncPacket from(BodySnapshot snapshot) {
        int[] counts = new int[Region.values().length * MaimType.values().length];
        for (Maim maim : snapshot.activeMaims())
            counts[maim.region().ordinal() * MaimType.values().length + maim.type().ordinal()]++;
        return new MaimSyncPacket(snapshot.playerId(), counts);
    }
    public static MaimSyncPacket empty(UUID playerId) {
        return new MaimSyncPacket(playerId, new int[Region.values().length * MaimType.values().length]);
    }
    public int count(Region region, MaimType type) {
        return counts[region.ordinal() * MaimType.values().length + type.ordinal()];
    }
    public static void encode(MaimSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.playerId);
        for (int count : packet.counts) buf.writeVarInt(count);
    }
    public static MaimSyncPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int[] counts = new int[Region.values().length * MaimType.values().length];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = buf.readVarInt();
            if (counts[i] < 0 || counts[i] > 1_000_000)
                throw new IllegalArgumentException("Invalid injury count");
        }
        return new MaimSyncPacket(id, counts);
    }
    public static void handle(MaimSyncPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> com.bettercontent.downedplayerrevival.client.ClientRevivalState.acceptMaims(packet));
        context.get().setPacketHandled(true);
    }
}
