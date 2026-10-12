package com.bettercontent.betterdeathsdoor.client;

/** Client countdown reconciled with server remaining ticks, never with a fresh window on periodic sync. */
public final class HeartbeatClock {
    private int remaining, beats;
    public void sync(boolean atDoor, int remainingTicks) {
        remaining = atDoor ? Math.max(0, Math.min(600, remainingTicks)) : 0;
        if (remaining == 0) beats = 0;
    }
    public boolean tick(boolean soundEnabled, int cadence) {
        if (remaining <= 0) { beats = 0; return false; }
        remaining--;
        if (!soundEnabled) { beats = 0; return false; }
        if (++beats >= Math.max(1, cadence)) { beats = 0; return true; }
        return false;
    }
    public int remainingTicks() { return remaining; }
    public void reset() { remaining = 0; beats = 0; }
}
