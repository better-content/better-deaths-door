package com.bettercontent.betterdeathsdoor.network;

import com.bettercontent.betterdeathsdoor.client.MendUi;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Subject-bound feedback cannot leak between own-body and teammate views. */
public record TreatmentStatusPacket(UUID subject, float progress, String text) {
    public static void encode(TreatmentStatusPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.subject); buffer.writeFloat(packet.progress); buffer.writeUtf(packet.text, 512);
    }
    public static TreatmentStatusPacket decode(FriendlyByteBuf buffer) {
        return new TreatmentStatusPacket(buffer.readUUID(), buffer.readFloat(), buffer.readUtf(512));
    }
    public static void handle(TreatmentStatusPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> MendUi.treatment(packet.subject, packet.progress, packet.text)));
        context.get().setPacketHandled(true);
    }
}
