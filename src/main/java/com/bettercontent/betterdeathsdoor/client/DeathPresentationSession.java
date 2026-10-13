package com.bettercontent.betterdeathsdoor.client;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Screen-episode clock. Init/resize and packet arrival deliberately do not restart it. */
public final class DeathPresentationSession {
    private final LongSupplier clock;
    private Object episode;
    private long started;
    private boolean details;
    private boolean finished;
    public DeathPresentationSession(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }
    public boolean render(Object screen) {
        if (episode == screen) return false;
        episode = Objects.requireNonNull(screen);
        started = clock.getAsLong(); details = false; finished = false;
        return true;
    }
    public boolean owns(Object screen) { return episode == screen; }
    public long elapsed() { return Math.max(0, clock.getAsLong() - started); }
    public boolean immediate(boolean reducedMotion) { return reducedMotion || finished; }
    public boolean summary(boolean reducedMotion) { return DeathRevealSchedule.summary(elapsed(), immediate(reducedMotion)); }
    public boolean card(int index, boolean reducedMotion) { return DeathRevealSchedule.card(elapsed(), index, immediate(reducedMotion)); }
    public boolean hint(int count, boolean reducedMotion) { return DeathRevealSchedule.hint(elapsed(), count, immediate(reducedMotion)); }
    public boolean details() { return details; }
    public void toggleDetails() { details = !details; finished = true; }
    public void clear() { episode = null; started = 0; details = false; finished = false; }
}
