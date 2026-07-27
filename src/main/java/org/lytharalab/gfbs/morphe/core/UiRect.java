package org.lytharalab.gfbs.morphe.core;

public record UiRect(double x, double y, double width, double height) {
    public static final UiRect ZERO = new UiRect(0, 0, 0, 0);

    public UiRect {
        if (!Double.isFinite(x) || !Double.isFinite(y)
            || !Double.isFinite(width) || !Double.isFinite(height)) {
            throw new IllegalArgumentException("Rectangle values must be finite");
        }
        width = Math.max(0, width);
        height = Math.max(0, height);
    }

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public double centerX() {
        return x + width / 2.0;
    }

    public double centerY() {
        return y + height / 2.0;
    }

    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
    }

    public UiRect inset(UiInsets insets) {
        return new UiRect(
            x + insets.left(),
            y + insets.top(),
            width - insets.horizontal(),
            height - insets.vertical()
        );
    }

    public UiRect intersect(UiRect other) {
        double left = Math.max(x, other.x);
        double top = Math.max(y, other.y);
        double right = Math.min(right(), other.right());
        double bottom = Math.min(bottom(), other.bottom());
        return new UiRect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }
}
