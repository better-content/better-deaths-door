package com.bettercontent.betterdeathsdoor.nativecommit;

import com.bettercontent.betterdeathsdoor.api.HealthGate;
import net.minecraft.resources.ResourceLocation;

/** Dependency-light source-spike tests. NO entity/world/payment simulation or game launch.
 * RED baseline is the frozen pre-ABI2 source snapshot; execute it BEFORE green checks
 * when Root releases the javac slot. Until then these tests are authored, NOT executed.
 */
public final class NativeCommitCorePureTest {
    private static int assertions;
    public static void main(String[] args) { run(); System.out.println("NativeCommitCorePureTest PASS " + assertions); }
    public static void run() {
        var scopes = new NativeStoreScope();
        var registry = new HealthGate.Registry();
        var core = new NativeCommitCore(scopes, registry::currentPrimitive);
        Object actor = new Object(), data = new Object(), key = new Object(), item = new Object();
        check(core.readiness() == NativeCommitCore.Readiness.BLOCKED_NO_ATTESTATION, "No enable path in spike");
        for (Object[] pair : new Object[][]{{1f, 2f}, {2f, 1f}, {Float.NaN, Float.NaN}, {null, "opaque"}}) {
            core.beforeStore(item, pair[0], pair[1]); // Original assignment remains caller-owned.
            assertions++;
        }
        try (var write = scopes.openWrite(actor, null, registry.current(), 14);
             var delegate = scopes.openDelegate(actor, data, key)) {
            try (var store = scopes.openItem(actor, data, key, item, true, NativeStoreScope.Route.SET)) {
                var binding = scopes.claimStore(item);
                check(binding != null && binding.write() != null, "Deterministic actual item binding");
                check(binding.write().actor() == actor && binding.write().admittedTarget() == 14, "Original actor/upper admission retained");
                check(scopes.claimStore(item) == null, "Field binding consumed only once");
            }
            try (var child = scopes.openItem(actor, data, key, item, true, NativeStoreScope.Route.SET)) {
                check(scopes.claimStore(item).write() == null, "Nested raw callback cannot reuse outer delegate/source budget");
            }
        }
        check(scopes.depth() == 0, "All identity scopes unwind");
        RuntimeException original = new RuntimeException("native setter failure");
        try {
            try (var write = scopes.openWrite(actor, null, registry.current(), 3);
                 var delegate = scopes.openDelegate(actor, data, key);
                 var store = scopes.openItem(actor, data, key, item, true, NativeStoreScope.Route.SET)) {
                throw original;
            }
        } catch (RuntimeException failure) { check(failure == original, "Native exception preserved"); }
        check(scopes.depth() == 0, "Exception cleanup complete");
        var outer = scopes.openDelegate(actor, data, key);
        var inner = scopes.openDelegate(actor, data, key);
        reject(outer::close, "Strict LIFO"); inner.close(); outer.close();
        scopes.rememberHealthKey(key); check(scopes.isHealthKey(key), "Captured real health key");
        scopes.rememberHealthKey(new Object()); check(!scopes.healthKeyKnown(), "Conflicting key cannot certify a provider path");

        int[] providerCalls = {0};
        var provider = new HealthGate.PrimitiveHealthGate() {
            public HealthGate.Decision beforeWrite(HealthGate.WriteContext context) { return HealthGate.identityDecision(context.projectedNativeTarget()); }
            public void afterWrite(HealthGate.WriteContext c, HealthGate.Decision d, float h, boolean normal) {}
            public HealthGate.Decision beforePrimitiveStore(HealthGate.PrimitiveContext context) {
                providerCalls[0]++; throw new AssertionError("Unattested preparation invoked");
            }
        };
        var lease = registry.installPrimitive(new ResourceLocation("test", "primitive"), 2, provider);
        check(lease.active() && !lease.recoveryEnabled(), "Owner installed but recovery NEVER enabled");
        reject(() -> registry.install(new ResourceLocation("test", "other"), 1, provider), "Single owner shared with ABI1");
        reject(() -> new HealthGate.Registry().installPrimitive(new ResourceLocation("test", "bad"), 1, provider), "ABI2 mismatch");
        int[] stores = {0};
        reject(() -> { core.beforeStore(item, 1f, 2f); stores[0]++; }, "Unbound positive fails BEFORE native assignment");
        check(stores[0] == 0 && providerCalls[0] == 0, "No benefit, provider side-effect, saturation write or fake attestation");
        try (var write = scopes.openWrite(actor, null, provider, 2);
             var delegate = scopes.openDelegate(actor, data, key);
             var binding = scopes.openItem(actor, data, key, item, true, NativeStoreScope.Route.SET)) {
            reject(() -> { core.beforeStore(item, 1f, 2f); stores[0]++; }, "Even bound positive is disabled without final-definition proof");
        }
        core.beforeStore(item, 2f, 1f); core.beforeStore(item, 2f, 2f);
        check(providerCalls[0] == 0 && stores[0] == 0, "Nonpositive native writes are not converted to paid recovery");
        try (var nonhealth = scopes.openItem(actor, data, key, item, false, NativeStoreScope.Route.ASSIGN)) {
            core.beforeStore(item, 1f, 2f); assertions++;
        }
        try (var unknownHealth = scopes.openItem(actor, data, key, item, true, NativeStoreScope.Route.ASSIGN)) {
            reject(() -> core.beforeStore(item, "bad", "bad"), "Invalid health value refused before storage");
        }
        check(scopes.depth() == 0, "No pending/authority leak after rejected provider paths");
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void reject(Runnable body, String message) {
        assertions++; try { body.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }
}
