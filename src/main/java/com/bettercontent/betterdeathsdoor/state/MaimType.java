package com.bettercontent.betterdeathsdoor.state;

public enum MaimType {
    CRACKED, BURNT, OPENED, LOW_OXYGEN;
    public String defaultTreatment() { return "better_deaths_door:care"; }
    public String translationKey() { return "maim.better_deaths_door." + name().toLowerCase(java.util.Locale.ROOT); }
}
