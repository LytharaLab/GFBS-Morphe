package org.lytharalab.gfbs.morphe.core;

public record UiSize(double width, double height) {
    public static final UiSize ZERO = new UiSize(0, 0);

    public UiSize {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }
}
