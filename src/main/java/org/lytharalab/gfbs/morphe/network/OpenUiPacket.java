package org.lytharalab.gfbs.morphe.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.lytharalab.gfbs.morphe.client.MorpheClient;

import java.util.UUID;
import java.util.function.Supplier;

public record OpenUiPacket(UUID sessionId, ResourceLocation document, CompoundTag data) {
    public static void encode(OpenUiPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeResourceLocation(packet.document);
        buffer.writeNbt(packet.data);
    }

    public static OpenUiPacket decode(FriendlyByteBuf buffer) {
        UUID sessionId = buffer.readUUID();
        ResourceLocation document = buffer.readResourceLocation();
        CompoundTag data = buffer.readNbt();
        return new OpenUiPacket(sessionId, document, data == null ? new CompoundTag() : data);
    }

    public static void handle(OpenUiPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
            Dist.CLIENT,
            () -> () -> MorpheClient.openFromServer(packet.document, packet.data, packet.sessionId)
        ));
        context.setPacketHandled(true);
    }
}
