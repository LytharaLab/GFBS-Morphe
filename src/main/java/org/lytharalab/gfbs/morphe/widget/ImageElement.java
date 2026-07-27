package org.lytharalab.gfbs.morphe.widget;

import org.lytharalab.gfbs.morphe.core.UiCanvas;
import org.lytharalab.gfbs.morphe.core.UiColor;
import org.lytharalab.gfbs.morphe.core.UiFrame;
import org.lytharalab.gfbs.morphe.core.UiImageRegion;

public final class ImageElement extends PanelElement {
    private String source = "";
    private UiColor tint = UiColor.WHITE;
    private double u0;
    private double v0;
    private double u1 = 1;
    private double v1 = 1;

    public ImageElement() {
        super("image");
        setProperty("pointer_events", false);
    }

    @Override
    protected Object getWidgetProperty(String property) {
        return switch (property) {
            case "source", "image", "texture" -> source;
            case "tint", "image_color" -> tint;
            case "u0" -> u0;
            case "v0" -> v0;
            case "u1" -> u1;
            case "v1" -> v1;
            default -> super.getWidgetProperty(property);
        };
    }

    @Override
    protected boolean setWidgetProperty(String property, Object value) {
        switch (property) {
            case "source", "image", "texture" -> source = value == null ? "" : value.toString();
            case "tint", "image_color" -> tint = value instanceof UiColor color
                ? color
                : UiColor.parse(value.toString());
            case "u0" -> u0 = number(value);
            case "v0" -> v0 = number(value);
            case "u1" -> u1 = number(value);
            case "v1" -> v1 = number(value);
            default -> {
                return super.setWidgetProperty(property, value);
            }
        }
        return true;
    }

    @Override
    protected void renderContent(UiCanvas canvas, UiFrame frame) {
        if (!source.isBlank()) {
            canvas.image(
                source,
                bounds(),
                tint.multiplyAlpha(style().opacity()),
                style().imageFit(),
                new UiImageRegion(u0, v0, u1, v1)
            );
        }
    }

    private static double number(Object value) {
        double number = value instanceof Number raw ? raw.doubleValue() : Double.parseDouble(value.toString());
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Image coordinates must be finite");
        }
        return number;
    }
}
