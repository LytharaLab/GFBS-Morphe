package org.lytharalab.gfbs.morphe.core;

import java.util.Locale;

/**
 * A CSS-like length supporting pixels, percentages and automatic sizing.
 */
public record UiLength(Unit unit, double value) {
    public enum Unit {
        AUTO,
        PIXEL,
        PERCENT
    }

    public static final UiLength AUTO = new UiLength(Unit.AUTO, 0.0);
    public static final UiLength ZERO = new UiLength(Unit.PIXEL, 0.0);

    public UiLength {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Length must be finite");
        }
    }

    public static UiLength px(double pixels) {
        return new UiLength(Unit.PIXEL, pixels);
    }

    public static UiLength percent(double percent) {
        return new UiLength(Unit.PERCENT, percent);
    }

    public static UiLength parse(Object value) {
        if (value == null) {
            return AUTO;
        }
        if (value instanceof UiLength length) {
            return length;
        }
        if (value instanceof Number number) {
            return px(number.doubleValue());
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        if (text.equals("auto")) {
            return AUTO;
        }
        if (text.endsWith("%")) {
            return percent(Double.parseDouble(text.substring(0, text.length() - 1).trim()));
        }
        if (text.endsWith("px")) {
            text = text.substring(0, text.length() - 2).trim();
        }
        return px(Double.parseDouble(text));
    }

    public double resolve(double reference, double automatic) {
        return switch (unit) {
            case AUTO -> automatic;
            case PIXEL -> value;
            case PERCENT -> reference * value / 100.0;
        };
    }

    public boolean isAuto() {
        return unit == Unit.AUTO;
    }

    @Override
    public String toString() {
        return switch (unit) {
            case AUTO -> "auto";
            case PIXEL -> format(value) + "px";
            case PERCENT -> format(value) + "%";
        };
    }

    private static String format(double number) {
        return number == Math.rint(number)
            ? Long.toString(Math.round(number))
            : String.format(Locale.ROOT, "%.3f", number).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
