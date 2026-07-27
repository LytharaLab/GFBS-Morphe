package org.lytharalab.gfbs.morphe.api;

import org.lytharalab.gfbs.morphe.core.UiElement;
import org.lytharalab.gfbs.morphe.core.UiStyle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class WidgetRegistry {
    private final Map<String, WidgetFactory> factories = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void register(String type, WidgetFactory factory) {
        String key = key(type);
        lock.writeLock().lock();
        try {
            if (factories.containsKey(key) || aliases.containsKey(key)) {
                throw new IllegalStateException("Widget type is already registered: " + type);
            }
            factories.put(key, Objects.requireNonNull(factory, "factory"));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void replace(String type, WidgetFactory factory) {
        lock.writeLock().lock();
        try {
            String key = key(type);
            aliases.remove(key);
            factories.put(key, Objects.requireNonNull(factory, "factory"));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void alias(String alias, String target) {
        lock.writeLock().lock();
        try {
            String aliasKey = key(alias);
            String targetKey = key(target);
            if (!factories.containsKey(targetKey)) {
                throw new IllegalArgumentException("Cannot alias unknown widget type: " + target);
            }
            if (factories.containsKey(aliasKey) || aliases.containsKey(aliasKey)) {
                throw new IllegalStateException("Widget type is already registered: " + alias);
            }
            aliases.put(aliasKey, targetKey);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean unregister(String type) {
        String key = key(type);
        lock.writeLock().lock();
        try {
            boolean removed = factories.remove(key) != null;
            removed |= aliases.remove(key) != null;
            if (removed) {
                aliases.entrySet().removeIf(entry -> entry.getValue().equals(key));
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public UiElement create(String type) {
        WidgetFactory factory;
        lock.readLock().lock();
        try {
            String key = key(type);
            factory = factories.get(aliases.getOrDefault(key, key));
            if (factory == null) {
                throw new IllegalArgumentException("Unknown Morphe widget type: " + type);
            }
        } finally {
            lock.readLock().unlock();
        }
        // Never invoke dependent-mod code while holding the registry lock.
        UiElement element = factory.create();
        if (element == null) {
            throw new IllegalStateException("Widget factory returned null: " + type);
        }
        return element;
    }

    public boolean contains(String type) {
        lock.readLock().lock();
        try {
            String key = key(type);
            return factories.containsKey(key) || aliases.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<String> types() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new LinkedHashSet<>(factories.keySet()));
        } finally {
            lock.readLock().unlock();
        }
    }

    private static String key(String value) {
        String key = UiStyle.normalize(Objects.requireNonNull(value, "type"));
        if (key.isBlank() || key.length() > 64) {
            throw new IllegalArgumentException("Widget type must contain 1-64 characters");
        }
        return key;
    }
}
