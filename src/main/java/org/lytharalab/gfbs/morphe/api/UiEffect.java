package org.lytharalab.gfbs.morphe.api;

import org.lytharalab.gfbs.morphe.core.UiCanvas;
import org.lytharalab.gfbs.morphe.core.UiElement;
import org.lytharalab.gfbs.morphe.core.UiEvent;
import org.lytharalab.gfbs.morphe.core.UiFrame;

/**
 * Per-element visual or behavioral effect. One instance belongs to one
 * element and may safely keep animation state.
 */
public interface UiEffect {
    default void onAttach(UiElement element) {
    }

    default void onDetach(UiElement element) {
    }

    default void onTick(UiElement element, double deltaSeconds) {
    }

    default void onFrame(UiElement element, double deltaSeconds) {
    }

    default void beforeRender(UiElement element, UiCanvas canvas, UiFrame frame) {
    }

    default void afterRender(UiElement element, UiCanvas canvas, UiFrame frame) {
    }

    default void onEvent(UiElement element, UiEvent event) {
    }
}
