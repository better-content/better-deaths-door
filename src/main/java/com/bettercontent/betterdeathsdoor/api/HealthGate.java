package com.bettercontent.betterdeathsdoor.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Optional all-player health-write admission. No physiology dependency or payment policy.
 * Installation is single-owner and must finish during mod initialization, before gameplay.
 * Every write captures the current gate once; that same gate receives its completion.
 */
public interface HealthGate {
    int ABI = 1;
    /** Experimental store-boundary contract. This source spike has NO activation/attestation path. */
    int PRIMITIVE_ABI = 2;

    enum WriteKind {
        HEAL_CALL, DIRECT, DAMAGE_WRITE, DAMAGE_SENTINEL, LEGACY_SENTINEL_INIT, MAX_HEALTH_CLAMP,
        CANCELED_DEATH_ROLLBACK, DEBUG_SETUP, UNKNOWN_INTERNAL
    }
    enum DoorEligibility { NOT_AT_DOOR, LOCKED, ELIGIBLE }

    record WriteContext(long invocationId, ServerPlayer player, float beforeHealth,
                        float rawRequestedTarget, float projectedNativeTarget,
                        double earnedBudget, WriteKind kind, DoorEligibility doorEligibility) {
        public WriteContext {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(doorEligibility, "doorEligibility");
        }
    }
    record Decision(float admittedTarget, Object receipt) {}

    Decision beforeWrite(WriteContext context);

    /** Exactly once, including beforeWrite throwing (decision=null). Observe physical health,
     * not a heal-event amount. Consumers must unwind their own pending state even on failure.
     * Direct child writes have separate callbacks; a consumer maintaining payment frames must
     * subtract their signed net deltas from the enclosing write's delta, not charge them twice.
     * If the physical health read itself throws, actualHealthAfter is NaN and the transaction
     * fails with that original error; NaN is unknown, never a fabricated zero-gain result.
     */
    void afterWrite(WriteContext context, Decision decision, float actualHealthAfter, boolean returnedNormally);

    interface Registration { boolean active(); }

    /** Original whole-write observer stays ABI1; primitive installation shares its owner lease.
     * Preparation is reserved for a future final-definition-qualified commit core. The current
     * spike NEVER invokes it for positive health and NEVER advertises enabled recovery.
     */
    interface PrimitiveHealthGate extends HealthGate {
        Decision beforePrimitiveStore(PrimitiveContext context);
    }
    record PrimitiveContext(WriteContext admission, Object synchedData, Object healthKey,
                            Object dataItem, float currentNativeHealth, float proposedNativeHealth,
                            float admittedUpperTarget) {}
    interface PrimitiveRegistration extends Registration { boolean recoveryEnabled(); }

    static PrimitiveRegistration installPrimitiveHealthGate(ResourceLocation owner, int expectedAbi,
                                                            PrimitiveHealthGate gate) {
        return Global.REGISTRY.installPrimitive(owner, expectedAbi, gate);
    }
    static PrimitiveHealthGate currentPrimitive() { return Global.REGISTRY.currentPrimitive(); }

    static Registration installHealthGate(ResourceLocation owner, int expectedAbi, HealthGate gate) {
        return Global.REGISTRY.install(owner, expectedAbi, gate);
    }
    static HealthGate current() { return Global.REGISTRY.current(); }

    static Decision identityDecision(float projected) { return new Decision(projected, null); }

    /** Native float projection only; no saturation accounting or fractional-credit convention. */
    static float project(float before, float raw, float maximum, boolean normalizeSentinel,
                         boolean locked, float sentinel) {
        if (!Float.isFinite(before) || !Float.isFinite(raw) || !Float.isFinite(maximum) || maximum <= 0)
            throw new IllegalArgumentException("Invalid native health input");
        float candidate = Math.max(0, Math.min(maximum, normalizeSentinel ? Math.max(0, raw - sentinel) : raw));
        if (raw > before && (locked || (normalizeSentinel && candidate <= before)))
            return Math.max(0, Math.min(maximum, before));
        return candidate;
    }

    /** A recovery gate may reduce a positive gain, never increase it or rewrite damage. */
    static float validateTarget(float before, float projected, Decision decision) {
        Objects.requireNonNull(decision, "decision");
        float admitted = decision.admittedTarget();
        if (!Float.isFinite(admitted) || (projected > before
                ? admitted < before || admitted > projected : admitted != projected))
            throw new IllegalArgumentException("HealthGate changed an unauthorized health range");
        return admitted;
    }

    /** Classify against the actual primitive baseline, never grant positive authority from a
     * method name. This is source-budget metadata only, NOT a saturation/pricing policy. */
    static Sources.Origin classify(Sources.Origin source, float before, float raw,
                                   float nativeCandidate, float sentinel) {
        Objects.requireNonNull(source, "source");
        if (!Float.isFinite(before) || !Float.isFinite(raw) || !Float.isFinite(nativeCandidate))
            throw new IllegalArgumentException("Invalid classification input");
        WriteKind kind = source.kind();
        if ((kind == WriteKind.DAMAGE_WRITE || kind == WriteKind.MAX_HEALTH_CLAMP) && nativeCandidate > before)
            return new Sources.Origin(WriteKind.DIRECT, positiveNativeBudget(before, nativeCandidate), Float.NaN);
        if (kind == WriteKind.DAMAGE_SENTINEL || kind == WriteKind.LEGACY_SENTINEL_INIT) {
            if (source.maximumTarget() != sentinel || raw != sentinel || nativeCandidate < 0 || nativeCandidate > sentinel)
                throw new IllegalArgumentException("Rewritten or unbounded sentinel target");
        } else if (kind == WriteKind.CANCELED_DEATH_ROLLBACK || kind == WriteKind.DEBUG_SETUP) {
            float bound = source.maximumTarget();
            if (!Float.isFinite(bound) || bound < 0 || raw > bound || nativeCandidate > bound)
                throw new IllegalArgumentException("Internal target exceeds captured authority");
        }
        double budget = kind == WriteKind.HEAL_CALL ? source.earnedBudget()
                : kind == WriteKind.DIRECT || kind == WriteKind.UNKNOWN_INTERNAL
                ? Math.max(0, (double) raw - before) : 0;
        return new Sources.Origin(kind, budget, source.maximumTarget());
    }

    /** Largest double not above the EXACT difference of two native floats. TwoDiff's
     * error term detects rounded-up subtraction even for MIN_VALUE -> 1f. A naive double
     * difference can overstate that source budget; always-nextDown would destroy exact1.
     */
    static double positiveNativeBudget(float before, float after) {
        if (!Float.isFinite(before) || !Float.isFinite(after)) throw new IllegalArgumentException("Invalid native budget");
        if (after <= before) return 0;
        double a = after, b = before, difference = a - b;
        double virtualB = a - difference, virtualA = difference + virtualB;
        double error = (a - virtualA) + (virtualB - b);
        return error < 0 ? Math.nextDown(difference) : difference;
    }

    /** Per-instance registry also permits isolated API tests without replacing the global gate. */
    final class Registry {
        private final HealthGate identity = new HealthGate() {
            public Decision beforeWrite(WriteContext c) { return identityDecision(c.projectedNativeTarget()); }
            public void afterWrite(WriteContext c, Decision d, float after, boolean normal) {}
        };
        private volatile HealthGate installed;
        private ResourceLocation owner;
        public synchronized Registration install(ResourceLocation owner, int expectedAbi, HealthGate gate) {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(gate, "gate");
            if (expectedAbi != ABI) throw new IllegalArgumentException("HealthGate ABI mismatch: " + expectedAbi);
            if (installed != null) throw new IllegalStateException("HealthGate already installed by " + this.owner);
            this.owner = owner;
            this.installed = gate;
            return () -> installed == gate;
        }
        public synchronized PrimitiveRegistration installPrimitive(ResourceLocation owner, int expectedAbi,
                                                                    PrimitiveHealthGate gate) {
            if (expectedAbi != PRIMITIVE_ABI) throw new IllegalArgumentException("Primitive HealthGate ABI mismatch: " + expectedAbi);
            Registration lease = install(owner, ABI, gate);
            return new PrimitiveRegistration() {
                public boolean active() { return lease.active(); }
                public boolean recoveryEnabled() { return false; } // No verifier/enablement in this spike.
            };
        }
        public PrimitiveHealthGate currentPrimitive() {
            HealthGate gate = installed;
            return gate instanceof PrimitiveHealthGate primitive ? primitive : null;
        }
        public HealthGate current() { HealthGate gate = installed; return gate == null ? identity : gate; }
        public boolean installed() { return installed != null; }
    }
    final class Global {
        private static final Registry REGISTRY = new Registry();
        private Global() {}
    }

    /** One-shot exact-call source scopes. A nested direct write must not inherit its caller's
     * heal budget/internal reason merely because the outer native call has not returned yet. */
    final class Sources {
        public record Origin(WriteKind kind, double earnedBudget, float maximumTarget) {}
        private static final Origin DIRECT = new Origin(WriteKind.DIRECT, 0, Float.NaN);
        private final ThreadLocal<ArrayDeque<Scope>> stack = ThreadLocal.withInitial(ArrayDeque::new);
        public final class Scope implements AutoCloseable {
            private final Object subject;
            private final Origin origin;
            private boolean claimed, closed;
            private Scope(Object subject, Origin origin) { this.subject = subject; this.origin = origin; }
            public void close() {
                var scopes = stack.get();
                if (closed || scopes.peek() != this) throw new IllegalStateException("Health source scope is not LIFO");
                scopes.pop(); closed = true;
                if (scopes.isEmpty()) stack.remove();
            }
        }
        public Scope open(Object subject, WriteKind kind, double earnedBudget) {
            return open(subject, kind, earnedBudget, Float.NaN);
        }
        /** Captured by the private internal caller BEFORE any foreign setHealth argument hook. */
        public Scope open(Object subject, WriteKind kind, double earnedBudget, float maximumTarget) {
            Objects.requireNonNull(subject, "subject"); Objects.requireNonNull(kind, "kind");
            if (!Double.isFinite(earnedBudget) || earnedBudget < 0) throw new IllegalArgumentException("Invalid earned budget");
            var scope = new Scope(subject, new Origin(kind, earnedBudget, maximumTarget));
            stack.get().push(scope);
            return scope;
        }
        public Origin claim(Object subject) {
            for (var scope : stack.get()) {
                if (scope.subject == subject) {
                    if (scope.claimed) return DIRECT;
                    scope.claimed = true;
                    return scope.origin;
                }
            }
            return DIRECT;
        }
        public int depth() { return stack.get().size(); }
    }

    /** Exception-safe write boundary shared with dependency-free numerical/callback tests.
     * This helper does not simulate an entity: callers supply the real native getter/writer.
     * The only native writer in production is the intercepted SynchedEntityData.set call.
     */
    final class Writes {
        private final AtomicLong ids = new AtomicLong();
        private final ThreadLocal<ArrayDeque<Frame>> stack = ThreadLocal.withInitial(ArrayDeque::new);

        public static final class Frame {
            private final long id;
            private final Object subject;
            private final float before;
            private float after;
            private double childDelta;
            private boolean observed;
            private Frame(long id, Object subject, float before) {
                this.id = id; this.subject = subject; this.before = before;
            }
            public long id() { return id; }
            public float before() { return before; }
            public float after() { if (!observed) throw new IllegalStateException("Write not observed"); return after; }
            public double ownDelta() { return (double) after() - before - childDelta; }
        }
        @FunctionalInterface
        public interface Completion<T> { void finish(Frame frame, T decision, boolean returnedNormally); }

        public int depth() { return stack.get().size(); }

        /** before/target/native-writer/completion failures are preserved, never converted to identity.
         * Completion runs even when preflight fails, then the frame always unwinds. A writer that
         * changed health before throwing still has its actual gain observed and reported.
         */
        public <T> void execute(Object subject, Supplier<Float> health, Function<Frame, T> before,
                                Function<T, Float> target, Consumer<Float> nativeWriter, Completion<T> completion) {
            Objects.requireNonNull(subject, "subject");
            var frames = stack.get();
            var frame = new Frame(ids.incrementAndGet(), subject, health.get());
            frames.push(frame);
            T decision = null;
            boolean normal = false;
            Throwable failure = null;
            try {
                decision = before.apply(frame);
                nativeWriter.accept(target.apply(decision));
                normal = true;
            } catch (Throwable caught) {
                failure = caught;
            } finally {
                try {
                    frame.after = health.get();
                    frame.observed = true;
                } catch (Throwable caught) {
                    frame.after = Float.NaN;
                    frame.observed = true;
                    failure = combine(failure, caught);
                }
                try { completion.finish(frame, decision, normal); }
                catch (Throwable caught) { failure = combine(failure, caught); }
                finally {
                    if (frames.peek() != frame) {
                        failure = combine(failure, new IllegalStateException("Health write frame is not LIFO"));
                    } else {
                        frames.pop();
                        for (var parent : frames) {
                            if (parent.subject == subject && !parent.observed) {
                                parent.childDelta += (double) frame.after - frame.before;
                                break;
                            }
                        }
                    }
                    if (frames.isEmpty()) stack.remove();
                }
            }
            rethrow(failure);
        }
        /** Run both completion obligations without losing the first failure to a finally throw. */
        public static void finishCallbacks(Runnable observer, Runnable continuation) {
            Throwable failure = null;
            try { observer.run(); } catch (Throwable caught) { failure = caught; }
            try { continuation.run(); } catch (Throwable caught) { failure = combine(failure, caught); }
            rethrow(failure);
        }
        private static Throwable combine(Throwable first, Throwable next) {
            if (first == null) return next;
            if (first != next) first.addSuppressed(next);
            return first;
        }
        private static void rethrow(Throwable failure) {
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            if (failure != null) throw new IllegalStateException("Health write failed", failure);
        }
    }
}
