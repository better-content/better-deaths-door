package com.bettercontent.betterdeathsdoor.client;
public final class InjuryHudMath {
 private InjuryHudMath(){}
 public static int heartRowSpacing(int vanilla,double probability){return probability>0?Math.max(vanilla,15):vanilla;}
 public static double skullCoverage(double probability,int index,int hearts){return Math.max(0,Math.min(1,probability*hearts-index));}
 public static int heartbeatTicks(double probability){return (int)Math.round(40-28*Math.max(0,Math.min(1,probability)));}
 public static float skullAlpha(boolean atDoor,float healthAlpha){return atDoor?1:Math.max(0,Math.min(1,healthAlpha))*.55f;}
}
