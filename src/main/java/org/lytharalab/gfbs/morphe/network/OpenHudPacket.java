package org.lytharalab.gfbs.morphe.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.lytharalab.gfbs.morphe.api.MorpheViewOptions;
import org.lytharalab.gfbs.morphe.client.MorpheClient;

import java.util.UUID;
import java.util.function.Supplier;

public record OpenHudPacket(
    UUID sessionId,
    ResourceLocation layerId,
    ResourceLocation document,
    CompoundTag data,
    MorpheViewOptions options
) {
    public static void encode(OpenHudPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeResourceLocation(packet.layerId);
        buffer.writeResourceLocation(packet.document);
        buffer.writeNbt(packet.data);
        buffer.writeNbt(packet.options.toTag());
    }

    public static OpenHudPacket decode(FriendlyByteBuf buffer) {
        UUID sessionId = buffer.readUUID();
        ResourceLocation layerId = buffer.readResourceLocation();
        ResourceLocation document = buffer.readResourceLocation();
        CompoundTag data = buffer.readNbt();
        CompoundTag options = buffer.readNbt();
        return new OpenHudPacket(
            sessionId,
            layerId,
            document,
            data == null ? new CompoundTag() : data,
            MorpheViewOptions.fromTag(options)
        );
    }

    public static void handle(OpenHudPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
            Dist.CLIENT,
            () -> () -> MorpheClient.showHudFromServer(
                packet.layerId,
                packet.document,
                packet.data,
                packet.options,
                packet.sessionId
            )
        ));
        context.setPacketHandled(true);
    }
}
