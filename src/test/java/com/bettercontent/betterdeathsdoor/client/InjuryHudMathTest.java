package com.bettercontent.betterdeathsdoor.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InjuryHudMathTest {
 @Test void skullCrownsHaveRoomBetweenHeartRows(){for(int vanilla=3;vanilla<=10;vanilla++){assertEquals(vanilla,InjuryHudMath.heartRowSpacing(vanilla,0));assertTrue(InjuryHudMath.heartRowSpacing(vanilla,.05)>=15);}}
 @Test void skullCoverageRepresentsExactProbability(){for(double probability:new double[]{0,.05,.15,.53,.99,1})for(int hearts:new int[]{1,10,17,40}){double sum=0;for(int i=0;i<hearts;i++)sum+=InjuryHudMath.skullCoverage(probability,i,hearts);assertEquals(probability,sum/hearts,1e-9);}}
 @Test void heartbeatEscalatesOnlyWithProbability(){int previous=41;for(int i=0;i<=100;i++){int ticks=InjuryHudMath.heartbeatTicks(i/100d);assertTrue(ticks<=previous);assertTrue(ticks>=12);previous=ticks;}}
 @Test void HealthySkullsFollowHealthFadeWhileDoorStaysVisible(){assertEquals(0,InjuryHudMath.skullAlpha(false,0));assertEquals(.275f,InjuryHudMath.skullAlpha(false,.5f));assertEquals(1,InjuryHudMath.skullAlpha(true,0));}
}
