package org.lytharalab.gfbs.morphe.core;

import java.util.Map;

/**
 * Platform bridge used by the script runtime without coupling the UI core to
 * Minecraft client classes.
 */
public interface UiHost {
    UiHost NONE = new UiHost() {
    };

    default Map<String, ?> environment() {
        return Map.of();
    }

    default void configure(Map<String, ?> options) {
    }

    default void setInteractive(boolean interactive) {
    }

    default boolean interactive() {
        return false;
    }

    default void playSound(String sound, float volume, float pitch) {
    }

    default void closeView() {
    }
}
