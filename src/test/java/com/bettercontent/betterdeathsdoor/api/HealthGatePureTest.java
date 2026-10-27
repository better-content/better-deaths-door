package com.bettercontent.betterdeathsdoor.api;

import com.bettercontent.betterdeathsdoor.state.BodyState;
import com.bettercontent.betterdeathsdoor.state.MaimType;
import com.bettercontent.betterdeathsdoor.state.Region;
import net.minecraft.resources.ResourceLocation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Actual API/body/control-flow tests. Numeric getters/writers are NOT a native entity fixture. */
public final class HealthGatePureTest {
    private static int assertions;
    private HealthGatePureTest() {}
    public static void main(String[] args) { run(); }
    public static void run() {
        assertions = 0;
        projectionAndIdentity();
        boundedInternalAuthority();
        sources();
        exceptions();
        nesting();
        observedDoorCommit();
        registration();
        System.out.println("HealthGatePureTest PASS " + assertions + " assertions; source/control-flow only, NOT native Mixin acceptance");
    }
    private static void projectionAndIdentity() {
        float sentinel = .0001f;
        eq(HealthGate.project(sentinel, sentinel + 4, 20, true, false, sentinel), 4, "Sentinel normalized exactly once");
        eq(HealthGate.project(sentinel, sentinel + 4, 20, true, true, sentinel), sentinel, "Locked request remains semantic zero");
        eq(HealthGate.project(sentinel, sentinel + .00001f, 20, true, false, sentinel), sentinel, "Tiny heal cannot damage player or manufacture Door exit");
        eq(HealthGate.project(25, 30, 20, false, true, sentinel), 20, "Lock never bypasses native maximum clamp");
        eq(HealthGate.project(10, -4, 20, false, false, sentinel), 0, "Native zero clamp");
        eq(HealthGate.project(20, 24, 20, false, false, sentinel), 20, "Full HP does not manufacture an actual gain");
        var random = new Random(0x444447415445L);
        for (int i = 0; i < 5_000; i++) {
            float before = random.nextInt(161) / 8f, raw = (random.nextInt(241) - 40) / 8f;
            float projected = HealthGate.project(before, raw, 20, false, false, sentinel);
            eq(projected, Math.max(0, Math.min(20, raw)), "Independent native clamp oracle");
            var identity = HealthGate.identityDecision(projected);
            eq(identity.admittedTarget(), projected, "Default identity leaves native candidate unchanged");
            check(identity.receipt() == null, "No hidden default payment state");
            eq(HealthGate.validateTarget(before, projected, identity), projected, "Identity validates every ordinary candidate");
        }
        eq(HealthGate.validateTarget(10, 14, new HealthGate.Decision(10, null)), 10, "Gate may deny positive recovery");
        reject(() -> HealthGate.validateTarget(10, 14, new HealthGate.Decision(15, null)), "Gate cannot increase candidate");
        reject(() -> HealthGate.validateTarget(10, 14, new HealthGate.Decision(9, null)), "Gate cannot turn healing into damage");
        reject(() -> HealthGate.validateTarget(10, 6, new HealthGate.Decision(7, null)), "Gate cannot reduce native damage");
        reject(() -> HealthGate.validateTarget(10, 6, new HealthGate.Decision(0, null)), "Gate cannot amplify native damage");
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            reject(() -> HealthGate.project(10, bad, 20, false, false, sentinel), "Invalid native request fails closed");
            reject(() -> HealthGate.validateTarget(10, 14, new HealthGate.Decision(bad, null)), "Invalid gate response fails closed");
        }
    }
    private static void boundedInternalAuthority() {
        float sentinel = .0001f;
        // Pinned Forge47.4.22: uncanceled LivingDamageEvent.setAmount(-1) is not
        // clamped; actuallyHurt checks !=0 and performs currentHealth - amount.
        float proposed = 10f - (-1f);
        var damage = new HealthGate.Sources.Origin(HealthGate.WriteKind.DAMAGE_WRITE, 0, proposed);
        var effective = HealthGate.classify(damage, 10, proposed, HealthGate.project(10, proposed, 20, false, false, sentinel), sentinel);
        check(effective.kind() == HealthGate.WriteKind.DIRECT, "Negative event damage cannot grant structural recovery");
        eq(effective.earnedBudget(), 1, "Reclassified budget is actual native candidate difference");
        eq(HealthGate.validateTarget(10, 11, new HealthGate.Decision(10, null)), 10, "Installed gate can deny that positive write");
        for (var kind : new HealthGate.WriteKind[]{HealthGate.WriteKind.DAMAGE_WRITE, HealthGate.WriteKind.MAX_HEALTH_CLAMP}) {
            var origin = new HealthGate.Sources.Origin(kind, 0, 16);
            var stale = HealthGate.classify(origin, 10, 16, 16, sentinel);
            check(stale.kind() == HealthGate.WriteKind.DIRECT, "Stale positive " + kind + " is ordinary");
            eq(stale.earnedBudget(), 6, "Stale positive bounded budget");
            var clamped = HealthGate.classify(origin, 10, 100, 20, sentinel);
            eq(clamped.earnedBudget(), 10, "Native candidate, NOT raw overshoot, bounds reclassified budget");
            var downward = HealthGate.classify(origin, 10, 6, 6, sentinel);
            check(downward.kind() == kind && downward.earnedBudget() == 0, "Nonpositive native target unchanged");
            var aboveMaximum = HealthGate.classify(origin, 25, 30, 20, sentinel);
            check(aboveMaximum.kind() == kind && aboveMaximum.earnedBudget() == 0, "Raw positive but native-clamped downward is not recovery");
            var same = HealthGate.classify(origin, 10, 10, 10, sentinel);
            check(same.kind() == kind && same.earnedBudget() == 0, "No-op " + kind + " is not an earned grant");
        }
        var genericSentinel = HealthGate.classify(damage, 0, sentinel, sentinel, sentinel);
        check(genericSentinel.kind() == HealthGate.WriteKind.DIRECT, "Generic positive DAMAGE even sentinel-sized is funded");
        eq(genericSentinel.earnedBudget(), (double) sentinel, "Generic small positive budget");
        for (var kind : new HealthGate.WriteKind[]{HealthGate.WriteKind.DAMAGE_SENTINEL, HealthGate.WriteKind.LEGACY_SENTINEL_INIT}) {
            var explicit = new HealthGate.Sources.Origin(kind, 0, sentinel);
            var allowed = HealthGate.classify(explicit, 0, sentinel, sentinel, sentinel);
            check(allowed.kind() == kind && allowed.earnedBudget() == 0, "Only explicit original sentinel has structural authority");
            reject(() -> HealthGate.classify(explicit, 0, Math.nextUp(sentinel), Math.nextUp(sentinel), sentinel), "Sentinel argument rewrite rejected");
            reject(() -> HealthGate.classify(new HealthGate.Sources.Origin(kind, 0, Float.NaN), 0, sentinel, sentinel, sentinel), "Uncaptured sentinel rejected");
            reject(() -> HealthGate.classify(new HealthGate.Sources.Origin(kind, 0, Math.nextUp(sentinel)), 0, sentinel, sentinel, sentinel), "Original non-sentinel cannot masquerade as sentinel");
        }
        for (var kind : new HealthGate.WriteKind[]{HealthGate.WriteKind.CANCELED_DEATH_ROLLBACK, HealthGate.WriteKind.DEBUG_SETUP}) {
            var captured = new HealthGate.Sources.Origin(kind, 0, 16);
            var allowed = HealthGate.classify(captured, 0, 16, 16, sentinel);
            check(allowed.kind() == kind && allowed.earnedBudget() == 0, "Captured bounded " + kind + " retained");
            reject(() -> HealthGate.classify(captured, 0, 18, 18, sentinel), "Positive rewrite exceeds private captured bound");
            reject(() -> HealthGate.classify(captured, 0, 18, 10, sentinel), "Max clamp cannot hide rewritten raw excess");
            reject(() -> HealthGate.classify(new HealthGate.Sources.Origin(kind, 0, Float.NaN), 0, 16, 16, sentinel), "Reason alone without bound is not authority");
        }
        var sources = new HealthGate.Sources(); var player = new Object();
        try (var scope = sources.open(player, HealthGate.WriteKind.CANCELED_DEATH_ROLLBACK, 0, 16)) {
            eq(sources.claim(player).maximumTarget(), 16, "Private original bound captured before target hook");
            check(Float.isNaN(sources.claim(player).maximumTarget()), "Nested callback cannot inherit captured authority");
        }
        var unknown = HealthGate.classify(new HealthGate.Sources.Origin(HealthGate.WriteKind.UNKNOWN_INTERNAL, 0, 16), 10, 16, 16, sentinel);
        check(unknown.kind() == HealthGate.WriteKind.UNKNOWN_INTERNAL && unknown.earnedBudget() == 6, "Legacy unknown remains ordinary funding path");
        eq(HealthGate.project(sentinel, 4, 20, false, true, sentinel), sentinel, "Reclassified direct positive respects Door lock");
        var body = new BodyState(); body.enterDoor(0);
        check(!body.commitHealing(39, 1) && body.atDoor(), "Reclassified positive cannot commit during lock");
        check(!body.commitHealing(40, 0) && body.atDoor(), "Denied positive cannot commit after lock");
        check(body.commitHealing(40, 1), "Actual funded positive remains eligible after lock");
        eq(HealthGate.positiveNativeBudget(10, 11), 1, "Exact integer budget is NOT arbitrarily nextDown'ed");
        nativeBudgetOracle(Float.MIN_VALUE, 1);
        nativeBudgetOracle(0, Float.MIN_VALUE);
        nativeBudgetOracle(sentinel, 4);
        var random = new Random(0x504f534954495645L);
        for (int i = 0; i < 2_000; i++) {
            float a = Float.intBitsToFloat(random.nextInt() & 0x7fffffff);
            float b = Float.intBitsToFloat(random.nextInt() & 0x7fffffff);
            if (Float.isFinite(a) && Float.isFinite(b) && a != b) nativeBudgetOracle(Math.min(a, b), Math.max(a, b));
        }
    }
    private static void nativeBudgetOracle(float before, float after) {
        BigDecimal exact = new BigDecimal((double) after).subtract(new BigDecimal((double) before));
        double budget = HealthGate.positiveNativeBudget(before, after);
        check(new BigDecimal(budget).compareTo(exact) <= 0, "Source double budget cannot exceed exact binary float delta");
        check(new BigDecimal(Math.nextUp(budget)).compareTo(exact) > 0, "Largest safe source double, no unnecessary budget destruction");
    }

    private static void sources() {
        var sources = new HealthGate.Sources(); var player = new Object(); var other = new Object();
        try (var scope = sources.open(player, HealthGate.WriteKind.HEAL_CALL, .1f)) {
            var first = sources.claim(player);
            check(first.kind() == HealthGate.WriteKind.HEAL_CALL, "Exact call gets heal source");
            eq(first.earnedBudget(), (double) .1f, "Post-Forge float budget kept exactly, not target subtraction");
            check(sources.claim(player).kind() == HealthGate.WriteKind.DIRECT, "Nested callback cannot reuse outer budget");
            try (var inner = sources.open(player, HealthGate.WriteKind.CANCELED_DEATH_ROLLBACK, 0)) {
                check(sources.claim(player).kind() == HealthGate.WriteKind.CANCELED_DEATH_ROLLBACK, "Explicit child reason wins");
                check(sources.claim(player).kind() == HealthGate.WriteKind.DIRECT, "Child reason is also one-shot");
            }
            check(sources.claim(other).kind() == HealthGate.WriteKind.DIRECT, "No source leakage to another entity");
            check(sources.claim(player).kind() == HealthGate.WriteKind.DIRECT, "Child close never re-arms parent");
        }
        eq(sources.depth(), 0, "Source scope unwound");
        var outer = sources.open(player, HealthGate.WriteKind.UNKNOWN_INTERNAL, 0);
        var inner = sources.open(player, HealthGate.WriteKind.DEBUG_SETUP, 0);
        reject(outer::close, "Out-of-order source close is not silently accepted");
        eq(sources.depth(), 2, "Failed close did not corrupt LIFO stack");
        inner.close(); outer.close();
        reject(outer::close, "Duplicate close rejected");
        var failure = new IllegalStateException("scoped failure");
        try (var scope = sources.open(player, HealthGate.WriteKind.HEAL_CALL, 4)) { throw failure; }
        catch (IllegalStateException caught) { check(caught == failure, "Source exception preserved"); }
        eq(sources.depth(), 0, "Exception cannot leak heal-call scope");
        check(sources.claim(player).kind() == HealthGate.WriteKind.DIRECT, "No stale reason after exception");
        reject(() -> sources.open(player, HealthGate.WriteKind.HEAL_CALL, Double.NaN), "Nonfinite source budget denied");
    }
    private static void exceptions() {
        var writes = new HealthGate.Writes(); var player = new Object();
        float[] hp = {10}; int[] completions = {0};
        writes.execute(player, () -> hp[0], f -> 14f, target -> target, v -> hp[0] = v, (f, d, normal) -> {
            completions[0]++; check(normal, "Identity write returned normally"); eq(f.ownDelta(), 4, "Identity actual gain");
        });
        eq(hp[0], 14, "Real supplied writer, not an inferred event amount"); eq(completions[0], 1, "One completion");
        for (int stage = 0; stage < 4; stage++) {
            hp[0] = 10; completions[0] = 0;
            final int phase = stage;
            var original = new IllegalStateException("phase " + stage);
            Throwable caught = null;
            try {
                writes.execute(player, () -> hp[0], f -> { if (phase == 0) throw original; return 14f; },
                        d -> { if (phase == 1) throw original; return d; },
                        v -> { if (phase == 2) throw original; hp[0] = v; },
                        (f, d, normal) -> {
                            completions[0]++;
                            if (phase == 0) check(d == null, "Preflight throw gets null decision cleanup");
                            eq(f.ownDelta(), phase == 3 ? 4 : 0, "Only observed change counts");
                            check(normal == (phase == 3), "Actual native-return status");
                            if (phase == 3) throw original;
                        });
            } catch (Throwable error) { caught = error; }
            check(caught == original, "Original failure, no identity fallback");
            eq(completions[0], 1, "One completion despite failure"); eq(writes.depth(), 0, "Always unwound");
        }
        hp[0] = 10; completions[0] = 0;
        var nativeFailure = new IllegalStateException("after physical change");
        var completionFailure = new IllegalArgumentException("completion also failed");
        Throwable caught = null;
        try {
            writes.execute(player, () -> hp[0], f -> 14f, d -> d,
                    v -> { hp[0] = v; throw nativeFailure; },
                    (f, d, normal) -> {
                        completions[0]++; check(!normal, "Partial throw is not ordinary completion");
                        eq(f.ownDelta(), 4, "Committed HP before throw is not free"); throw completionFailure;
                    });
        } catch (Throwable error) { caught = error; }
        check(caught == nativeFailure, "Native failure remains primary");
        check(caught.getSuppressed().length == 1 && caught.getSuppressed()[0] == completionFailure, "Completion failure retained as suppressed");
        eq(completions[0], 1, "Partial native failure completed once"); eq(writes.depth(), 0, "Partial failure unwound");
        writes.execute(player, () -> hp[0], f -> 18f, d -> d, v -> {},
                (f, d, normal) -> eq(f.ownDelta(), 0, "Canceled/no-op writer cannot be mistaken for accepted healing"));
        eq(writes.depth(), 0, "No-op unwind");
        var observeFailure = new IllegalStateException("observer failure");
        var transitionFailure = new IllegalArgumentException("transition failure");
        caught = null;
        try { HealthGate.Writes.finishCallbacks(() -> { throw observeFailure; }, () -> { throw transitionFailure; }); }
        catch (Throwable error) { caught = error; }
        check(caught == observeFailure && caught.getSuppressed().length == 1
                && caught.getSuppressed()[0] == transitionFailure, "Observer failure never lost to Door continuation failure");
        int[] reads = {0}; var readFailure = new IllegalStateException("physical read failed"); caught = null;
        try {
            writes.execute(player, () -> { if (reads[0]++ == 0) return hp[0]; throw readFailure; },
                    f -> 16f, d -> d, v -> hp[0] = v, (f, d, normal) -> {
                        check(Float.isNaN(f.after()) && Double.isNaN(f.ownDelta()), "Unknown read is not fabricated zero gain");
                        check(normal, "Native return status does not hide failed observation");
                    });
        } catch (Throwable error) { caught = error; }
        check(caught == readFailure, "Physical read error preserved, not silent success");
        eq(writes.depth(), 0, "Observation failure cannot leak pending frame");
    }
    private static void nesting() {
        var writes = new HealthGate.Writes(); var subject = new Object(); float[] hp = {10};
        List<String> order = new ArrayList<>();
        writes.execute(subject, () -> hp[0], f -> { order.add("outer-before"); return 14f; }, d -> d, v -> {
            hp[0] = v;
            writes.execute(subject, () -> hp[0], f -> { order.add("child-before"); return 12f; }, d -> d,
                    child -> hp[0] = child, (f, d, normal) -> {
                        order.add("child-after"); eq(f.ownDelta(), -2, "Child native damage preserved"); eq(writes.depth(), 2, "Child LIFO frame");
                    });
        }, (f, d, normal) -> {
            order.add("outer-after"); eq(f.after(), 12, "Observe physical final HP");
            eq(f.ownDelta(), 4, "Nested damage does not hide two units of paid healing");
        });
        check(order.equals(List.of("outer-before", "child-before", "child-after", "outer-after")), "Exact nested callback order");
        eq(writes.depth(), 0, "Nested frames unwind");
        hp[0] = 10;
        writes.execute(subject, () -> hp[0], f -> 14f, d -> d, v -> {
            hp[0] = v;
            writes.execute(subject, () -> hp[0], f -> 16f, d -> d, child -> hp[0] = child,
                    (f, d, normal) -> eq(f.ownDelta(), 2, "Child positive write has separate receipt"));
        }, (f, d, normal) -> eq(f.ownDelta(), 4, "No double charge of child's positive delta"));
        hp[0] = 10;
        writes.execute(subject, () -> hp[0], f -> 14f, d -> d, v -> hp[0] = v, (f, d, normal) -> {
            eq(f.ownDelta(), 4, "Parent frozen observation precedes completion callbacks");
            writes.execute(subject, () -> hp[0], inner -> 15f, target -> target, v -> hp[0] = v,
                    (inner, target, ok) -> eq(inner.ownDelta(), 1, "After-callback write is separate, not free"));
            eq(f.ownDelta(), 4, "Later callback cannot rewrite parent's observed gain");
        });
        eq(writes.depth(), 0, "After-callback frame unwind");
    }
    private static void observedDoorCommit() {
        var body = new BodyState(); body.enterDoor(100); body.addMaim(Region.HEAD, MaimType.CRACKED, 100);
        check(!body.canHeal(139) && body.atDoor(), "Locked read-only check");
        check(body.canHeal(140) && body.atDoor(), "Eligible read-only check does not exit");
        for (double gain : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            check(!body.commitHealing(140, gain) && body.atDoor(), "No request/event/nonpositive commit");
        check(!body.commitHealing(139, 4) && body.atDoor(), "Actual gain cannot bypass lock");
        check(body.commitHealing(140, .25) && !body.atDoor(), "Observed positive write commits once");
        check(!body.commitHealing(140, .25), "No duplicate Door commit/event");
        eq(body.activeMaims().size(), 1, "Healing leaves independent maims intact");
        body.enterDoor(200); body.cureOldest(Region.HEAD, MaimType.CRACKED, "test:care", 201).orElseThrow();
        check(body.atDoor() && body.healingLocked(201), "Maim mending does not cause recovery or reset Door");
        var writes = new HealthGate.Writes(); Object subject = new Object(); float[] hp = {.0001f};
        writes.execute(subject, () -> hp[0], f -> 4f, d -> d, v -> {},
                (f, d, normal) -> check(!body.commitHealing(240, f.ownDelta()) && body.atDoor(), "No-op native writer cannot exit eligible Door"));
        writes.execute(subject, () -> hp[0], f -> 4f, d -> d, v -> hp[0] = v,
                (f, d, normal) -> check(body.commitHealing(240, f.ownDelta()), "Door commits after actual numeric writer only"));
        var legacy = new BodyState(); legacy.enterDoor(0);
        check(!legacy.tryHeal(39) && legacy.tryHeal(40), "Legacy body API remains compatible");
    }
    private static void registration() {
        var registry = new HealthGate.Registry(); check(!registry.installed(), "Default identity without provider");
        HealthGate observer = new HealthGate() {
            public Decision beforeWrite(WriteContext c) { return HealthGate.identityDecision(c.projectedNativeTarget()); }
            public void afterWrite(WriteContext c, Decision d, float after, boolean normal) {}
        };
        var owner = new ResourceLocation("test", "health_gate");
        reject(() -> registry.install(owner, HealthGate.ABI + 1, observer), "ABI mismatch fails before installation");
        check(!registry.installed(), "Failed install cannot disable fallback");
        var registration = registry.install(owner, HealthGate.ABI, observer);
        check(registration.active() && registry.current() == observer, "Active registration identifies authoritative gate");
        reject(() -> registry.install(owner, HealthGate.ABI, observer), "No duplicate/replacement provider");
        check(registration.active(), "Rejected replacement did not mutate installed gate");
    }
    private static void eq(double actual, double expected, String message) { check(actual == expected, message + ": " + actual + " != " + expected); }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void reject(Runnable operation, String message) {
        assertions++;
        try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }
}
