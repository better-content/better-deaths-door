package com.bettercontent.betterdeathsdoor.state;

public enum Region {
    HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG;
    public String translationKey() { return "region.better_deaths_door." + name().toLowerCase(java.util.Locale.ROOT); }
}
