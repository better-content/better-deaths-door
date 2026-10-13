package com.bettercontent.betterdeathsdoor.network;

import com.bettercontent.betterdeathsdoor.records.*;
import com.bettercontent.betterdeathsdoor.client.ClientLifeRecords;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Fixed ten counters, at most three cards/five badges. Decoder never accepts NBT or strings. */
public record LifeRecapPacket(LifeRecap recap) {
    public static void encode(LifeRecapPacket packet, FriendlyByteBuf buf) {
        LifeRecap r = packet.recap;
        buf.writeVarInt(r.version()); buf.writeUUID(r.lifeId()); buf.writeBoolean(r.partial()); buf.writeBoolean(r.eligible());
        r.values().forEach(buf::writeDouble); r.previousBests().forEach(buf::writeDouble);
        buf.writeVarInt(r.cards().size());
        for (LifeRecap.Card c : r.cards()) {
            buf.writeVarInt(c.metric().ordinal()); buf.writeVarInt(c.milestoneTier()); buf.writeVarInt(c.badges().size());
            c.badges().forEach(b -> buf.writeVarInt(b.ordinal()));
        }
    }
    public static LifeRecapPacket decode(FriendlyByteBuf buf) {
        int version = buf.readVarInt(); if (version != LifeRecap.VERSION) throw new IllegalArgumentException("Unknown life packet version");
        UUID id = buf.readUUID(); boolean partial = buf.readBoolean(), eligible = buf.readBoolean();
        List<Double> values = new ArrayList<>(), bests = new ArrayList<>();
        for (int i = 0; i < 10; i++) values.add(buf.readDouble());
        for (int i = 0; i < 10; i++) bests.add(buf.readDouble());
        int count = bounded(buf.readVarInt(), 3); List<LifeRecap.Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LifeMetric m = LifeMetric.values()[bounded(buf.readVarInt(), 9)]; int tier = bounded(buf.readVarInt(), 3);
            int badgeCount = bounded(buf.readVarInt(), 5); List<LifeRecap.Badge> badges = new ArrayList<>();
            for (int j = 0; j < badgeCount; j++) badges.add(LifeRecap.Badge.values()[bounded(buf.readVarInt(), 4)]);
            cards.add(new LifeRecap.Card(m, values.get(m.ordinal()), bests.get(m.ordinal()), badges, tier));
        }
        return new LifeRecapPacket(new LifeRecap(version, id, partial, eligible, values, bests, cards));
    }
    private static int bounded(int value, int max) { if (value < 0 || value > max) throw new IllegalArgumentException("Life packet bound"); return value; }
    public static void handle(LifeRecapPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientLifeRecords.accept(packet.recap)));
        context.setPacketHandled(true);
    }
}
