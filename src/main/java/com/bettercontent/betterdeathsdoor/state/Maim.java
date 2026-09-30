package com.bettercontent.betterdeathsdoor.state;

import java.util.Objects;

public record Maim(long id, Region region, MaimType type, long tick) {
    public Maim { Objects.requireNonNull(region); Objects.requireNonNull(type); }
}
