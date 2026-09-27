package com.bettercontent.downedplayerrevival.state;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Deterministic geometry and classification; adapters supply damage tags and the server's random roll. */
public final class DamageSelection {
    private DamageSelection() {}
    /** side < 0 means incoming origin on player's left; elevation > 0 means origin above. */
    public record Context(double side, double elevationDegrees, boolean fall, boolean explosion) {}
    public record Weights(double head, double torso, double arm, double leg,
                          double sideMultiplier, double explosionMultiplier, double elevationThreshold,
                          double fallLeg, double fallTorso) {
        public static final Weights DEFAULT = new Weights(1, 2, 1, 1, 3, 2, 30, .45, .1);
        public Weights {
            double[] values = {head, torso, arm, leg, sideMultiplier, explosionMultiplier, elevationThreshold, fallLeg, fallTorso};
            for (double value : values) if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid region weighting");
            if (head + torso + 2 * arm + 2 * leg <= 0 || 2 * fallLeg + fallTorso <= 0)
                throw new IllegalArgumentException("Region distribution must have positive weight");
        }
    }
    public static Map<Region, Double> regionWeights(Context context) { return regionWeights(context, Weights.DEFAULT); }
    public static Map<Region, Double> regionWeights(Context context, Weights settings) {
        EnumMap<Region, Double> weights = new EnumMap<>(Region.class);
        weights.put(Region.HEAD, settings.head()); weights.put(Region.TORSO, settings.torso());
        weights.put(Region.LEFT_ARM, settings.arm()); weights.put(Region.RIGHT_ARM, settings.arm());
        weights.put(Region.LEFT_LEG, settings.leg()); weights.put(Region.RIGHT_LEG, settings.leg());
        if (context.fall()) {
            weights.replaceAll((region, weight) -> switch(region) {
                case LEFT_LEG, RIGHT_LEG -> settings.fallLeg();
                case TORSO -> settings.fallTorso();
                default -> 0.0;
            });
        } else {
            double bias = context.explosion() ? settings.explosionMultiplier() : settings.sideMultiplier();
            if (context.side() < -1.0e-6) { multiply(weights, Region.LEFT_ARM, bias); multiply(weights, Region.LEFT_LEG, bias); }
            if (context.side() > 1.0e-6) { multiply(weights, Region.RIGHT_ARM, bias); multiply(weights, Region.RIGHT_LEG, bias); }
            if (context.elevationDegrees() > settings.elevationThreshold()) { multiply(weights, Region.HEAD, bias); multiply(weights, Region.TORSO, bias); }
            if (context.elevationDegrees() < -settings.elevationThreshold()) {
                multiply(weights, Region.LEFT_LEG, bias); multiply(weights, Region.RIGHT_LEG, bias); multiply(weights, Region.TORSO, bias);
            }
        }
        return java.util.Collections.unmodifiableMap(weights);
    }
    private static void multiply(Map<Region, Double> weights, Region region, double factor) { weights.compute(region, (key, weight) -> weight * factor); }
    public static Region selectRegion(Context context, double roll) { return selectRegion(context, roll, Weights.DEFAULT); }
    public static Region selectRegion(Context context, double roll, Weights weights) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1) throw new IllegalArgumentException("Roll must be in [0,1)");
        Map<Region, Double> distribution = regionWeights(context, weights);
        double total = distribution.values().stream().mapToDouble(Double::doubleValue).sum();
        double remaining = roll * total;
        Region last = Region.TORSO;
        for (Region region : Region.values()) {
            double weight = distribution.get(region);
            if (weight <= 0) continue;
            last = region;
            if (remaining < weight) return region;
            remaining -= weight;
        }
        return last;
    }

    /** Tags are normalized categories, supplied by Forge tag/config adapters rather than guessed from source names. */
    public static MaimType selectType(String damageId, Set<String> tags, boolean projectile, boolean armed,
                                     Map<String, MaimType> overrides) {
        MaimType override = overrides.get(damageId);
        if (override != null) return override;
        if (damageId.equals("minecraft:drown") || damageId.equals("adpother:low_oxygen")
            || tags.contains("low_oxygen")) return MaimType.LOW_OXYGEN;
        if (tags.contains("burnt") || tags.contains("fire") || tags.contains("heat") || tags.contains("acid")
            || tags.contains("corrosion") || tags.contains("freezing") || tags.contains("friction")) return MaimType.BURNT;
        if (tags.contains("cracked") || tags.contains("blunt") || tags.contains("crushing") || tags.contains("fall")) return MaimType.CRACKED;
        if (tags.contains("opened") || tags.contains("piercing") || tags.contains("cutting") || tags.contains("tear")
            || projectile || armed) return MaimType.OPENED;
        return MaimType.CRACKED;
    }
}
