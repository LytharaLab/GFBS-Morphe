package org.lytharalab.gfbs.morphe.core;

@FunctionalInterface
public interface UiSubscription extends AutoCloseable {
    void disconnect();

    @Override
    default void close() {
        disconnect();
    }
}
