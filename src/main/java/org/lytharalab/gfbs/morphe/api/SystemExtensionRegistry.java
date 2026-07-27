package org.lytharalab.gfbs.morphe.api;

import net.minecraft.resources.ResourceLocation;
import org.lytharalab.gfbs.morphe.core.UiDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class SystemExtensionRegistry {
    private final Map<ResourceLocation, UiSystemExtensionFactory> factories = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void register(ResourceLocation id, UiSystemExtensionFactory factory) {
        Objects.requireNonNull(id, "id");
        lock.writeLock().lock();
        try {
            if (factories.containsKey(id)) {
                throw new IllegalStateException("UI system extension is already registered: " + id);
            }
            factories.put(id, Objects.requireNonNull(factory, "factory"));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void replace(ResourceLocation id, UiSystemExtensionFactory factory) {
        lock.writeLock().lock();
        try {
            factories.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(factory, "factory"));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean unregister(ResourceLocation id) {
        lock.writeLock().lock();
        try {
            return factories.remove(Objects.requireNonNull(id, "id")) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Creates and attaches extensions one at a time. If any factory or attach
     * hook fails, the document is closed so already attached extensions are
     * released in normal lifecycle order.
     */
    public void attachAll(UiDocument document) {
        Objects.requireNonNull(document, "document");
        List<Map.Entry<ResourceLocation, UiSystemExtensionFactory>> snapshot;
        lock.readLock().lock();
        try {
            snapshot = new ArrayList<>(factories.entrySet());
        } finally {
            lock.readLock().unlock();
        }
        try {
            for (Map.Entry<ResourceLocation, UiSystemExtensionFactory> entry : snapshot) {
                UiSystemExtension extension = entry.getValue().create(document);
                if (extension == null) {
                    throw new IllegalStateException(
                        "UI system extension factory returned null: " + entry.getKey()
                    );
                }
                document.addSystemExtension(entry.getKey().toString(), extension);
            }
        } catch (RuntimeException exception) {
            document.close();
            throw exception;
        }
    }

    public Set<ResourceLocation> ids() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new LinkedHashSet<>(factories.keySet()));
        } finally {
            lock.readLock().unlock();
        }
    }
}
