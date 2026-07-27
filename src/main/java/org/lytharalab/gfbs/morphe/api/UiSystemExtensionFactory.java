package org.lytharalab.gfbs.morphe.api;

import org.lytharalab.gfbs.morphe.core.UiDocument;

@FunctionalInterface
public interface UiSystemExtensionFactory {
    UiSystemExtension create(UiDocument document);
}
