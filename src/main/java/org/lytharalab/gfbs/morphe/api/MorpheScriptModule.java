package org.lytharalab.gfbs.morphe.api;

import net.minecraft.resources.ResourceLocation;
import org.lytharalab.gfbs.morphe.core.UiDocument;
import org.lytharalab.gfbs.morphe.core.UiStyle;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A namespaced, sandbox-safe Java extension exposed to Lua below {@code ext}.
 * Implementations exchange plain Java values rather than LuaJ objects, so
 * dependent mods cannot accidentally escape the Morphe sandbox.
 */
public final class MorpheScriptModule {
    public enum UpdateRate {
        TICK,
        FRAME
    }

    public enum Phase {
        INITIAL,
        TICK,
        FRAME,
        CALL
    }

    @FunctionalInterface
    public interface Function {
        Object invoke(Context context, List<Object> arguments) throws Exception;
    }

    @FunctionalInterface
    public interface ValueProvider {
        Object get(Context context) throws Exception;
    }

    public record Context(
        UiDocument document,
        Map<String, ?> environment,
        Phase phase,
        double deltaSeconds,
        double logicSeconds,
        double frameSeconds
    ) {
        public Context {
            document = Objects.requireNonNull(document, "document");
            environment = freezeStringMap(environment);
            phase = phase == null ? Phase.CALL : phase;
        }
    }

    public record DynamicValue(UpdateRate updateRate, ValueProvider provider) {
        public DynamicValue {
            updateRate = updateRate == null ? UpdateRate.FRAME : updateRate;
            provider = Objects.requireNonNull(provider, "provider");
        }
    }

    private final ResourceLocation id;
    private final Map<String, Object> constants;
    private final Map<String, DynamicValue> variables;
    private final Map<String, Function> functions;

    private MorpheScriptModule(Builder builder) {
        id = builder.id;
        LinkedHashMap<String, Object> frozenConstants = new LinkedHashMap<>();
        builder.constants.forEach((key, value) ->
            frozenConstants.put(key, freezeValue(value, 0)));
        constants = Collections.unmodifiableMap(frozenConstants);
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(builder.variables));
        functions = Collections.unmodifiableMap(new LinkedHashMap<>(builder.functions));
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public ResourceLocation id() {
        return id;
    }

    public Map<String, Object> constants() {
        return constants;
    }

    public Map<String, DynamicValue> variables() {
        return variables;
    }

    public Map<String, Function> functions() {
        return functions;
    }

    public static final class Builder {
        private final ResourceLocation id;
        private final Map<String, Object> constants = new LinkedHashMap<>();
        private final Map<String, DynamicValue> variables = new LinkedHashMap<>();
        private final Map<String, Function> functions = new LinkedHashMap<>();

        private Builder(ResourceLocation id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder constant(String name, Object value) {
            String key = claim(name);
            constants.put(key, value);
            return this;
        }

        public Builder variable(String name, ValueProvider provider) {
            return frameVariable(name, provider);
        }

        public Builder frameVariable(String name, ValueProvider provider) {
            String key = claim(name);
            variables.put(key, new DynamicValue(UpdateRate.FRAME, provider));
            return this;
        }

        public Builder tickVariable(String name, ValueProvider provider) {
            String key = claim(name);
            variables.put(key, new DynamicValue(UpdateRate.TICK, provider));
            return this;
        }

        public Builder function(String name, Function function) {
            String key = claim(name);
            functions.put(key, Objects.requireNonNull(function, "function"));
            return this;
        }

        public MorpheScriptModule build() {
            if (constants.isEmpty() && variables.isEmpty() && functions.isEmpty()) {
                throw new IllegalStateException("Script module must expose at least one entry");
            }
            return new MorpheScriptModule(this);
        }

        private String claim(String name) {
            String key = normalizeEntry(name);
            if (constants.containsKey(key) || variables.containsKey(key) || functions.containsKey(key)) {
                throw new IllegalStateException("Duplicate script module entry: " + key);
            }
            return key;
        }
    }

    public static String normalizeEntry(String value) {
        String key = UiStyle.normalize(Objects.requireNonNull(value, "name"));
        if (!key.matches("[a-z_][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException(
                "Extension entry must match [a-z_][a-z0-9_]{0,63}: " + value
            );
        }
        return key;
    }

    private static Map<String, ?> freezeStringMap(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) ->
            result.put(Objects.requireNonNull(key, "environment key"), freezeValue(value, 0)));
        return Collections.unmodifiableMap(result);
    }

    private static Object freezeValue(Object value, int depth) {
        if (depth >= 16) {
            throw new IllegalArgumentException("Extension value nesting exceeds 16 levels");
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > 256) {
                throw new IllegalArgumentException("Extension map exceeds 256 entries");
            }
            LinkedHashMap<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) ->
                copy.put(key, freezeValue(nested, depth + 1)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayList<Object> copy = new ArrayList<>();
            for (Object nested : iterable) {
                if (copy.size() >= 256) {
                    throw new IllegalArgumentException("Extension list exceeds 256 entries");
                }
                copy.add(freezeValue(nested, depth + 1));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
