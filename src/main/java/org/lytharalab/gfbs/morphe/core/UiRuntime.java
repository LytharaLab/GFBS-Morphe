package org.lytharalab.gfbs.morphe.core;

public interface UiRuntime extends AutoCloseable {
    void execute(String source, String chunkName);

    void tick(double deltaSeconds);

    /**
     * Advances visual work with the actual rendered frame duration.
     * Logic timers remain in {@link #tick(double)}.
     */
    default void frame(double deltaSeconds) {
    }

    String error();

    @Override
    void close();
}
