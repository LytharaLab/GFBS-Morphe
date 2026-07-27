package org.lytharalab.gfbs.morphe.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lytharalab.gfbs.morphe.api.Morphe;
import org.lytharalab.gfbs.morphe.api.MorpheViewOptions;
import org.lytharalab.gfbs.morphe.core.UiDocument;
import org.lytharalab.gfbs.morphe.core.UiElement;
import org.lytharalab.gfbs.morphe.network.MorpheNetwork;
import org.lytharalab.gfbs.morphe.network.NbtDataCodec;
import org.lytharalab.gfbs.morphe.script.MorpheLuaRuntime;
import org.lytharalab.gfbs.morphe.script.UiActionSink;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Multi-layer scripted HUD compositor. Passive layers never disturb gameplay;
 * cursor layers can temporarily enter a transparent interaction screen.
 */
public final class MorpheHudManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final MorpheHudManager INSTANCE = new MorpheHudManager();

    private final Map<ResourceLocation, HudLayer> layers = new LinkedHashMap<>();
    private final Set<ResourceLocation> pendingHide = new LinkedHashSet<>();
    private long lastFrameNanos;

    private MorpheHudManager() {
    }

    public static MorpheHudManager get() {
        return INSTANCE;
    }

    public void show(ResourceLocation layerId, ResourceLocation documentId, CompoundTag data) {
        show(layerId, documentId, data, MorpheViewOptions.hud(), null);
    }

    public void show(
        ResourceLocation layerId,
        ResourceLocation documentId,
        CompoundTag data,
        MorpheViewOptions requestedOptions,
        UUID sessionId
    ) {
        Minecraft.getInstance().tell(() ->
            showNow(layerId, documentId, data, requestedOptions, sessionId));
    }

    public void hide(ResourceLocation layerId) {
        Minecraft.getInstance().tell(() -> hideNow(layerId));
    }

    public void hideIfSession(ResourceLocation layerId, UUID sessionId) {
        Minecraft.getInstance().tell(() -> {
            HudLayer layer = layers.get(layerId);
            if (layer != null && sessionId != null && sessionId.equals(layer.sessionId)) {
                hideNow(layerId);
            }
        });
    }

    public void clear() {
        Minecraft.getInstance().tell(this::clearNow);
    }

    public void setInteractive(ResourceLocation layerId, boolean interactive) {
        Minecraft.getInstance().tell(() -> setInteractiveNow(layerId, interactive));
    }

    public boolean interactive(ResourceLocation layerId) {
        HudLayer layer = layers.get(layerId);
        return layer != null && layer.interactive;
    }

    public boolean contains(ResourceLocation layerId) {
        return layers.containsKey(layerId);
    }

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    void tick(Minecraft minecraft) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        for (HudLayer layer : layers.values()) {
            layer.document.resize(width, height);
            layer.host.gameTick();
            layer.document.tick(1.0 / 20.0);
        }
        flushPendingHide();
    }

    void renderBeforeScreen(Minecraft minecraft, GuiGraphics graphics, float partialTick) {
        double mouseX = MorpheEnvironment.scaledMouseX(minecraft);
        double mouseY = MorpheEnvironment.scaledMouseY(minecraft);
        double frameDelta = nextFrameDelta();
        for (HudLayer layer : layers.values()) {
            layer.host.delta(frameDelta);
            layer.document.frame(frameDelta);
        }
        renderPass(
            minecraft,
            graphics,
            partialTick,
            mouseX,
            mouseY,
            MorpheViewOptions.ScreenLayer.BELOW
        );
    }

    void renderAfterScreen(Minecraft minecraft, GuiGraphics graphics, float partialTick) {
        renderPass(
            minecraft,
            graphics,
            partialTick,
            MorpheEnvironment.scaledMouseX(minecraft),
            MorpheEnvironment.scaledMouseY(minecraft),
            MorpheViewOptions.ScreenLayer.ABOVE
        );
    }

    private void renderPass(
        Minecraft minecraft,
        GuiGraphics graphics,
        float partialTick,
        double mouseX,
        double mouseY,
        MorpheViewOptions.ScreenLayer pass
    ) {
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(graphics);
        for (HudLayer layer : ordered(false)) {
            if (!renderable(layer, minecraft, pass)) {
                continue;
            }
            if (layer.interactive) {
                layer.document.input().pointerMoved(mouseX, mouseY);
            }
            layer.document.render(canvas, mouseX, mouseY, partialTick);
        }
        renderTooltip(graphics, (int) mouseX, (int) mouseY, minecraft, pass);
    }

    boolean pointerMoved(double mouseX, double mouseY) {
        boolean handled = false;
        for (HudLayer layer : ordered(true)) {
            if (layer.interactive) {
                layer.document.input().pointerMoved(mouseX, mouseY);
                handled |= layer.document.input().hovered() != null;
            }
        }
        return handled;
    }

    boolean pointerDown(double x, double y, int button) {
        for (HudLayer layer : ordered(true)) {
            if (layer.interactive && layer.document.input().pointerDown(x, y, button)) return true;
        }
        return false;
    }

    boolean pointerUp(double x, double y, int button) {
        for (HudLayer layer : ordered(true)) {
            if (layer.interactive && layer.document.input().pointerUp(x, y, button)) return true;
        }
        return false;
    }

    boolean pointerDragged(double x, double y, int button, double dragX, double dragY) {
        for (HudLayer layer : ordered(true)) {
            if (layer.interactive && layer.document.input().pointerDragged(x, y, button, dragX, dragY)) return true;
        }
        return false;
    }

    boolean scrolled(double x, double y, double delta) {
        for (HudLayer layer : ordered(true)) {
            if (layer.interactive && layer.document.input().scrolled(x, y, delta)) return true;
        }
        return false;
    }

    boolean keyDown(int keyCode, int scanCode, int modifiers) {
        for (HudLayer layer : ordered(true)) {
            if (layer.interactive && layer.document.input().keyDown(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    boolean keyUp(int keyCode, int scanCode, int modifiers) {
        for (HudLayer layer : ordered(true)) {
            if (layer.interactive && layer.document.input().keyUp(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    boolean charTyped(char character, int modifiers) {
        for (HudLayer layer : ordered(true)) {
            if (layer.interactive && layer.document.input().charTyped(character, modifiers)) return true;
        }
        return false;
    }

    void interactionScreenClosed(ResourceLocation owner) {
        for (HudLayer layer : layers.values()) {
            if (layer.interactive) {
                layer.interactive = false;
                layer.host.setInteractiveFromManager(false);
            }
        }
    }

    private void showNow(
        ResourceLocation layerId,
        ResourceLocation documentId,
        CompoundTag data,
        MorpheViewOptions requestedOptions,
        UUID sessionId
    ) {
        if (layers.isEmpty()) {
            lastFrameNanos = 0;
        }
        hideNow(layerId);
        UiDocument document = null;
        try {
            MorpheViewOptions options = hudOptions(requestedOptions);
            document = Morphe.get().createDocument();
            MorpheClientHost host = new MorpheClientHost(
                options,
                () -> closeFromScript(layerId, documentId, sessionId),
                value -> setInteractiveNow(layerId, value),
                () -> { }
            );
            UiActionSink actionSink = sessionId == null ? UiActionSink.NOOP
                : (action, payload) -> MorpheNetwork.sendAction(
                    sessionId, documentId, action, NbtDataCodec.fromMap(payload));
            MorpheLuaRuntime runtime = new MorpheLuaRuntime(
                document,
                NbtDataCodec.toMap(data == null ? new CompoundTag() : data),
                actionSink,
                host
            );
            document.soundSink(host::playSound);
            document.runtime(runtime);
            // A HUD root is only a full-screen layout container. Keeping it
            // pointer-transparent prevents an empty top layer from swallowing
            // input intended for widgets in lower-priority HUD layers.
            document.root().setProperty("pointer_events", false);
            layers.put(layerId, new HudLayer(layerId, document, options, host, sessionId));
            runtime.execute(MorpheResourceLoader.readScript(documentId), documentId.toString());
        } catch (Exception exception) {
            if (document != null) document.close();
            layers.remove(layerId);
            LOGGER.error("Failed to load Morphe HUD {} from {}", layerId, documentId, exception);
        }
    }

    private static MorpheViewOptions hudOptions(MorpheViewOptions requested) {
        if (requested == null) return MorpheViewOptions.hud();
        MorpheViewOptions source = requested.copy();
        if (source.surface() == MorpheViewOptions.Surface.HUD) return source;
        return MorpheViewOptions.hud()
            .background(source.background())
            .inputMode(source.inputMode())
            .pauseWorld(source.pauseWorld())
            .closeOnEscape(source.closeOnEscape())
            .hideWithGui(source.hideWithGui())
            .screenLayer(source.screenLayer())
            .priority(source.priority());
    }

    private void closeFromScript(ResourceLocation layerId, ResourceLocation documentId, UUID sessionId) {
        pendingHide.add(layerId);
        if (sessionId != null) {
            MorpheNetwork.sendAction(sessionId, documentId, "__close", new CompoundTag());
        }
    }

    private void setInteractiveNow(ResourceLocation layerId, boolean interactive) {
        HudLayer layer = layers.get(layerId);
        if (layer == null) return;
        boolean next = interactive && layer.options.inputMode() != MorpheViewOptions.InputMode.PASSIVE;
        if (next) {
            for (HudLayer other : layers.values()) {
                if (other != layer && other.interactive) {
                    other.interactive = false;
                    other.host.setInteractiveFromManager(false);
                }
            }
        }
        layer.interactive = next;
        layer.host.setInteractiveFromManager(next);
        Minecraft minecraft = Minecraft.getInstance();
        if (next && (!(minecraft.screen instanceof MorpheHudInteractionScreen screen)
            || !screen.owner().equals(layerId))) {
            minecraft.setScreen(new MorpheHudInteractionScreen(layerId));
        } else if (!next && minecraft.screen instanceof MorpheHudInteractionScreen screen
            && screen.owner().equals(layerId)) {
            minecraft.setScreen(null);
        }
    }

    private void hideNow(ResourceLocation layerId) {
        HudLayer layer = layers.remove(layerId);
        if (layer == null) return;
        if (Minecraft.getInstance().screen instanceof MorpheHudInteractionScreen screen
            && screen.owner().equals(layerId)) {
            Minecraft.getInstance().setScreen(null);
        }
        layer.document.close();
        if (layers.isEmpty()) {
            lastFrameNanos = 0;
        }
    }

    private void clearNow() {
        if (Minecraft.getInstance().screen instanceof MorpheHudInteractionScreen) {
            Minecraft.getInstance().setScreen(null);
        }
        for (HudLayer layer : layers.values()) layer.document.close();
        layers.clear();
        pendingHide.clear();
        lastFrameNanos = 0;
    }

    private void flushPendingHide() {
        for (ResourceLocation layerId : Set.copyOf(pendingHide)) hideNow(layerId);
        pendingHide.clear();
    }

    private List<HudLayer> ordered(boolean reverse) {
        List<HudLayer> result = new ArrayList<>(layers.values());
        result.sort(Comparator.comparingInt((HudLayer layer) -> layer.options.priority())
            .thenComparing(layer -> layer.layerId.toString()));
        if (reverse) java.util.Collections.reverse(result);
        return result;
    }

    private void renderTooltip(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        Minecraft minecraft,
        MorpheViewOptions.ScreenLayer pass
    ) {
        for (HudLayer layer : ordered(true)) {
            if (!layer.interactive || !renderable(layer, minecraft, pass)) continue;
            UiElement hovered = layer.document.input().hovered();
            if (hovered == null) continue;
            Object tooltip = hovered.getProperty("tooltip");
            if (tooltip != null && !tooltip.toString().isBlank()) {
                graphics.renderTooltip(Minecraft.getInstance().font, Component.literal(tooltip.toString()), mouseX, mouseY);
                return;
            }
        }
    }

    private static boolean renderable(
        HudLayer layer,
        Minecraft minecraft,
        MorpheViewOptions.ScreenLayer pass
    ) {
        if (layer.options.hideWithGui() && minecraft.options.hideGui) {
            return false;
        }
        if (minecraft.screen == null) {
            return pass == MorpheViewOptions.ScreenLayer.BELOW;
        }
        if (minecraft.screen instanceof MorpheHudInteractionScreen && layer.interactive
            && layer.options.screenLayer() == MorpheViewOptions.ScreenLayer.HIDDEN) {
            return pass == MorpheViewOptions.ScreenLayer.BELOW;
        }
        return layer.options.screenLayer() == pass;
    }

    private double nextFrameDelta() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0) {
            lastFrameNanos = now;
            return 0;
        }
        double delta = (now - lastFrameNanos) / 1_000_000_000.0;
        lastFrameNanos = now;
        return Math.max(0, Math.min(0.25, delta));
    }

    private static final class HudLayer {
        private final ResourceLocation layerId;
        private final UiDocument document;
        private final MorpheViewOptions options;
        private final MorpheClientHost host;
        private final UUID sessionId;
        private boolean interactive;

        private HudLayer(
            ResourceLocation layerId,
            UiDocument document,
            MorpheViewOptions options,
            MorpheClientHost host,
            UUID sessionId
        ) {
            this.layerId = layerId;
            this.document = document;
            this.options = options;
            this.host = host;
            this.sessionId = sessionId;
        }
    }
}
