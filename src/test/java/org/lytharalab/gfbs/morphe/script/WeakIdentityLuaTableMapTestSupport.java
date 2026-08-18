package org.lytharalab.gfbs.morphe.script;

import org.luaj.vm2.LuaTable;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Deterministic regression coverage for the weak identity registry used by
 * Morphe's read-only Lua proxies.
 */
final class WeakIdentityLuaTableMapTestSupport {
    private WeakIdentityLuaTableMapTestSupport() {
    }

    static void run() {
        preservesIdentityLookup();
        removesQueuedStaleKeysWithoutWaitingForTheGc();
    }

    private static void preservesIdentityLookup() {
        WeakIdentityLuaTableMap registry = new WeakIdentityLuaTableMap();
        LuaTable proxy = new LuaTable();
        LuaTable backing = new LuaTable();
        LuaTable otherProxy = new LuaTable();

        registry.put(proxy, backing);
        if (registry.get(proxy) != backing) {
            throw new AssertionError("Weak Lua registry lost a live identity mapping");
        }
        if (registry.get(otherProxy) != null) {
            throw new AssertionError("Weak Lua registry matched a different table identity");
        }
        if (registry.size() != 1) {
            throw new AssertionError("Weak Lua registry reported an invalid live entry count");
        }
    }

    @SuppressWarnings("unchecked")
    private static void removesQueuedStaleKeysWithoutWaitingForTheGc() {
        try {
            WeakIdentityLuaTableMap registry = new WeakIdentityLuaTableMap();
            LuaTable proxy = new LuaTable();
            registry.put(proxy, new LuaTable());

            Field entriesField = WeakIdentityLuaTableMap.class.getDeclaredField("entries");
            entriesField.setAccessible(true);
            Map<Object, LuaTable> entries = (Map<Object, LuaTable>) entriesField.get(registry);
            if (entries.size() != 1) {
                throw new AssertionError("Weak Lua registry did not record the test proxy");
            }

            Object weakKey = entries.keySet().iterator().next();
            if (!(weakKey instanceof Reference<?> reference) || !reference.enqueue()) {
                throw new AssertionError("Unable to enqueue the weak Lua proxy key for regression test");
            }

            // Every public registry operation drains the ReferenceQueue. This
            // is the same path used while frame snapshots continuously create
            // and replace read-only proxies.
            if (registry.size() != 0 || !registry.isEmpty()) {
                throw new AssertionError("Queued Lua proxy key was retained by the registry");
            }
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect weak Lua registry", exception);
        }
    }
}
