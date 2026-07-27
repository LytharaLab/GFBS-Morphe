package org.lytharalab.gfbs.morphe.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lytharalab.gfbs.morphe.api.MorpheViewOptions;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.UUID;

public final class MorpheClient {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MorpheClient() {
    }

    public static void open(ResourceLocation document) {
        open(document, new CompoundTag());
    }

    public static void open(ResourceLocation document, CompoundTag data) {
        open(document, data, null, MorpheViewOptions.screen());
    }

    public static void open(ResourceLocation document, CompoundTag data, MorpheViewOptions options) {
        open(document, data, null, options);
    }

    public static void openTransparent(ResourceLocation document, CompoundTag data) {
        open(document, data, null, MorpheViewOptions.transparentScreen());
    }

    public static void openFromServer(ResourceLocation document, CompoundTag data, UUID sessionId) {
        open(document, data, Objects.requireNonNull(sessionId, "sessionId"), MorpheViewOptions.screen());
    }

    public static void openFromServer(
        ResourceLocation document,
        CompoundTag data,
        UUID sessionId,
        MorpheViewOptions options
    ) {
        open(document, data, Objects.requireNonNull(sessionId, "sessionId"), options);
    }

    public static void showHud(ResourceLocation layerId, ResourceLocation document, CompoundTag data) {
        MorpheHudManager.get().show(layerId, document, data, MorpheViewOptions.hud(), null);
    }

    public static void showHud(
        ResourceLocation layerId,
        ResourceLocation document,
        CompoundTag data,
        MorpheViewOptions options
    ) {
        MorpheHudManager.get().show(layerId, document, data, options, null);
    }

    public static void hideHud(ResourceLocation layerId) {
        MorpheHudManager.get().hide(layerId);
    }

    public static void showHudFromServer(
        ResourceLocation layerId,
        ResourceLocation document,
        CompoundTag data,
        MorpheViewOptions options,
        UUID sessionId
    ) {
        MorpheHudManager.get().show(
            layerId,
            document,
            data,
            options,
            Objects.requireNonNull(sessionId, "sessionId")
        );
    }

    public static void hideHudFromServer(ResourceLocation layerId, UUID sessionId) {
        MorpheHudManager.get().hideIfSession(layerId, sessionId);
    }

    public static void setHudInteractive(ResourceLocation layerId, boolean interactive) {
        MorpheHudManager.get().setInteractive(layerId, interactive);
    }

    public static MorpheScreen currentScreen() {
        return Minecraft.getInstance().screen instanceof MorpheScreen screen ? screen : null;
    }

    public static void closeSession(UUID sessionId) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            MorpheScreen screen = currentScreen();
            if (screen != null && screen.isSession(sessionId)) {
                minecraft.setScreen(null);
            }
        });
    }

    public static boolean reloadCurrent() {
        MorpheScreen screen = currentScreen();
        if (screen == null) {
            return false;
        }
        screen.reloadDocument();
        return true;
    }

    public static boolean toggleInspector() {
        MorpheScreen screen = currentScreen();
        if (screen == null) {
            return false;
        }
        screen.toggleInspector();
        return true;
    }

    private static void open(
        ResourceLocation document,
        CompoundTag data,
        UUID sessionId,
        MorpheViewOptions options
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        CompoundTag safeData = data == null ? new CompoundTag() : data.copy();
        MorpheViewOptions safeOptions = options == null ? MorpheViewOptions.screen() : options.copy();
        // Client commands run while ChatScreen is still handling Enter. Opening
        // synchronously there is immediately overwritten when chat closes, so
        // force this onto the next client task instead of using execute(), which
        // may run inline on the render thread.
        minecraft.tell(() -> {
            try {
                minecraft.setScreen(new MorpheScreen(document, safeData, sessionId, safeOptions));
            } catch (RuntimeException | LinkageError exception) {
                LOGGER.error("Failed to open Morphe document {}", document, exception);
                minecraft.setScreen(null);
                if (minecraft.player != null) {
                    String detail = exception.getMessage();
                    minecraft.player.displayClientMessage(
                        Component.literal(
                            "Failed to open Morphe document " + document + ": "
                                + exception.getClass().getSimpleName()
                                + (detail == null || detail.isBlank() ? "" : " — " + detail)
                        ),
                        false
                    );
                }
            }
        });
    }
}
