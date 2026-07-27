package org.lytharalab.gfbs.morphe.widget;

import org.lytharalab.gfbs.morphe.core.UiCanvas;
import org.lytharalab.gfbs.morphe.core.UiFrame;
import org.lytharalab.gfbs.morphe.core.UiSize;

public final class ItemElement extends PanelElement {
    private String item = "minecraft:air";
    private int count = 1;
    private boolean decorations = true;

    public ItemElement() {
        super("item");
        setProperty("width", 16);
        setProperty("height", 16);
        setProperty("pointer_events", false);
    }

    @Override
    protected Object getWidgetProperty(String property) {
        return switch (property) {
            case "item", "item_id", "source" -> item;
            case "count" -> count;
            case "decorations", "show_count" -> decorations;
            default -> super.getWidgetProperty(property);
        };
    }

    @Override
    protected boolean setWidgetProperty(String property, Object value) {
        switch (property) {
            case "item", "item_id", "source" -> item = value == null ? "minecraft:air" : value.toString();
            case "count" -> count = Math.max(1, Math.min(99, value instanceof Number number
                ? number.intValue()
                : Integer.parseInt(value.toString())));
            case "decorations", "show_count" -> decorations = value instanceof Boolean bool
                ? bool
                : Boolean.parseBoolean(value.toString());
            default -> {
                return super.setWidgetProperty(property, value);
            }
        }
        return true;
    }

    @Override
    public UiSize measure(double availableWidth, double availableHeight) {
        return new UiSize(Math.min(16, availableWidth), Math.min(16, availableHeight));
    }

    @Override
    protected void renderContent(UiCanvas canvas, UiFrame frame) {
        canvas.item(item, count, bounds(), decorations);
    }
}
