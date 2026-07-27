package org.lytharalab.gfbs.morphe.widget;

import org.lytharalab.gfbs.morphe.core.UiElement;
import org.lytharalab.gfbs.morphe.core.UiEvent;

import java.util.Map;

public final class ScrollElement extends PanelElement {
    private double scrollY;
    private double maxScroll;
    private double speed = 18;

    public ScrollElement() {
        super("scroll");
        setProperty("layout", "column");
        setProperty("clip", true);
        setProperty("pointer_events", true);
    }

    @Override
    protected Object getWidgetProperty(String property) {
        return switch (property) {
            case "scroll", "scroll_y" -> scrollY;
            case "max_scroll" -> maxScroll;
            case "scroll_speed" -> speed;
            default -> super.getWidgetProperty(property);
        };
    }

    @Override
    protected boolean setWidgetProperty(String property, Object raw) {
        switch (property) {
            case "scroll", "scroll_y" -> {
                double value = number(raw);
                scrollY = Math.max(0, value);
                markDirty();
            }
            case "scroll_speed" -> speed = Math.max(1, number(raw));
            case "max_scroll" -> throw new IllegalArgumentException("Read-only property: max_scroll");
            default -> {
                return super.setWidgetProperty(property, raw);
            }
        }
        return true;
    }

    private static double number(Object raw) {
        double value = raw instanceof Number number ? number.doubleValue() : Double.parseDouble(raw.toString());
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Scroll values must be finite");
        }
        return value;
    }

    @Override
    public double childOffsetY() {
        return -scrollY;
    }

    @Override
    public void afterLayout() {
        double contentBottom = bounds().y() + style().padding().top();
        for (UiElement child : children()) {
            if (child.style().visible()) {
                contentBottom = Math.max(
                    contentBottom,
                    child.bounds().bottom() + child.style().margin().bottom() + scrollY
                );
            }
        }
        double viewportBottom = bounds().bottom() - style().padding().bottom();
        double nextMax = Math.max(0, contentBottom - viewportBottom);
        if (Double.compare(maxScroll, nextMax) != 0) {
            maxScroll = nextMax;
            if (scrollY > maxScroll) {
                scrollY = maxScroll;
                markDirty();
            }
        }
    }

    @Override
    protected void handleEvent(UiEvent event) {
        if (event.type().equals("scroll") && maxScroll > 0) {
            setScroll(scrollY - event.number("scroll", 0) * speed);
            emitLocal("change", Map.of("value", scrollY, "max", maxScroll));
            event.preventDefault();
        }
    }

    private void setScroll(double value) {
        double next = Math.max(0, Math.min(maxScroll, value));
        if (Double.compare(scrollY, next) != 0) {
            scrollY = next;
            markDirty();
        }
    }
}
