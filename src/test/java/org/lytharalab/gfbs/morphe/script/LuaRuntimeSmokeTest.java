package org.lytharalab.gfbs.morphe.script;

import net.minecraft.resources.ResourceLocation;
import org.lytharalab.gfbs.morphe.api.Morphe;
import org.lytharalab.gfbs.morphe.api.MorpheAPI;
import org.lytharalab.gfbs.morphe.api.MorpheScriptModule;
import org.lytharalab.gfbs.morphe.api.UiEffect;
import org.lytharalab.gfbs.morphe.core.UiDocument;
import org.lytharalab.gfbs.morphe.core.UiElement;
import org.lytharalab.gfbs.morphe.core.UiHost;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Boots the real LuaJ runtime and covers both the allowed standard libraries
 * and the deep read-only environment boundary.
 */
public final class LuaRuntimeSmokeTest {
    private LuaRuntimeSmokeTest() {
    }

    public static void main(String[] args) {
        Morphe.get().initialize();
        AtomicInteger effectAttach = new AtomicInteger();
        AtomicInteger effectFrames = new AtomicInteger();
        AtomicInteger effectDetach = new AtomicInteger();
        MorpheAPI.registerScriptModule(MorpheScriptModule.builder(
                new ResourceLocation("smoke", "reactor/control")
            )
            .constant("max_output", 5000)
            .constant("profile", Map.of("name", "RX-1", "ports", 4))
            .constant("modes", java.util.List.of("idle", "active"))
            .frameVariable("health", context ->
                ((Number) ((Map<?, ?>) context.environment().get("player")).get("health"))
                    .intValue()
            )
            .tickVariable("logic_time", MorpheScriptModule.Context::logicSeconds)
            .function("sum", (context, arguments) ->
                ((Number) arguments.get(0)).doubleValue()
                    + ((Number) arguments.get(1)).doubleValue()
            )
            .function("map_size", (context, arguments) ->
                ((Map<?, ?>) arguments.get(0)).size()
            )
            .function("is_nil", (context, arguments) -> arguments.get(0) == null)
            .build());
        MorpheAPI.registerEffect(new ResourceLocation("smoke", "pulse"), options ->
            new UiEffect() {
                @Override
                public void onAttach(UiElement element) {
                    effectAttach.incrementAndGet();
                }

                @Override
                public void onFrame(UiElement element, double deltaSeconds) {
                    effectFrames.incrementAndGet();
                }

                @Override
                public void onDetach(UiElement element) {
                    effectDetach.incrementAndGet();
                }
            });
        try (UiDocument document = new UiDocument()) {
            MorpheLuaRuntime runtime = new MorpheLuaRuntime(
                document,
                Map.of("answer", 42),
                UiActionSink.NOOP,
                new UiHost() {
                    @Override
                    public Map<String, ?> environment() {
                        return Map.of("player", Map.of("health", 20));
                    }
                }
            );
            document.runtime(runtime);
            runtime.execute("""
                assert(bit32.band(0xF0, 0x3C) == 0x30)
                assert(table.concat({"m", "o", "r", "p", "h", "e"}) == "morphe")
                assert(string.upper("lua") == "LUA")
                assert(math.floor(3.9) == 3)
                assert(data.answer == 42)
                assert(package == nil and require == nil)
                assert(loadfile == nil and dofile == nil)
                assert(rawget == nil and rawset == nil)
                assert(getmetatable == nil and setmetatable == nil)
                assert(io == nil and os == nil and debug == nil and luajava == nil)
                assert(env.player.health == 20)
                assert(ext.smoke.reactor.control.max_output == 5000)
                assert(ext.smoke.reactor.control.health == 20)
                assert(ext.smoke.reactor.control.sum(2, 3) == 5)
                assert(ext.smoke.reactor.control.map_size(
                    ext.smoke.reactor.control.profile) == 2)
                assert(ext.smoke.reactor.control.is_nil(nil))
                assert(#ext.smoke.reactor.control.modes == 2)
                local mode_count = 0
                for _, mode in ipairs(ext.smoke.reactor.control.modes) do
                    assert(mode == "idle" or mode == "active")
                    mode_count = mode_count + 1
                end
                assert(mode_count == 2)
                local profile_count = 0
                for key, value in pairs(ext.smoke.reactor.control.profile) do
                    assert(key ~= nil and value ~= nil)
                    profile_count = profile_count + 1
                end
                assert(profile_count == 2)
                local module_writable = pcall(function()
                    ext.smoke.reactor.control.max_output = 0
                end)
                assert(module_writable == false)
                local writable = pcall(function()
                    env.player.health = 0
                end)
                assert(writable == false)
                ui.mount(ui.text({text = "sandbox ok"})
                    :effect("smoke:pulse", {speed = 2}))
                """, "morphe_runtime_smoke");

            document.frame(1.0 / 60.0);
            document.tick(0.05);
            if (runtime.error() != null) {
                throw new AssertionError(runtime.error());
            }
            if (document.root().children().size() != 1) {
                throw new AssertionError("Lua UI construction failed");
            }
            if (effectAttach.get() != 1 || effectFrames.get() != 1) {
                throw new AssertionError("Lua effect integration failed");
            }
        }
        if (effectDetach.get() != 1) {
            throw new AssertionError("Lua effect detach failed");
        }
        System.out.println("GFBS Morphe Lua runtime smoke tests passed.");
    }
}
