package org.lytharalab.gfbs.morphe.script;

import org.lytharalab.gfbs.morphe.api.Morphe;
import org.lytharalab.gfbs.morphe.core.UiDocument;

import java.util.Map;

/**
 * Starts the real LuaJ runtime and verifies both the allowed libraries and the
 * sandbox boundary. This catches library-installation failures before launch.
 */
public final class LuaSandboxSmokeTest {
    private LuaSandboxSmokeTest() {
    }

    public static void main(String[] args) {
        Morphe.get().initialize();
        try (UiDocument document = new UiDocument()) {
            MorpheLuaRuntime runtime = new MorpheLuaRuntime(
                document,
                Map.of("answer", 42),
                UiActionSink.NOOP,
                () -> { }
            );
            document.runtime(runtime);
            runtime.execute("""
                assert(bit32.band(0xF0, 0x3C) == 0x30)
                assert(table.concat({"m", "o", "r", "p", "h", "e"}) == "morphe")
                assert(string.upper("lua") == "LUA")
                assert(math.floor(3.9) == 3)
                assert(data.answer == 42)
                assert(package == nil)
                assert(require == nil)
                assert(loadfile == nil)
                assert(dofile == nil)
                assert(io == nil and os == nil and debug == nil and luajava == nil)
                ui.mount(ui.text({text = "sandbox ok"}))
                """, "morphe_sandbox_smoke");

            if (runtime.error() != null) {
                throw new AssertionError(runtime.error());
            }
            if (document.root().children().size() != 1) {
                throw new AssertionError("Lua UI construction failed");
            }
        }
        System.out.println("GFBS Morphe Lua sandbox smoke tests passed.");
    }
}
