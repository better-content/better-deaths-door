package com.bettercontent.betterdeathsdoor.records;

import java.util.*;

/** Bounded immutable server-frozen DTO. No player-supplied labels or unbounded history. */
public record LifeRecap(int version, UUID lifeId, boolean partial, boolean eligible,
                        List<Double> values, List<Double> previousBests, List<Card> cards) {
    public static final int VERSION = 1;
    public enum Badge { BEST, MILESTONE, MATCHED, NEAR, FIRST }
    public record Card(LifeMetric metric, double value, double previousBest, List<Badge> badges, int milestoneTier) {
        public Card {
            Objects.requireNonNull(metric); check(value); check(previousBest);
            badges = List.copyOf(badges);
            if (badges.isEmpty() || badges.size() > Badge.values().length || new HashSet<>(badges).size() != badges.size()
                || milestoneTier < 0 || milestoneTier > 3) throw new IllegalArgumentException("Invalid life card");
        }
    }
    public LifeRecap {
        if (version != VERSION) throw new IllegalArgumentException("Unknown life recap version");
        Objects.requireNonNull(lifeId);
        values = checked(values); previousBests = checked(previousBests); cards = List.copyOf(cards);
        if (cards.size() > 3 || ((partial || !eligible) && !cards.isEmpty())
            || cards.stream().map(Card::metric).distinct().count() != cards.size()) throw new IllegalArgumentException("Invalid life cards");
        for (Card c : cards) if (c.value() != values.get(c.metric().ordinal()) || c.previousBest() != previousBests.get(c.metric().ordinal()))
            throw new IllegalArgumentException("Card counters disagree");
    }
    private static List<Double> checked(List<Double> values) {
        if (values.size() != LifeMetric.values().length) throw new IllegalArgumentException("Ten life metrics required");
        values.forEach(LifeRecap::check); return List.copyOf(values);
    }
    public static void check(double value) { if (!Double.isFinite(value) || value < 0 || value > Long.MAX_VALUE) throw new IllegalArgumentException("Invalid life counter"); }
    public double value(LifeMetric metric) { return values.get(metric.ordinal()); }
}
