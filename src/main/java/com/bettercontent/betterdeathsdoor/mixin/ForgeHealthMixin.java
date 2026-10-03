package com.bettercontent.betterdeathsdoor.mixin;
import com.bettercontent.betterdeathsdoor.client.ClientRevivalState;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.bettercontent.betterdeathsdoor.client.InjuryHudMath;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.injection.At;
/** Keep native heart types, absorption and layout while hiding the engine-only sentinel. */
@Mixin(value=ForgeGui.class,remap=false)
public abstract class ForgeHealthMixin extends net.minecraft.client.gui.Gui {
 protected ForgeHealthMixin(Minecraft minecraft,net.minecraft.client.renderer.entity.ItemRenderer renderer){super(minecraft,renderer);}
 @Shadow(remap=false) public int leftHeight;
 private boolean injuryAtDoor(){var player=Minecraft.getInstance().player;return player!=null&&ClientRevivalState.get(player.getUUID()).map(v->v.atDoor()).orElse(false);}
 private double injuryRisk(){var player=Minecraft.getInstance().player;return player==null?0:ClientRevivalState.get(player.getUUID()).map(v->v.probability()).orElse(0d);}
 // Forge 47.4.13 stores the heart-row spacing in local 12; verify through the actual client lane.
 @ModifyVariable(method="renderHealth",at=@At("STORE"),index=12,remap=false)
 private int injuryReserveHeartCrowns(int vanilla){return InjuryHudMath.heartRowSpacing(vanilla,injuryRisk());}
 @Inject(method="renderHealth",at=@At("RETURN"),remap=false)
 private void injuryReserveArmorClearance(int width,int height,GuiGraphics graphics,CallbackInfo callback){if(injuryRisk()>0)leftHeight+=6;}
 @Inject(method="renderHealth",at=@At("HEAD"),remap=false)
 private void injuryClearRememberedHealth(int width,int height,GuiGraphics graphics,CallbackInfo callback){
  if(injuryAtDoor()){displayHealth=0;lastHealth=0;healthBlinkTime=0;}
 }
 // Forge 47.4.13 stores current and remembered health in locals 5 and 7.
 @ModifyVariable(method="renderHealth",at=@At("STORE"),index=5,remap=false)
 private int injuryEmptyCurrentHearts(int health){return injuryAtDoor()?0:health;}
 @ModifyVariable(method="renderHealth",at=@At("STORE"),index=7,remap=false)
 private int injuryEmptyRememberedHearts(int health){return injuryAtDoor()?0:health;}
}
