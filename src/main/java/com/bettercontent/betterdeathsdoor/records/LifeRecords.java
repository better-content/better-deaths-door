package com.bettercontent.betterdeathsdoor.records;

import com.bettercontent.betterdeathsdoor.RevivalManager;
import com.bettercontent.betterdeathsdoor.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;

/** Logical-server adapter. Arena is supplied by its native owner, without reflection or guessed tags. */
public final class LifeRecords {
    private static Predicate<ServerPlayer> arena = player -> false;
    private static Predicate<ServerPlayer> arenaCollection = player -> false;
    private record DeathAttempt(int xp, boolean arena) {}
    private static final Map<ServerPlayer, DeathAttempt> PENDING = new IdentityHashMap<>();
    private LifeRecords() {}
    public static void registerArenaEligibility(Predicate<ServerPlayer> provider) { registerArenaEligibility(provider, provider); }
    public static void registerArenaEligibility(Predicate<ServerPlayer> participant, Predicate<ServerPlayer> collectionSuspended) {
        arena = Objects.requireNonNull(participant); arenaCollection = Objects.requireNonNull(collectionSuspended);
    }
    public static boolean inArena(ServerPlayer player) { return arena.test(player); }
    private static LifeRecord record(ServerPlayer p) {
        LifeRecordsData data = LifeRecordsData.get(p.server);
        LifeRecord present = data.find(p.getUUID()); if (present != null) return present;
        boolean existing = Files.exists(p.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID() + ".dat"));
        return data.getOrCreate(p.getUUID(), existing);
    }
    private static void dirty(ServerPlayer p) { LifeRecordsData.get(p.server).setDirty(); }
    private static boolean collecting(ServerPlayer p) {
        if (p instanceof net.minecraftforge.common.util.FakePlayer) return false;
        LifeRecord r = record(p);
        GameType mode = p.gameMode.getGameModeForPlayer();
        if (mode == GameType.CREATIVE || mode == GameType.ADVENTURE) { r.invalidate(); dirty(p); }
        return mode == GameType.SURVIVAL && !arenaCollection.test(p) && !r.containedDeath && !r.finalized;
    }
    public static void login(ServerPlayer p) { if (!(p instanceof net.minecraftforge.common.util.FakePlayer)) record(p); }
    public static void tick(ServerPlayer p) {
        if (p instanceof net.minecraftforge.common.util.FakePlayer) return;
        if (p.isAlive() && !RevivalManager.isFinalized(p) && collecting(p)) {
            LifeRecord r = record(p); r.add(LifeMetric.DURATION, 1); r.level(p.experienceLevel); dirty(p);
        }
        if (RevivalManager.isFinalized(p) && p.tickCount % 20 == 0) sendRecap(p);
    }
    public static void add(ServerPlayer p, LifeMetric metric, double amount) {
        if (!p.isAlive()) return;
        if (!collecting(p)) return;
        record(p).add(metric, amount); dirty(p);
    }
    /** Only DamageLedger's committed accepted outcome calls this, including after its frame is popped. */
    public static void acceptedHpLoss(ServerPlayer p, double amount) {
        if (!collecting(p)) return;
        record(p).add(LifeMetric.HP_LOST, amount); dirty(p);
    }
    public static void observeLevel(ServerPlayer p) {
        // Mode changes invalidate even a dead life, but XP maxima only observe an alive player.
        boolean collect = collecting(p);
        if (collect && p.isAlive()) { record(p).level(p.experienceLevel); dirty(p); }
    }
    /** die HEAD: before vanilla clears XP and before Arena's native death listener removes its match. */
    public static void beginDeath(ServerPlayer p) {
        if (p instanceof net.minecraftforge.common.util.FakePlayer) return;
        PENDING.put(p, new DeathAttempt(p.experienceLevel, inArena(p)));
    }
    public static void canceledDeath(ServerPlayer p) { PENDING.remove(p); }
    public static void finalDeath(ServerPlayer p) {
        if (p instanceof net.minecraftforge.common.util.FakePlayer) return;
        DeathAttempt pending = PENDING.remove(p);
        LifeRecord r = record(p);
        if ((pending != null && pending.arena) || inArena(p)) { r.containedDeath = true; dirty(p); return; }
        if (collecting(p)) r.level(pending == null ? p.experienceLevel : pending.xp);
        r.freeze(); dirty(p); sendRecap(p);
    }
    public static void clonePlayer(ServerPlayer old, ServerPlayer next, boolean death) {
        if (old instanceof net.minecraftforge.common.util.FakePlayer || next instanceof net.minecraftforge.common.util.FakePlayer) return;
        PENDING.remove(old);
        LifeRecord r = record(old);
        if (death) {
            if (!r.containedDeath && !inArena(old)) r.nextLife();
            else r.containedDeath = false;
            dirty(old);
        }
    }
    public static void sendRecap(ServerPlayer p) {
        if (p instanceof net.minecraftforge.common.util.FakePlayer) return;
        LifeRecord r = record(p);
        if (r.finalized && r.latest() != null) RevivalNetwork.send(p, new LifeRecapPacket(r.latest()));
    }
    public static void logout(ServerPlayer p) { PENDING.remove(p); }
    public static void shutdown() { PENDING.clear(); }
}
