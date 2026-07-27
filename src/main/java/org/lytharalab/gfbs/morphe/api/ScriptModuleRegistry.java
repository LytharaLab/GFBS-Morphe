package org.lytharalab.gfbs.morphe.api;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class ScriptModuleRegistry {
    private final Map<ResourceLocation, MorpheScriptModule> modules = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void register(MorpheScriptModule module) {
        MorpheScriptModule safe = Objects.requireNonNull(module, "module");
        lock.writeLock().lock();
        try {
            if (modules.containsKey(safe.id())) {
                throw new IllegalStateException("Script module is already registered: " + safe.id());
            }
            modules.put(safe.id(), safe);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void replace(MorpheScriptModule module) {
        MorpheScriptModule safe = Objects.requireNonNull(module, "module");
        lock.writeLock().lock();
        try {
            modules.put(safe.id(), safe);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean unregister(ResourceLocation id) {
        lock.writeLock().lock();
        try {
            return modules.remove(Objects.requireNonNull(id, "id")) != null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<MorpheScriptModule> snapshot() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(modules.values()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean contains(ResourceLocation id) {
        lock.readLock().lock();
        try {
            return modules.containsKey(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<ResourceLocation> ids() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new LinkedHashSet<>(modules.keySet()));
        } finally {
            lock.readLock().unlock();
        }
    }
}
