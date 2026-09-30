package com.bettercontent.betterdeathsdoor.network;

import com.bettercontent.betterdeathsdoor.state.MaimType;
import com.bettercontent.betterdeathsdoor.state.Region;
import net.minecraft.network.FriendlyByteBuf;
import java.util.*;

/** Compact current-life view. Historical totals remain in each region for the death recap. */
public record BodyView(UUID playerId, String name, float health, float maxHealth, boolean atDoor,
        int healingLockTicks, int trauma, int traumaLifetimeTicks, double probability,
        double multiplier, double treatmentSeconds, List<RegionView> regions, boolean treatmentActive) {
    public record RegionView(Region region, Map<MaimType, Integer> active, double reduction,
            Map<MaimType, Integer> treatedCounts) {
        public RegionView {
            active = Map.copyOf(active);
            treatedCounts = Map.copyOf(treatedCounts);
        }
        public int count(MaimType type) { return active.getOrDefault(type, 0); }
        public int treatedCount(MaimType type) { return treatedCounts.getOrDefault(type, 0); }
        public int total() { return active.values().stream().mapToInt(Integer::intValue).sum(); }
        public int treated() { return treatedCounts.values().stream().mapToInt(Integer::intValue).sum(); }
    }
    public BodyView { regions = List.copyOf(regions); }
    public RegionView region(Region region) { return regions.get(region.ordinal()); }

    public static void write(BodyView view, FriendlyByteBuf buf) {
        buf.writeUUID(view.playerId); buf.writeUtf(view.name, 128);
        buf.writeFloat(view.health); buf.writeFloat(view.maxHealth); buf.writeBoolean(view.atDoor);
        buf.writeVarInt(view.healingLockTicks); buf.writeVarInt(view.trauma);
        buf.writeVarInt(view.traumaLifetimeTicks); buf.writeDouble(view.probability);
        buf.writeDouble(view.multiplier); buf.writeDouble(view.treatmentSeconds);
        for (RegionView region : view.regions) {
            for (MaimType type : MaimType.values()) buf.writeVarInt(region.count(type));
            buf.writeDouble(region.reduction);
            for (MaimType type : MaimType.values()) buf.writeVarInt(region.treatedCount(type));
        }
        buf.writeBoolean(view.treatmentActive);
    }

    public static BodyView read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID(); String name = buf.readUtf(128);
        float health = buf.readFloat(), max = buf.readFloat(); boolean door = buf.readBoolean();
        int lock = buf.readVarInt(), trauma = buf.readVarInt(), lifetime = buf.readVarInt();
        double odds = buf.readDouble(), multiplier = buf.readDouble(), seconds = buf.readDouble();
        List<RegionView> regions = new ArrayList<>();
        for (Region region : Region.values()) {
            Map<MaimType, Integer> active = new EnumMap<>(MaimType.class);
            for (MaimType type : MaimType.values()) active.put(type, checkedCount(buf.readVarInt()));
            double reduction = buf.readDouble();
            Map<MaimType, Integer> treated = new EnumMap<>(MaimType.class);
            for (MaimType type : MaimType.values()) treated.put(type, checkedCount(buf.readVarInt()));
            regions.add(new RegionView(region, active, reduction, treated));
        }
        return new BodyView(id, name, health, max, door, lock, trauma, lifetime, odds,
                multiplier, seconds, regions, buf.readBoolean());
    }
    private static int checkedCount(int count) {
        if (count < 0 || count > 1_000_000) throw new IllegalArgumentException("Invalid injury count");
        return count;
    }
    public String traumaLifetimeLabel() {
        return traumaLifetimeTicks % 1200 == 0 ? traumaLifetimeTicks / 1200 + " min"
                : String.format(Locale.ROOT, "%.1fs", traumaLifetimeTicks / 20.0);
    }
    public static String label(Enum<?> value) {
        String name = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
