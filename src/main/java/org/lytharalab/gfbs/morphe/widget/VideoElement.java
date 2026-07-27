package org.lytharalab.gfbs.morphe.widget;

import org.lytharalab.gfbs.morphe.core.UiCanvas;
import org.lytharalab.gfbs.morphe.core.UiColor;
import org.lytharalab.gfbs.morphe.core.UiFrame;
import org.lytharalab.gfbs.morphe.core.UiImageRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resource-safe video/animated-image widget. It can play either an explicit
 * sequence of image resources or frames from a texture atlas. This keeps media
 * deterministic in resource packs and does not require native codecs.
 */
public final class VideoElement extends PanelElement {
    private final List<String> frames = new ArrayList<>();
    private String source = "";
    private UiColor tint = UiColor.WHITE;
    private int columns = 1;
    private int rows = 1;
    /** Zero means use every cell in the configured atlas. */
    private int frameCount;
    private int frame;
    private double fps = 20;
    private double speed = 1;
    private double accumulator;
    private boolean playing = true;
    private boolean loop = true;
    private boolean pingPong;
    private int direction = 1;

    public VideoElement() {
        super("video");
        setProperty("pointer_events", false);
    }

    @Override
    protected Object getWidgetProperty(String property) {
        return switch (property) {
            case "source", "texture" -> source;
            case "frames" -> List.copyOf(frames);
            case "tint", "image_color" -> tint;
            case "columns" -> columns;
            case "rows" -> rows;
            case "frame_count" -> effectiveFrameCount();
            case "frame", "current_frame" -> frame;
            case "fps" -> fps;
            case "speed", "playback_rate" -> speed;
            case "playing", "play" -> playing;
            case "loop" -> loop;
            case "ping_pong" -> pingPong;
            default -> super.getWidgetProperty(property);
        };
    }

    @Override
    protected boolean setWidgetProperty(String property, Object value) {
        switch (property) {
            case "source", "texture" -> source = value == null ? "" : value.toString();
            case "frames" -> setFrames(value);
            case "tint", "image_color" -> tint = color(value);
            case "columns" -> columns = boundedInteger(value, 1, 256);
            case "rows" -> rows = boundedInteger(value, 1, 256);
            case "frame_count" -> frameCount = boundedInteger(value, 0, 65_536);
            case "frame", "current_frame" -> {
                frame = boundedInteger(value, 0, Math.max(0, effectiveFrameCount() - 1));
                accumulator = 0;
            }
            case "fps" -> fps = clamp(number(value), 0.01, 240);
            case "speed", "playback_rate" -> speed = clamp(number(value), 0.01, 32);
            case "playing", "play" -> playing = bool(value);
            case "loop" -> loop = bool(value);
            case "ping_pong" -> pingPong = bool(value);
            default -> {
                return super.setWidgetProperty(property, value);
            }
        }
        frame = Math.min(frame, Math.max(0, effectiveFrameCount() - 1));
        return true;
    }

    @Override
    protected void onFrame(double deltaSeconds) {
        int count = effectiveFrameCount();
        if (!playing || count <= 1) {
            return;
        }
        accumulator += Math.max(0, deltaSeconds) * fps * speed;
        int advances = Math.min(1024, (int) accumulator);
        if (advances <= 0) {
            return;
        }
        accumulator -= advances;
        for (int i = 0; i < advances && playing; i++) {
            advance(count);
        }
        markDirty();
    }

    @Override
    protected void renderContent(UiCanvas canvas, UiFrame uiFrame) {
        String resource = frames.isEmpty() ? source : frames.get(Math.min(frame, frames.size() - 1));
        if (resource.isBlank()) {
            return;
        }
        UiImageRegion region = frames.isEmpty() ? atlasRegion() : UiImageRegion.FULL;
        canvas.image(
            resource,
            bounds(),
            tint.multiplyAlpha(style().opacity()),
            style().imageFit(),
            region
        );
    }

    private void advance(int count) {
        int next = frame + direction;
        if (next >= 0 && next < count) {
            frame = next;
            emitLocal("frame", Map.of("frame", frame, "count", count));
            return;
        }
        if (pingPong && count > 1) {
            direction = -direction;
            frame = Math.max(0, Math.min(count - 1, frame + direction));
            emitLocal("loop", Map.of("frame", frame));
            return;
        }
        if (loop) {
            frame = direction > 0 ? 0 : count - 1;
            emitLocal("loop", Map.of("frame", frame));
            return;
        }
        playing = false;
        frame = direction > 0 ? count - 1 : 0;
        emitLocal("ended", Map.of("frame", frame));
    }

    private UiImageRegion atlasRegion() {
        int safeColumns = Math.max(1, columns);
        int safeRows = Math.max(1, rows);
        int safeFrame = Math.min(frame, safeColumns * safeRows - 1);
        int column = safeFrame % safeColumns;
        int row = safeFrame / safeColumns;
        return new UiImageRegion(
            column / (double) safeColumns,
            row / (double) safeRows,
            (column + 1) / (double) safeColumns,
            (row + 1) / (double) safeRows
        );
    }

    private int effectiveFrameCount() {
        if (!frames.isEmpty()) {
            return frames.size();
        }
        int atlasCells = columns * rows;
        return frameCount <= 0 ? atlasCells : Math.min(frameCount, atlasCells);
    }

    private void setFrames(Object value) {
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            frames.clear();
            frame = 0;
            direction = 1;
            accumulator = 0;
            return;
        }
        if (!(value instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("frames must be an array of resource ids");
        }
        frames.clear();
        for (Object item : iterable) {
            if (frames.size() >= 4096) {
                throw new IllegalArgumentException("Video frame list exceeds 4096 entries");
            }
            String resource = String.valueOf(item).trim();
            if (!resource.isEmpty()) {
                frames.add(resource);
            }
        }
        frame = 0;
        direction = 1;
        accumulator = 0;
    }

    private static UiColor color(Object value) {
        return value instanceof UiColor color ? color : UiColor.parse(String.valueOf(value));
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static int boundedInteger(Object value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, (int) Math.round(number(value))));
    }

    private static double number(Object value) {
        double result = value instanceof Number number
            ? number.doubleValue()
            : Double.parseDouble(String.valueOf(value));
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Video values must be finite");
        }
        return result;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
