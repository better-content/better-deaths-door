package com.bettercontent.betterdeathsdoor.records;

import com.bettercontent.betterdeathsdoor.RevivalMod;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import com.mojang.logging.LogUtils;
import java.util.*;

/** Only this records SavedData is reset on corruption; body, lineage and native stats are untouched. */
public final class LifeRecordsData extends SavedData {
    private static final int SCHEMA = 1;
    private final Map<UUID, LifeRecord> players = new HashMap<>();
    public static LifeRecordsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LifeRecordsData::load, LifeRecordsData::new, RevivalMod.MOD_ID + "_life_records");
    }
    public LifeRecord find(UUID player) { return players.get(player); }
    public LifeRecord getOrCreate(UUID player, boolean partial) {
        LifeRecord old = players.get(player);
        if (old != null) return old;
        LifeRecord created = new LifeRecord(partial); players.put(player, created); setDirty(); return created;
    }
    public static LifeRecordsData load(CompoundTag tag) {
        LifeRecordsData data = new LifeRecordsData();
        try {
            if (!tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != SCHEMA || !tag.contains("players", Tag.TAG_LIST))
                throw new IllegalArgumentException("Unknown/incomplete schema");
            ListTag entries = tag.getList("players", Tag.TAG_COMPOUND);
            for (Tag raw : entries) {
                CompoundTag entry = (CompoundTag) raw;
                UUID uuid = entry.getUUID("player");
                LifeRecord record = new LifeRecord(entry.getBoolean("partial"));
                record.lifeId = entry.getUUID("life"); record.eligible = entry.getBoolean("eligible"); record.finalized = entry.getBoolean("finalized"); record.containedDeath = entry.getBoolean("containedDeath");
                readCounters(entry.getList("values", Tag.TAG_DOUBLE), record.values);
                readCounters(entry.getList("bests", Tag.TAG_DOUBLE), record.bests);
                int[] milestones = entry.getIntArray("milestones"), reached = entry.getIntArray("reachedTiers");
                if (milestones.length != 10 || reached.length != 10) throw new IllegalArgumentException("Milestone length");
                for (int i = 0; i < 10; i++) {
                    if (milestones[i] != LifeMetric.values()[i].tier(record.values[i])) throw new IllegalArgumentException("Milestone value");
                    record.milestoneIds[i] = milestones[i];
                    if (reached[i] < 0 || reached[i] > 3) throw new IllegalArgumentException("Reached tier");
                    record.reachedTiers[i] = reached[i];
                }
                if (entry.contains("recap")) record.latest = readRecap(entry.getCompound("recap"));
                if (record.finalized && (record.latest == null || !record.latest.lifeId().equals(record.lifeId)
                    || !record.latest.values().equals(LifeRecord.list(record.values)) || record.latest.partial() != record.partial
                    || record.latest.eligible() != record.eligible)) throw new IllegalArgumentException("Inconsistent frozen recap");
                if (data.players.put(uuid, record) != null) throw new IllegalArgumentException("Duplicate player");
            }
        } catch (RuntimeException bad) {
            LogUtils.getLogger().warn("Resetting only Death's Door life records: corrupt/unknown records schema ({})", bad.getMessage());
            data.players.clear(); data.setDirty();
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", SCHEMA); ListTag entries = new ListTag();
        players.forEach((uuid, record) -> {
            CompoundTag e = new CompoundTag(); e.putUUID("player", uuid); e.putUUID("life", record.lifeId);
            e.putBoolean("partial", record.partial); e.putBoolean("eligible", record.eligible); e.putBoolean("finalized", record.finalized); e.putBoolean("containedDeath", record.containedDeath);
            e.put("values", counters(LifeRecord.list(record.values))); e.put("bests", counters(LifeRecord.list(record.bests)));
            e.putIntArray("milestones", record.milestoneIds); e.putIntArray("reachedTiers", record.reachedTiers);
            if (record.latest != null) e.put("recap", writeRecap(record.latest)); entries.add(e);
        });
        tag.put("players", entries); return tag;
    }
    private static ListTag counters(List<Double> values) { ListTag list = new ListTag(); values.forEach(v -> list.add(DoubleTag.valueOf(v))); return list; }
    private static void readCounters(ListTag list, double[] destination) {
        if (list.size() != 10) throw new IllegalArgumentException("Counter length");
        for (int i = 0; i < 10; i++) { double value = list.getDouble(i); LifeRecap.check(value); destination[i] = value; }
    }
    public static CompoundTag writeRecap(LifeRecap recap) {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", recap.version()); tag.putUUID("life", recap.lifeId());
        tag.putBoolean("partial", recap.partial()); tag.putBoolean("eligible", recap.eligible());
        tag.put("values", counters(recap.values())); tag.put("bests", counters(recap.previousBests()));
        ListTag cards = new ListTag();
        for (LifeRecap.Card card : recap.cards()) {
            CompoundTag c = new CompoundTag(); c.putInt("metric", card.metric().ordinal()); c.putInt("tier", card.milestoneTier());
            c.putIntArray("badges", card.badges().stream().mapToInt(Enum::ordinal).toArray()); cards.add(c);
        }
        tag.put("cards", cards); return tag;
    }
    public static LifeRecap readRecap(CompoundTag tag) {
        double[] values = new double[10], bests = new double[10];
        readCounters(tag.getList("values", Tag.TAG_DOUBLE), values); readCounters(tag.getList("bests", Tag.TAG_DOUBLE), bests);
        ListTag raw = tag.getList("cards", Tag.TAG_COMPOUND); if (raw.size() > 3) throw new IllegalArgumentException("Card bound");
        List<LifeRecap.Card> cards = new ArrayList<>();
        for (Tag t : raw) {
            CompoundTag c = (CompoundTag) t; LifeMetric metric = LifeMetric.values()[c.getInt("metric")];
            int[] ids = c.getIntArray("badges"); if (ids.length > 5) throw new IllegalArgumentException("Badge bound");
            List<LifeRecap.Badge> badges = Arrays.stream(ids).mapToObj(i -> LifeRecap.Badge.values()[i]).toList();
            cards.add(new LifeRecap.Card(metric, values[metric.ordinal()], bests[metric.ordinal()], badges, c.getInt("tier")));
        }
        return new LifeRecap(tag.getInt("version"), tag.getUUID("life"), tag.getBoolean("partial"), tag.getBoolean("eligible"), LifeRecord.list(values), LifeRecord.list(bests), cards);
    }
}
