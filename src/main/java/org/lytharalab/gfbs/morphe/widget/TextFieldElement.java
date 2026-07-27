package org.lytharalab.gfbs.morphe.widget;

import org.lytharalab.gfbs.morphe.core.UiCanvas;
import org.lytharalab.gfbs.morphe.core.UiColor;
import org.lytharalab.gfbs.morphe.core.UiEvent;
import org.lytharalab.gfbs.morphe.core.UiFrame;
import org.lytharalab.gfbs.morphe.core.UiRect;
import org.lytharalab.gfbs.morphe.core.UiSize;

import java.util.Map;

public final class TextFieldElement extends PanelElement {
    private String value = "";
    private String placeholder = "";
    private int maxLength = 1024;
    private int cursor;
    private boolean focused;
    private double blinkTimer;

    public TextFieldElement() {
        super("input");
        setProperty("height", 22);
        setProperty("padding", "5 7");
        setProperty("background", "#FF171D26");
        setProperty("border", "#FF384353");
        setProperty("border_width", 1);
        setProperty("radius", 3);
        setProperty("vertical_align", "center");
        setProperty("pointer_events", true);
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    protected Object getWidgetProperty(String property) {
        return switch (property) {
            case "value", "text" -> value;
            case "placeholder" -> placeholder;
            case "max_length" -> maxLength;
            case "cursor" -> cursor;
            default -> super.getWidgetProperty(property);
        };
    }

    @Override
    protected boolean setWidgetProperty(String property, Object raw) {
        switch (property) {
            case "value", "text" -> {
                String next = raw == null ? "" : raw.toString();
                value = next.substring(0, Math.min(maxLength, next.length()));
                cursor = Math.min(cursor, value.length());
            }
            case "placeholder" -> placeholder = raw == null ? "" : raw.toString();
            case "max_length" -> {
                maxLength = Math.max(0, Math.min(32767, raw instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(raw.toString())));
                if (value.length() > maxLength) {
                    value = value.substring(0, maxLength);
                }
                cursor = Math.min(cursor, value.length());
            }
            case "cursor" -> throw new IllegalArgumentException("Read-only property: cursor");
            default -> {
                return super.setWidgetProperty(property, raw);
            }
        }
        return true;
    }

    @Override
    public UiSize measure(double availableWidth, double availableHeight) {
        return new UiSize(Math.min(availableWidth, 120), 22);
    }

    @Override
    protected void onFrame(double deltaSeconds) {
        blinkTimer = (blinkTimer + deltaSeconds) % 1.0;
    }

    @Override
    protected void handleEvent(UiEvent event) {
        switch (event.type()) {
            case "focus" -> {
                focused = true;
                blinkTimer = 0;
                markDirty();
            }
            case "blur" -> {
                focused = false;
                markDirty();
            }
            case "pointer_down" -> {
                double local = Math.max(0, event.number("x", bounds().x()) - bounds().x() - style().padding().left());
                double averageWidth = Math.max(1, style().fontSize() * 0.58);
                cursor = Math.max(0, Math.min(value.length(), (int) Math.round(local / averageWidth)));
                event.preventDefault();
                markDirty();
            }
            case "char_typed" -> {
                String character = String.valueOf(event.get("character"));
                if (!character.isEmpty() && !Character.isISOControl(character.charAt(0)) && value.length() < maxLength) {
                    replaceValue(value.substring(0, cursor) + character.charAt(0) + value.substring(cursor));
                    cursor++;
                    event.preventDefault();
                }
            }
            case "key_down" -> handleKey(event);
            default -> {
            }
        }
    }

    @Override
    protected void renderContent(UiCanvas canvas, UiFrame frame) {
        UiRect content = bounds().inset(style().padding());
        String shown = value.isEmpty() ? placeholder : value;
        UiColor color = value.isEmpty()
            ? style().foreground().multiplyAlpha(style().opacity() * 0.45)
            : style().foreground().multiplyAlpha(style().opacity());
        canvas.text(
            shown,
            content,
            color,
            style().fontSize(),
            org.lytharalab.gfbs.morphe.core.UiStyle.TextAlign.LEFT,
            style().verticalAlign(),
            false,
            style().textShadow()
        );
        if (focused && blinkTimer < 0.55) {
            String prefix = value.substring(0, cursor);
            double cursorX = content.x() + canvas.textWidth(prefix, style().fontSize());
            UiRect caret = new UiRect(cursorX, content.y() + 1, 1, Math.max(1, content.height() - 2));
            canvas.fill(caret, style().foreground().multiplyAlpha(style().opacity()), 0);
        }
    }

    private void handleKey(UiEvent event) {
        int key = (int) event.number("key_code", -1);
        switch (key) {
            case 259 -> {
                if (cursor > 0) {
                    replaceValue(value.substring(0, cursor - 1) + value.substring(cursor));
                    cursor--;
                }
                event.preventDefault();
            }
            case 261 -> {
                if (cursor < value.length()) {
                    replaceValue(value.substring(0, cursor) + value.substring(cursor + 1));
                }
                event.preventDefault();
            }
            case 263 -> {
                cursor = Math.max(0, cursor - 1);
                event.preventDefault();
            }
            case 262 -> {
                cursor = Math.min(value.length(), cursor + 1);
                event.preventDefault();
            }
            case 268 -> {
                cursor = 0;
                event.preventDefault();
            }
            case 269 -> {
                cursor = value.length();
                event.preventDefault();
            }
            case 257, 335 -> {
                emitLocal("submit", Map.of("value", value));
                event.preventDefault();
            }
            default -> {
            }
        }
        blinkTimer = 0;
        markDirty();
    }

    private void replaceValue(String next) {
        value = next;
        emitLocal("change", Map.of("value", value));
        blinkTimer = 0;
        markDirty();
    }
}
