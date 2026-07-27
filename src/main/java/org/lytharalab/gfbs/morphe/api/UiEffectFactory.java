package org.lytharalab.gfbs.morphe.api;

import java.util.Map;

@FunctionalInterface
public interface UiEffectFactory {
    UiEffect create(Map<String, ?> options);
}
