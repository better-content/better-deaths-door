package com.bettercontent.downedplayerrevival.state;

public enum MaimType {
    CRACKED, BURNT, OPENED, LOW_OXYGEN;
    public String defaultTreatment() { return "downed_player_revival:care"; }
    public String translationKey() { return "maim.downed_player_revival." + name().toLowerCase(java.util.Locale.ROOT); }
}
