package org.lytharalab.gfbs.morphe.core;

import java.util.Locale;

/**
 * Immutable ARGB color used by the renderer-independent UI core.
 */
public record UiColor(int argb) {
    public static final UiColor TRANSPARENT = new UiColor(0x00000000);
    public static final UiColor WHITE = new UiColor(0xFFFFFFFF);
    public static final UiColor BLACK = new UiColor(0xFF000000);

    public static UiColor rgb(int red, int green, int blue) {
        return rgba(red, green, blue, 255);
    }

    public static UiColor rgba(int red, int green, int blue, int alpha) {
        return new UiColor(
            clampByte(alpha) << 24 |
            clampByte(red) << 16 |
            clampByte(green) << 8 |
            clampByte(blue)
        );
    }

    public static UiColor parse(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x")) {
            normalized = normalized.substring(2);
        }

        return switch (normalized.length()) {
            case 3 -> rgb(
                Integer.parseInt(normalized.substring(0, 1).repeat(2), 16),
                Integer.parseInt(normalized.substring(1, 2).repeat(2), 16),
                Integer.parseInt(normalized.substring(2, 3).repeat(2), 16)
            );
            case 6 -> new UiColor(0xFF000000 | Integer.parseUnsignedInt(normalized, 16));
            case 8 -> new UiColor((int) Long.parseLong(normalized, 16));
            default -> throw new IllegalArgumentException("Expected #RGB, #RRGGBB or #AARRGGBB, got: " + value);
        };
    }

    public int alpha() {
        return argb >>> 24 & 0xFF;
    }

    public int red() {
        return argb >>> 16 & 0xFF;
    }

    public int green() {
        return argb >>> 8 & 0xFF;
    }

    public int blue() {
        return argb & 0xFF;
    }

    public UiColor multiplyAlpha(double opacity) {
        return rgba(red(), green(), blue(), (int) Math.round(alpha() * clamp01(opacity)));
    }

    public static UiColor lerp(UiColor from, UiColor to, double progress) {
        double t = clamp01(progress);
        return rgba(
            lerpChannel(from.red(), to.red(), t),
            lerpChannel(from.green(), to.green(), t),
            lerpChannel(from.blue(), to.blue(), t),
            lerpChannel(from.alpha(), to.alpha(), t)
        );
    }

    private static int lerpChannel(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "#%08X", Integer.toUnsignedLong(argb));
    }
}
