package com.bettercontent.betterdeathsdoor.state;

import java.util.Objects;

public record TreatmentRecord(long maimId, Region region, MaimType type, String itemId, long tick) {
    public TreatmentRecord { Objects.requireNonNull(region); Objects.requireNonNull(type); Objects.requireNonNull(itemId); }
}
