package org.lytharalab.gfbs.morphe.widget;

import org.lytharalab.gfbs.morphe.core.UiCanvas;
import org.lytharalab.gfbs.morphe.core.UiColor;
import org.lytharalab.gfbs.morphe.core.UiEvent;
import org.lytharalab.gfbs.morphe.core.UiFrame;
import org.lytharalab.gfbs.morphe.core.UiRect;
import org.lytharalab.gfbs.morphe.core.UiSize;

import java.util.Map;

public final class CheckboxElement extends PanelElement {
    private boolean checked;
    private String label = "";
    private UiColor accent = UiColor.rgb(38, 198, 218);

    public CheckboxElement() {
        super("checkbox");
        setProperty("height", 18);
        setProperty("pointer_events", true);
        setProperty("vertical_align", "center");
        setProperty("click_sound", "minecraft:ui.button.click");
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    protected Object getWidgetProperty(String property) {
        return switch (property) {
            case "checked", "value" -> checked;
            case "label", "text" -> label;
            case "accent", "accent_color" -> accent;
            default -> super.getWidgetProperty(property);
        };
    }

    @Override
    protected boolean setWidgetProperty(String property, Object value) {
        switch (property) {
            case "checked", "value" -> checked = value instanceof Boolean bool
                ? bool
                : Boolean.parseBoolean(value.toString());
            case "label", "text" -> label = value == null ? "" : value.toString();
            case "accent", "accent_color" -> accent = value instanceof UiColor color
                ? color
                : UiColor.parse(value.toString());
            default -> {
                return super.setWidgetProperty(property, value);
            }
        }
        return true;
    }

    @Override
    public UiSize measure(double availableWidth, double availableHeight) {
        return new UiSize(Math.min(availableWidth, 20 + label.length() * style().fontSize() * 0.58), 18);
    }

    @Override
    protected void handleEvent(UiEvent event) {
        if (event.type().equals("click") && style().enabled()) {
            checked = !checked;
            emitLocal("change", Map.of("value", checked));
            event.preventDefault();
            markDirty();
        } else if (event.type().equals("key_down")) {
            int key = (int) event.number("key_code", -1);
            if (key == 257 || key == 32) {
                checked = !checked;
                emitLocal("change", Map.of("value", checked));
                event.preventDefault();
                markDirty();
            }
        }
    }

    @Override
    protected void renderContent(UiCanvas canvas, UiFrame frame) {
        double boxSize = Math.min(14, bounds().height());
        UiRect box = new UiRect(bounds().x(), bounds().centerY() - boxSize / 2, boxSize, boxSize);
        canvas.fill(box, checked ? accent : UiColor.rgb(35, 43, 54), 2);
        canvas.stroke(box, checked ? accent : UiColor.rgb(110, 120, 135), 1, 2);
        if (checked) {
            canvas.text(
                "✓",
                box,
                UiColor.WHITE,
                Math.max(8, style().fontSize()),
                org.lytharalab.gfbs.morphe.core.UiStyle.TextAlign.CENTER,
                org.lytharalab.gfbs.morphe.core.UiStyle.VerticalAlign.CENTER,
                false,
                false
            );
        }
        if (!label.isEmpty()) {
            UiRect textBounds = new UiRect(box.right() + 6, bounds().y(), Math.max(0, bounds().right() - box.right() - 6), bounds().height());
            canvas.text(
                label,
                textBounds,
                style().foreground().multiplyAlpha(style().opacity()),
                style().fontSize(),
                style().textAlign(),
                style().verticalAlign(),
                style().wrapText(),
                style().textShadow()
            );
        }
    }
}
