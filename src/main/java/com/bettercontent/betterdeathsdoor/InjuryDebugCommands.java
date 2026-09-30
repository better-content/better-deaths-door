package com.bettercontent.betterdeathsdoor;
import com.bettercontent.betterdeathsdoor.network.*;
import com.bettercontent.betterdeathsdoor.state.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import static net.minecraft.commands.Commands.*;
/** Operator commands use the same authority and client screens as normal interaction. */
@Mod.EventBusSubscriber(modid=RevivalMod.MOD_ID)
public final class InjuryDebugCommands {
 @SubscribeEvent public static void register(RegisterCommandsEvent event){register(event.getDispatcher());}
 public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
  var gui=literal("gui").then(argument("viewer",EntityArgument.player())
   .then(literal("own-body").executes(c->{RevivalNetwork.send(EntityArgument.getPlayer(c,"viewer"),new UiControlPacket("own-body","",0,0));return 1;}))
   .then(literal("inventory").executes(c->{RevivalNetwork.send(EntityArgument.getPlayer(c,"viewer"),new UiControlPacket("inventory","",0,0));return 1;}))
   .then(literal("mend").then(argument("subject",EntityArgument.player()).executes(c->{RevivalManager.openBody(EntityArgument.getPlayer(c,"viewer"),EntityArgument.getPlayer(c,"subject"));return 1;})))
   .then(literal("close").executes(c->{var p=EntityArgument.getPlayer(c,"viewer");RevivalManager.closeBody(p);RevivalNetwork.send(p,new UiControlPacket("close","",0,0));return 1;}))
   .then(literal("death-recap").executes(c->{var player=EntityArgument.getPlayer(c,"viewer");if(player.isAlive()){c.getSource().sendFailure(Component.literal("Death recap requires an actual final death in the isolated review world. Use scenario final_death."));return 0;}RevivalManager.sendRecap(player);RevivalNetwork.send(player,new UiControlPacket("death-recap","",0,0));return 1;})));
  var scenario=literal("scenario").then(argument("player",EntityArgument.player()).then(argument("fixture",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"healthy","mixed","severe","long_history","healing_lock","trauma_expiry","final_death"},b)).executes(c->{RevivalManager.debugScenario(EntityArgument.getPlayer(c,"player"),StringArgumentType.getString(c,"fixture"));return 1;})));
  var capture=literal("capture").then(argument("viewer",EntityArgument.player()).then(argument("label",StringArgumentType.word()).executes(c->{RevivalNetwork.send(EntityArgument.getPlayer(c,"viewer"),new UiControlPacket("capture",StringArgumentType.getString(c,"label"),0,5));return 1;}).then(argument("delayTicks",IntegerArgumentType.integer(2,200)).executes(c->{RevivalNetwork.send(EntityArgument.getPlayer(c,"viewer"),new UiControlPacket("capture",StringArgumentType.getString(c,"label"),0,IntegerArgumentType.getInteger(c,"delayTicks")));return 1;}))));
  var treatment=literal("treatment")
   .then(literal("cancel").then(argument("healer",EntityArgument.player()).executes(c->{RevivalManager.cancelTreatment(EntityArgument.getPlayer(c,"healer"),"Treatment cancelled");return 1;})))
   .then(literal("start").then(argument("healer",EntityArgument.player()).then(argument("subject",EntityArgument.player()).executes(c->{RevivalManager.startTreatment(EntityArgument.getPlayer(c,"healer"),EntityArgument.getPlayer(c,"subject"));return 1;}))));
  var pressure=literal("pressure").then(argument("player",EntityArgument.player()).then(argument("maims",IntegerArgumentType.integer(0,1000)).then(argument("atDoor",BoolArgumentType.bool()).then(argument("maxHp",IntegerArgumentType.integer(1,200)).executes(c->{
   var player=EntityArgument.getPlayer(c,"player");RevivalManager.debugScenario(player,"healthy");
   player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(IntegerArgumentType.getInteger(c,"maxHp"));
   var body=RevivalManager.state(player);body.clear();
   for(int i=0;i<IntegerArgumentType.getInteger(c,"maims");i++)body.addMaim(Region.LEFT_LEG,MaimType.CRACKED,RevivalManager.now(player));
   boolean door=BoolArgumentType.getBool(c,"atDoor");if(door)body.enterDoor(RevivalManager.now(player),RevivalConfig.tuning());
   RevivalManager.setHealthInternal(player,door?RevivalManager.SENTINEL:player.getMaxHealth());RevivalManager.refresh(player);
   c.getSource().sendSuccess(()->Component.literal("Actual next-hit risk: "+RevivalManager.snapshot(player).deathProbability()),false);return 1;
  })))));
  var maim=literal("maim").then(argument("player",EntityArgument.player()).then(argument("region",StringArgumentType.word()).then(argument("type",StringArgumentType.word()).executes(c->{try{var player=EntityArgument.getPlayer(c,"player");var region=Region.valueOf(StringArgumentType.getString(c,"region").toUpperCase(java.util.Locale.ROOT));var type=MaimType.valueOf(StringArgumentType.getString(c,"type").toUpperCase(java.util.Locale.ROOT));RevivalManager.state(player).addMaim(region,type,RevivalManager.now(player));RevivalManager.refresh(player);return 1;}catch(IllegalArgumentException e){c.getSource().sendFailure(Component.literal("Invalid region or injury type"));return 0;}}))));
  var preferences=literal("presentation").then(argument("viewer",EntityArgument.player()).then(argument("reducedMotion",BoolArgumentType.bool()).then(argument("sound",BoolArgumentType.bool()).executes(c->{RevivalNetwork.send(EntityArgument.getPlayer(c,"viewer"),new UiControlPacket("presentation",Boolean.toString(BoolArgumentType.getBool(c,"sound")),BoolArgumentType.getBool(c,"reducedMotion")?1:0,0));return 1;}))));
  dispatcher.register(literal("betterdeathsdoor").requires(s->s.hasPermission(2)).then(literal("debug").then(gui).then(scenario).then(capture).then(treatment).then(pressure).then(maim).then(preferences)));
 }
}
