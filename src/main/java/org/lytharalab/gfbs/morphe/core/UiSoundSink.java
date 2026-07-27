package org.lytharalab.gfbs.morphe.core;

@FunctionalInterface
public interface UiSoundSink {
    UiSoundSink SILENT = (sound, volume, pitch) -> {
    };

    void play(String sound, float volume, float pitch);
}
