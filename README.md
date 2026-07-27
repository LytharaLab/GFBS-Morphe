# GFBS: Morphe

A safe, extensible, and script-driven UI runtime for Minecraft Forge 1.20.1.

GFBS: Morphe lets resource packs and mods define interfaces with sandboxed Lua scripts while Java handles registration, rendering integration, networking, and server-authoritative actions. It supports full screens, transparent screens, passive HUDs, and interactive overlay HUDs without requiring a dedicated Minecraft `Screen` class for every interface.

## Repository

- [Source code](https://github.com/LytharaLab/GFBS-Morphe)
- [Releases](https://github.com/LytharaLab/GFBS-Morphe/releases)
- [Issue tracker](https://github.com/LytharaLab/GFBS-Morphe/issues)
- [Pull requests](https://github.com/LytharaLab/GFBS-Morphe/pulls)

## Status and compatibility

| Component | Version |
| --- | --- |
| GFBS: Morphe | `1.0.0` |
| Script API | `1` |
| Minecraft | `1.20.1` |
| Minecraft Forge | `47.4.16` |
| Java | `17` |
| Lua runtime | LuaJ `3.0.1` |

## Features

- Lua-defined UI trees loaded from namespaced client resources.
- Pixel, percentage, and automatic sizing.
- Absolute, row, column, and grid layouts with padding, margin, alignment, gaps, flex growth, and scrolling.
- Built-in `panel`, `text`, `button`, `image`, `video`, `item`, `checkbox`, `slider`, `progress`, `input`, and `scroll` widgets.
- Capture, target, and bubble input propagation with focus, pointer capture, keyboard input, and default widget behavior.
- Reactive state, themes, bindings, reusable Lua components, timers, frame callbacks, keyframes, and property animations.
- Modal screens, transparent screens, passive HUDs, and cursor-enabled interactive HUDs.
- Configurable HUD ordering and behavior when another Minecraft screen is open.
- Frame-time-driven animation, video playback, cursor blinking, and environment bindings independent of the 20 TPS game tick.
- Live player, camera, movement, world, input, window, sway, and bob values exposed to Lua.
- Image stretch, contain, cover, UV-region, frame-sequence, and sprite-sheet rendering.
- Configurable click, hover, and focus sounds.
- Minecraft item-stack rendering.
- Server-opened screens and HUD sessions with document validation, session IDs, payload limits, and action rate limiting.
- Extension APIs for widgets, effects, script modules, dynamic variables, functions, and document-level systems.
- A backend-neutral `UiCanvas` API with explicit access to Minecraft `GuiGraphics` when required.

## Installation

1. Install Minecraft Forge for Minecraft 1.20.1.
2. Download a GFBS: Morphe JAR from [GitHub Releases](https://github.com/LytharaLab/GFBS-Morphe/releases), or build the project from source.
3. Place the JAR in the Minecraft `mods` directory on every required client and server.

Mods that use GFBS: Morphe as a library should declare it as a dependency and package their Lua documents under their own asset namespace.

## Building from source

Clone the official repository and run:

```bash
git clone https://github.com/LytharaLab/GFBS-Morphe.git
cd GFBS-Morphe
./gradlew build
```

On Windows PowerShell:

```powershell
git clone https://github.com/LytharaLab/GFBS-Morphe.git
Set-Location GFBS-Morphe
.\gradlew.bat build
```

The built JAR is written to `build/libs/`.

Run the full verification suite with:

```bash
./gradlew check
```

The project also provides focused smoke-test tasks:

```bash
./gradlew coreSmoke
./gradlew luaSmoke
```

## Quick start

Start a development client and open the included showcase:

```text
/morpheui open gfbs_morphe:ui/showcase.lua
```

The script is stored at:

```text
src/main/resources/assets/gfbs_morphe/ui/showcase.lua
```

A minimal Lua document looks like this:

```lua
ui.root()
    :set("layout", "column")
    :set("align", "center")
    :set("justify", "center")

local count = 0
local label = ui.text({
    width = 180,
    height = 20,
    text = "Count: 0",
    text_align = "center",
    vertical_align = "center"
})

local button = ui.button({
    width = 100,
    height = 24,
    text = "COUNT",
    on_click = function()
        count = count + 1
        label.text = "Count: " .. count
    end
})

ui.mount(ui.panel({
    width = 220,
    height = 90,
    layout = "column",
    align = "center",
    justify = "center",
    gap = 8,
    background = "#E818202B",
    radius = 5
}, {
    label,
    button
}))
```

A document ID maps directly to a client resource. For example:

```text
example:ui/reactor.lua
```

maps to:

```text
assets/example/ui/reactor.lua
```

## Java integration

### Open a client screen

```java
MorpheClient.open(new ResourceLocation("example", "ui/reactor.lua"));
```

### Show a passive HUD

```java
MorpheClient.showHud(
    new ResourceLocation("example", "reactor_status"),
    new ResourceLocation("example", "ui/reactor_hud.lua"),
    initialData
);
```

Passive HUDs do not release the mouse or block player movement. To create a cursor-enabled HUD and enter interaction mode:

```java
ResourceLocation layerId = new ResourceLocation("example", "reactor_status");

MorpheClient.showHud(
    layerId,
    new ResourceLocation("example", "ui/reactor_hud.lua"),
    initialData,
    MorpheViewOptions.interactiveHud()
);
MorpheClient.setHudInteractive(layerId, true);
```

### Open a server-authoritative screen

```java
CompoundTag data = new CompoundTag();
data.putInt("output", 63);

MorpheServer.open(
    player,
    new ResourceLocation("example", "ui/reactor.lua"),
    data
);
```

Lua can send a named action:

```lua
ui.action("set_output", { value = state.output })
```

Register a server-side handler and validate all client-provided values before changing game state:

```java
MorpheServer.registerActionHandler(
    new ResourceLocation("example", "ui/reactor.lua"),
    (player, action) -> {
        if (action.action().equals("set_output")) {
            int value = action.payload().getInt("value");
            // Validate the value and apply the authoritative server-side change.
        }
    }
);
```

Every accepted action is also published as a `MorpheUiActionEvent` on the Forge event bus.

## Extension API

Register a namespaced widget:

```java
MorpheAPI.registerWidget(
    new ResourceLocation("example", "reactor_gauge"),
    ReactorGaugeElement::new
);
```

Use it from Lua with its full ID:

```lua
local gauge = ui.create("example:reactor_gauge", {
    value = 0.72,
    width = 120,
    height = 120
})
```

Register a read-only external script module:

```java
MorpheAPI.registerScriptModule(
    MorpheScriptModule.builder(new ResourceLocation("example", "reactor"))
        .frameVariable("temperature", context -> readTemperature())
        .function("clamp", (context, args) ->
            Math.max(0, Math.min(100, ((Number) args.get(0)).doubleValue())))
        .build()
);
```

```lua
print(ext.example.reactor.temperature)
local output = ext.example.reactor.clamp(120)
```

The public extension entry point is [`MorpheAPI`](src/main/java/org/lytharalab/gfbs/morphe/api/MorpheAPI.java). The included showcase scripts provide working examples of the current Lua API.

## Commands

### Client commands

| Command | Description |
| --- | --- |
| `/morpheui open <namespace:path.lua>` | Open a normal Morphe screen. |
| `/morpheui open_transparent <namespace:path.lua>` | Open a transparent Morphe screen. |
| `/morpheui hud <namespace:layer> <namespace:path.lua>` | Show a HUD layer from a Lua document. |
| `/morpheui hud_interactive <namespace:layer> <true\|false>` | Enable or disable interaction for a HUD layer. |
| `/morpheui hud_hide <namespace:layer>` | Hide a HUD layer. |
| `/morpheui reload` | Reload the currently open document during development. |
| `/morpheui inspect` | Toggle the UI inspector for the current screen. |
| `/morpheui widgets` | List registered widget types. |

### Server administrator commands

| Command | Description |
| --- | --- |
| `/morphe open <targets> <namespace:path.lua>` | Open a client-available document for selected players. |
| `/morphe close <targets>` | Close active Morphe screen sessions for selected players. |

## Security model

GFBS: Morphe does not accept Lua source uploaded by clients and does not send arbitrary script text over the network. Server packets reference client-installed resources and contain only bounded initial data, document identifiers, and random session identifiers.

Client actions must match an active session and document and are rate-limited. The Lua sandbox does not expose `package`, `io`, `os`, `debug`, or Java reflection libraries.

Lua documents are executable content. Only install mods and resource packs from sources you trust.

## Project structure

```text
src/main/java/        Java runtime, API, widgets, networking, and Forge integration
src/main/resources/   Mod metadata, translations, and Lua UI resources
src/test/java/        Core and Lua sandbox smoke tests
```

## Contributing

Contributions are welcome. Read [`CONTRIBUTING.md`](CONTRIBUTING.md) before opening an issue or pull request.

## License

GFBS: Morphe is available under the [MIT License](LICENSE).

Copyright © 2026 LytharaLab.

Minecraft is a trademark of Microsoft Corporation. This project is not affiliated with or endorsed by Microsoft or Mojang Studios.
