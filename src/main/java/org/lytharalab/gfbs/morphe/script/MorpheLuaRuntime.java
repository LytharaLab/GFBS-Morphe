package org.lytharalab.gfbs.morphe.script;

import com.mojang.logging.LogUtils;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.lytharalab.gfbs.morphe.api.Morphe;
import org.lytharalab.gfbs.morphe.api.MorpheScriptModule;
import org.lytharalab.gfbs.morphe.core.UiAnimator;
import org.lytharalab.gfbs.morphe.core.UiColor;
import org.lytharalab.gfbs.morphe.core.UiDocument;
import org.lytharalab.gfbs.morphe.core.UiElement;
import org.lytharalab.gfbs.morphe.core.UiEvent;
import org.lytharalab.gfbs.morphe.core.UiInsets;
import org.lytharalab.gfbs.morphe.core.UiLength;
import org.lytharalab.gfbs.morphe.core.UiHost;
import org.lytharalab.gfbs.morphe.core.UiRuntime;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Sandboxed LuaJ runtime. It intentionally omits package, io, os, debug and
 * Java reflection libraries.
 */
public final class MorpheLuaRuntime implements UiRuntime {
    public static final int MAX_SCRIPT_BYTES = 512 * 1024;
    public static final int MAX_ELEMENTS = 4096;
    public static final int MAX_BINDINGS = 4096;
    public static final int MAX_CALLBACKS = 8192;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HANDLE_KEY = "\u0000morphe_handle";

    private final UiDocument document;
    private final Globals globals = new Globals();
    private final UiActionSink actionSink;
    private final UiHost host;
    private final LuaTable stateBacking = new LuaTable();
    private final LuaTable themeBacking = new LuaTable();
    private final LuaTable environmentBacking = new LuaTable();
    private final Map<String, LuaFunction> scriptComponents = new LinkedHashMap<>();
    private final List<Binding> bindings = new ArrayList<>();
    private final List<LuaFunction> tickHandlers = new ArrayList<>();
    private final List<LuaFunction> frameHandlers = new ArrayList<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final List<Scheduled> scheduledFrames = new ArrayList<>();
    private final List<ExternalVariableBinding> externalVariables = new ArrayList<>();
    private final Map<LuaTable, LuaTable> readOnlyBackings = new IdentityHashMap<>();
    private Map<String, ?> environmentSnapshot = Map.of();
    private boolean stateDirty = true;
    private boolean closed;
    private int elementCount;
    private int callbackCount;
    private long nextTimerId = 1;
    private double elapsed;
    private double frameElapsed;
    private String error;

    public MorpheLuaRuntime(
        UiDocument document,
        Map<String, ?> initialData,
        UiActionSink actionSink,
        Runnable closeAction
    ) {
        this(document, initialData, actionSink, new UiHost() {
            @Override
            public void closeView() {
                if (closeAction != null) closeAction.run();
            }
        });
    }

    public MorpheLuaRuntime(
        UiDocument document,
        Map<String, ?> initialData,
        UiActionSink actionSink,
        UiHost host
    ) {
        this.document = Objects.requireNonNull(document, "document");
        this.actionSink = actionSink == null ? UiActionSink.NOOP : actionSink;
        this.host = host == null ? UiHost.NONE : host;
        installSafeLibraries();
        installApi(initialData == null ? Map.of() : initialData);
    }

    public Globals globals() {
        return globals;
    }

    @Override
    public void execute(String source, String chunkName) {
        ensureOpen();
        int bytes = source.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_SCRIPT_BYTES) {
            fail("Script exceeds " + MAX_SCRIPT_BYTES + " UTF-8 bytes", null);
            return;
        }
        try {
            globals.load(source, chunkName).call();
            evaluateBindings();
        } catch (RuntimeException exception) {
            fail("Failed to execute " + chunkName + ": " + exception.getMessage(), exception);
        }
    }

    @Override
    public void tick(double deltaSeconds) {
        if (closed) {
            return;
        }
        elapsed += Math.max(0, deltaSeconds);
        refreshExternalVariables(
            MorpheScriptModule.UpdateRate.TICK,
            MorpheScriptModule.Phase.TICK,
            deltaSeconds
        );
        for (LuaFunction handler : List.copyOf(tickHandlers)) {
            callSafely(handler, LuaValue.valueOf(deltaSeconds));
        }
        for (Scheduled item : List.copyOf(scheduled)) {
            if (item.cancelled || item.deadline > elapsed) continue;
            callSafely(item.callback);
            if (item.repeating && !item.cancelled) {
                item.deadline = elapsed + item.interval;
            } else {
                scheduled.remove(item);
            }
        }
        scheduled.removeIf(item -> item.cancelled);
        if (stateDirty) {
            evaluateBindings();
        }
    }

    @Override
    public void frame(double deltaSeconds) {
        if (closed) {
            return;
        }
        double safeDelta = Math.max(0, deltaSeconds);
        frameElapsed += safeDelta;
        environmentSnapshot = safeEnvironment(host.environment());
        replaceTable(environmentBacking, environmentSnapshot);
        refreshExternalVariables(
            MorpheScriptModule.UpdateRate.FRAME,
            MorpheScriptModule.Phase.FRAME,
            safeDelta
        );
        stateDirty = true;
        for (LuaFunction handler : List.copyOf(frameHandlers)) {
            callSafely(handler, LuaValue.valueOf(safeDelta));
        }
        for (Scheduled item : List.copyOf(scheduledFrames)) {
            if (item.cancelled) continue;
            if (item.owner != null && item.owner.animator().paused()) {
                item.deadline += safeDelta;
                continue;
            }
            if (item.deadline > frameElapsed) continue;
            callSafely(item.callback);
            scheduledFrames.remove(item);
        }
        scheduledFrames.removeIf(item -> item.cancelled);
        if (stateDirty) {
            evaluateBindings();
        }
    }

    @Override
    public String error() {
        return error;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        bindings.clear();
        tickHandlers.clear();
        frameHandlers.clear();
        scheduled.clear();
        scheduledFrames.clear();
        externalVariables.clear();
        readOnlyBackings.clear();
    }

    private void installSafeLibraries() {
        globals.load(new BaseLib());

        // LuaJ's module libraries register themselves in package.loaded even
        // when PackageLib is not otherwise needed. Supply only that internal
        // table during installation, then remove every package/file-loading
        // entry before user code can run.
        LuaTable packageTable = new LuaTable();
        packageTable.set("loaded", new LuaTable());
        globals.set("package", packageTable);
        globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new MathLib());

        globals.set("package", LuaValue.NIL);
        globals.set("require", LuaValue.NIL);
        globals.set("loadfile", LuaValue.NIL);
        globals.set("dofile", LuaValue.NIL);
        globals.set("rawget", LuaValue.NIL);
        globals.set("rawset", LuaValue.NIL);
        globals.set("getmetatable", LuaValue.NIL);
        globals.set("setmetatable", LuaValue.NIL);
        installReadOnlyIterators();

        LoadState.install(globals);
        LuaC.install(globals);

        globals.set("print", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                StringBuilder message = new StringBuilder();
                for (int i = 1; i <= args.narg(); i++) {
                    if (i > 1) {
                        message.append('\t');
                    }
                    message.append(args.arg(i).tojstring());
                }
                LOGGER.info("[Morphe Lua] {}", message);
                return LuaValue.NONE;
            }
        });
    }

    private void installApi(Map<String, ?> initialData) {
        LuaTable ui = new LuaTable();
        ui.set("version", Morphe.VERSION);
        ui.set("api_version", Morphe.SCRIPT_API_VERSION);
        ui.set("create", createFunction(null));
        for (String type : Morphe.get().widgetTypes()) {
            ui.set(type, createFunction(type));
        }
        ui.set("frame", createFunction("panel"));
        ui.set("label", createFunction("text"));
        ui.set("text_field", createFunction("input"));
        ui.set("scroll_view", createFunction("scroll"));

        ui.set("mount", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                document.root().add(requireElement(value));
                return value;
            }
        });
        ui.set("root", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return handle(document.root());
            }
        });
        ui.set("find", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue id) {
                UiElement found = document.root().find(id.checkjstring());
                return found == null ? LuaValue.NIL : handle(found);
            }
        });
        ui.set("color", colorFunction());
        ui.set("px", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                return LuaValue.userdataOf(UiLength.px(value.checkdouble()));
            }
        });
        ui.set("percent", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                return LuaValue.userdataOf(UiLength.percent(value.checkdouble()));
            }
        });
        ui.set("inset", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.userdataOf(switch (args.narg()) {
                    case 0 -> UiInsets.ZERO;
                    case 1 -> UiInsets.all(args.arg(1).checkdouble());
                    case 2 -> UiInsets.symmetric(args.arg(1).checkdouble(), args.arg(2).checkdouble());
                    default -> new UiInsets(
                        args.arg(1).checkdouble(),
                        args.arg(2).checkdouble(),
                        args.arg(3).checkdouble(),
                        args.arg(4).checkdouble()
                    );
                });
            }
        });
        ui.set("bind", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue element, LuaValue property, LuaValue callback) {
                if (bindings.size() >= MAX_BINDINGS) {
                    throw new LuaError("Binding limit exceeded: " + MAX_BINDINGS);
                }
                Binding binding = new Binding(
                    requireElement(element),
                    property.checkjstring(),
                    callback.checkfunction()
                );
                bindings.add(binding);
                evaluate(binding);
                return LuaValue.NIL;
            }
        });
        ui.set("on_tick", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue callback) {
                countCallback();
                tickHandlers.add(callback.checkfunction());
                return LuaValue.NIL;
            }
        });
        ui.set("on_frame", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue callback) {
                countCallback();
                frameHandlers.add(callback.checkfunction());
                return LuaValue.NIL;
            }
        });
        ui.set("after", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue delay, LuaValue callback) {
                countCallback();
                long id = nextTimerId++;
                scheduled.add(new Scheduled(id, elapsed + Math.max(0, delay.checkdouble()), 0, false, callback.checkfunction()));
                return LuaValue.valueOf((double) id);
            }
        });
        ui.set("every", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue interval, LuaValue callback) {
                countCallback();
                double seconds = Math.max(0.001, interval.checkdouble());
                long id = nextTimerId++;
                scheduled.add(new Scheduled(id, elapsed + seconds, seconds, true, callback.checkfunction()));
                return LuaValue.valueOf((double) id);
            }
        });
        ui.set("cancel_timer", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                long id = value.checklong();
                scheduled.stream().filter(item -> item.id == id).forEach(item -> item.cancelled = true);
                return LuaValue.NIL;
            }
        });
        ui.set("action", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String action = args.arg(1).checkjstring();
                if (action.isBlank() || action.length() > 64) {
                    throw new LuaError("Action id must contain 1-64 characters");
                }
                actionSink.send(action, LuaValues.payload(args.arg(2)));
                return LuaValue.NIL;
            }
        });
        ui.set("close", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                host.closeView();
                return LuaValue.NIL;
            }
        });
        ui.set("configure", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                if (!value.istable()) throw new LuaError("ui.configure expects a table");
                host.configure(stringMap(value.checktable()));
                return LuaValue.NIL;
            }
        });
        ui.set("set_interactive", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue value) {
                host.setInteractive(value.checkboolean());
                return LuaValue.NIL;
            }
        });
        ui.set("is_interactive", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(host.interactive());
            }
        });
        ui.set("sound", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                host.playSound(
                    args.arg(1).checkjstring(),
                    (float) args.arg(2).optdouble(1),
                    (float) args.arg(3).optdouble(1)
                );
                return LuaValue.NIL;
            }
        });
        ui.set("widgets", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                List<String> types = new ArrayList<>(Morphe.get().widgetTypes());
                types.addAll(scriptComponents.keySet());
                return LuaValues.toLua(types);
            }
        });
        ui.set("effects", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValues.toLua(Morphe.get().effects().types());
            }
        });
        ui.set("extensions", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("script_modules", Morphe.get().scriptModules().ids());
                result.put("effects", Morphe.get().effects().types());
                result.put("systems", Morphe.get().systemExtensions().ids());
                return LuaValues.toLua(result);
            }
        });
        ui.set("component", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue name, LuaValue factory) {
                String type = org.lytharalab.gfbs.morphe.core.UiStyle.normalize(name.checkjstring());
                if (type.isBlank() || type.length() > 64) {
                    throw new LuaError("Component name must contain 1-64 characters");
                }
                if (Morphe.get().widgets().contains(type) || scriptComponents.containsKey(type)) {
                    throw new LuaError("Component type is already registered: " + type);
                }
                if (scriptComponents.size() >= 256) {
                    throw new LuaError("Script component limit exceeded: 256");
                }
                LuaFunction componentFactory = factory.checkfunction();
                scriptComponents.put(type, componentFactory);
                ui.set(type, createFunction(type));
                return LuaValue.NIL;
            }
        });

        globals.set("ui", ui);
        globals.set("state", reactiveTable(stateBacking));
        installDefaultTheme();
        globals.set("theme", reactiveTable(themeBacking));
        globals.set("data", readOnlyTable(initialData));
        environmentSnapshot = safeEnvironment(host.environment());
        replaceTable(environmentBacking, environmentSnapshot);
        globals.set("env", readOnlyProxy(environmentBacking, "env"));
        installExternalModules();
    }

    private LuaValue createFunction(String fixedType) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int offset = fixedType == null ? 1 : 0;
                String type = fixedType == null ? args.arg(1).checkjstring() : fixedType;
                LuaValue props = args.arg(1 + offset);
                LuaValue children = args.arg(2 + offset);
                LuaFunction componentFactory = scriptComponents.get(
                    org.lytharalab.gfbs.morphe.core.UiStyle.normalize(type)
                );
                if (componentFactory != null) {
                    LuaValue safeProps = props.isnil() ? new LuaTable() : props.checktable();
                    LuaValue safeChildren = children.isnil() ? new LuaTable() : children.checktable();
                    LuaValue result = componentFactory.call(safeProps, safeChildren);
                    requireElement(result);
                    return result;
                }
                UiElement element = createElement(type);
                if (props.istable()) {
                    applyProperties(element, props.checktable());
                } else if (!props.isnil()) {
                    throw new LuaError("Widget properties must be a table");
                }
                if (children.istable()) {
                    addChildren(element, children.checktable());
                } else if (!children.isnil()) {
                    throw new LuaError("Widget children must be an array table");
                }
                return handle(element);
            }
        };
    }

    private UiElement createElement(String type) {
        if (++elementCount > MAX_ELEMENTS) {
            throw new LuaError("Element limit exceeded: " + MAX_ELEMENTS);
        }
        try {
            return Morphe.get().create(type);
        } catch (IllegalArgumentException exception) {
            throw new LuaError(exception.getMessage());
        }
    }

    private void applyProperties(UiElement element, LuaTable properties) {
        List<PropertyEntry> entries = new ArrayList<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = properties.next(key);
            key = next.arg1();
            if (key.isnil()) {
                break;
            }
            entries.add(new PropertyEntry(key.tojstring(), next.arg(2)));
        }
        for (PropertyEntry entry : entries) {
            if (!isDeferredValue(entry.property)) {
                applyProperty(element, entry);
            }
        }
        for (PropertyEntry entry : entries) {
            if (isDeferredValue(entry.property)) {
                applyProperty(element, entry);
            }
        }
    }

    private void applyProperty(UiElement element, PropertyEntry entry) {
        String property = entry.property;
        LuaValue value = entry.value;
        if (property.startsWith("on_") && value.isfunction()) {
            connect(element, property.substring(3), value.checkfunction(), false);
        } else if (property.equals("children") && value.istable()) {
            addChildren(element, value.checktable());
        } else {
            setProperty(element, property, value);
        }
    }

    private static boolean isDeferredValue(String property) {
        String key = org.lytharalab.gfbs.morphe.core.UiStyle.normalize(property);
        return key.equals("value") || key.equals("checked") || key.equals("text");
    }

    private void addChildren(UiElement parent, LuaTable children) {
        for (int index = 1; index <= children.length(); index++) {
            parent.add(requireElement(children.get(index)));
        }
    }

    private LuaTable handle(UiElement element) {
        LuaTable table = new LuaTable();
        table.set(HANDLE_KEY, LuaValue.userdataOf(element));
        table.set("set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                setProperty(element, args.arg(2).checkjstring(), args.arg(3));
                return args.arg1();
            }
        });
        table.set("get", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue property) {
                return LuaValues.toLua(element.getProperty(property.checkjstring()));
            }
        });
        table.set("add", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue child) {
                element.add(requireElement(child));
                return self;
            }
        });
        table.set("remove", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue child) {
                element.remove(requireElement(child));
                return self;
            }
        });
        table.set("clear", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue self) {
                element.clear();
                return self;
            }
        });
        table.set("destroy", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue self) {
                element.destroy();
                return LuaValue.NIL;
            }
        });
        table.set("find", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue id) {
                UiElement found = element.find(id.checkjstring());
                return found == null ? LuaValue.NIL : handle(found);
            }
        });
        table.set("on", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String event = args.arg(2).checkjstring();
                LuaFunction callback = args.arg(3).checkfunction();
                boolean capture = args.arg(4).optboolean(false);
                connect(element, event, callback, capture);
                return args.arg1();
            }
        });
        table.set("animate", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Map<String, Object> targets = stringMap(args.arg(2).checktable());
                LuaValue third = args.arg(3);
                if (third.istable()) {
                    element.animator().animate(targets, animationSpec(third.checktable()));
                } else {
                    element.animator().animate(
                        targets,
                        third.optdouble(0.2),
                        UiAnimator.Easing.parse(args.arg(4).optjstring("ease_out"))
                    );
                }
                return args.arg1();
            }
        });
        table.set("keyframes", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                scheduleKeyframes(element, args.arg(2).checktable(), args.arg(3));
                return args.arg1();
            }
        });
        table.set("stop_animation", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.arg(2).isnil()) {
                    element.animator().cancelAll();
                    scheduledFrames.removeIf(item -> item.owner == element);
                } else {
                    String property = org.lytharalab.gfbs.morphe.core.UiStyle.normalize(
                        args.arg(2).checkjstring()
                    );
                    element.animator().cancel(property);
                    scheduledFrames.removeIf(item ->
                        item.owner == element && item.properties.contains(property));
                }
                return args.arg1();
            }
        });
        table.set("finish_animations", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue self) {
                scheduledFrames.removeIf(item -> item.owner == element);
                element.animator().finishAll();
                return self;
            }
        });
        table.set("pause_animations", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                element.animator().paused(args.arg(2).optboolean(true));
                return args.arg1();
            }
        });
        table.set("effect", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String id = args.arg(2).checkjstring();
                Map<String, Object> options = args.arg(3).istable()
                    ? stringMap(args.arg(3).checktable())
                    : Map.of();
                try {
                    element.addEffect(id, Morphe.get().effects().create(id, options));
                } catch (RuntimeException exception) {
                    throw new LuaError("Failed to attach UI effect " + id + ": " + exception.getMessage());
                }
                return args.arg1();
            }
        });
        table.set("remove_effect", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue id) {
                return LuaValue.valueOf(element.removeEffect(id.checkjstring()));
            }
        });
        table.set("clear_effects", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue self) {
                element.clearEffects();
                return self;
            }
        });

        LuaTable metatable = new LuaTable();
        metatable.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue property) {
                return LuaValues.toLua(element.getProperty(property.checkjstring()));
            }
        });
        metatable.set("__newindex", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue property, LuaValue value) {
                setProperty(element, property.checkjstring(), value);
                return LuaValue.NIL;
            }
        });
        table.setmetatable(metatable);
        return table;
    }

    private void connect(UiElement element, String event, LuaFunction callback, boolean capture) {
        countCallback();
        element.on(event, capture, uiEvent -> callSafely(callback, eventTable(uiEvent)));
    }

    private LuaTable eventTable(UiEvent event) {
        LuaTable table = (LuaTable) LuaValues.toLua(event.data());
        table.set("type", event.type());
        table.set("target", handle(event.target()));
        table.set("current_target", handle(event.currentTarget()));
        table.set("phase", event.phase().name().toLowerCase(Locale.ROOT));
        table.set("stop", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                event.stopPropagation();
                return LuaValue.NIL;
            }
        });
        table.set("prevent", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                event.preventDefault();
                return LuaValue.NIL;
            }
        });
        return table;
    }

    private LuaTable reactiveTable(LuaTable backing) {
        LuaTable exposed = new LuaTable();
        LuaTable metatable = new LuaTable();
        metatable.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue key) {
                return backing.get(key);
            }
        });
        metatable.set("__newindex", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue key, LuaValue value) {
                backing.set(key, value);
                stateDirty = true;
                return LuaValue.NIL;
            }
        });
        exposed.setmetatable(metatable);
        return exposed;
    }

    private void installExternalModules() {
        LuaTable rootBacking = new LuaTable();
        Map<String, LuaTable> nodes = new LinkedHashMap<>();
        Set<String> modulePaths = new java.util.LinkedHashSet<>();
        nodes.put("", rootBacking);

        for (MorpheScriptModule module : Morphe.get().scriptModules().snapshot()) {
            List<String> segments = new ArrayList<>();
            segments.add(MorpheScriptModule.normalizeEntry(module.id().getNamespace()));
            for (String segment : module.id().getPath().split("/")) {
                if (!segment.isBlank()) {
                    segments.add(MorpheScriptModule.normalizeEntry(segment));
                }
            }

            StringBuilder path = new StringBuilder();
            LuaTable moduleBacking = rootBacking;
            for (String segment : segments) {
                if (path.length() > 0) path.append('.');
                path.append(segment);
                String nodePath = path.toString();
                LuaTable parent = moduleBacking;
                LuaTable existing = nodes.get(nodePath);
                if (existing == null) {
                    if (!parent.get(segment).isnil()) {
                        throw new LuaError(
                            "External module path collides with an entry: " + nodePath
                        );
                    }
                    LuaTable created = new LuaTable();
                    parent.set(segment, readOnlyProxy(created, "ext." + nodePath));
                    nodes.put(nodePath, created);
                    moduleBacking = created;
                } else {
                    moduleBacking = existing;
                }
            }
            String qualifiedPath = path.toString();
            if (!modulePaths.add(qualifiedPath)) {
                throw new LuaError(
                    "External module path collision after normalization: " + module.id()
                );
            }

            for (Map.Entry<String, Object> entry : module.constants().entrySet()) {
                if (nodes.containsKey(qualifiedPath + "." + entry.getKey())) {
                    throw new LuaError(
                        "External module entry collides with a child module: "
                            + qualifiedPath + "." + entry.getKey()
                    );
                }
                moduleBacking.set(entry.getKey(), externalValue(entry.getValue(), 0));
            }
            for (Map.Entry<String, MorpheScriptModule.Function> entry : module.functions().entrySet()) {
                if (nodes.containsKey(qualifiedPath + "." + entry.getKey())) {
                    throw new LuaError(
                        "External module entry collides with a child module: "
                            + qualifiedPath + "." + entry.getKey()
                    );
                }
                String qualifiedName = module.id() + "." + entry.getKey();
                MorpheScriptModule.Function function = entry.getValue();
                moduleBacking.set(entry.getKey(), new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs args) {
                        if (args.narg() > 64) {
                            throw new LuaError("External API accepts at most 64 arguments");
                        }
                        List<Object> arguments = new ArrayList<>(args.narg());
                        for (int index = 1; index <= args.narg(); index++) {
                            arguments.add(externalArgument(args.arg(index)));
                        }
                        try {
                            Object result = function.invoke(
                                scriptContext(MorpheScriptModule.Phase.CALL, 0),
                                java.util.Collections.unmodifiableList(
                                    new ArrayList<>(arguments)
                                )
                            );
                            return externalValue(result, 0);
                        } catch (LuaError exception) {
                            throw exception;
                        } catch (Exception exception) {
                            throw new LuaError(
                                "External API " + qualifiedName + " failed: " + exception.getMessage()
                            );
                        }
                    }
                });
            }
            for (Map.Entry<String, MorpheScriptModule.DynamicValue> entry
                : module.variables().entrySet()) {
                if (nodes.containsKey(qualifiedPath + "." + entry.getKey())) {
                    throw new LuaError(
                        "External module entry collides with a child module: "
                            + qualifiedPath + "." + entry.getKey()
                    );
                }
                externalVariables.add(new ExternalVariableBinding(
                    module.id() + "." + entry.getKey(),
                    moduleBacking,
                    entry.getKey(),
                    entry.getValue()
                ));
            }
        }

        globals.set("ext", readOnlyProxy(rootBacking, "ext"));
        refreshExternalVariables(null, MorpheScriptModule.Phase.INITIAL, 0);
    }

    private void refreshExternalVariables(
        MorpheScriptModule.UpdateRate updateRate,
        MorpheScriptModule.Phase phase,
        double deltaSeconds
    ) {
        MorpheScriptModule.Context context = scriptContext(phase, deltaSeconds);
        for (ExternalVariableBinding binding : externalVariables) {
            if (updateRate != null && binding.value.updateRate() != updateRate) {
                continue;
            }
            try {
                Object value = binding.value.provider().get(context);
                binding.backing.set(binding.name, externalValue(value, 0));
                binding.failed = false;
                stateDirty = true;
            } catch (Exception exception) {
                binding.backing.set(binding.name, LuaValue.NIL);
                if (!binding.failed) {
                    LOGGER.warn(
                        "External variable {} failed and was replaced with nil",
                        binding.qualifiedName,
                        exception
                    );
                }
                binding.failed = true;
            }
        }
    }

    private MorpheScriptModule.Context scriptContext(
        MorpheScriptModule.Phase phase,
        double deltaSeconds
    ) {
        return new MorpheScriptModule.Context(
            document,
            environmentSnapshot,
            phase,
            Math.max(0, deltaSeconds),
            elapsed,
            frameElapsed
        );
    }

    private Object externalArgument(LuaValue value) {
        return externalArgument(value, 0);
    }

    private Object externalArgument(LuaValue value, int depth) {
        if (depth >= 16) {
            throw new IllegalArgumentException("External argument nesting exceeds 16 levels");
        }
        if (value.isnil()) {
            return null;
        }
        if (value.isboolean()) {
            return value.toboolean();
        }
        if (value.isnumber()) {
            return value.todouble();
        }
        if (value.isstring()) {
            return value.tojstring();
        }
        if (value.isuserdata()) {
            return value.touserdata();
        }
        if (value.istable()) {
            LuaTable exposed = value.checktable();
            LuaTable table = readOnlyBackings.getOrDefault(exposed, exposed);
            LuaValue handle = table.rawget(LuaValue.valueOf(HANDLE_KEY));
            if (handle.isuserdata(UiElement.class)) {
                return handle.touserdata(UiElement.class);
            }
            int length = table.length();
            boolean array = length > 0;
            int entries = 0;
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = table.next(key);
                key = next.arg1();
                if (key.isnil()) {
                    break;
                }
                if (++entries > 256) {
                    throw new IllegalArgumentException(
                        "External argument table exceeds 256 entries"
                    );
                }
                if (!key.isint() || key.toint() < 1 || key.toint() > length) {
                    array = false;
                }
            }
            if (array) {
                List<Object> result = new ArrayList<>(length);
                for (int index = 1; index <= length; index++) {
                    result.add(externalArgument(table.get(index), depth + 1));
                }
                return result;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            key = LuaValue.NIL;
            while (true) {
                Varargs next = table.next(key);
                key = next.arg1();
                if (key.isnil()) {
                    break;
                }
                result.put(
                    key.tojstring(),
                    externalArgument(next.arg(2), depth + 1)
                );
            }
            return result;
        }
        return value.tojstring();
    }

    private LuaValue externalValue(Object value, int depth) {
        if (depth >= 16) {
            throw new IllegalArgumentException("External value nesting exceeds 16 levels");
        }
        if (value instanceof LuaValue) {
            throw new IllegalArgumentException(
                "External modules may not expose LuaJ values directly"
            );
        }
        if (value instanceof UiElement element) {
            return handle(element);
        }
        if (value instanceof Map<?, ?> map) {
            LuaTable nested = new LuaTable();
            if (map.size() > 256) {
                throw new IllegalArgumentException("External map exceeds 256 entries");
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                nested.set(
                    String.valueOf(entry.getKey()),
                    externalValue(entry.getValue(), depth + 1)
                );
            }
            return readOnlyProxy(nested, "ext");
        }
        if (value instanceof Iterable<?> iterable) {
            LuaTable nested = new LuaTable();
            int index = 1;
            for (Object item : iterable) {
                if (index > 256) {
                    throw new IllegalArgumentException("External list exceeds 256 entries");
                }
                nested.set(index++, externalValue(item, depth + 1));
            }
            return readOnlyProxy(nested, "ext");
        }
        return LuaValues.toLua(value);
    }

    private static Map<String, ?> safeEnvironment(Map<String, ?> environment) {
        return environment == null ? Map.of() : environment;
    }

    private void installDefaultTheme() {
        themeBacking.set("background", LuaValue.userdataOf(UiColor.parse("#FF111720")));
        themeBacking.set("surface", LuaValue.userdataOf(UiColor.parse("#FF1B2430")));
        themeBacking.set("border", LuaValue.userdataOf(UiColor.parse("#FF3A4658")));
        themeBacking.set("text", LuaValue.userdataOf(UiColor.WHITE));
        themeBacking.set("muted", LuaValue.userdataOf(UiColor.parse("#FF93A1B3")));
        themeBacking.set("accent", LuaValue.userdataOf(UiColor.parse("#FF26C6DA")));
        themeBacking.set("danger", LuaValue.userdataOf(UiColor.parse("#FFFF5D68")));
    }

    private LuaTable readOnlyTable(Map<String, ?> values) {
        LuaTable backing = (LuaTable) LuaValues.toLua(values);
        return readOnlyProxy(backing, "data");
    }

    private LuaTable readOnlyProxy(LuaTable backing, String name) {
        LuaTable exposed = new LuaTable();
        readOnlyBackings.put(exposed, backing);
        LuaTable metatable = new LuaTable();
        metatable.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue key) {
                return backing.get(key);
            }
        });
        metatable.set("__newindex", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue key, LuaValue value) {
                throw new LuaError(name + " is read-only");
            }
        });
        metatable.set("__len", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue table) {
                return LuaValue.valueOf(backing.length());
            }
        });
        exposed.setmetatable(metatable);
        return exposed;
    }

    private void installReadOnlyIterators() {
        LuaValue normalPairs = globals.get("pairs");
        LuaValue normalIpairs = globals.get("ipairs");
        globals.set("pairs", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaTable exposed = args.arg1().checktable();
                LuaTable backing = readOnlyBackings.get(exposed);
                if (backing == null) {
                    return normalPairs.invoke(args);
                }
                LuaValue iterator = new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs iteratorArgs) {
                        return backing.next(iteratorArgs.arg(2));
                    }
                };
                return LuaValue.varargsOf(new LuaValue[] {
                    iterator,
                    exposed,
                    LuaValue.NIL
                });
            }
        });
        globals.set("ipairs", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaTable exposed = args.arg1().checktable();
                LuaTable backing = readOnlyBackings.get(exposed);
                if (backing == null) {
                    return normalIpairs.invoke(args);
                }
                LuaValue iterator = new VarArgFunction() {
                    @Override
                    public Varargs invoke(Varargs iteratorArgs) {
                        int index = iteratorArgs.arg(2).optint(0) + 1;
                        LuaValue next = backing.get(index);
                        return next.isnil()
                            ? LuaValue.NIL
                            : LuaValue.varargsOf(new LuaValue[] {
                                LuaValue.valueOf(index),
                                next
                            });
                    }
                };
                return LuaValue.varargsOf(new LuaValue[] {
                    iterator,
                    exposed,
                    LuaValue.ZERO
                });
            }
        });
    }

    private UiAnimator.Spec animationSpec(LuaTable options) {
        double duration = options.get("duration").optdouble(0.2);
        double delay = options.get("delay").optdouble(0);
        UiAnimator.Easing easing = UiAnimator.Easing.parse(options.get("easing").optjstring("ease_out"));
        int repeat = options.get("repeat").optint(0);
        boolean yoyo = options.get("yoyo").optboolean(false);
        LuaValue completion = options.get("on_complete");
        Runnable onComplete = null;
        if (completion.isfunction()) {
            countCallback();
            LuaFunction callback = completion.checkfunction();
            onComplete = () -> callSafely(callback);
        }
        return new UiAnimator.Spec(duration, delay, easing, repeat, yoyo, onComplete);
    }

    private void scheduleKeyframes(UiElement element, LuaTable frames, LuaValue rawOptions) {
        int length = frames.length();
        if (length < 1 || length > 256) {
            throw new LuaError("keyframes expects 1..256 frame tables");
        }
        LuaTable options = rawOptions.istable() ? rawOptions.checktable() : new LuaTable();
        double duration = Math.max(0, options.get("duration").optdouble(1));
        String defaultEasing = options.get("easing").optjstring("ease_out");
        LuaTable first = frames.get(1).checktable();
        Map<String, Object> initial = keyframeProperties(first);
        initial.forEach(element::setProperty);

        LuaValue completion = options.get("on_complete");
        LuaFunction completionCallback = null;
        if (completion.isfunction()) {
            countCallback();
            completionCallback = completion.checkfunction();
        }
        final LuaFunction finalCompletion = completionCallback;

        for (int index = 2; index <= length; index++) {
            LuaTable previous = frames.get(index - 1).checktable();
            LuaTable next = frames.get(index).checktable();
            double previousAt = clamp01(previous.get("at").optdouble((index - 2.0) / Math.max(1, length - 1)));
            double nextAt = clamp01(next.get("at").optdouble((index - 1.0) / Math.max(1, length - 1)));
            double segmentDuration = Math.max(0, (nextAt - previousAt) * duration);
            Map<String, Object> targets = keyframeProperties(next);
            UiAnimator.Easing easing = UiAnimator.Easing.parse(next.get("easing").optjstring(defaultEasing));
            boolean completesTimeline = index == length
                && nextAt >= 1.0 - 1.0E-9
                && !targets.isEmpty();
            LuaFunction action = new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    Runnable onComplete = completesTimeline && finalCompletion != null
                        ? () -> callSafely(finalCompletion)
                        : null;
                    element.animator().animate(targets, new UiAnimator.Spec(
                        segmentDuration,
                        0,
                        easing,
                        0,
                        false,
                        onComplete
                    ));
                    return LuaValue.NIL;
                }
            };
            long id = nextTimerId++;
            scheduledFrames.add(new Scheduled(
                id,
                frameElapsed + previousAt * duration,
                0,
                false,
                action,
                element,
                Set.copyOf(targets.keySet())
            ));
        }

        LuaTable last = frames.get(length).checktable();
        double lastAt = clamp01(last.get("at").optdouble(1));
        boolean completionHandledByTween = length > 1
            && lastAt >= 1.0 - 1.0E-9
            && !keyframeProperties(last).isEmpty();
        if (finalCompletion != null && !completionHandledByTween) {
            long id = nextTimerId++;
            scheduledFrames.add(new Scheduled(
                id,
                frameElapsed + duration,
                0,
                false,
                finalCompletion,
                element,
                Set.of()
            ));
        }
    }

    private Map<String, Object> keyframeProperties(LuaTable frame) {
        LuaValue props = frame.get("props");
        if (props.istable()) {
            return stringMap(props.checktable());
        }
        Map<String, Object> result = stringMap(frame);
        result.remove("at");
        result.remove("easing");
        return result;
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private void replaceTable(LuaTable backing, Map<String, ?> values) {
        List<LuaValue> keys = new ArrayList<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = backing.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            keys.add(key);
        }
        keys.forEach(entry -> backing.set(entry, LuaValue.NIL));
        Map<String, ?> safeValues = values == null ? Map.of() : values;
        for (Map.Entry<String, ?> entry : safeValues.entrySet()) {
            backing.set(entry.getKey(), environmentValue(entry.getValue(), 0));
        }
    }

    private LuaValue environmentValue(Object value, int depth) {
        if (depth >= 16) {
            throw new IllegalArgumentException("Environment nesting exceeds 16 levels");
        }
        if (value instanceof Map<?, ?> map) {
            LuaTable nested = new LuaTable();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                nested.set(String.valueOf(entry.getKey()), environmentValue(entry.getValue(), depth + 1));
            }
            return readOnlyProxy(nested, "env");
        }
        if (value instanceof Iterable<?> iterable) {
            LuaTable nested = new LuaTable();
            int index = 1;
            for (Object item : iterable) {
                nested.set(index++, environmentValue(item, depth + 1));
            }
            return readOnlyProxy(nested, "env");
        }
        return LuaValues.toLua(value);
    }

    private LuaValue colorFunction() {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.arg(1).isstring()) {
                    return LuaValue.userdataOf(UiColor.parse(args.arg(1).checkjstring()));
                }
                double r = args.arg(1).checkdouble();
                double g = args.arg(2).checkdouble();
                double b = args.arg(3).checkdouble();
                LuaValue alphaValue = args.arg(4);
                double providedAlpha = alphaValue.optdouble(1.0);
                boolean normalized = Math.max(r, Math.max(g, b)) <= 1.0
                    && (alphaValue.isnil() || providedAlpha <= 1.0);
                double a = alphaValue.optdouble(normalized ? 1.0 : 255.0);
                double scale = normalized ? 255.0 : 1.0;
                return LuaValue.userdataOf(UiColor.rgba(
                    (int) Math.round(r * scale),
                    (int) Math.round(g * scale),
                    (int) Math.round(b * scale),
                    (int) Math.round(a * scale)
                ));
            }
        };
    }

    private void setProperty(UiElement element, String property, LuaValue value) {
        try {
            element.setProperty(property, LuaValues.toJava(value));
        } catch (RuntimeException exception) {
            throw new LuaError("Invalid " + element.type() + "." + property + ": " + exception.getMessage());
        }
    }

    private UiElement requireElement(LuaValue value) {
        if (!value.istable()) {
            throw new LuaError("Expected a Morphe element");
        }
        LuaValue handle = value.checktable().rawget(LuaValue.valueOf(HANDLE_KEY));
        if (!handle.isuserdata(UiElement.class)) {
            throw new LuaError("Expected a Morphe element");
        }
        return (UiElement) handle.touserdata(UiElement.class);
    }

    private Map<String, Object> stringMap(LuaTable table) {
        Map<String, Object> result = new LinkedHashMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) {
                break;
            }
            result.put(key.checkjstring(), LuaValues.toJava(next.arg(2)));
        }
        return result;
    }

    private void evaluateBindings() {
        stateDirty = false;
        for (Binding binding : List.copyOf(bindings)) {
            if (!binding.element.destroyed()) {
                evaluate(binding);
            }
        }
    }

    private void evaluate(Binding binding) {
        try {
            LuaValue result = binding.callback.call();
            setProperty(binding.element, binding.property, result);
        } catch (RuntimeException exception) {
            fail("Binding failed for " + binding.element.id() + "." + binding.property, exception);
        }
    }

    private void callSafely(LuaFunction function, LuaValue... args) {
        try {
            function.invoke(LuaValue.varargsOf(args));
        } catch (RuntimeException exception) {
            fail("Lua callback failed: " + exception.getMessage(), exception);
        }
    }

    private void countCallback() {
        if (++callbackCount > MAX_CALLBACKS) {
            throw new LuaError("Callback limit exceeded: " + MAX_CALLBACKS);
        }
    }

    private void fail(String message, Throwable throwable) {
        error = message;
        if (throwable == null) {
            LOGGER.error("[Morphe Lua] {}", message);
        } else {
            LOGGER.error("[Morphe Lua] {}", message, throwable);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Lua runtime is closed");
        }
    }

    private record Binding(UiElement element, String property, LuaFunction callback) {
    }

    private record PropertyEntry(String property, LuaValue value) {
    }

    private static final class Scheduled {
        private final long id;
        private double deadline;
        private final double interval;
        private final boolean repeating;
        private final LuaFunction callback;
        private final UiElement owner;
        private final Set<String> properties;
        private boolean cancelled;

        private Scheduled(long id, double deadline, double interval, boolean repeating, LuaFunction callback) {
            this(id, deadline, interval, repeating, callback, null, Set.of());
        }

        private Scheduled(
            long id,
            double deadline,
            double interval,
            boolean repeating,
            LuaFunction callback,
            UiElement owner,
            Set<String> properties
        ) {
            this.id = id;
            this.deadline = deadline;
            this.interval = interval;
            this.repeating = repeating;
            this.callback = callback;
            this.owner = owner;
            this.properties = properties;
        }
    }

    private static final class ExternalVariableBinding {
        private final String qualifiedName;
        private final LuaTable backing;
        private final String name;
        private final MorpheScriptModule.DynamicValue value;
        private boolean failed;

        private ExternalVariableBinding(
            String qualifiedName,
            LuaTable backing,
            String name,
            MorpheScriptModule.DynamicValue value
        ) {
            this.qualifiedName = qualifiedName;
            this.backing = backing;
            this.name = name;
            this.value = value;
        }
    }
}
