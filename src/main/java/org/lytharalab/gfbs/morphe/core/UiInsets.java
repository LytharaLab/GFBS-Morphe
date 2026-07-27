package org.lytharalab.gfbs.morphe.core;

import java.util.List;
import java.util.Map;

public record UiInsets(double top, double right, double bottom, double left) {
    public static final UiInsets ZERO = new UiInsets(0, 0, 0, 0);

    public UiInsets {
        if (!Double.isFinite(top) || !Double.isFinite(right)
            || !Double.isFinite(bottom) || !Double.isFinite(left)) {
            throw new IllegalArgumentException("Insets must be finite");
        }
    }

    public static UiInsets all(double value) {
        return new UiInsets(value, value, value, value);
    }

    public static UiInsets symmetric(double vertical, double horizontal) {
        return new UiInsets(vertical, horizontal, vertical, horizontal);
    }

    public static UiInsets parse(Object value) {
        if (value == null) {
            return ZERO;
        }
        if (value instanceof UiInsets insets) {
            return insets;
        }
        if (value instanceof Number number) {
            return all(number.doubleValue());
        }
        if (value instanceof List<?> list) {
            return fromList(list);
        }
        if (value instanceof Map<?, ?> map) {
            double vertical = number(map.get("vertical"), 0);
            double horizontal = number(map.get("horizontal"), 0);
            return new UiInsets(
                number(map.get("top"), vertical),
                number(map.get("right"), horizontal),
                number(map.get("bottom"), vertical),
                number(map.get("left"), horizontal)
            );
        }

        String[] parts = value.toString().trim().split("[,\\s]+");
        return fromList(List.of(parts));
    }

    private static UiInsets fromList(List<?> list) {
        return switch (list.size()) {
            case 0 -> ZERO;
            case 1 -> all(number(list.get(0), 0));
            case 2 -> symmetric(number(list.get(0), 0), number(list.get(1), 0));
            case 3 -> new UiInsets(
                number(list.get(0), 0),
                number(list.get(1), 0),
                number(list.get(2), 0),
                number(list.get(1), 0)
            );
            default -> new UiInsets(
                number(list.get(0), 0),
                number(list.get(1), 0),
                number(list.get(2), 0),
                number(list.get(3), 0)
            );
        };
    }

    private static double number(Object value, double fallback) {
        return value == null ? fallback : Double.parseDouble(value.toString());
    }

    public double horizontal() {
        return left + right;
    }

    public double vertical() {
        return top + bottom;
    }
}
