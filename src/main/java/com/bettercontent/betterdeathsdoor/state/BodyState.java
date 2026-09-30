package com.bettercontent.betterdeathsdoor.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/** Mutable current-life state, accessed on the authoritative server thread only. */
public final class BodyState {
    public static final String ROOT_TAG = "better_deaths_door:body";
    private boolean atDoor;
    private long healingLockedUntil;
    private long nextMaimId = 1;
    private final List<Maim> activeMaims = new ArrayList<>();
    private final List<TreatmentRecord> treatmentHistory = new ArrayList<>();
    private final List<Long> traumaExpiries = new ArrayList<>();

    public boolean atDoor() { return atDoor; }
    public long healingLockedUntil() { return healingLockedUntil; }
    public boolean healingLocked(long now) { return atDoor && now < healingLockedUntil; }
    public List<Maim> activeMaims() { return List.copyOf(activeMaims); }
    public List<TreatmentRecord> treatmentHistory() { return List.copyOf(treatmentHistory); }
    public List<Long> traumaExpiries() { return List.copyOf(traumaExpiries); }
    public int count(Region region) { return (int) activeMaims.stream().filter(m -> m.region() == region).count(); }

    public void enterDoor(long now) { enterDoor(now, BodyTuning.DEFAULT); }
    public void enterDoor(long now, BodyTuning tuning) {
        if (atDoor) return;
        atDoor = true;
        healingLockedUntil = now + tuning.healingLockTicks();
    }
    public void exitDoor() { atDoor = false; healingLockedUntil = 0; }
    /** Call only for a real positive healing attempt. */
    public boolean tryHeal(long now) {
        if (healingLocked(now)) return false;
        exitDoor();
        return true;
    }
    public Maim addMaim(Region region, MaimType type, long now) {
        Maim maim = new Maim(nextMaimId++, region, type, now);
        activeMaims.add(maim);
        return maim;
    }
    public Optional<TreatmentRecord> cureOldest(Region region, MaimType type, String itemId, long now) {
        Optional<Maim> target = activeMaims.stream().filter(m -> m.region() == region && m.type() == type)
            .min(Comparator.comparingLong(Maim::tick).thenComparingLong(Maim::id));
        return target.map(maim -> {
            activeMaims.remove(maim);
            TreatmentRecord record = new TreatmentRecord(maim.id(), region, type, itemId, now);
            treatmentHistory.add(record);
            return record;
        });
    }
    public void addTrauma(long now) { addTrauma(now, BodyTuning.DEFAULT); }
    public void addTrauma(long now, BodyTuning tuning) {
        expireTrauma(now);
        traumaExpiries.add(now + tuning.traumaLifetimeTicks());
    }
    public int traumaCount(long now) { return (int) traumaExpiries.stream().filter(expiry -> expiry > now).count(); }
    public boolean expireTrauma(long now) { return traumaExpiries.removeIf(expiry -> expiry <= now); }

    /** The caller already established HP-consuming accepted damage. Absorption-only hits never call this. */
    public HitResult resolveHit(boolean reachesZero, Region region, MaimType type, long now,
                                DoubleSupplier deathRoll, BodyTuning tuning) {
        if (!atDoor) {
            if (!reachesZero) return HitResult.HEALTH_DAMAGE;
            enterDoor(now, tuning);
            addMaim(region, type, now);
            return HitResult.ENTERED_DOOR;
        }
        double chance = BodyRules.deathProbability(activeMaims.size(), count(Region.HEAD), tuning);
        if (chance >= 1 || (chance > 0 && deathRoll.getAsDouble() < chance)) return HitResult.FINAL_DEATH;
        addMaim(region, type, now);
        return HitResult.MAIMED;
    }
    public enum HitResult { HEALTH_DAMAGE, ENTERED_DOOR, MAIMED, FINAL_DEATH }

    public void clear() {
        exitDoor(); activeMaims.clear(); treatmentHistory.clear(); traumaExpiries.clear(); nextMaimId = 1;
    }
    public BodyState copy() {
        BodyState copy = new BodyState();
        copy.atDoor = atDoor; copy.healingLockedUntil = healingLockedUntil; copy.nextMaimId = nextMaimId;
        copy.activeMaims.addAll(activeMaims); copy.treatmentHistory.addAll(treatmentHistory); copy.traumaExpiries.addAll(traumaExpiries);
        return copy;
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1); tag.putBoolean("atDoor", atDoor); tag.putLong("healingLockedUntil", healingLockedUntil);
        tag.putLong("nextMaimId", nextMaimId);
        ListTag maims = new ListTag();
        for (Maim m : activeMaims) { CompoundTag entry = entry(m.id(), m.region(), m.type(), m.tick()); maims.add(entry); }
        tag.put("activeMaims", maims);
        ListTag history = new ListTag();
        for (TreatmentRecord r : treatmentHistory) {
            CompoundTag entry = entry(r.maimId(), r.region(), r.type(), r.tick());
            entry.putString("itemId", r.itemId()); history.add(entry);
        }
        tag.put("treatmentHistory", history);
        tag.putLongArray("traumaExpiries", traumaExpiries);
        return tag;
    }
    private static CompoundTag entry(long id, Region region, MaimType type, long tick) {
        CompoundTag entry = new CompoundTag(); entry.putLong("id", id); entry.putString("region", region.name());
        entry.putString("type", type.name()); entry.putLong("tick", tick); return entry;
    }
    public static BodyState load(CompoundTag tag, long now) {
        BodyState state = new BodyState();
        state.atDoor = tag.getBoolean("atDoor"); state.healingLockedUntil = tag.getLong("healingLockedUntil");
        state.nextMaimId = Math.max(1, tag.getLong("nextMaimId"));
        for (Tag raw : tag.getList("activeMaims", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            try {
                Maim m = new Maim(entry.getLong("id"), Region.valueOf(entry.getString("region")), MaimType.valueOf(entry.getString("type")), entry.getLong("tick"));
                state.activeMaims.add(m); state.nextMaimId = Math.max(state.nextMaimId, m.id() + 1);
            } catch (IllegalArgumentException ignored) { /* Unrecognized future region/type must not corrupt other bodily records. */ }
        }
        for (Tag raw : tag.getList("treatmentHistory", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            try {
                TreatmentRecord record = new TreatmentRecord(entry.getLong("id"), Region.valueOf(entry.getString("region")), MaimType.valueOf(entry.getString("type")), entry.getString("itemId"), entry.getLong("tick"));
                state.treatmentHistory.add(record); state.nextMaimId = Math.max(state.nextMaimId, record.maimId() + 1);
            } catch (IllegalArgumentException ignored) { /* Other readable history survives unknown future entries. */ }
        }
        for (long expiry : tag.getLongArray("traumaExpiries")) if (expiry > now) state.traumaExpiries.add(expiry);
        return state;
    }
}
