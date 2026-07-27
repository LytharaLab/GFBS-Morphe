package org.lytharalab.gfbs.morphe.core;

import java.util.Locale;

/**
 * Mutable layout and visual style shared by every widget.
 */
public final class UiStyle {
    public enum Position {
        FLOW,
        ABSOLUTE
    }

    public enum Layout {
        FREE,
        ROW,
        COLUMN,
        GRID
    }

    public enum Align {
        START,
        CENTER,
        END,
        STRETCH
    }

    public enum Justify {
        START,
        CENTER,
        END,
        SPACE_BETWEEN,
        SPACE_AROUND,
        SPACE_EVENLY
    }

    public enum TextAlign {
        LEFT,
        CENTER,
        RIGHT
    }

    public enum VerticalAlign {
        TOP,
        CENTER,
        BOTTOM
    }

    public enum ImageFit {
        STRETCH,
        CONTAIN,
        COVER
    }

    private UiLength width = UiLength.AUTO;
    private UiLength height = UiLength.AUTO;
    private UiLength left = UiLength.ZERO;
    private UiLength top = UiLength.ZERO;
    private double minWidth;
    private double minHeight;
    private double maxWidth = Double.MAX_VALUE;
    private double maxHeight = Double.MAX_VALUE;

    private UiInsets margin = UiInsets.ZERO;
    private UiInsets padding = UiInsets.ZERO;
    private Position position = Position.FLOW;
    private Layout layout = Layout.FREE;
    private Align alignItems = Align.STRETCH;
    private Align alignSelf;
    private Justify justifyContent = Justify.START;
    private double gap;
    private double flexGrow;
    private int gridColumns = 1;
    private int gridSpan = 1;

    private boolean visible = true;
    private boolean enabled = true;
    private boolean pointerEvents = true;
    private boolean clip;
    private int zIndex;
    private double opacity = 1.0;
    private double rotation;
    private double translateX;
    private double translateY;
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private double pivotX = 0.5;
    private double pivotY = 0.5;

    private UiColor background = UiColor.TRANSPARENT;
    private UiColor border = UiColor.TRANSPARENT;
    private double borderWidth;
    private double radius;
    private UiColor foreground = UiColor.WHITE;
    private UiColor hoverBackground = UiColor.TRANSPARENT;
    private UiColor pressedBackground = UiColor.TRANSPARENT;
    private int fontSize = 9;
    private TextAlign textAlign = TextAlign.LEFT;
    private VerticalAlign verticalAlign = VerticalAlign.TOP;
    private boolean wrapText;
    private boolean textShadow;
    private ImageFit imageFit = ImageFit.STRETCH;
    private String clickSound = "";
    private String hoverSound = "";
    private String focusSound = "";
    private double soundVolume = 1.0;
    private double soundPitch = 1.0;

    public boolean set(String property, Object value) {
        String key = normalize(property);
        switch (key) {
            case "width" -> width = UiLength.parse(value);
            case "height" -> height = UiLength.parse(value);
            case "x", "left" -> left = UiLength.parse(value);
            case "y", "top" -> top = UiLength.parse(value);
            case "min_width" -> minWidth = nonNegative(value);
            case "min_height" -> minHeight = nonNegative(value);
            case "max_width" -> maxWidth = nonNegative(value);
            case "max_height" -> maxHeight = nonNegative(value);
            case "margin" -> margin = UiInsets.parse(value);
            case "padding" -> padding = UiInsets.parse(value);
            case "position" -> position = enumValue(Position.class, value);
            case "layout", "direction" -> layout = layoutValue(value);
            case "align", "align_items" -> alignItems = enumValue(Align.class, value);
            case "align_self" -> alignSelf = nullableEnum(Align.class, value);
            case "justify", "justify_content" -> justifyContent = enumValue(Justify.class, value);
            case "gap" -> gap = nonNegative(value);
            case "flex", "flex_grow" -> flexGrow = nonNegative(value);
            case "columns", "grid_columns" -> gridColumns = Math.max(1, Math.min(256, integer(value)));
            case "span", "grid_span" -> gridSpan = Math.max(1, integer(value));
            case "visible" -> visible = bool(value);
            case "enabled" -> enabled = bool(value);
            case "pointer_events", "active" -> pointerEvents = bool(value);
            case "clip", "clips_descendants" -> clip = bool(value);
            case "z", "z_index" -> zIndex = integer(value);
            case "opacity" -> opacity = clamp01(number(value));
            case "rotation" -> rotation = number(value);
            case "translate_x", "translation_x", "visual_x" -> translateX = number(value);
            case "translate_y", "translation_y", "visual_y" -> translateY = number(value);
            case "scale" -> {
                scaleX = number(value);
                scaleY = scaleX;
            }
            case "scale_x" -> scaleX = number(value);
            case "scale_y" -> scaleY = number(value);
            case "pivot_x", "origin_x" -> pivotX = number(value);
            case "pivot_y", "origin_y" -> pivotY = number(value);
            case "background", "background_color" -> background = color(value);
            case "border", "border_color" -> border = color(value);
            case "border_width" -> borderWidth = nonNegative(value);
            case "radius", "corner_radius" -> radius = nonNegative(value);
            case "color", "foreground", "text_color" -> foreground = color(value);
            case "hover_background" -> hoverBackground = color(value);
            case "pressed_background", "active_background" -> pressedBackground = color(value);
            case "font_size", "text_size" -> fontSize = Math.max(1, Math.min(256, integer(value)));
            case "text_align" -> textAlign = enumValue(TextAlign.class, value);
            case "vertical_align" -> verticalAlign = enumValue(VerticalAlign.class, value);
            case "wrap", "text_wrapped" -> wrapText = bool(value);
            case "shadow", "text_shadow" -> textShadow = bool(value);
            case "image_fit", "fit" -> imageFit = enumValue(ImageFit.class, value);
            case "click_sound", "sound" -> clickSound = string(value);
            case "hover_sound" -> hoverSound = string(value);
            case "focus_sound" -> focusSound = string(value);
            case "sound_volume" -> soundVolume = nonNegative(value);
            case "sound_pitch" -> soundPitch = clamp(number(value), 0.01, 4.0);
            default -> {
                return false;
            }
        }
        return true;
    }

    public Object get(String property) {
        return switch (normalize(property)) {
            case "width" -> width;
            case "height" -> height;
            case "x", "left" -> left;
            case "y", "top" -> top;
            case "min_width" -> minWidth;
            case "min_height" -> minHeight;
            case "max_width" -> maxWidth;
            case "max_height" -> maxHeight;
            case "margin" -> margin;
            case "padding" -> padding;
            case "position" -> position.name().toLowerCase(Locale.ROOT);
            case "layout", "direction" -> layout.name().toLowerCase(Locale.ROOT);
            case "align", "align_items" -> alignItems.name().toLowerCase(Locale.ROOT);
            case "align_self" -> alignSelf == null ? null : alignSelf.name().toLowerCase(Locale.ROOT);
            case "justify", "justify_content" -> justifyContent.name().toLowerCase(Locale.ROOT);
            case "gap" -> gap;
            case "flex", "flex_grow" -> flexGrow;
            case "columns", "grid_columns" -> gridColumns;
            case "span", "grid_span" -> gridSpan;
            case "visible" -> visible;
            case "enabled" -> enabled;
            case "pointer_events", "active" -> pointerEvents;
            case "clip", "clips_descendants" -> clip;
            case "z", "z_index" -> zIndex;
            case "opacity" -> opacity;
            case "rotation" -> rotation;
            case "translate_x", "translation_x", "visual_x" -> translateX;
            case "translate_y", "translation_y", "visual_y" -> translateY;
            case "scale" -> Double.compare(scaleX, scaleY) == 0 ? scaleX : null;
            case "scale_x" -> scaleX;
            case "scale_y" -> scaleY;
            case "pivot_x", "origin_x" -> pivotX;
            case "pivot_y", "origin_y" -> pivotY;
            case "background", "background_color" -> background;
            case "border", "border_color" -> border;
            case "border_width" -> borderWidth;
            case "radius", "corner_radius" -> radius;
            case "color", "foreground", "text_color" -> foreground;
            case "hover_background" -> hoverBackground;
            case "pressed_background", "active_background" -> pressedBackground;
            case "font_size", "text_size" -> fontSize;
            case "text_align" -> textAlign.name().toLowerCase(Locale.ROOT);
            case "vertical_align" -> verticalAlign.name().toLowerCase(Locale.ROOT);
            case "wrap", "text_wrapped" -> wrapText;
            case "shadow", "text_shadow" -> textShadow;
            case "image_fit", "fit" -> imageFit.name().toLowerCase(Locale.ROOT);
            case "click_sound", "sound" -> clickSound;
            case "hover_sound" -> hoverSound;
            case "focus_sound" -> focusSound;
            case "sound_volume" -> soundVolume;
            case "sound_pitch" -> soundPitch;
            default -> null;
        };
    }

    public UiLength width() { return width; }
    public UiLength height() { return height; }
    public UiLength left() { return left; }
    public UiLength top() { return top; }
    public double minWidth() { return minWidth; }
    public double minHeight() { return minHeight; }
    public double maxWidth() { return maxWidth; }
    public double maxHeight() { return maxHeight; }
    public UiInsets margin() { return margin; }
    public UiInsets padding() { return padding; }
    public Position position() { return position; }
    public Layout layout() { return layout; }
    public Align alignItems() { return alignItems; }
    public Align alignSelf() { return alignSelf; }
    public Justify justifyContent() { return justifyContent; }
    public double gap() { return gap; }
    public double flexGrow() { return flexGrow; }
    public int gridColumns() { return gridColumns; }
    public int gridSpan() { return gridSpan; }
    public boolean visible() { return visible; }
    public boolean enabled() { return enabled; }
    public boolean pointerEvents() { return pointerEvents; }
    public boolean clip() { return clip; }
    public int zIndex() { return zIndex; }
    public double opacity() { return opacity; }
    public double rotation() { return rotation; }
    public double translateX() { return translateX; }
    public double translateY() { return translateY; }
    public double scaleX() { return scaleX; }
    public double scaleY() { return scaleY; }
    public double pivotX() { return pivotX; }
    public double pivotY() { return pivotY; }
    public UiTransform transform() {
        return new UiTransform(translateX, translateY, scaleX, scaleY, rotation, pivotX, pivotY);
    }
    public UiColor background() { return background; }
    public UiColor border() { return border; }
    public double borderWidth() { return borderWidth; }
    public double radius() { return radius; }
    public UiColor foreground() { return foreground; }
    public UiColor hoverBackground() { return hoverBackground; }
    public UiColor pressedBackground() { return pressedBackground; }
    public int fontSize() { return fontSize; }
    public TextAlign textAlign() { return textAlign; }
    public VerticalAlign verticalAlign() { return verticalAlign; }
    public boolean wrapText() { return wrapText; }
    public boolean textShadow() { return textShadow; }
    public ImageFit imageFit() { return imageFit; }
    public String clickSound() { return clickSound; }
    public String hoverSound() { return hoverSound; }
    public String focusSound() { return focusSound; }
    public double soundVolume() { return soundVolume; }
    public double soundPitch() { return soundPitch; }

    private static Layout layoutValue(Object value) {
        String normalized = normalize(value.toString());
        return switch (normalized) {
            case "horizontal" -> Layout.ROW;
            case "vertical" -> Layout.COLUMN;
            case "absolute", "none" -> Layout.FREE;
            default -> enumValue(Layout.class, normalized);
        };
    }

    private static UiColor color(Object value) {
        if (value instanceof UiColor color) {
            return color;
        }
        if (value instanceof Number number) {
            return new UiColor(number.intValue());
        }
        return UiColor.parse(value.toString());
    }

    private static double nonNegative(Object value) {
        return Math.max(0.0, number(value));
    }

    private static double number(Object value) {
        double result = value instanceof Number number
            ? number.doubleValue()
            : Double.parseDouble(value.toString());
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Numeric style values must be finite");
        }
        return result;
    }

    private static int integer(Object value) {
        return (int) Math.round(number(value));
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static <T extends Enum<T>> T nullableEnum(Class<T> type, Object value) {
        if (value == null || value.toString().equalsIgnoreCase("auto")) {
            return null;
        }
        return enumValue(type, value);
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, Object value) {
        return Enum.valueOf(type, normalize(value.toString()).toUpperCase(Locale.ROOT));
    }

    public static String normalize(String value) {
        String withUnderscores = value
            .replace('-', '_')
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        return withUnderscores.trim().toLowerCase(Locale.ROOT);
    }
}
