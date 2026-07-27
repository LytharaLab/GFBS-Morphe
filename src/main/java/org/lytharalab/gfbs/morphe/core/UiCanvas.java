package org.lytharalab.gfbs.morphe.core;

import java.util.List;

/**
 * Rendering backend abstraction. The UI tree and widgets do not depend on
 * Minecraft rendering classes.
 */
public interface UiCanvas {
    /**
     * Returns a renderer-specific backend object when available. Portable
     * widgets should use UiCanvas methods; low-level integrations may request
     * objects such as Minecraft's GuiGraphics explicitly.
     */
    default <T> T backend(Class<T> type) {
        return null;
    }

    void pushTransform(UiRect bounds, double rotationDegrees);

    default void pushTransform(UiRect bounds, UiTransform transform) {
        pushTransform(bounds, transform.rotation());
    }

    void popTransform();

    void pushClip(UiRect bounds);

    void popClip();

    void fill(UiRect bounds, UiColor color, double radius);

    void stroke(UiRect bounds, UiColor color, double width, double radius);

    void text(
        String text,
        UiRect bounds,
        UiColor color,
        int fontSize,
        UiStyle.TextAlign horizontal,
        UiStyle.VerticalAlign vertical,
        boolean wrap,
        boolean shadow
    );

    void image(String resource, UiRect bounds, UiColor tint, UiStyle.ImageFit fit);

    default void image(
        String resource,
        UiRect bounds,
        UiColor tint,
        UiStyle.ImageFit fit,
        UiImageRegion region
    ) {
        image(resource, bounds, tint, fit);
    }

    default void item(String itemId, int count, UiRect bounds, boolean decorations) {
    }

    int textWidth(String text, int fontSize);

    int lineHeight(int fontSize);

    List<String> wrapText(String text, int maxWidth, int fontSize);
}
