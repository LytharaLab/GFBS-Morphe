package org.lytharalab.gfbs.morphe.core;

/**
 * Normalized texture coordinates. Values outside 0..1 are accepted so custom
 * render backends may intentionally tile a texture.
 */
public record UiImageRegion(double u0, double v0, double u1, double v1) {
    public static final UiImageRegion FULL = new UiImageRegion(0, 0, 1, 1);

    public UiImageRegion {
        if (!Double.isFinite(u0) || !Double.isFinite(v0) || !Double.isFinite(u1) || !Double.isFinite(v1)) {
            throw new IllegalArgumentException("Image region values must be finite");
        }
    }
}
