package org.lytharalab.gfbs.morphe.core;

import org.lytharalab.gfbs.morphe.api.UiSystemExtension;
import org.lytharalab.gfbs.morphe.layout.UiLayoutEngine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class UiDocument implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(UiDocument.class.getName());

    private final UiRoot root = new UiRoot();
    private final UiLayoutEngine layout = new UiLayoutEngine();
    private final UiInputRouter input = new UiInputRouter(root);
    private final Map<String, UiSystemExtension> systemExtensions = new LinkedHashMap<>();
    private UiRuntime runtime;
    private boolean debug;
    private boolean closing;
    private boolean closed;

    public UiDocument() {
        input.eventObserver(event ->
            invokeExtensions("input", extension -> extension.onInput(this, event)));
    }

    public UiRoot root() {
        return root;
    }

    public UiInputRouter input() {
        return input;
    }

    public void soundSink(UiSoundSink sink) {
        input.soundSink(sink);
    }

    public void addSystemExtension(String id, UiSystemExtension extension) {
        ensureOpen();
        String key = Objects.requireNonNull(id, "id").trim().toLowerCase();
        if (key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("Invalid UI system extension id: " + id);
        }
        if (systemExtensions.containsKey(key)) {
            throw new IllegalStateException("UI system extension is already attached: " + key);
        }
        UiSystemExtension safe = Objects.requireNonNull(extension, "extension");
        systemExtensions.put(key, safe);
        try {
            safe.onAttach(this);
        } catch (RuntimeException exception) {
            systemExtensions.remove(key);
            try {
                safe.onClose(this);
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            }
            throw new IllegalStateException("Failed to attach UI system extension " + key, exception);
        }
    }

    public Map<String, UiSystemExtension> systemExtensions() {
        return Collections.unmodifiableMap(systemExtensions);
    }

    public void runtime(UiRuntime runtime) {
        if (this.runtime != null) {
            this.runtime.close();
        }
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public UiRuntime runtime() {
        return runtime;
    }

    public void resize(int width, int height) {
        root.resize(Math.max(0, width), Math.max(0, height));
    }

    public void tick(double deltaSeconds) {
        ensureOpen();
        invokeExtensions("beforeTick", extension -> extension.beforeTick(this, deltaSeconds));
        if (runtime != null) {
            runtime.tick(deltaSeconds);
        }
        root.tick(deltaSeconds);
        invokeExtensions("afterTick", extension -> extension.afterTick(this, deltaSeconds));
        ensureLayout();
    }

    public void frame(double deltaSeconds) {
        ensureOpen();
        double safeDelta = Math.max(0, Math.min(0.25, deltaSeconds));
        invokeExtensions("beforeFrame", extension -> extension.beforeFrame(this, safeDelta));
        if (runtime != null) {
            runtime.frame(safeDelta);
        }
        root.frame(safeDelta);
        invokeExtensions("afterFrame", extension -> extension.afterFrame(this, safeDelta));
    }

    public void render(UiCanvas canvas, double mouseX, double mouseY, float partialTick) {
        ensureOpen();
        ensureLayout();
        UiFrame frame = new UiFrame(mouseX, mouseY, partialTick, debug);
        invokeExtensions("beforeRender", extension -> extension.beforeRender(this, canvas, frame));
        root.render(canvas, frame);
        invokeExtensions("afterRender", extension -> extension.afterRender(this, canvas, frame));
    }

    public boolean debug() {
        return debug;
    }

    public void debug(boolean value) {
        debug = value;
    }

    public String error() {
        return runtime == null ? null : runtime.error();
    }

    private void ensureLayout() {
        if (root.treeDirty()) {
            invokeExtensions("beforeLayout", extension -> extension.beforeLayout(this));
            layout.layout(root);
            invokeExtensions("afterLayout", extension -> extension.afterLayout(this));
        }
    }

    private void invokeExtensions(String hook, Consumer<UiSystemExtension> invocation) {
        for (Map.Entry<String, UiSystemExtension> entry
            : new LinkedHashMap<>(systemExtensions).entrySet()) {
            try {
                invocation.accept(entry.getValue());
            } catch (RuntimeException exception) {
                LOGGER.log(
                    System.Logger.Level.WARNING,
                    "UI system extension " + entry.getKey() + " failed during " + hook,
                    exception
                );
            }
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("UI document is closed");
        }
    }

    @Override
    public void close() {
        if (closed || closing) {
            return;
        }
        closing = true;
        try {
            input.reset();
            if (runtime != null) {
                try {
                    runtime.close();
                } catch (RuntimeException exception) {
                    LOGGER.log(
                        System.Logger.Level.WARNING,
                        "UI runtime failed while closing",
                        exception
                    );
                }
            }
            invokeExtensions("close", extension -> extension.onClose(this));
            systemExtensions.clear();
            for (UiElement child : List.copyOf(root.children())) {
                child.destroy();
            }
        } finally {
            closed = true;
            closing = false;
        }
    }
}
