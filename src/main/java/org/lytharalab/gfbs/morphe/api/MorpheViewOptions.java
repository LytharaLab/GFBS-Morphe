package org.lytharalab.gfbs.morphe.api;

import net.minecraft.nbt.CompoundTag;
import org.lytharalab.gfbs.morphe.core.UiStyle;

import java.util.Map;

/**
 * Presentation and input policy for a scripted document. The surface is fixed
 * by the Java entry point; the remaining values may also be changed by
 * {@code ui.configure(...)} while the document is alive.
 */
public final class MorpheViewOptions {
    public enum Surface {
        SCREEN,
        HUD
    }

    public enum Background {
        DEFAULT,
        DIM,
        NONE
    }

    public enum InputMode {
        /** Normal Screen input: the document owns keyboard and pointer input. */
        EXCLUSIVE,
        /** Pure display HUD: gameplay input and the captured mouse are untouched. */
        PASSIVE,
        /** HUD with an opt-in cursor interaction layer. */
        CURSOR
    }

    /**
     * Controls how a HUD is composed while a Minecraft Screen is open.
     */
    public enum ScreenLayer {
        /** Do not draw the HUD while another Screen is open. */
        HIDDEN,
        /** Draw with the in-world GUI, before the Screen. */
        BELOW,
        /** Draw after the Screen as a topmost overlay. */
        ABOVE
    }

    private final Surface surface;
    private Background background;
    private InputMode inputMode;
    private boolean pauseWorld;
    private boolean closeOnEscape = true;
    private boolean hideWithGui = true;
    private ScreenLayer screenLayer = ScreenLayer.BELOW;
    private int priority;

    private MorpheViewOptions(Surface surface, Background background, InputMode inputMode) {
        this.surface = surface;
        this.background = background;
        this.inputMode = inputMode;
    }

    public static MorpheViewOptions screen() {
        return new MorpheViewOptions(Surface.SCREEN, Background.DIM, InputMode.EXCLUSIVE);
    }

    public static MorpheViewOptions transparentScreen() {
        return new MorpheViewOptions(Surface.SCREEN, Background.NONE, InputMode.EXCLUSIVE);
    }

    public static MorpheViewOptions hud() {
        return new MorpheViewOptions(Surface.HUD, Background.NONE, InputMode.PASSIVE);
    }

    public static MorpheViewOptions interactiveHud() {
        return new MorpheViewOptions(Surface.HUD, Background.NONE, InputMode.CURSOR);
    }

    public MorpheViewOptions copy() {
        return new MorpheViewOptions(surface, background, inputMode)
            .pauseWorld(pauseWorld)
            .closeOnEscape(closeOnEscape)
            .hideWithGui(hideWithGui)
            .screenLayer(screenLayer)
            .priority(priority);
    }

    public Surface surface() {
        return surface;
    }

    public Background background() {
        return background;
    }

    public InputMode inputMode() {
        return inputMode;
    }

    public boolean pauseWorld() {
        return pauseWorld;
    }

    public boolean closeOnEscape() {
        return closeOnEscape;
    }

    public boolean hideWithGui() {
        return hideWithGui;
    }

    public boolean renderOverScreens() {
        return screenLayer != ScreenLayer.HIDDEN;
    }

    public ScreenLayer screenLayer() {
        return screenLayer;
    }

    public int priority() {
        return priority;
    }

    public MorpheViewOptions background(Background value) {
        background = value == null ? Background.NONE : value;
        return this;
    }

    public MorpheViewOptions inputMode(InputMode value) {
        inputMode = value == null ? InputMode.PASSIVE : value;
        return this;
    }

    public MorpheViewOptions pauseWorld(boolean value) {
        pauseWorld = value;
        return this;
    }

    public MorpheViewOptions closeOnEscape(boolean value) {
        closeOnEscape = value;
        return this;
    }

    public MorpheViewOptions hideWithGui(boolean value) {
        hideWithGui = value;
        return this;
    }

    public MorpheViewOptions renderOverScreens(boolean value) {
        screenLayer = value ? ScreenLayer.BELOW : ScreenLayer.HIDDEN;
        return this;
    }

    public MorpheViewOptions screenLayer(ScreenLayer value) {
        screenLayer = value == null ? ScreenLayer.BELOW : value;
        return this;
    }

    public MorpheViewOptions priority(int value) {
        priority = Math.max(-10_000, Math.min(10_000, value));
        return this;
    }

    public void apply(Map<String, ?> values) {
        if (values == null) {
            return;
        }
        if (values.containsKey("background")) {
            background(enumValue(Background.class, values.get("background")));
        }
        if (values.containsKey("input") || values.containsKey("input_mode")) {
            Object raw = values.containsKey("input") ? values.get("input") : values.get("input_mode");
            inputMode(parseInput(raw));
        }
        if (values.containsKey("pause_world")) {
            pauseWorld(bool(values.get("pause_world")));
        }
        if (values.containsKey("close_on_escape")) {
            closeOnEscape(bool(values.get("close_on_escape")));
        }
        if (values.containsKey("hide_with_gui")) {
            hideWithGui(bool(values.get("hide_with_gui")));
        }
        if (values.containsKey("screen_layer")) {
            screenLayer(parseScreenLayer(values.get("screen_layer")));
        } else if (values.containsKey("render_over_screens")) {
            renderOverScreens(bool(values.get("render_over_screens")));
        }
        if (values.containsKey("priority")) {
            priority(integer(values.get("priority")));
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("surface", surface.name());
        tag.putString("background", background.name());
        tag.putString("input", inputMode.name());
        tag.putBoolean("pauseWorld", pauseWorld);
        tag.putBoolean("closeOnEscape", closeOnEscape);
        tag.putBoolean("hideWithGui", hideWithGui);
        tag.putString("screenLayer", screenLayer.name());
        tag.putBoolean("renderOverScreens", renderOverScreens());
        tag.putInt("priority", priority);
        return tag;
    }

    public static MorpheViewOptions fromTag(CompoundTag tag) {
        CompoundTag safe = tag == null ? new CompoundTag() : tag;
        Surface surface = parseEnum(Surface.class, safe.getString("surface"), Surface.HUD);
        MorpheViewOptions result = surface == Surface.SCREEN ? screen() : hud();
        result.background(parseEnum(Background.class, safe.getString("background"), result.background));
        result.inputMode(parseEnum(InputMode.class, safe.getString("input"), result.inputMode));
        if (safe.contains("pauseWorld")) {
            result.pauseWorld(safe.getBoolean("pauseWorld"));
        }
        if (safe.contains("closeOnEscape")) {
            result.closeOnEscape(safe.getBoolean("closeOnEscape"));
        }
        if (safe.contains("hideWithGui")) {
            result.hideWithGui(safe.getBoolean("hideWithGui"));
        }
        if (safe.contains("screenLayer")) {
            result.screenLayer(parseEnum(ScreenLayer.class, safe.getString("screenLayer"), result.screenLayer));
        } else if (safe.contains("renderOverScreens")) {
            result.renderOverScreens(safe.getBoolean("renderOverScreens"));
        }
        if (safe.contains("priority")) {
            result.priority(safe.getInt("priority"));
        }
        return result;
    }

    private static InputMode parseInput(Object value) {
        String key = UiStyle.normalize(String.valueOf(value));
        return switch (key) {
            case "none", "display", "passthrough", "passive" -> InputMode.PASSIVE;
            case "interactive", "overlay", "cursor" -> InputMode.CURSOR;
            case "modal", "exclusive" -> InputMode.EXCLUSIVE;
            default -> enumValue(InputMode.class, value);
        };
    }

    private static ScreenLayer parseScreenLayer(Object value) {
        String key = UiStyle.normalize(String.valueOf(value));
        return switch (key) {
            case "none", "hide", "hidden", "off" -> ScreenLayer.HIDDEN;
            case "behind", "below", "under", "hud", "normal" -> ScreenLayer.BELOW;
            case "above", "over", "overlay", "top", "topmost" -> ScreenLayer.ABOVE;
            default -> enumValue(ScreenLayer.class, value);
        };
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int integer(Object value) {
        return value instanceof Number number
            ? number.intValue()
            : Integer.parseInt(String.valueOf(value));
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, Object value) {
        return Enum.valueOf(type, UiStyle.normalize(String.valueOf(value)).toUpperCase());
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
