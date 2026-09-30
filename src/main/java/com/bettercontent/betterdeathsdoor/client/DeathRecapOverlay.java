package com.bettercontent.betterdeathsdoor.client;
import com.bettercontent.betterdeathsdoor.RevivalMod;
import com.bettercontent.betterdeathsdoor.mixin.DeathCauseAccessor;
import com.bettercontent.betterdeathsdoor.network.BodyView;
import com.bettercontent.betterdeathsdoor.state.Region;
import com.bettercontent.betterdeathsdoor.state.MaimType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Native death controls retain their handlers; the bodily record uses the space above them. */
@Mod.EventBusSubscriber(modid=RevivalMod.MOD_ID,value=Dist.CLIENT)
public final class DeathRecapOverlay {
 public record Bounds(int x,int y,int width,int height){}
 private static Screen previous;private static long opened;
 private static Screen hintScreen;private static int hintHeight;
 /** Consumers reserve measured copy space; this class alone positions native death controls. */
 public static Bounds reserveHint(Screen screen,int measuredHeight){hintScreen=screen;hintHeight=Math.max(0,measuredHeight);int w=Math.min(560,screen.width-16);return new Bounds(Math.max(8,(screen.width-w)/2),78,w,hintHeight);}
 private static int hintOffset(int width,int height){return hintScreen!=null&&hintScreen.width==width&&hintScreen.height==height?hintHeight+4:0;}
 public static Bounds reservedBounds(int width,int height){int top=Math.min(Math.max(84,78+hintOffset(width,height)),Math.max(56,height-120));int w=Math.min(560,width-16);return new Bounds(Math.max(8,(width-w)/2),top,w,Math.min(300,Math.max(0,height-60-top)));}
 @SubscribeEvent public static void init(ScreenEvent.Init.Post event){if(!(event.getScreen() instanceof DeathScreen screen))return;hintScreen=null;hintHeight=0;var buttons=event.getListenersList().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();if(buttons.size()==2){int buttonWidth=Math.min(200,(screen.width-28)/2);for(int i=0;i<2;i++){var button=buttons.get(i);button.setWidth(buttonWidth);button.setX(screen.width/2+(i==0?-buttonWidth-3:3));button.setY(screen.height-34);}}}
 @SubscribeEvent(priority=EventPriority.LOW) public static void render(ScreenEvent.Render.Post event){if(!(event.getScreen() instanceof DeathScreen screen))return;var mc=Minecraft.getInstance();var header=event.getGuiGraphics();com.mojang.blaze3d.systems.RenderSystem.disableScissor();int hw=Math.min(560,screen.width-16),hx=Math.max(8,(screen.width-hw)/2);header.pose().pushPose();header.pose().translate(0,0,500);header.fill(0,0,screen.width,screen.height,0xFF17231C);header.fill(hx,8,hx+hw,screen.height-8,0xFFA48657);header.fill(hx+2,10,hx+hw-2,screen.height-10,0xFFEFE3C4);header.fill(hx+10,65,hx+hw-10,66,0xFFA48657);header.drawString(mc.font,screen.getTitle(),(screen.width-mc.font.width(screen.getTitle()))/2,25,0xFF254637,false);var cause=((DeathCauseAccessor)screen).betterDeathsDoor$causeOfDeath();if(cause!=null)header.drawString(mc.font,mc.font.plainSubstrByWidth(cause.getString(),hw-24),(screen.width-Math.min(hw-24,mc.font.width(cause)))/2,44,0xFF5D6752,false);BodyView body=ClientRevivalState.recap();if(body==null){drawButtons(screen,event);header.pose().popPose();return;}if(previous!=screen){previous=screen;opened=System.currentTimeMillis();if(InjuryClientConfig.SOUND.get()&&mc.player!=null)mc.player.playSound(SoundEvents.BASALT_BREAK,.35f,.65f);}
  var box=reservedBounds(screen.width,screen.height);var g=event.getGuiGraphics();if(box.height()<48){drawButtons(screen,event);header.pose().popPose();return;}
  g.fill(box.x()+6,box.y()+12,box.x()+box.width()-6,box.y()+13,0xFFAC9163);
  var damage=ClientRevivalState.recapDamage();
  boolean compact=box.height()<270;
  int ink=0xFF254637, muted=0xFF5D6752, red=0xFF9A4B42, green=0xFF496951;
  g.drawCenteredString(mc.font,"FINAL LIFE / "+damage.hits()+" ACCEPTED HITS",box.x()+box.width()/2,box.y()+2,ink);
  g.drawString(mc.font,"Incoming "+amount(damage.incoming())+"   Mitigated "+amount(damage.mitigation()),box.x()+8,box.y()+17,ink,false);
  g.drawString(mc.font,"Absorbed "+amount(damage.absorption())+"   Applied "+amount(damage.applied()),box.x()+8,box.y()+30,ink,false);
  g.drawString(mc.font,"HP lost "+amount(damage.healthLost())+"   Unknown "+amount(damage.unknown()),box.x()+8,box.y()+43,ink,false);
  double total=Math.max(1,damage.incoming());
  int barX=box.x()+8,barY=box.y()+56,barW=box.width()-16;
  g.fill(barX,barY,barX+barW,barY+7,0xFFD1BE96);
  int absorbed=(int)Math.round(barW*Math.max(0,Math.min(1,damage.absorption()/total)));
  int mitigated=(int)Math.round(barW*Math.max(0,Math.min(1,damage.mitigation()/total)));
  int lost=(int)Math.round(barW*Math.max(0,Math.min(1,damage.healthLost()/total)));
  g.fill(barX,barY,barX+absorbed,barY+7,0xFF597F75);
  g.fill(barX+absorbed,barY,Math.min(barX+barW,barX+absorbed+mitigated),barY+7,green);
  g.fill(Math.min(barX+barW,barX+absorbed+mitigated),barY,Math.min(barX+barW,barX+absorbed+mitigated+lost),barY+7,red);
  if(compact){int active=body.regions().stream().mapToInt(BodyView.RegionView::total).sum(),treated=body.regions().stream().mapToInt(BodyView.RegionView::treated).sum();g.drawString(mc.font,"Injuries: "+active+" active · "+treated+" treated",box.x()+8,box.y()+69,active>0?red:green,false);int cellW=(box.width()-16)/2;for(var region:Region.values()){int i=region.ordinal(),cx=box.x()+8+(i%2)*cellW,cy=box.y()+89+(i/2)*19;var r=body.region(region);g.drawString(mc.font,BodyView.label(region)+"  "+r.total()+" / "+r.treated(),cx,cy,r.total()>0?red:ink,false);}drawButtons(screen,event);header.pose().popPose();return;}
  g.drawCenteredString(mc.font,"BODY RECORD / ACTIVE + TREATED",box.x()+box.width()/2,box.y()+70,ink);
  int columns=2,rows=3,cellW=(box.width()-16)/columns,cellH=(box.height()-93)/rows;
  for(var region:Region.values()){int i=region.ordinal();if(!InjuryClientConfig.REDUCED_MOTION.get()&&System.currentTimeMillis()-opened<i*80L)continue;int x=box.x()+8+(i%columns)*cellW,y=box.y()+91+(i/columns)*cellH;var r=body.region(region);
   g.fill(x,y-2,x+cellW-6,y+cellH-6,0xFFF5EBD3);
   g.drawString(mc.font,BodyView.label(region).toUpperCase(java.util.Locale.ROOT),x+5,y+3,r.total()>0?red:ink,false);
   int gauge=Math.max(1,cellW-18);g.fill(x+5,y+16,x+5+gauge,y+20,0xFFD3C3A2);
   g.fill(x+5,y+16,x+5+(int)(gauge*Math.min(1,r.total()/6.0)),y+20,red);
   g.drawString(mc.font,"Active "+r.total()+"  "+types(r,false),x+5,y+25,red,false);
   g.drawString(mc.font,"Treated "+r.treated()+"  "+types(r,true),x+5,y+36,green,false);
  }
  drawButtons(screen,event);
  header.pose().popPose();
 }
 private static void drawButtons(DeathScreen screen,ScreenEvent.Render.Post event){
  var g=event.getGuiGraphics();var font=Minecraft.getInstance().font;
  for(var listener:screen.children()) if(listener instanceof Button button && button.visible){
   int x=button.getX(),y=button.getY(),w=button.getWidth(),h=button.getHeight();
   boolean hover=event.getMouseX()>=x&&event.getMouseX()<x+w&&event.getMouseY()>=y&&event.getMouseY()<y+h;
   g.fill(x,y,x+w,y+h,hover?0xFF59755C:0xFF405D49);
   g.fill(x,y,x+w,y+2,0xFFAC9163);
   g.drawString(font,button.getMessage(),x+(w-font.width(button.getMessage()))/2,y+(h-font.lineHeight)/2,0xFFF5EBD3,false);
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
