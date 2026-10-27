package com.bettercontent.betterdeathsdoor.nativecommit;

import com.bettercontent.betterdeathsdoor.api.HealthGate;
import java.util.ArrayDeque;
import java.util.Objects;

/** Store identity only, NOT payment/reservation/attestation. Per-instance for pure tests.
 * A delegate and final field binding are each one-shot: callbacks cannot inherit a
 * still-open parent's source merely by writing the same player's same data key.
 */
public final class NativeStoreScope {
    public static final NativeStoreScope GLOBAL = new NativeStoreScope();
    public enum Route { SET, ASSIGN }
    public record WriteBinding(Object actor, HealthGate.WriteContext admission,
                               HealthGate gate, float admittedTarget) {}
    public record Binding(Object actor, Object data, Object key, Object item,
                          boolean serverHealth, Route route, WriteBinding write) {}
    /** Internal native field authority implemented by the merged DataItem mixin.
     * Not a policy/provider callback. Actual merged methods still need attestation.
     */
    public interface RawItem {
        Object revival$rawDataValue();
        void revival$storeRawDataValue(Object value);
    }
    private final ThreadLocal<ArrayDeque<Scope>> stack = ThreadLocal.withInitial(ArrayDeque::new);
    private volatile Object healthKey;
    private volatile boolean conflictingKey;
    public synchronized void rememberHealthKey(Object key) {
        Objects.requireNonNull(key, "key");
        if (healthKey == null) healthKey = key;
        else if (healthKey != key) conflictingKey = true; // Identity mode must not invent a new failure.
    }
    public boolean healthKeyKnown() { return healthKey != null && !conflictingKey; }
    public boolean isHealthKey(Object key) { return healthKeyKnown() && healthKey == key; }

    public final class Scope implements AutoCloseable {
        private final Thread thread = Thread.currentThread();
        private final WriteBinding write;
        private final Object actor, data, key;
        private final Binding binding;
        private final int type; // 0 write, 1 delegate, 2 actual item invocation
        private boolean claimed, closed;
        private Scope(int type, WriteBinding write, Object actor, Object data, Object key, Binding binding) {
            this.type = type; this.write = write; this.actor = actor; this.data = data; this.key = key; this.binding = binding;
        }
        public void close() {
            var frames = stack.get();
            if (closed || thread != Thread.currentThread() || frames.peek() != this)
                throw new IllegalStateException("Native store scope is not same-thread LIFO");
            frames.pop(); closed = true;
            if (frames.isEmpty()) stack.remove();
        }
    }
    private Scope push(Scope scope) { stack.get().push(scope); return scope; }
    public Scope openWrite(Object actor, HealthGate.WriteContext admission, HealthGate gate, float admitted) {
        Objects.requireNonNull(actor, "actor"); Objects.requireNonNull(gate, "gate");
        if (!Float.isFinite(admitted)) throw new IllegalArgumentException("Invalid admitted native target");
        var write = new WriteBinding(actor, admission, gate, admitted);
        return push(new Scope(0, write, actor, null, null, null));
    }
    public Scope openDelegate(Object actor, Object data, Object key) {
        Objects.requireNonNull(actor, "actor"); Objects.requireNonNull(data, "data"); Objects.requireNonNull(key, "key");
        Scope parent = stack.get().peek();
        WriteBinding write = parent != null && parent.type == 0 && parent.actor == actor ? parent.write : null;
        return push(new Scope(1, write, actor, data, key, null));
    }
    public Scope openItem(Object actor, Object data, Object key, Object item, boolean serverHealth, Route route) {
        Objects.requireNonNull(actor, "actor"); Objects.requireNonNull(data, "data");
        Objects.requireNonNull(key, "key"); Objects.requireNonNull(item, "item"); Objects.requireNonNull(route, "route");
        Scope parent = stack.get().peek();
        WriteBinding write = null;
        if (parent != null && parent.type == 1 && !parent.claimed && parent.actor == actor
                && parent.data == data && parent.key == key) {
            parent.claimed = true; write = parent.write;
        }
        var binding = new Binding(actor, data, key, item, serverHealth, route, write);
        return push(new Scope(2, write, actor, data, key, binding));
    }
    public Binding claimStore(Object item) {
        Scope scope = stack.get().peek();
        if (scope == null || scope.type != 2 || scope.claimed || scope.binding.item() != item) return null;
        scope.claimed = true;
        return scope.binding;
    }
    public int depth() { return stack.get().size(); }
}
