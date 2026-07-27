package org.lytharalab.gfbs.morphe.widget;

import org.lytharalab.gfbs.morphe.core.UiCanvas;
import org.lytharalab.gfbs.morphe.core.UiColor;
import org.lytharalab.gfbs.morphe.core.UiEvent;
import org.lytharalab.gfbs.morphe.core.UiFrame;
import org.lytharalab.gfbs.morphe.core.UiRect;
import org.lytharalab.gfbs.morphe.core.UiSize;

import java.util.Map;

public final class SliderElement extends PanelElement {
    private double minimum;
    private double maximum = 1;
    private double value;
    private double step;
    private UiColor trackColor = UiColor.rgb(48, 57, 70);
    private UiColor fillColor = UiColor.rgb(38, 198, 218);
    private UiColor thumbColor = UiColor.WHITE;

    public SliderElement() {
        super("slider");
        setProperty("height", 18);
        setProperty("pointer_events", true);
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    protected Object getWidgetProperty(String property) {
        return switch (property) {
            case "min", "minimum" -> minimum;
            case "max", "maximum" -> maximum;
            case "value" -> value;
            case "step" -> step;
            case "track_color" -> trackColor;
            case "fill_color", "accent" -> fillColor;
            case "thumb_color" -> thumbColor;
            default -> super.getWidgetProperty(property);
        };
    }

    @Override
    protected boolean setWidgetProperty(String property, Object raw) {
        switch (property) {
            case "min", "minimum" -> {
                minimum = number(raw);
                if (maximum < minimum) {
                    maximum = minimum;
                }
                value = clamp(value);
            }
            case "max", "maximum" -> {
                maximum = Math.max(minimum, number(raw));
                value = clamp(value);
            }
            case "value" -> value = clampAndSnap(number(raw));
            case "step" -> step = Math.max(0, number(raw));
            case "track_color" -> trackColor = color(raw);
            case "fill_color", "accent" -> fillColor = color(raw);
            case "thumb_color" -> thumbColor = color(raw);
            default -> {
                return super.setWidgetProperty(property, raw);
            }
        }
        return true;
    }

    @Override
    public UiSize measure(double availableWidth, double availableHeight) {
        return new UiSize(Math.min(availableWidth, 120), 18);
    }

    @Override
    protected void handleEvent(UiEvent event) {
        switch (event.type()) {
            case "pointer_down", "pointer_drag" -> {
                if (style().enabled()) {
                    setFromPointer(event.number("x", bounds().x()));
                    event.preventDefault();
                }
            }
            case "key_down" -> {
                int key = (int) event.number("key_code", -1);
                double amount = step > 0 ? step : Math.max(0.01, (maximum - minimum) / 100.0);
                if (key == 263 || key == 264) {
                    updateValue(value - amount);
                    event.preventDefault();
                } else if (key == 262 || key == 265) {
                    updateValue(value + amount);
                    event.preventDefault();
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void renderContent(UiCanvas canvas, UiFrame frame) {
        double thumb = Math.min(10, bounds().height());
        double trackHeight = Math.max(2, Math.min(4, bounds().height() / 3));
        UiRect track = new UiRect(
            bounds().x() + thumb / 2,
            bounds().centerY() - trackHeight / 2,
            Math.max(0, bounds().width() - thumb),
            trackHeight
        );
        double fraction = fraction();
        canvas.fill(track, trackColor.multiplyAlpha(style().opacity()), trackHeight / 2);
        canvas.fill(
            new UiRect(track.x(), track.y(), track.width() * fraction, track.height()),
            fillColor.multiplyAlpha(style().opacity()),
            trackHeight / 2
        );
        double thumbX = track.x() + track.width() * fraction - thumb / 2;
        canvas.fill(
            new UiRect(thumbX, bounds().centerY() - thumb / 2, thumb, thumb),
            thumbColor.multiplyAlpha(style().opacity()),
            thumb / 2
        );
    }

    private void setFromPointer(double pointerX) {
        double width = Math.max(1, bounds().width());
        double fraction = Math.max(0, Math.min(1, (pointerX - bounds().x()) / width));
        updateValue(minimum + fraction * (maximum - minimum));
    }

    private void updateValue(double next) {
        double normalized = clampAndSnap(next);
        if (Double.compare(value, normalized) != 0) {
            value = normalized;
            emitLocal("change", Map.of("value", value));
            markDirty();
        }
    }

    private double clampAndSnap(double candidate) {
        double result = clamp(candidate);
        if (step > 0) {
            result = minimum + Math.round((result - minimum) / step) * step;
        }
        return clamp(result);
    }

    private double clamp(double candidate) {
        return Math.max(minimum, Math.min(maximum, candidate));
    }

    private double fraction() {
        return maximum <= minimum ? 0 : (value - minimum) / (maximum - minimum);
    }

    private static double number(Object value) {
        double result = value instanceof Number number
            ? number.doubleValue()
            : Double.parseDouble(value.toString());
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Slider values must be finite");
        }
        return result;
    }

    private static UiColor color(Object value) {
        return value instanceof UiColor color ? color : UiColor.parse(value.toString());
    }
}
