package org.lytharalab.gfbs.morphe.widget;

import org.lytharalab.gfbs.morphe.core.UiColor;
import org.lytharalab.gfbs.morphe.core.UiEvent;
import org.lytharalab.gfbs.morphe.core.UiFrame;
import org.lytharalab.gfbs.morphe.core.UiSize;

public final class ButtonElement extends TextElement {
    private boolean hovered;
    private boolean pressed;

    public ButtonElement() {
        super("button");
        setProperty("height", 22);
        setProperty("padding", "6 10");
        setProperty("background", "#FF2D3748");
        setProperty("hover_background", "#FF3B4A61");
        setProperty("pressed_background", "#FF1D2736");
        setProperty("radius", 3);
        setProperty("text_align", "center");
        setProperty("vertical_align", "center");
        setProperty("pointer_events", true);
        setProperty("click_sound", "minecraft:ui.button.click");
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public UiSize measure(double availableWidth, double availableHeight) {
        UiSize text = super.measure(availableWidth, availableHeight);
        return new UiSize(text.width() + 12, Math.max(22, text.height() + 8));
    }

    @Override
    protected UiColor resolvedBackground(UiFrame frame) {
        if (!style().enabled()) {
            return style().background().multiplyAlpha(0.45);
        }
        if (pressed && style().pressedBackground().alpha() > 0) {
            return style().pressedBackground();
        }
        if (hovered && style().hoverBackground().alpha() > 0) {
            return style().hoverBackground();
        }
        return style().background();
    }

    @Override
    protected void handleEvent(UiEvent event) {
        switch (event.type()) {
            case "pointer_enter" -> {
                hovered = true;
                markDirty();
            }
            case "pointer_leave" -> {
                hovered = false;
                pressed = false;
                markDirty();
            }
            case "pointer_down" -> {
                if (style().enabled()) {
                    pressed = true;
                    event.preventDefault();
                    markDirty();
                }
            }
            case "pointer_up" -> {
                pressed = false;
                markDirty();
            }
            case "key_down" -> {
                int key = (int) event.number("key_code", -1);
                if (key == 257 || key == 32) {
                    emitLocal("click", event.data());
                    event.preventDefault();
                }
            }
            default -> {
            }
        }
    }
}
