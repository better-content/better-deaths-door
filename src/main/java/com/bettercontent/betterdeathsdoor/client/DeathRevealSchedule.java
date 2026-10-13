package com.bettercontent.betterdeathsdoor.client;

/** Pure discrete reveal policy. Visibility never alters layout or native action timing. */
public final class DeathRevealSchedule {
    public static final long STEP_MILLIS = 150;
    private DeathRevealSchedule() {}
    public static int cardStages(int selectedCards) { return Math.max(1, Math.min(3, selectedCards)); }
    public static boolean summary(long elapsed, boolean immediate) { return immediate || elapsed >= STEP_MILLIS; }
    public static boolean card(long elapsed, int index, boolean immediate) {
        return index >= 0 && index < 3 && (immediate || elapsed >= STEP_MILLIS * (index + 2L));
    }
    public static boolean hint(long elapsed, int selectedCards, boolean immediate) {
        return immediate || elapsed >= STEP_MILLIS * (cardStages(selectedCards) + 2L);
    }
}
