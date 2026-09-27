package com.bettercontent.downedplayerrevival.client;
import com.bettercontent.downedplayerrevival.RevivalMod;
import com.bettercontent.downedplayerrevival.network.BodyView;
import com.bettercontent.downedplayerrevival.state.Region;
import com.bettercontent.downedplayerrevival.state.MaimType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Native death controls retain their handlers; the bodily record uses the space above them. */
@Mod.EventBusSubscriber(modid=RevivalMod.MOD_ID,value=Dist.CLIENT)
public final class DeathRecapOverlay {
 public record Bounds(int x,int y,int width,int height){}
 private static Screen previous;private static long opened;
 private static Screen hintScreen;private static int hintHeight;
 /** Consumers reserve measured copy space; this class alone positions native death controls. */
 public static Bounds reserveHint(Screen screen,int measuredHeight){hintScreen=screen;hintHeight=Math.max(0,measuredHeight);int w=Math.min(560,screen.width-16);return new Bounds(Math.max(8,(screen.width-w)/2),108,w,hintHeight);}
 private static int hintOffset(int width,int height){return hintScreen!=null&&hintScreen.width==width&&hintScreen.height==height?hintHeight+4:0;}
 public static Bounds reservedBounds(int width,int height){int top=Math.min(Math.max(110,108+hintOffset(width,height)),Math.max(56,height-120));return new Bounds(Math.max(8,(width-560)/2),top,Math.min(560,width-16),Math.max(0,height-30-top));}
 @SubscribeEvent public static void init(ScreenEvent.Init.Post event){if(!(event.getScreen() instanceof DeathScreen screen))return;hintScreen=null;hintHeight=0;var buttons=event.getListenersList().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();if(buttons.size()==2){int buttonWidth=Math.min(200,(screen.width-28)/2);for(int i=0;i<2;i++){var button=buttons.get(i);button.setWidth(buttonWidth);button.setX(screen.width/2+(i==0?-buttonWidth-3:3));button.setY(screen.height-24);}}}
 @SubscribeEvent public static void render(ScreenEvent.Render.Post event){if(!(event.getScreen() instanceof DeathScreen screen))return;BodyView body=ClientRevivalState.recap();if(body==null)return;var mc=Minecraft.getInstance();if(previous!=screen){previous=screen;opened=System.currentTimeMillis();if(InjuryClientConfig.SOUND.get()&&mc.player!=null)mc.player.playSound(SoundEvents.BASALT_BREAK,.35f,.65f);}
  var box=reservedBounds(screen.width,screen.height);var g=event.getGuiGraphics();if(box.height()<48)return;g.fill(box.x(),box.y(),box.x()+box.width(),box.y()+box.height(),0xDA181D23);
  var damage=ClientRevivalState.recapDamage();
  boolean compact=box.height()<170;
  g.drawCenteredString(mc.font,"FINAL LIFE · "+damage.hits()+" accepted hits",box.x()+box.width()/2,box.y()+3,MendUi.INK);
  g.drawString(mc.font,"Incoming "+amount(damage.incoming())+"   Mitigated "+amount(damage.mitigation()),box.x()+6,box.y()+17,MendUi.INK,false);
  g.drawString(mc.font,"Absorbed "+amount(damage.absorption())+"   Applied "+amount(damage.applied()),box.x()+6,box.y()+30,MendUi.INK,false);
  g.drawString(mc.font,"HP lost "+amount(damage.healthLost())+"   Unknown "+amount(damage.unknown()),box.x()+6,box.y()+43,MendUi.INK,false);
  if(compact){int active=body.regions().stream().mapToInt(BodyView.RegionView::total).sum(),treated=body.regions().stream().mapToInt(BodyView.RegionView::treated).sum();g.drawString(mc.font,"Injuries: "+active+" active · "+treated+" treated",box.x()+6,box.y()+58,active>0?MendUi.RED:MendUi.GREEN,false);return;}
  g.drawCenteredString(mc.font,"BODY RECORD · active / treated",box.x()+box.width()/2,box.y()+61,MendUi.INK);
  int columns=box.width()>=440?3:2,rows=(6+columns-1)/columns,cellW=(box.width()-12)/columns,cellH=(box.height()-76)/rows;
  for(var region:Region.values()){int i=region.ordinal();if(!InjuryClientConfig.REDUCED_MOTION.get()&&System.currentTimeMillis()-opened<i*80L)continue;int x=box.x()+6+(i%columns)*cellW,y=box.y()+76+(i/columns)*cellH;var r=body.region(region);
   g.drawString(mc.font,BodyView.label(region),x,y,r.total()>0?MendUi.RED:MendUi.GREEN,false);
   g.drawString(mc.font,"Active "+r.total()+"  "+types(r,false),x,y+9,MendUi.RED,false);
   g.drawString(mc.font,"Treated "+r.treated()+"  "+types(r,true),x,y+18,MendUi.GREEN,false);
  }
 }
 private static String types(BodyView.RegionView region,boolean treated){
  StringBuilder result=new StringBuilder();
  for(MaimType type:MaimType.values()){
   int count=treated?region.treatedCount(type):region.count(type);
   if(count>0){if(!result.isEmpty())result.append(" ");result.append(BodyView.label(type),0,Math.min(3,BodyView.label(type).length())).append(count);}
  }
  return result.toString();
 }
 private static String amount(double value){return !Double.isFinite(value)?"unknown":Math.abs(value)>=10000?String.format(java.util.Locale.ROOT,"%.2g",value):String.format(java.util.Locale.ROOT,"%.1f",value);}
}
