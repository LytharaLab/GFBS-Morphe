package org.lytharalab.gfbs.morphe.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class EffectRegistry {
    private final Map<String, UiEffectFactory> factories = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void register(ResourceLocation id, UiEffectFactory factory) {
        register(id.toString(), factory);
    }

    public void register(String id, UiEffectFactory factory) {
        String key = key(id);
        lock.writeLock().lock();
        try {
            if (factories.containsKey(key)) {
                throw new IllegalStateException("UI effect is already registered: " + key);
            }
            factories.put(key, Objects.requireNonNull(factory, "factory"));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void replace(ResourceLocation id, UiEffectFactory factory) {
        lock.writeLock().lock();
        try {
            factories.put(key(id.toString()), Objects.requireNonNull(factory, "factory"));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean unregister(ResourceLocation id) {
        lock.writeLock().lock();
        try {
            return factories.remove(key(id.toString())) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public UiEffect create(String id, Map<String, ?> options) {
        UiEffectFactory factory;
        String normalized = key(id);
        lock.readLock().lock();
        try {
            factory = factories.get(normalized);
            if (factory == null) {
                throw new IllegalArgumentException("Unknown Morphe UI effect: " + id);
            }
        } finally {
            lock.readLock().unlock();
        }
        // Factories belong to dependent mods and may register more extensions.
        UiEffect effect = factory.create(options == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(options)));
        if (effect == null) {
            throw new IllegalStateException("UI effect factory returned null: " + id);
        }
        return effect;
    }

    public Set<String> types() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new LinkedHashSet<>(factories.keySet()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public static String key(String value) {
        String key = Objects.requireNonNull(value, "id").trim().toLowerCase();
        if (!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || key.length() > 128) {
            throw new IllegalArgumentException("Effect id must be a namespaced resource id: " + value);
        }
        return key;
    }
}
