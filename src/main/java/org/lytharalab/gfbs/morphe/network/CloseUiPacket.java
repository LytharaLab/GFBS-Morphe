package org.lytharalab.gfbs.morphe.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.lytharalab.gfbs.morphe.client.MorpheClient;

import java.util.UUID;
import java.util.function.Supplier;

public record CloseUiPacket(UUID sessionId) {
    public static void encode(CloseUiPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
    }

    public static CloseUiPacket decode(FriendlyByteBuf buffer) {
        return new CloseUiPacket(buffer.readUUID());
    }

    public static void handle(CloseUiPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
            Dist.CLIENT,
            () -> () -> MorpheClient.closeSession(packet.sessionId)
        ));
        context.setPacketHandled(true);
    }
}
