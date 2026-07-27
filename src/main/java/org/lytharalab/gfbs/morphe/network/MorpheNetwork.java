package org.lytharalab.gfbs.morphe.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.lytharalab.gfbs.morphe.api.Morphe;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MorpheNetwork {
    private static final String PROTOCOL = "1";
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(Morphe.MOD_ID, "main"),
        () -> PROTOCOL,
        PROTOCOL::equals,
        PROTOCOL::equals
    );

    private MorpheNetwork() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        int id = 0;
        CHANNEL.registerMessage(
            id++,
            OpenUiPacket.class,
            OpenUiPacket::encode,
            OpenUiPacket::decode,
            OpenUiPacket::handle
        );
        CHANNEL.registerMessage(
            id++,
            UiActionPacket.class,
            UiActionPacket::encode,
            UiActionPacket::decode,
            UiActionPacket::handle
        );
        CHANNEL.registerMessage(
            id++,
            CloseUiPacket.class,
            CloseUiPacket::encode,
            CloseUiPacket::decode,
            CloseUiPacket::handle
        );
        CHANNEL.registerMessage(
            id++,
            OpenHudPacket.class,
            OpenHudPacket::encode,
            OpenHudPacket::decode,
            OpenHudPacket::handle
        );
        CHANNEL.registerMessage(
            id,
            CloseHudPacket.class,
            CloseHudPacket::encode,
            CloseHudPacket::decode,
            CloseHudPacket::handle
        );
    }

    public static void sendOpen(
        ServerPlayer player,
        UUID sessionId,
        ResourceLocation document,
        CompoundTag data
    ) {
        CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new OpenUiPacket(sessionId, document, data.copy())
        );
    }

    public static void sendAction(
        UUID sessionId,
        ResourceLocation document,
        String action,
        CompoundTag payload
    ) {
        CHANNEL.sendToServer(new UiActionPacket(sessionId, document, action, payload.copy()));
    }

    public static void sendOpenHud(
        ServerPlayer player,
        UUID sessionId,
        ResourceLocation layerId,
        ResourceLocation document,
        CompoundTag data,
        org.lytharalab.gfbs.morphe.api.MorpheViewOptions options
    ) {
        CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new OpenHudPacket(sessionId, layerId, document, data.copy(), options.copy())
        );
    }

    public static void sendClose(ServerPlayer player, UUID sessionId) {
        CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new CloseUiPacket(sessionId)
        );
    }

    public static void sendCloseHud(ServerPlayer player, UUID sessionId, ResourceLocation layerId) {
        CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new CloseHudPacket(sessionId, layerId)
        );
    }
}
