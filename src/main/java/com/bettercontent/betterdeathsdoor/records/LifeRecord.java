package com.bettercontent.betterdeathsdoor.records;

import java.util.*;
import static com.bettercontent.betterdeathsdoor.records.LifeRecap.Badge.*;

/** World-owned one-current-life state plus completed bests. Pure policy, independent of Minecraft. */
public final class LifeRecord {
    UUID lifeId = UUID.randomUUID();
    boolean partial, eligible = true, finalized, containedDeath;
    final double[] values = new double[10], bests = new double[10];
    final int[] milestoneIds = new int[10], reachedTiers = new int[10];
    LifeRecap latest;
    public LifeRecord(boolean partial) { this.partial = partial; }
    public UUID lifeId() { return lifeId; }
    public LifeRecap latest() { return latest; }
    public double value(LifeMetric metric) { return values[metric.ordinal()]; }
    public void invalidate() { eligible = false; }
    public void add(LifeMetric metric, double amount) {
        if (finalized || !Double.isFinite(amount) || amount <= 0) return;
        int i = metric.ordinal(); values[i] = Math.min((double) Long.MAX_VALUE, values[i] + amount);
        milestoneIds[i] = Math.max(milestoneIds[i], metric.tier(values[i]));
    }
    public void level(int level) { if (!finalized && level > values[1]) { values[1] = level; milestoneIds[1] = LifeMetric.XP_LEVEL.tier(level); } }
    public void nextLife() {
        lifeId = UUID.randomUUID(); partial = false; eligible = true; finalized = false; containedDeath = false;
        Arrays.fill(values, 0); Arrays.fill(milestoneIds, 0);
    }
    public LifeRecap freeze() {
        if (finalized) return latest;
        latest = new LifeRecap(1, lifeId, partial, eligible, list(values), list(bests), partial || !eligible ? List.of() : cards());
        finalized = true; // Freeze BEFORE updating bests. Repeated finalization returns the exact same DTO.
        if (!partial && eligible) for (int i = 0; i < 10; i++) {
            bests[i] = Math.max(bests[i], values[i]);
            reachedTiers[i] = Math.max(reachedTiers[i], milestoneIds[i]);
        }
        return latest;
    }
    private record Candidate(LifeMetric metric, int category, double rank) {}
    private List<LifeRecap.Card> cards() {
        List<Candidate> candidates = new ArrayList<>();
        for (LifeMetric m : LifeMetric.values()) {
            int i = m.ordinal(); double value = values[i], old = bests[i];
            if (old > 0 && value > old) candidates.add(new Candidate(m, 0, (value - old) / old));
            else if (milestoneIds[i] > reachedTiers[i]) candidates.add(new Candidate(m, 1, 0));
            else if (old > 0 && value / old >= .9) candidates.add(new Candidate(m, 2, value / old));
            else if (old == 0 && value > 0) candidates.add(new Candidate(m, 3, value / m.milestones()[0]));
        }
        candidates.sort(Comparator.comparingInt(Candidate::category).thenComparing(Comparator.comparingDouble(Candidate::rank).reversed())
            .thenComparingInt(c -> c.metric.ordinal()));
        List<LifeRecap.Card> cards = new ArrayList<>();
        for (Candidate candidate : candidates.subList(0, Math.min(3, candidates.size()))) {
            LifeMetric m = candidate.metric; int i = m.ordinal(); double value = values[i], old = bests[i];
            List<LifeRecap.Badge> badges = new ArrayList<>();
            if (old > 0 && value > old) badges.add(BEST);
            if (milestoneIds[i] > reachedTiers[i]) badges.add(MILESTONE);
            if (old > 0 && value == old) badges.add(MATCHED);
            else if (old > 0 && value < old && value / old >= .9) badges.add(NEAR);
            if (old == 0 && value > 0) badges.add(FIRST);
            cards.add(new LifeRecap.Card(m, value, old, badges, milestoneIds[i]));
        }
        return List.copyOf(cards);
    }
    static List<Double> list(double[] values) { return Arrays.stream(values).boxed().toList(); }
}
