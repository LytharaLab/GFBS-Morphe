package org.lytharalab.gfbs.morphe.core;

/**
 * Visual transform applied after layout. Pivot values are normalized inside
 * the element bounds, so {@code 0.5, 0.5} is the center.
 */
public record UiTransform(
    double translateX,
    double translateY,
    double scaleX,
    double scaleY,
    double rotation,
    double pivotX,
    double pivotY
) {
    public static final UiTransform IDENTITY = new UiTransform(0, 0, 1, 1, 0, 0.5, 0.5);

    public UiTransform {
        if (!Double.isFinite(translateX)
            || !Double.isFinite(translateY)
            || !Double.isFinite(scaleX)
            || !Double.isFinite(scaleY)
            || !Double.isFinite(rotation)
            || !Double.isFinite(pivotX)
            || !Double.isFinite(pivotY)) {
            throw new IllegalArgumentException("Transform values must be finite");
        }
    }
}
