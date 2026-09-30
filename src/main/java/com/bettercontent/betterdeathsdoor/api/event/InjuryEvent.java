package com.bettercontent.betterdeathsdoor.api.event;

import com.bettercontent.betterdeathsdoor.state.BodySnapshot;
import com.bettercontent.betterdeathsdoor.state.Maim;
import com.bettercontent.betterdeathsdoor.state.TreatmentRecord;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Notifications of committed server changes, never cancelable requests. */
public abstract class InjuryEvent extends PlayerEvent {
    private final BodySnapshot snapshot;
    protected InjuryEvent(ServerPlayer player, BodySnapshot snapshot) { super(player); this.snapshot = snapshot; }
    public BodySnapshot snapshot() { return snapshot; }
    public static final class EnteredDoor extends InjuryEvent {
        private final DamageSource source;
        public EnteredDoor(ServerPlayer p, BodySnapshot s, DamageSource source) { super(p, s); this.source = source; }
        public DamageSource source() { return source; }
    }
    /** Accepted-hit trauma, captured before adding this hit's possible new maim. */
    public static final class TraumaIncreased extends InjuryEvent {
        private final BodySnapshot before;
        private final DamageSource source;
        public TraumaIncreased(ServerPlayer player, BodySnapshot before, BodySnapshot after, DamageSource source) {
            super(player, after); this.before = before; this.source = source;
        }
        public BodySnapshot before() { return before; }
        public DamageSource source() { return source; }
        public boolean amplifiedExistingInjury() { return snapshot().traumaAmplifies(before); }
    }
    public static final class Healed extends InjuryEvent { public Healed(ServerPlayer p, BodySnapshot s) { super(p, s); } }
    public static final class MaimAdded extends InjuryEvent {
        private final Maim maim;
        public MaimAdded(ServerPlayer p, BodySnapshot s, Maim maim) { super(p, s); this.maim = maim; }
        public Maim maim() { return maim; }
    }
    public static final class Treated extends InjuryEvent {
        private final ServerPlayer healer; private final TreatmentRecord record;
        public Treated(ServerPlayer p, BodySnapshot s, ServerPlayer healer, TreatmentRecord record) { super(p, s); this.healer = healer; this.record = record; }
        public ServerPlayer healer() { return healer; }
        public TreatmentRecord record() { return record; }
    }
    public static final class FinalDeath extends InjuryEvent {
        private final DamageSource source;
        public FinalDeath(ServerPlayer p, BodySnapshot s, DamageSource source) { super(p, s); this.source = source; }
        public DamageSource source() { return source; }
    }
}
