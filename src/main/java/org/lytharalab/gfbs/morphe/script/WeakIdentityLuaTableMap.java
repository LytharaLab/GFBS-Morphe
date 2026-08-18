package org.lytharalab.gfbs.morphe.script;

import org.luaj.vm2.LuaTable;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Identity-keyed map for Lua tables whose keys are weakly referenced.
 *
 * <p>Morphe read-only proxies need an identity mapping back to their backing
 * tables for {@code pairs}/{@code ipairs} and external argument conversion.
 * A normal {@code IdentityHashMap} makes every proxy live for the entire Lua
 * runtime lifetime, which is catastrophic for frame-scoped environment
 * snapshots. This map preserves identity lookup semantics for live proxies
 * while allowing unreachable proxy keys to be collected.</p>
 *
 * <p>The values remain strongly referenced while their proxy key is live, so
 * iterator and conversion behavior is identical to the old registry. Stale
 * entries are removed opportunistically on every registry operation through a
 * {@link ReferenceQueue}; no background thread or LuaJ-specific GC hook is
 * required.</p>
 */
final class WeakIdentityLuaTableMap extends AbstractMap<LuaTable, LuaTable> {
    private final ReferenceQueue<LuaTable> staleKeys = new ReferenceQueue<>();
    private final Map<IdentityWeakReference, LuaTable> entries = new HashMap<>();

    @Override
    public LuaTable put(LuaTable key, LuaTable value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        expungeStaleEntries();
        return entries.put(new IdentityWeakReference(key, staleKeys), value);
    }

    @Override
    public LuaTable get(Object key) {
        if (!(key instanceof LuaTable table)) {
            return null;
        }
        expungeStaleEntries();
        return entries.get(new IdentityWeakReference(table));
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof LuaTable table)) {
            return false;
        }
        expungeStaleEntries();
        return entries.containsKey(new IdentityWeakReference(table));
    }

    @Override
    public LuaTable remove(Object key) {
        if (!(key instanceof LuaTable table)) {
            return null;
        }
        expungeStaleEntries();
        return entries.remove(new IdentityWeakReference(table));
    }

    @Override
    public int size() {
        expungeStaleEntries();
        return entries.size();
    }

    @Override
    public boolean isEmpty() {
        expungeStaleEntries();
        return entries.isEmpty();
    }

    @Override
    public void clear() {
        entries.clear();
        while (staleKeys.poll() != null) {
            // Drain references left in the queue so a reused registry starts clean.
        }
    }

    @Override
    public Set<Entry<LuaTable, LuaTable>> entrySet() {
        expungeStaleEntries();
        LinkedHashSet<Entry<LuaTable, LuaTable>> liveEntries = new LinkedHashSet<>();
        for (Map.Entry<IdentityWeakReference, LuaTable> entry : entries.entrySet()) {
            LuaTable key = entry.getKey().get();
            if (key != null) {
                liveEntries.add(new AbstractMap.SimpleImmutableEntry<>(key, entry.getValue()));
            }
        }
        return Collections.unmodifiableSet(liveEntries);
    }

    private void expungeStaleEntries() {
        Reference<? extends LuaTable> reference;
        while ((reference = staleKeys.poll()) != null) {
            entries.remove(reference);
        }
    }

    private static final class IdentityWeakReference extends WeakReference<LuaTable> {
        private final int identityHash;

        private IdentityWeakReference(LuaTable referent, ReferenceQueue<LuaTable> queue) {
            super(referent, queue);
            identityHash = System.identityHashCode(referent);
        }

        private IdentityWeakReference(LuaTable referent) {
            super(referent);
            identityHash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof IdentityWeakReference reference)) {
                return false;
            }
            LuaTable value = get();
            return value != null && value == reference.get();
        }
    }
}
