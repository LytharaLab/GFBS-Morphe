package org.lytharalab.gfbs.morphe.api;

import org.lytharalab.gfbs.morphe.core.UiElement;

@FunctionalInterface
public interface WidgetFactory {
    UiElement create();
}
