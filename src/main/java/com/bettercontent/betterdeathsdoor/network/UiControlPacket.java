package com.bettercontent.betterdeathsdoor.network;
import com.bettercontent.betterdeathsdoor.client.ClientRevivalState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record UiControlPacket(String operation,String text,float progress,int delay) {
 public static void encode(UiControlPacket p,FriendlyByteBuf b){b.writeUtf(p.operation,32);b.writeUtf(p.text,512);b.writeFloat(p.progress);b.writeVarInt(p.delay);}
 public static UiControlPacket decode(FriendlyByteBuf b){return new UiControlPacket(b.readUtf(32),b.readUtf(512),b.readFloat(),b.readVarInt());}
 public static void handle(UiControlPacket p,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientRevivalState.control(p)));c.get().setPacketHandled(true);}
}
