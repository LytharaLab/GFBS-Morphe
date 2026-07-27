package org.lytharalab.gfbs.morphe.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.lytharalab.gfbs.morphe.api.MorpheUiAction;
import org.lytharalab.gfbs.morphe.server.MorpheSessionManager;

import java.util.UUID;
import java.util.function.Supplier;

public record UiActionPacket(
    UUID sessionId,
    ResourceLocation document,
    String action,
    CompoundTag payload
) {
    public static void encode(UiActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeResourceLocation(packet.document);
        buffer.writeUtf(packet.action, 64);
        buffer.writeNbt(packet.payload);
    }

    public static UiActionPacket decode(FriendlyByteBuf buffer) {
        UUID sessionId = buffer.readUUID();
        ResourceLocation document = buffer.readResourceLocation();
        String action = buffer.readUtf(64);
        CompoundTag payload = buffer.readNbt();
        return new UiActionPacket(sessionId, document, action, payload == null ? new CompoundTag() : payload);
    }

    public static void handle(UiActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null && !packet.action.isBlank()) {
                if (packet.action.equals("__close")) {
                    MorpheSessionManager.closeIfMatches(sender, packet.sessionId, packet.document);
                    return;
                }
                try {
                    NbtDataCodec.toMap(packet.payload);
                } catch (RuntimeException ignored) {
                    return;
                }
                MorpheSessionManager.accept(
                    sender,
                    new MorpheUiAction(packet.sessionId, packet.document, packet.action, packet.payload)
                );
            }
        });
        context.setPacketHandled(true);
    }
}
