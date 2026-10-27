package com.bettercontent.betterdeathsdoor.nativecommit;

import com.bettercontent.betterdeathsdoor.api.HealthGate;
import java.util.Objects;
import java.util.function.Supplier;

/** DEFAULT-IDENTITY SOURCE SPIKE ONLY. No paid recovery registration, raw saturation
 * access, currency, escrow, field-pair commit, or fake readiness token is implemented.
 * The final field hook calls this BEFORE its one original native assignment.
 * Future final-definition attestation/commit implementation requires separate approval.
 */
public final class NativeCommitCore {
    public enum Readiness { BLOCKED_NO_ATTESTATION }
    public static final NativeCommitCore GLOBAL = new NativeCommitCore(NativeStoreScope.GLOBAL, HealthGate::currentPrimitive);
    private final NativeStoreScope scopes;
    private final Supplier<HealthGate.PrimitiveHealthGate> providers;
    public NativeCommitCore(NativeStoreScope scopes, Supplier<HealthGate.PrimitiveHealthGate> providers) {
        this.scopes = Objects.requireNonNull(scopes); this.providers = Objects.requireNonNull(providers);
    }
    public Readiness readiness() { return Readiness.BLOCKED_NO_ATTESTATION; }

    /** Does not write any field or invoke provider preparation. Identity preserves even
     * opaque/non-Float values exactly; only a pending ABI2 provider adds refusal guards.
     * A caller cannot turn on readiness with a property or an unverified callback.
     */
    public void beforeStore(Object item, Object before, Object proposed) {
        HealthGate.PrimitiveHealthGate provider = providers.get();
        if (provider == null) return;
        var binding = scopes.claimStore(item);
        if (binding != null && !binding.serverHealth()) return;
        if (before instanceof Float oldHealth && proposed instanceof Float newHealth) {
            if (Float.isFinite(oldHealth) && Float.isFinite(newHealth) && newHealth <= oldHealth) return;
            throw new IllegalStateException("ABI2 positive/unknown native write refused BEFORE storage: no final-definition attestation");
        }
        if (binding != null && binding.serverHealth())
            throw new IllegalStateException("ABI2 invalid native health value refused BEFORE storage");
        // Unbound generic nonhealth values retain native behavior. Unbound positive FLOAT
        // writes above are conservatively refused; they cannot claim an outer heal budget.
    }
}
