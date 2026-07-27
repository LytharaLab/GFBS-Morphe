package org.lytharalab.gfbs.morphe.widget;

import org.lytharalab.gfbs.morphe.core.UiCanvas;
import org.lytharalab.gfbs.morphe.core.UiColor;
import org.lytharalab.gfbs.morphe.core.UiFrame;
import org.lytharalab.gfbs.morphe.core.UiRect;
import org.lytharalab.gfbs.morphe.core.UiSize;

public final class ProgressElement extends PanelElement {
    private double minimum;
    private double maximum = 1;
    private double value;
    private UiColor trackColor = UiColor.rgb(38, 45, 57);
    private UiColor fillColor = UiColor.rgb(38, 198, 218);

    public ProgressElement() {
        super("progress");
        setProperty("height", 8);
        setProperty("radius", 4);
        setProperty("pointer_events", false);
    }

    @Override
    protected Object getWidgetProperty(String property) {
        return switch (property) {
            case "min", "minimum" -> minimum;
            case "max", "maximum" -> maximum;
            case "value" -> value;
            case "track_color" -> trackColor;
            case "fill_color", "accent" -> fillColor;
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
            case "value" -> value = clamp(number(raw));
            case "track_color" -> trackColor = color(raw);
            case "fill_color", "accent" -> fillColor = color(raw);
            default -> {
                return super.setWidgetProperty(property, raw);
            }
        }
        return true;
    }

    @Override
    public UiSize measure(double availableWidth, double availableHeight) {
        return new UiSize(Math.min(availableWidth, 120), 8);
    }

    @Override
    protected void renderContent(UiCanvas canvas, UiFrame frame) {
        double fraction = maximum <= minimum ? 0 : (value - minimum) / (maximum - minimum);
        fraction = Math.max(0, Math.min(1, fraction));
        canvas.fill(bounds(), trackColor.multiplyAlpha(style().opacity()), style().radius());
        if (fraction > 0) {
            canvas.fill(
                new UiRect(bounds().x(), bounds().y(), bounds().width() * fraction, bounds().height()),
                fillColor.multiplyAlpha(style().opacity()),
                style().radius()
            );
        }
    }

    private static double number(Object value) {
        double result = value instanceof Number number
            ? number.doubleValue()
            : Double.parseDouble(value.toString());
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Progress values must be finite");
        }
        return result;
    }

    private double clamp(double candidate) {
        return Math.max(minimum, Math.min(maximum, candidate));
    }

    private static UiColor color(Object value) {
        return value instanceof UiColor color ? color : UiColor.parse(value.toString());
    }
}
