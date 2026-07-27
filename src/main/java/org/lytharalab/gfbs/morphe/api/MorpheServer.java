package org.lytharalab.gfbs.morphe.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import org.lytharalab.gfbs.morphe.network.MorpheNetwork;
import org.lytharalab.gfbs.morphe.server.MorpheSessionManager;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server API for opening trusted resource-pack UI documents.
 */
public final class MorpheServer {
    private static final Map<ResourceLocation, MorpheActionHandler> HANDLERS = new ConcurrentHashMap<>();

    private MorpheServer() {
    }

    public static void open(ServerPlayer player, ResourceLocation document) {
        open(player, document, new CompoundTag());
    }

    public static void open(ServerPlayer player, ResourceLocation document, CompoundTag initialData) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(document, "document");
        CompoundTag safeData = initialData == null ? new CompoundTag() : initialData.copy();
        var session = MorpheSessionManager.open(player, document);
        MorpheNetwork.sendOpen(player, session.sessionId(), document, safeData);
    }

    public static void close(ServerPlayer player) {
        var session = MorpheSessionManager.close(player);
        if (session != null) {
            MorpheNetwork.sendClose(player, session.sessionId());
        }
    }

    public static void showHud(
        ServerPlayer player,
        ResourceLocation layerId,
        ResourceLocation document,
        CompoundTag initialData
    ) {
        showHud(player, layerId, document, initialData, MorpheViewOptions.hud());
    }

    public static void showHud(
        ServerPlayer player,
        ResourceLocation layerId,
        ResourceLocation document,
        CompoundTag initialData,
        MorpheViewOptions options
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(layerId, "layerId");
        Objects.requireNonNull(document, "document");
        CompoundTag safeData = initialData == null ? new CompoundTag() : initialData.copy();
        MorpheViewOptions safeOptions = options == null ? MorpheViewOptions.hud() : options.copy();
        var session = MorpheSessionManager.openHud(player, layerId, document);
        MorpheNetwork.sendOpenHud(
            player,
            session.sessionId(),
            layerId,
            document,
            safeData,
            safeOptions
        );
    }

    public static void hideHud(ServerPlayer player, ResourceLocation layerId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(layerId, "layerId");
        var session = MorpheSessionManager.closeHud(player, layerId);
        if (session != null) {
            MorpheNetwork.sendCloseHud(player, session.sessionId(), layerId);
        }
    }

    public static void registerActionHandler(ResourceLocation document, MorpheActionHandler handler) {
        MorpheActionHandler previous = HANDLERS.putIfAbsent(
            Objects.requireNonNull(document, "document"),
            Objects.requireNonNull(handler, "handler")
        );
        if (previous != null) {
            throw new IllegalStateException("A Morphe action handler is already registered for " + document);
        }
    }

    public static void replaceActionHandler(ResourceLocation document, MorpheActionHandler handler) {
        HANDLERS.put(
            Objects.requireNonNull(document, "document"),
            Objects.requireNonNull(handler, "handler")
        );
    }

    public static void unregisterActionHandler(ResourceLocation document) {
        HANDLERS.remove(document);
    }

    public static void dispatch(ServerPlayer player, MorpheUiAction action) {
        MinecraftForge.EVENT_BUS.post(new MorpheUiActionEvent(player, action));
        MorpheActionHandler handler = HANDLERS.get(action.document());
        if (handler != null) {
            handler.handle(player, action);
        }
    }
}
