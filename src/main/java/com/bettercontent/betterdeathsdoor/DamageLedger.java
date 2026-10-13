package com.bettercontent.betterdeathsdoor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayDeque;
import java.util.Deque;

/** Per-life accepted-hit accounting at the actual Player.actuallyHurt call sites. */
public final class DamageLedger {
    private static final String KEY = "better_deaths_door:damage_ledger";
    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private DamageLedger() {}

    private static final class Frame {
        final ServerPlayer player;
        final float incoming, healthBefore, maxHealthBefore, absorptionBefore;
        final boolean atDoorBefore;
        float admitted, unabsorbed, applied;
        boolean reachedDamage, committed;
        Frame(ServerPlayer player, float incoming) {
            this.player = player; this.incoming = incoming;
            healthBefore = player.getHealth(); maxHealthBefore = player.getMaxHealth();
            absorptionBefore = player.getAbsorptionAmount();
            atDoorBefore = RevivalManager.state(player).atDoor();
        }
    }

    public record Summary(long hits, double incoming, double mitigation, double absorption,
                          double applied, double healthLost, double unknown) {
        public static Summary empty() { return new Summary(0, 0, 0, 0, 0, 0, 0); }
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("hits", hits); tag.putDouble("incoming", incoming);
            tag.putDouble("mitigation", mitigation); tag.putDouble("absorption", absorption);
            tag.putDouble("applied", applied); tag.putDouble("healthLost", healthLost);
            tag.putDouble("unknown", unknown);
            return tag;
        }
        public static Summary load(CompoundTag tag) {
            return new Summary(tag.getLong("hits"), tag.getDouble("incoming"), tag.getDouble("mitigation"),
                tag.getDouble("absorption"), tag.getDouble("applied"), tag.getDouble("healthLost"), tag.getDouble("unknown"));
        }
    }

    /** Pure accumulation used by the live hook and by boundary tests. */
    static Summary accountHit(Summary old, float incoming, float admitted, float unabsorbed, float applied,
                              float absorptionBefore, float absorptionAfter, float healthBefore, float healthAfter) {
        double absorbed = Math.max(0, absorptionBefore - absorptionAfter);
        double postMagic = Math.max(0, unabsorbed) + absorbed;
        double mitigated = Math.max(0, admitted - postMagic);
        double lost = Math.max(0, healthBefore - healthAfter);
        double unknown = incoming - mitigated - absorbed - applied;
        return new Summary(old.hits + 1, old.incoming + incoming, old.mitigation + mitigated,
            old.absorption + absorbed, old.applied + applied, old.healthLost + lost, old.unknown + unknown);
    }

    public static Summary snapshot(ServerPlayer player) { return Summary.load(player.getPersistentData().getCompound(KEY)); }
    public static void cloneLife(ServerPlayer old, ServerPlayer replacement, boolean death) {
        if (death) replacement.getPersistentData().remove(KEY);
        else replacement.getPersistentData().put(KEY, snapshot(old).save());
    }
    public static void begin(ServerPlayer player, float incoming) { FRAMES.get().push(new Frame(player, incoming)); }
    public static void admitted(ServerPlayer player, float amount) {
        Frame frame = current(player); if (frame != null) frame.admitted = amount;
    }
    public static void damaged(ServerPlayer player, float unabsorbed, float applied) {
        Frame frame = current(player);
        if (frame != null) { frame.unabsorbed = unabsorbed; frame.applied = applied; frame.reachedDamage = true; }
    }
    /** Accepted damage, captured before bodily modifiers can change maximum HP. */
    public record AcceptedHit(float healthBefore, float maxHealthBefore, float hpDamage) {}
    public static AcceptedHit acceptedHit(ServerPlayer player) {
        Frame frame = current(player);
        return frame == null || !frame.reachedDamage ? null
            : new AcceptedHit(frame.healthBefore, frame.maxHealthBefore, frame.applied);
    }
    public static float absorbedInCurrentHit(ServerPlayer player) {
        Frame frame = current(player);
        return frame == null ? 0 : Math.max(0, frame.absorptionBefore - player.getAbsorptionAmount());
    }
    public static void finish(ServerPlayer player) {
        Deque<Frame> stack = FRAMES.get();
        if (stack.isEmpty()) return;
        Frame frame = stack.pop();
        if (stack.isEmpty()) FRAMES.remove();
        if (frame.player != player) return;
        commit(player, frame);
    }
    /** FinalDeath occurs inside actuallyHurt, before its RETURN hook. Commit lethal damage once. */
    public static void commitCurrent(ServerPlayer player) {
        Frame frame = current(player); if (frame != null) commit(player, frame);
    }
    private static void commit(ServerPlayer player, Frame frame) {
        if (!frame.reachedDamage || frame.committed) return;
        frame.committed = true;
        double lost = semanticHpLoss(frame.applied, frame.healthBefore, player.getHealth(), frame.atDoorBefore, RevivalManager.state(player).atDoor());
        com.bettercontent.betterdeathsdoor.records.LifeRecords.acceptedHpLoss(player, lost);
        Summary old = snapshot(player);
        Summary next = accountHit(old, frame.incoming, frame.admitted, frame.unabsorbed, frame.applied,
            frame.absorptionBefore, player.getAbsorptionAmount(), frame.healthBefore, player.getHealth());
        player.getPersistentData().put(KEY, next.save());
    }
    /** Records use real bodily HP; the internal Door sentinel is neither HP lost nor HP remaining. */
    static double semanticHpLoss(float accepted, float before, float after, boolean beforeDoor, boolean afterDoor) {
        if (!Float.isFinite(accepted) || !Float.isFinite(before) || !Float.isFinite(after)) return 0;
        double bodilyBefore = beforeDoor ? 0 : Math.max(0, before);
        double bodilyAfter = afterDoor ? 0 : Math.max(0, after);
        return Math.min(Math.max(0, accepted), Math.max(0, bodilyBefore - bodilyAfter));
    }
    private static Frame current(ServerPlayer player) {
        Deque<Frame> stack = FRAMES.get();
        Frame frame = stack.peek(); return frame != null && frame.player == player ? frame : null;
    }
}
