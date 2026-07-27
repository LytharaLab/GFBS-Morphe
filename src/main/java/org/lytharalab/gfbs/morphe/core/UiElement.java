package org.lytharalab.gfbs.morphe.core;

import org.lytharalab.gfbs.morphe.api.UiEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Renderer-independent node in a Morphe UI tree.
 */
public class UiElement {
    public static final int MAX_TREE_DEPTH = 64;
    private static final System.Logger LOGGER = System.getLogger(UiElement.class.getName());

    private final String type;
    private String id;
    private String tooltip = "";
    private UiElement parent;
    private final List<UiElement> children = new ArrayList<>();
    private final Map<String, CopyOnWriteArrayList<Listener>> listeners = new LinkedHashMap<>();
    private final UiStyle style = new UiStyle();
    private final UiAnimator animator = new UiAnimator(this);
    private final Map<String, UiEffect> effects = new LinkedHashMap<>();
    private UiRect bounds = UiRect.ZERO;
    private boolean treeDirty = true;
    private boolean destroyed;

    public UiElement(String type) {
        this.type = Objects.requireNonNull(type, "type");
        this.id = UiStyle.normalize(type);
    }

    public final String type() {
        return type;
    }

    public final String id() {
        return id;
    }

    public final UiStyle style() {
        return style;
    }

    public final UiRect bounds() {
        return bounds;
    }

    public final UiElement parent() {
        return parent;
    }

    public final List<UiElement> children() {
        return Collections.unmodifiableList(children);
    }

    public final UiAnimator animator() {
        return animator;
    }

    public final boolean destroyed() {
        return destroyed;
    }

    public final void add(UiElement child) {
        Objects.requireNonNull(child, "child");
        ensureAlive();
        child.ensureAlive();
        if (child == this || isDescendantOf(child)) {
            throw new IllegalArgumentException("Cannot create a cyclic UI hierarchy");
        }
        if (depth() + subtreeDepth(child) > MAX_TREE_DEPTH) {
            throw new IllegalArgumentException("UI tree exceeds maximum depth " + MAX_TREE_DEPTH);
        }
        if (child.parent == this) {
            return;
        }
        if (child.parent != null) {
            child.parent.children.remove(child);
            child.parent.markDirty();
        }
        child.parent = this;
        children.add(child);
        child.onMounted();
        child.emitLocal("mount", Map.of());
        markDirty();
    }

    public final void remove(UiElement child) {
        if (children.remove(child)) {
            child.emitLocal("unmount", Map.of());
            child.parent = null;
            child.onUnmounted();
            markDirty();
        }
    }

    public final void clear() {
        for (UiElement child : List.copyOf(children)) {
            remove(child);
        }
    }

    public final void destroy() {
        if (destroyed) {
            return;
        }
        clear();
        if (parent != null) {
            parent.remove(this);
        }
        listeners.clear();
        animator.cancelAll();
        clearEffects();
        destroyed = true;
        onDestroyed();
    }

    public final UiElement find(String targetId) {
        if (id.equals(targetId)) {
            return this;
        }
        for (UiElement child : children) {
            UiElement found = child.find(targetId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public final Object getProperty(String property) {
        String key = UiStyle.normalize(property);
        return switch (key) {
            case "id", "name" -> id;
            case "type" -> type;
            case "tooltip" -> tooltip;
            case "bounds" -> bounds;
            default -> {
                Object widget = getWidgetProperty(key);
                Object styled = style.get(key);
                yield widget != null ? widget : styled;
            }
        };
    }

    public final void setProperty(String property, Object value) {
        ensureAlive();
        String key = UiStyle.normalize(property);
        switch (key) {
            case "id", "name" -> id = requireId(value);
            case "tooltip" -> tooltip = value == null ? "" : value.toString();
            case "type", "bounds" -> throw new IllegalArgumentException("Read-only property: " + property);
            default -> {
                // Widget-specific meanings win over generic style aliases.
                // For example, video.columns controls atlas columns while
                // video.grid_columns still controls its child layout.
                if (!setWidgetProperty(key, value) && !style.set(key, value)) {
                    throw new IllegalArgumentException("Unknown property for " + type + ": " + property);
                }
            }
        }
        markDirty();
    }

    protected Object getWidgetProperty(String property) {
        return null;
    }

    protected boolean setWidgetProperty(String property, Object value) {
        return false;
    }

    public final UiSubscription on(String eventType, Consumer<UiEvent> listener) {
        return on(eventType, false, listener);
    }

    public final void addEffect(String id, UiEffect effect) {
        ensureAlive();
        String key = effectKey(id);
        UiEffect safe = Objects.requireNonNull(effect, "effect");
        UiEffect previous = effects.put(key, safe);
        try {
            safe.onAttach(this);
        } catch (RuntimeException exception) {
            if (previous != null) {
                effects.put(key, previous);
            } else {
                effects.remove(key);
            }
            try {
                safe.onDetach(this);
            } catch (RuntimeException detachException) {
                exception.addSuppressed(detachException);
            }
            throw new IllegalStateException("Failed to attach UI effect " + key, exception);
        }
        if (previous != null) {
            invokeEffect(key, previous, item -> item.onDetach(this));
        }
    }

    public final boolean removeEffect(String id) {
        String key = effectKey(id);
        UiEffect removed = effects.remove(key);
        if (removed != null) {
            invokeEffect(key, removed, item -> item.onDetach(this));
            return true;
        }
        return false;
    }

    public final void clearEffects() {
        for (Map.Entry<String, UiEffect> entry : new LinkedHashMap<>(effects).entrySet()) {
            invokeEffect(entry.getKey(), entry.getValue(), effect -> effect.onDetach(this));
        }
        effects.clear();
    }

    public final Map<String, UiEffect> effects() {
        return Collections.unmodifiableMap(effects);
    }

    public final UiSubscription on(String eventType, boolean capture, Consumer<UiEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        String key = UiStyle.normalize(eventType);
        Listener entry = new Listener(capture, listener);
        listeners.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(entry);
        return () -> {
            List<Listener> entries = listeners.get(key);
            if (entries != null) {
                entries.remove(entry);
            }
        };
    }

    final void dispatchLocal(UiEvent event, boolean capture) {
        if (event.phase() == UiEvent.Phase.TARGET && !capture) {
            handleEvent(event);
            forEachEffect((id, effect) -> effect.onEvent(this, event));
        }
        List<Listener> entries = listeners.get(event.type());
        if (entries == null) {
            return;
        }
        for (Listener listener : entries) {
            if (listener.capture == capture) {
                listener.consumer.accept(event);
                if (event.propagationStopped()) {
                    return;
                }
            }
        }
    }

    protected void handleEvent(UiEvent event) {
    }

    public boolean focusable() {
        return false;
    }

    protected final void emitLocal(String eventType, Map<String, ?> data) {
        UiEvent event = new UiEvent(eventType, this, data);
        event.prepare(this, UiEvent.Phase.TARGET);
        dispatchLocal(event, true);
        if (!event.propagationStopped()) {
            dispatchLocal(event, false);
        }
    }

    public UiSize measure(double availableWidth, double availableHeight) {
        return UiSize.ZERO;
    }

    public double childOffsetX() {
        return 0;
    }

    public double childOffsetY() {
        return 0;
    }

    public void afterLayout() {
    }

    public final void tick(double deltaSeconds) {
        forEachEffect((id, effect) -> effect.onTick(this, deltaSeconds));
        onTick(deltaSeconds);
        for (UiElement child : List.copyOf(children)) {
            child.tick(deltaSeconds);
        }
    }

    protected void onTick(double deltaSeconds) {
    }

    public final void frame(double deltaSeconds) {
        animator.tick(deltaSeconds);
        forEachEffect((id, effect) -> effect.onFrame(this, deltaSeconds));
        onFrame(deltaSeconds);
        for (UiElement child : List.copyOf(children)) {
            child.frame(deltaSeconds);
        }
    }

    protected void onFrame(double deltaSeconds) {
    }

    public final void render(UiCanvas canvas, UiFrame frame) {
        if (!style.visible() || destroyed) {
            return;
        }

        canvas.pushTransform(bounds, style.transform());
        forEachEffect((id, effect) -> effect.beforeRender(this, canvas, frame));
        UiColor background = resolvedBackground(frame);
        if (background.alpha() > 0 && style.opacity() > 0) {
            canvas.fill(bounds, background.multiplyAlpha(style.opacity()), style.radius());
        }
        if (style.borderWidth() > 0 && style.border().alpha() > 0) {
            canvas.stroke(
                bounds,
                style.border().multiplyAlpha(style.opacity()),
                style.borderWidth(),
                style.radius()
            );
        }
        renderContent(canvas, frame);

        if (style.clip()) {
            canvas.pushClip(bounds);
        }
        List<UiElement> ordered = new ArrayList<>(children);
        ordered.sort(Comparator.comparingInt(child -> child.style.zIndex()));
        for (UiElement child : ordered) {
            child.render(canvas, frame);
        }
        if (style.clip()) {
            canvas.popClip();
        }

        if (frame.debug()) {
            canvas.stroke(bounds, UiColor.rgba(0, 255, 180, 190), 1, 0);
        }
        List<Map.Entry<String, UiEffect>> reversedEffects =
            new ArrayList<>(effects.entrySet());
        Collections.reverse(reversedEffects);
        for (Map.Entry<String, UiEffect> entry : reversedEffects) {
            invokeEffect(
                entry.getKey(),
                entry.getValue(),
                effect -> effect.afterRender(this, canvas, frame)
            );
        }
        canvas.popTransform();
    }

    protected UiColor resolvedBackground(UiFrame frame) {
        return style.background();
    }

    protected void renderContent(UiCanvas canvas, UiFrame frame) {
    }

    protected void onMounted() {
    }

    protected void onUnmounted() {
    }

    protected void onDestroyed() {
    }

    public final void markDirty() {
        treeDirty = true;
        if (parent != null) {
            parent.markDirty();
        }
    }

    public final boolean treeDirty() {
        return treeDirty;
    }

    public final void clearDirtyRecursively() {
        treeDirty = false;
        for (UiElement child : children) {
            child.clearDirtyRecursively();
        }
    }

    public final void setBounds(UiRect bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    private void forEachEffect(EffectInvocation invocation) {
        for (Map.Entry<String, UiEffect> entry : new LinkedHashMap<>(effects).entrySet()) {
            invokeEffect(entry.getKey(), entry.getValue(), effect ->
                invocation.invoke(entry.getKey(), effect));
        }
    }

    private void invokeEffect(String id, UiEffect effect, Consumer<UiEffect> invocation) {
        try {
            invocation.accept(effect);
        } catch (RuntimeException exception) {
            LOGGER.log(
                System.Logger.Level.WARNING,
                "UI effect " + id + " failed on " + type + "#" + this.id,
                exception
            );
        }
    }

    private static String effectKey(String value) {
        String key = Objects.requireNonNull(value, "id").trim().toLowerCase();
        if (key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("Invalid UI effect id: " + value);
        }
        return key;
    }

    private boolean isDescendantOf(UiElement potentialAncestor) {
        for (UiElement current = parent; current != null; current = current.parent) {
            if (current == potentialAncestor) {
                return true;
            }
        }
        return false;
    }

    private int depth() {
        int depth = 0;
        for (UiElement current = parent; current != null; current = current.parent) {
            depth++;
        }
        return depth;
    }

    private static int subtreeDepth(UiElement element) {
        int depth = 1;
        for (UiElement child : element.children) {
            depth = Math.max(depth, 1 + subtreeDepth(child));
        }
        return depth;
    }

    @FunctionalInterface
    private interface EffectInvocation {
        void invoke(String id, UiEffect effect);
    }

    private static String requireId(Object value) {
        String id = Objects.requireNonNull(value, "id").toString().trim();
        if (id.isEmpty() || id.length() > 128) {
            throw new IllegalArgumentException("Element id must contain 1-128 characters");
        }
        return id;
    }

    private void ensureAlive() {
        if (destroyed) {
            throw new IllegalStateException("UI element has been destroyed: " + id);
        }
    }

    private record Listener(boolean capture, Consumer<UiEvent> consumer) {
    }
}
