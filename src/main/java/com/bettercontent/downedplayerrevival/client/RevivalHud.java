package com.bettercontent.downedplayerrevival.client;
import com.bettercontent.downedplayerrevival.RevivalMod;
import com.bettercontent.downedplayerrevival.network.BodyView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=RevivalMod.MOD_ID,value=Dist.CLIENT)
public final class RevivalHud {
 private static int beats;
 public static void reset(){beats=0;SurvivalHudBridge.update(false,0,0); }
 @SubscribeEvent public static void hideUnderDeathScreen(RenderGuiEvent.Pre event){var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen&&ClientRevivalState.recap()!=null)event.setCanceled(true);}

 public static void tick(){var mc=Minecraft.getInstance();if(mc.player==null)return;var state=ClientRevivalState.get(mc.player.getUUID()).orElse(null);if(state==null){SurvivalHudBridge.update(false,mc.player.getHealth(),0);return;}SurvivalHudBridge.update(state.atDoor(),state.health(),state.probability());if(state.atDoor()&&InjuryClientConfig.SOUND.get()&&++beats>=InjuryHudMath.heartbeatTicks(state.probability())){beats=0;mc.player.playSound(SoundEvents.NOTE_BLOCK_BASEDRUM.get(),(float)(.08+.18*state.probability()),.55f);}if(!state.atDoor())beats=0;}
 @SubscribeEvent public static void render(RenderGuiEvent.Post event){var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;var state=ClientRevivalState.get(mc.player.getUUID()).orElse(null);if(state==null)return;renderMarks(event.getGuiGraphics(),mc,state,event.getPartialTick());}
 public static void renderMarks(GuiGraphics g,Minecraft mc,BodyView state,float partial){int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight(),hearts=(int)Math.ceil(state.maxHealth()/2);int x=w/2-91,y=h-39;int rows=(int)Math.ceil((state.maxHealth()+mc.player.getAbsorptionAmount())/20);int spacing=InjuryHudMath.heartRowSpacing(Math.max(10-(rows-2),3),state.probability());float alpha=InjuryHudMath.skullAlpha(state.atDoor(),SurvivalHudBridge.alpha(partial));int color=((int)(alpha*255)<<24)|0xE5B2A6;
  if(!state.atDoor())for(int i=0;i<hearts;i++){double coverage=InjuryHudMath.skullCoverage(state.probability(),i,hearts);if(coverage<=0||alpha<=0)continue;int xx=x+(i%10)*8,yy=y-(i/10)*spacing;g.enableScissor(xx,yy-5,xx+(int)Math.ceil(8*coverage),yy+10);skull(g,xx,yy-5,color);g.disableScissor();}
  if(state.atDoor()){
   int c=0xFFE6B4A9;g.drawCenteredString(mc.font,"DEATH'S DOOR · 0 HP",w/2,13,c);
   int risk=(int)Math.round(state.probability()*100);
   String line=state.healingLockTicks()>0?String.format(java.util.Locale.ROOT,"%d%% death risk · Heal in %.1fs",risk,state.healingLockTicks()/20.0):risk+"% death risk · Heal to regain HP";
   g.drawCenteredString(mc.font,line,w/2,26,0xFFE6DFD7);
   if(!InjuryClientConfig.REDUCED_MOTION.get()){int edge=((int)(state.probability()*48)<<24)|0x9E493D;g.fill(0,0,2,h,edge);g.fill(w-2,0,w,h,edge);}
  }
 }
 private static void skull(GuiGraphics g,int x,int y,int color){g.fill(x+2,y,x+6,y+1,color);g.fill(x+1,y+1,x+7,y+4,color);g.fill(x+2,y+4,x+6,y+5,color);g.fill(x+2,y+2,x+3,y+3,(color&0xFF000000)|0x171B21);g.fill(x+5,y+2,x+6,y+3,(color&0xFF000000)|0x171B21);g.fill(x+3,y+5,x+4,y+6,color);g.fill(x+5,y+5,x+6,y+6,color);}
}
