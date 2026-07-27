package org.lytharalab.gfbs.morphe.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.lytharalab.gfbs.morphe.client.MorpheClient;

import java.util.UUID;
import java.util.function.Supplier;

public record CloseHudPacket(UUID sessionId, ResourceLocation layerId) {
    public static void encode(CloseHudPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeResourceLocation(packet.layerId);
    }

    public static CloseHudPacket decode(FriendlyByteBuf buffer) {
        return new CloseHudPacket(buffer.readUUID(), buffer.readResourceLocation());
    }

    public static void handle(CloseHudPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
            Dist.CLIENT,
            () -> () -> MorpheClient.hideHudFromServer(packet.layerId, packet.sessionId)
        ));
        context.setPacketHandled(true);
    }
}
