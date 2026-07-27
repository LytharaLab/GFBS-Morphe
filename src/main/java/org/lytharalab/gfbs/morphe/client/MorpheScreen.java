package org.lytharalab.gfbs.morphe.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lytharalab.gfbs.morphe.api.MorpheViewOptions;
import org.lytharalab.gfbs.morphe.api.Morphe;
import org.lytharalab.gfbs.morphe.core.UiDocument;
import org.lytharalab.gfbs.morphe.core.UiElement;
import org.lytharalab.gfbs.morphe.network.MorpheNetwork;
import org.lytharalab.gfbs.morphe.network.NbtDataCodec;
import org.lytharalab.gfbs.morphe.script.MorpheLuaRuntime;
import org.lytharalab.gfbs.morphe.script.UiActionSink;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public final class MorpheScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ResourceLocation documentId;
    private final CompoundTag initialData;
    private final UUID sessionId;
    private final MorpheViewOptions options;
    private final MorpheClientHost host;
    private UiDocument document;
    private long lastFrameNanos;

    public MorpheScreen(ResourceLocation documentId, CompoundTag initialData, UUID sessionId) {
        this(documentId, initialData, sessionId, MorpheViewOptions.screen());
    }

    public MorpheScreen(
        ResourceLocation documentId,
        CompoundTag initialData,
        UUID sessionId,
        MorpheViewOptions options
    ) {
        super(Component.translatable("gfbs_morphe.screen.title"));
        this.documentId = documentId;
        this.initialData = initialData == null ? new CompoundTag() : initialData.copy();
        this.sessionId = sessionId;
        this.options = options == null ? MorpheViewOptions.screen() : options.copy();
        this.host = new MorpheClientHost(
            this.options,
            () -> Minecraft.getInstance().setScreen(null),
            ignored -> { },
            () -> { }
        );
    }

    public ResourceLocation documentId() {
        return documentId;
    }

    public UiDocument document() {
        return document;
    }

    public MorpheViewOptions options() {
        return options.copy();
    }

    public boolean isSession(UUID value) {
        return sessionId != null && sessionId.equals(value);
    }

    public void reloadDocument() {
        MinecraftUiCanvas.clearImageSizeCache();
        lastFrameNanos = 0;
        loadDocument();
        if (document != null) {
            document.resize(width, height);
        }
    }

    public void toggleInspector() {
        if (document != null) {
            document.debug(!document.debug());
        }
    }

    @Override
    protected void init() {
        if (document == null) {
            loadDocument();
        }
        if (document != null) {
            document.resize(width, height);
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        if (document != null) {
            document.resize(width, height);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (document != null) {
            host.gameTick();
            document.tick(1.0 / 20.0);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (options.background() != MorpheViewOptions.Background.NONE) {
            renderBackground(graphics);
        }
        if (document != null) {
            double frameDelta = nextFrameDelta();
            host.delta(frameDelta);
            document.frame(frameDelta);
            document.input().pointerMoved(mouseX, mouseY);
            document.render(new MinecraftUiCanvas(graphics), mouseX, mouseY, partialTick);
            renderRuntimeError(graphics);
            renderTooltip(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
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

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (document != null) {
            document.input().pointerMoved(mouseX, mouseY);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return document != null && document.input().pointerDown(mouseX, mouseY, button)
            || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return document != null && document.input().pointerUp(mouseX, mouseY, button)
            || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return document != null && document.input().pointerDragged(mouseX, mouseY, button, dragX, dragY)
            || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return document != null && document.input().scrolled(mouseX, mouseY, delta)
            || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return document != null && document.input().keyDown(keyCode, scanCode, modifiers)
            || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return document != null && document.input().keyUp(keyCode, scanCode, modifiers)
            || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return document != null && document.input().charTyped(codePoint, modifiers)
            || super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        if (sessionId != null && Minecraft.getInstance().getConnection() != null) {
            MorpheNetwork.sendAction(sessionId, documentId, "__close", new CompoundTag());
        }
        if (document != null) {
            document.close();
            document = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return options.pauseWorld();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return options.closeOnEscape();
    }

    private void loadDocument() {
        if (document != null) {
            document.close();
        }
        UiDocument next = Morphe.get().createDocument();
        next.soundSink(host::playSound);
        Map<String, Object> data;
        try {
            data = NbtDataCodec.toMap(initialData);
        } catch (RuntimeException exception) {
            LOGGER.error("Invalid Morphe initial data for {}", documentId, exception);
            data = Map.of();
        }
        UiActionSink actionSink = sessionId == null
            ? UiActionSink.NOOP
            : (action, payload) -> MorpheNetwork.sendAction(
                sessionId,
                documentId,
                action,
                NbtDataCodec.fromMap(payload)
            );
        MorpheLuaRuntime runtime = new MorpheLuaRuntime(next, data, actionSink, host);
        next.runtime(runtime);
        document = next;

        try {
            runtime.execute(MorpheResourceLoader.readScript(documentId), documentId.toString());
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to load Morphe document {}", documentId, exception);
            runtime.execute(
                "ui.mount(ui.text({text = " + luaQuote("Failed to load " + documentId + ": " + exception.getMessage()) +
                    ", x = 12, y = 12, width = '90%', height = 40, color = '#FFFF7777', wrap = true}))",
                documentId + "#load_error"
            );
        }
    }

    private void renderRuntimeError(GuiGraphics graphics) {
        if (document == null || document.error() == null) {
            return;
        }
        String message = document.error();
        if (message.length() > 240) {
            message = message.substring(0, 240) + "…";
        }
        graphics.fill(6, height - 28, width - 6, height - 6, 0xDD4A1111);
        graphics.drawString(font, message, 12, height - 21, 0xFFFFB4B4, false);
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        UiElement hovered = document.input().hovered();
        if (hovered == null) {
            return;
        }
        Object tooltip = hovered.getProperty("tooltip");
        if (tooltip != null && !tooltip.toString().isBlank()) {
            graphics.renderTooltip(font, Component.literal(tooltip.toString()), mouseX, mouseY);
        }
    }

    private static String luaQuote(String value) {
        String safe = value == null ? "unknown error" : value;
        return '"' + safe
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n") + '"';
    }
}
