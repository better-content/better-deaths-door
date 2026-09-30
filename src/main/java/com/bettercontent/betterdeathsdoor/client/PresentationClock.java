package com.bettercontent.betterdeathsdoor.client;
/** Presentation time excludes intervals during which its surface is intentionally hidden. */
public final class PresentationClock {
    private long pausedAt = -1;
    private long excluded;
    public void update(long now, boolean paused) {
        if (paused && pausedAt < 0) pausedAt = now;
        if (!paused && pausedAt >= 0) { excluded += now - pausedAt; pausedAt = -1; }
    }
    public long now(long now) { return (pausedAt < 0 ? now : pausedAt) - excluded; }
}
