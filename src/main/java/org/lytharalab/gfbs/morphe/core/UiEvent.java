package org.lytharalab.gfbs.morphe.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UiEvent {
    public enum Phase {
        CAPTURE,
        TARGET,
        BUBBLE
    }

    private final String type;
    private final UiElement target;
    private final Map<String, Object> data;
    private UiElement currentTarget;
    private Phase phase = Phase.TARGET;
    private boolean propagationStopped;
    private boolean defaultPrevented;

    public UiEvent(String type, UiElement target, Map<String, ?> data) {
        this.type = UiStyle.normalize(type);
        this.target = target;
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public String type() {
        return type;
    }

    public UiElement target() {
        return target;
    }

    public UiElement currentTarget() {
        return currentTarget;
    }

    public Phase phase() {
        return phase;
    }

    public Map<String, Object> data() {
        return data;
    }

    public Object get(String key) {
        return data.get(key);
    }

    public double number(String key, double fallback) {
        Object value = data.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    public boolean bool(String key, boolean fallback) {
        Object value = data.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    public void stopPropagation() {
        propagationStopped = true;
    }

    public boolean propagationStopped() {
        return propagationStopped;
    }

    public void preventDefault() {
        defaultPrevented = true;
    }

    public boolean defaultPrevented() {
        return defaultPrevented;
    }

    void prepare(UiElement currentTarget, Phase phase) {
        this.currentTarget = currentTarget;
        this.phase = phase;
    }
}
