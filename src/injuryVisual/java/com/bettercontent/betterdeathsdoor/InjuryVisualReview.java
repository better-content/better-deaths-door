package com.bettercontent.betterdeathsdoor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** Isolated real-world review. Console commands invoke production authority; no synthetic inputs. */
@Mod.EventBusSubscriber(modid=RevivalMod.MOD_ID,value=Dist.CLIENT)
public final class InjuryVisualReview {
 private record Frame(String name,int width,int height,int scale,List<String> commands,int delay){}
 private static final boolean MANUAL="1".equals(System.getenv("INJURY_REVIEW_MANUAL"));
 private static final boolean HELPER=Boolean.getBoolean("injury.review.helper");
 private static final boolean MULTIPLAYER="1".equals(System.getenv("INJURY_REVIEW_MULTIPLAYER"));
 private static int pendingScale=-1;
 private static boolean starting,ready,capturing,published;private static int ticks,index=-1;private static final List<Frame> FRAMES=new ArrayList<>();
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) throws Exception {
  if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
  if(pendingScale>=0){mc.options.guiScale().set(pendingScale);mc.resizeDisplay();pendingScale=-1;System.out.println("INJURY_REVIEW_VIEWPORT "+mc.getWindow().getWidth()+"x"+mc.getWindow().getHeight()+" gui="+mc.getWindow().getGuiScaledWidth()+"x"+mc.getWindow().getGuiScaledHeight());}
  if(!starting&&(mc.screen instanceof TitleScreen || mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)&&mc.getOverlay()==null){starting=true;mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.options.pauseOnLostFocus=false;if(HELPER){net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(),mc,new net.minecraft.client.multiplayer.resolver.ServerAddress("localhost",56694),new net.minecraft.client.multiplayer.ServerData("Injury review","localhost:56694",false),false);return;}mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);
   mc.createWorldOpenFlows().createFreshLevel("injury-review-"+System.currentTimeMillis(),new LevelSettings("Injury review",GameType.SURVIVAL,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(245,false,false),registries->registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());return;}
  if(HELPER){Path stop=mc.gameDirectory.toPath().resolve("review.stop");if(Files.exists(stop)){Files.delete(stop);mc.stop();}return;}
  if(mc.player==null||mc.getSingleplayerServer()==null)return;
  if(MULTIPLAYER&&!published){mc.getSingleplayerServer().setUsesAuthentication(false);published=mc.getSingleplayerServer().publishServer(GameType.SURVIVAL,true,56694);System.out.println("INJURY_REVIEW_WAITING_HELPER port=56694");}
  if(MULTIPLAYER&&mc.getSingleplayerServer().getPlayerList().getPlayerByName("InjuryHelper")==null)return;
  if(!ready){if(++ticks<30)return;ready=true;ticks=0;prepare();command("gamerule doDaylightCycle false");command("gamerule naturalRegeneration false");command("gamerule doMobSpawning false");command("gamerule announceAdvancements false");command("time set noon");command("gamerule sendCommandFeedback false");if(MULTIPLAYER)command("execute at @a run tp InjuryHelper ~2 ~ ~");if(!MANUAL)next();else { viewport(1280,960,4);System.out.println("INJURY_REVIEW_MANUAL_READY"); }return;}
  Path queue=mc.gameDirectory.toPath().resolve("review.commands");if(Files.isRegularFile(queue)){var commands=Files.readAllLines(queue);Files.delete(queue);for(String line:commands){if(line.equals("stop")){mc.stop();return;}if(line.startsWith("viewport ")){var dimensions=line.split(" ");viewport(Integer.parseInt(dimensions[1]),Integer.parseInt(dimensions[2]),Integer.parseInt(dimensions[3]));continue;}if(!line.isBlank())command(line);}}
  if(MANUAL)return;
  if(ticks==FRAMES.get(index).delay-3)mc.gui.getChat().clearMessages(false);
  if(capturing||++ticks<FRAMES.get(index).delay)return;
  if(FRAMES.get(index).name.equals("treatment-success") && mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
      && com.bettercontent.betterdeathsdoor.client.ClientRevivalState.get(mc.player.getUUID()).map(v->v.treatmentActive()).orElse(false)) {
   if(ticks>1200)throw new IllegalStateException("Treatment did not complete in review fixture");
   return;
  }
  capturing=true;Screenshot.grab(mc.gameDirectory,FRAMES.get(index).name+".png",mc.getMainRenderTarget(),message->{System.out.println("INJURY_REVIEW_CAPTURE "+FRAMES.get(index).name+" "+message.getString());mc.execute(InjuryVisualReview::next);});
 }
 private static void viewport(int width,int height,int scale){GLFW.glfwSetWindowSize(Minecraft.getInstance().getWindow().getWindow(),width,height);pendingScale=scale;}
 private static void command(String command){command=command.replace("@a",Minecraft.getInstance().player.getGameProfile().getName());final String submitted=command;var server=Minecraft.getInstance().getSingleplayerServer();server.execute(()->server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),submitted));}
 private static String debug(String tail){return "betterdeathsdoor debug "+tail;}
 private static void next(){var mc=Minecraft.getInstance();if(++index>=FRAMES.size()){System.out.println("INJURY_REVIEW_COMPLETE frames="+FRAMES.size());if(MULTIPLAYER)try{Files.writeString(mc.gameDirectory.toPath().resolve("../injury-helper/review.stop"),"stop");}catch(java.io.IOException e){throw new RuntimeException(e);}mc.stop();return;}var frame=FRAMES.get(index);viewport(frame.width,frame.height,frame.scale);for(String c:frame.commands)command(c);ticks=0;capturing=false;}
 private static void prepare(){
  for(int[] size:new int[][]{{1280,720},{1280,960},{1920,1080}})for(int scale:new int[]{2,3,4}){
   String suffix="-"+size[0]+"x"+size[1]+"-s"+scale;
   add("inventory"+suffix,size,scale,List.of(debug("scenario @a healthy"),debug("gui @a inventory")),12);
   add("healthy"+suffix,size,scale,List.of(debug("scenario @a healthy"),debug("gui @a inventory")),12);
   add("mixed"+suffix,size,scale,List.of(debug("scenario @a mixed"),debug("gui @a inventory")),12);
   add("severe"+suffix,size,scale,List.of(debug("scenario @a severe"),debug("gui @a inventory")),12);
   add("armored"+suffix,size,scale,List.of(debug("scenario @a mixed"),"item replace entity @a armor.chest with minecraft:diamond_chestplate",debug("gui @a inventory")),12);
  }
  int[] wide={1920,1080};
  add("treatment-progress",wide,3,List.of(debug("scenario @a healthy"),debug("maim @a LEFT_ARM OPENED"),debug("gui @a inventory"),debug("treatment start @a @a")),15);
  add("treatment-success",wide,3,List.of(),200);
  add("treatment-interrupted",wide,3,List.of(debug("scenario @a mixed"),debug("gui @a inventory"),debug("treatment start @a @a"),"damage @a 1 minecraft:generic"),12);
  add("door-hud",wide,3,List.of(debug("scenario @a severe"),debug("gui @a close")),12);
  for(int maxHp:new int[]{20,60})for(boolean door:new boolean[]{true,false})for(int maims:new int[]{0,1,3,11,20}) {
   add("pressure-"+maims+"-hp"+maxHp+"-door"+door,wide,3,List.of(debug("pressure @a "+maims+" "+door+" "+maxHp),debug("gui @a close")),50);
  }
  add("pressure-armored-absorption",wide,3,List.of(debug("pressure @a 11 true 60"),"item replace entity @a armor.chest with minecraft:diamond_chestplate","effect give @a minecraft:absorption 60 0 true"),50);
  add("pressure-reduced-motion",wide,3,List.of(debug("presentation @a true false"),debug("pressure @a 11 true 20")),50);
  add("pressure-healthy-faded",wide,3,List.of(debug("pressure @a 11 false 20")),220);
  add("reset-presentation",wide,3,List.of(debug("presentation @a false true"),debug("pressure @a 0 false 20")),12);
  if(MULTIPLAYER){
   add("teammate-victim",wide,3,List.of(debug("scenario @a mixed"),"execute at @a run tp InjuryHelper ~2 ~ ~",debug("gui @a inventory"),debug("gui InjuryHelper mend @a"),debug("capture InjuryHelper teammate-before 8")),15);
   add("teammate-progress",wide,3,List.of(debug("treatment start InjuryHelper @a"),debug("capture InjuryHelper teammate-progress 8")),15);
   add("teammate-success",wide,3,List.of(debug("capture InjuryHelper teammate-success 60")),75);
   add("teammate-interrupted",wide,3,List.of(debug("scenario @a mixed"),debug("gui InjuryHelper mend @a"),debug("treatment start InjuryHelper @a"),"damage @a 1 minecraft:generic",debug("capture InjuryHelper teammate-interrupted 8")),15);
  }
  // Final death uses the real server damage/death pipeline and the native DeathScreen.
  for(int[] size:new int[][]{{1280,720},{1280,960},{1920,1080}})for(int scale:new int[]{2,3,4})add("death-"+size[0]+"x"+size[1]+"-s"+scale,size,scale,FRAMES.stream().anyMatch(f->f.name.startsWith("death-"))?List.of():List.of(debug("scenario @a long_history"),debug("scenario @a final_death")),30);
 }
 private static void add(String name,int[] size,int scale,List<String> commands,int delay){FRAMES.add(new Frame(name,size[0],size[1],scale,commands,delay));}
}
