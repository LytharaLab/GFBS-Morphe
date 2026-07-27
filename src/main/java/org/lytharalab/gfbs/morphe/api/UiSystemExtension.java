package org.lytharalab.gfbs.morphe.api;

import org.lytharalab.gfbs.morphe.core.UiCanvas;
import org.lytharalab.gfbs.morphe.core.UiDocument;
import org.lytharalab.gfbs.morphe.core.UiEvent;
import org.lytharalab.gfbs.morphe.core.UiFrame;

/**
 * Per-document hook set for integrations that need to extend the core UI
 * pipeline without replacing Morphe internals.
 */
public interface UiSystemExtension {
    default void onAttach(UiDocument document) {
    }

    default void beforeTick(UiDocument document, double deltaSeconds) {
    }

    default void afterTick(UiDocument document, double deltaSeconds) {
    }

    default void beforeFrame(UiDocument document, double deltaSeconds) {
    }

    default void afterFrame(UiDocument document, double deltaSeconds) {
    }

    default void beforeLayout(UiDocument document) {
    }

    default void afterLayout(UiDocument document) {
    }

    default void beforeRender(UiDocument document, UiCanvas canvas, UiFrame frame) {
    }

    default void afterRender(UiDocument document, UiCanvas canvas, UiFrame frame) {
    }

    default void onInput(UiDocument document, UiEvent event) {
    }

    default void onClose(UiDocument document) {
    }
}
