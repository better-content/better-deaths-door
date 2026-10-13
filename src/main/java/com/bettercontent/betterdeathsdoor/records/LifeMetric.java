package com.bettercontent.betterdeathsdoor.records;

/** Wire/persistence order is fixed. Values use native ticks, hunger points and HP. */
public enum LifeMetric {
    DURATION(24000,72000,168000), XP_LEVEL(10,20,30), MOB_KILLS(10,50,100),
    BLOCKS_PLACED(100,500,1000), BLOCKS_MINED(100,500,1000),
    HUNGER_RESTORED(100,500,1000), HP_LOST(100,500,1000),
    CHECKS_SURVIVED(1,5,10), DOOR_ENTRIES(1,5,10), INJURIES_TREATED(1,5,10);
    private final double[] milestones;
    LifeMetric(double... milestones) { this.milestones = milestones; }
    public double[] milestones() { return milestones.clone(); }
    public int tier(double value) {
        int tier = 0; for (double threshold : milestones) if (value >= threshold) tier++; return tier;
    }
}
