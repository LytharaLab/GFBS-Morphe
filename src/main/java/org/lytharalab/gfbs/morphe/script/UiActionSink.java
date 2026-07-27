package org.lytharalab.gfbs.morphe.script;

import java.util.Map;

@FunctionalInterface
public interface UiActionSink {
    UiActionSink NOOP = (action, payload) -> {
    };

    void send(String action, Map<String, Object> payload);
}
