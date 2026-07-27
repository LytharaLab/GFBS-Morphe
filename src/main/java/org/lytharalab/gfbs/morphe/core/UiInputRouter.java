package org.lytharalab.gfbs.morphe.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Pointer, focus and keyboard router with capture/target/bubble phases.
 */
public final class UiInputRouter {
    private final UiRoot root;
    private UiElement hovered;
    private UiElement focused;
    private UiElement pressed;
    private UiElement captured;
    private int pressedButton = -1;
    private double pointerX;
    private double pointerY;
    private UiSoundSink soundSink = UiSoundSink.SILENT;
    private long lastClickNanos;
    private UiElement lastClicked;
    private int clickCount;
    private Consumer<UiEvent> eventObserver = ignored -> {
    };

    public UiInputRouter(UiRoot root) {
        this.root = root;
    }

    public UiElement hovered() {
        return hovered;
    }

    public UiElement focused() {
        return focused;
    }

    public void soundSink(UiSoundSink value) {
        soundSink = value == null ? UiSoundSink.SILENT : value;
    }

    public void eventObserver(Consumer<UiEvent> observer) {
        eventObserver = observer == null ? ignored -> {
        } : observer;
    }

    public void pointerMoved(double x, double y) {
        double deltaX = x - pointerX;
        double deltaY = y - pointerY;
        pointerX = x;
        pointerY = y;

        UiElement next = hitTest(x, y);
        if (next != hovered) {
            if (hovered != null) {
                dispatch(hovered, "pointer_leave", pointerData(x, y, -1, 0, 0));
            }
            hovered = next;
            if (hovered != null) {
                dispatch(hovered, "pointer_enter", pointerData(x, y, -1, 0, 0));
                play(hovered.style().hoverSound(), hovered);
            }
        }

        UiElement target = captured != null ? captured : hovered;
        if (target != null) {
            dispatch(target, "pointer_move", pointerData(x, y, pressedButton, deltaX, deltaY));
        }
    }

    public boolean pointerDown(double x, double y, int button) {
        pointerMoved(x, y);
        UiElement target = hovered;
        if (target == null || !target.style().enabled()) {
            focus(null);
            return false;
        }
        pressed = target;
        captured = target;
        pressedButton = button;
        if (target.focusable()) {
            focus(target);
        } else {
            focus(null);
        }
        UiEvent event = dispatch(target, "pointer_down", pointerData(x, y, button, 0, 0));
        return event.defaultPrevented() || target.style().pointerEvents();
    }

    public boolean pointerUp(double x, double y, int button) {
        pointerMoved(x, y);
        UiElement target = captured != null ? captured : hovered;
        if (target == null) {
            resetPointerCapture();
            return false;
        }
        dispatch(target, "pointer_up", pointerData(x, y, button, 0, 0));
        if (pressed == hovered && button == pressedButton) {
            long now = System.nanoTime();
            clickCount = lastClicked == pressed && now - lastClickNanos <= 400_000_000L
                ? Math.min(3, clickCount + 1)
                : 1;
            lastClicked = pressed;
            lastClickNanos = now;
            Map<String, Object> clickData = pointerData(x, y, button, 0, 0);
            clickData.put("click_count", clickCount);
            dispatch(pressed, "click", clickData);
            if (clickCount == 2) {
                dispatch(pressed, "double_click", clickData);
            }
            play(pressed.style().clickSound(), pressed);
        }
        resetPointerCapture();
        return true;
    }

    public boolean pointerDragged(double x, double y, int button, double dragX, double dragY) {
        pointerX = x;
        pointerY = y;
        UiElement target = captured;
        if (target == null || button != pressedButton) {
            return false;
        }
        dispatch(target, "pointer_drag", pointerData(x, y, button, dragX, dragY));
        return true;
    }

    public boolean scrolled(double x, double y, double delta) {
        pointerMoved(x, y);
        if (hovered == null) {
            return false;
        }
        Map<String, Object> data = pointerData(x, y, -1, 0, 0);
        data.put("scroll", delta);
        UiEvent event = dispatch(hovered, "scroll", data);
        return event.defaultPrevented();
    }

    public boolean keyDown(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258) {
            return focusNext((modifiers & 1) != 0);
        }
        if (focused == null || !focused.style().enabled()) {
            return false;
        }
        UiEvent event = dispatch(focused, "key_down", Map.of(
            "key_code", keyCode,
            "scan_code", scanCode,
            "modifiers", modifiers
        ));
        if (event.defaultPrevented() && (keyCode == 257 || keyCode == 335 || keyCode == 32)) {
            play(focused.style().clickSound(), focused);
        }
        return event.defaultPrevented();
    }

    public boolean keyUp(int keyCode, int scanCode, int modifiers) {
        if (focused == null || !focused.style().enabled()) {
            return false;
        }
        UiEvent event = dispatch(focused, "key_up", Map.of(
            "key_code", keyCode,
            "scan_code", scanCode,
            "modifiers", modifiers
        ));
        return event.defaultPrevented();
    }

    public boolean charTyped(char character, int modifiers) {
        if (focused == null || !focused.style().enabled()) {
            return false;
        }
        UiEvent event = dispatch(focused, "char_typed", Map.of(
            "character", Character.toString(character),
            "codepoint", (int) character,
            "modifiers", modifiers
        ));
        return event.defaultPrevented();
    }

    public UiEvent emit(UiElement target, String type, Map<String, ?> data) {
        return dispatch(target, type, data);
    }

    public void reset() {
        focus(null);
        hovered = null;
        resetPointerCapture();
    }

    private void focus(UiElement next) {
        if (next == focused) {
            return;
        }
        UiElement previous = focused;
        focused = next;
        if (previous != null) {
            dispatch(previous, "blur", Map.of());
        }
        if (focused != null) {
            dispatch(focused, "focus", Map.of());
            play(focused.style().focusSound(), focused);
        }
    }

    private void resetPointerCapture() {
        pressed = null;
        captured = null;
        pressedButton = -1;
    }

    private UiEvent dispatch(UiElement target, String type, Map<String, ?> data) {
        UiEvent event = new UiEvent(type, target, data);
        event.prepare(target, UiEvent.Phase.TARGET);
        eventObserver.accept(event);
        if (event.propagationStopped()) {
            return event;
        }
        List<UiElement> path = pathTo(target);

        for (int i = 0; i < path.size() - 1; i++) {
            UiElement current = path.get(i);
            event.prepare(current, UiEvent.Phase.CAPTURE);
            current.dispatchLocal(event, true);
            if (event.propagationStopped()) {
                return event;
            }
        }

        event.prepare(target, UiEvent.Phase.TARGET);
        target.dispatchLocal(event, true);
        if (!event.propagationStopped()) {
            target.dispatchLocal(event, false);
        }

        for (int i = path.size() - 2; i >= 0 && !event.propagationStopped(); i--) {
            UiElement current = path.get(i);
            event.prepare(current, UiEvent.Phase.BUBBLE);
            current.dispatchLocal(event, false);
        }
        return event;
    }

    private List<UiElement> pathTo(UiElement target) {
        List<UiElement> reversed = new ArrayList<>();
        for (UiElement current = target; current != null; current = current.parent()) {
            reversed.add(current);
        }
        List<UiElement> path = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) {
            path.add(reversed.get(i));
        }
        return path;
    }

    private UiElement hitTest(double x, double y) {
        return hitTestRecursive(root, x, y, Affine.IDENTITY);
    }

    private UiElement hitTestRecursive(UiElement element, double x, double y, Affine parentTransform) {
        if (!element.style().visible() || element.destroyed()) {
            return null;
        }
        Affine transform = parentTransform.multiply(Affine.from(element.bounds(), element.style().transform()));
        Point local = transform.inverse(x, y);
        if (element.style().clip() && !element.bounds().contains(local.x, local.y)) {
            return null;
        }

        List<UiElement> ordered = new ArrayList<>(element.children());
        ordered.sort(Comparator.comparingInt((UiElement child) -> child.style().zIndex()).reversed());
        for (UiElement child : ordered) {
            UiElement found = hitTestRecursive(child, x, y, transform);
            if (found != null) {
                return found;
            }
        }
        return element.style().pointerEvents() && element.bounds().contains(local.x, local.y) ? element : null;
    }

    private static Map<String, Object> pointerData(
        double x,
        double y,
        int button,
        double deltaX,
        double deltaY
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("x", x);
        data.put("y", y);
        data.put("button", button);
        data.put("delta_x", deltaX);
        data.put("delta_y", deltaY);
        return data;
    }

    private boolean focusNext(boolean reverse) {
        List<UiElement> focusable = new ArrayList<>();
        collectFocusable(root, focusable);
        if (focusable.isEmpty()) {
            return false;
        }
        int index = focused == null ? -1 : focusable.indexOf(focused);
        int next = reverse
            ? (index <= 0 ? focusable.size() - 1 : index - 1)
            : (index + 1) % focusable.size();
        focus(focusable.get(next));
        return true;
    }

    private static void collectFocusable(UiElement element, List<UiElement> result) {
        if (element.style().visible() && element.style().enabled() && element.focusable()) {
            result.add(element);
        }
        for (UiElement child : element.children()) {
            collectFocusable(child, result);
        }
    }

    private void play(String sound, UiElement element) {
        if (sound == null || sound.isBlank() || !element.style().enabled()) {
            return;
        }
        soundSink.play(
            sound,
            (float) element.style().soundVolume(),
            (float) element.style().soundPitch()
        );
    }

    private record Point(double x, double y) {
    }

    private record Affine(double a, double b, double c, double d, double tx, double ty) {
        private static final Affine IDENTITY = new Affine(1, 0, 0, 1, 0, 0);

        private static Affine from(UiRect bounds, UiTransform transform) {
            double radians = Math.toRadians(transform.rotation());
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            double a = cos * transform.scaleX();
            double b = sin * transform.scaleX();
            double c = -sin * transform.scaleY();
            double d = cos * transform.scaleY();
            double pivotX = bounds.x() + bounds.width() * transform.pivotX();
            double pivotY = bounds.y() + bounds.height() * transform.pivotY();
            double tx = transform.translateX() + pivotX - a * pivotX - c * pivotY;
            double ty = transform.translateY() + pivotY - b * pivotX - d * pivotY;
            return new Affine(a, b, c, d, tx, ty);
        }

        private Affine multiply(Affine next) {
            return new Affine(
                a * next.a + c * next.b,
                b * next.a + d * next.b,
                a * next.c + c * next.d,
                b * next.c + d * next.d,
                a * next.tx + c * next.ty + tx,
                b * next.tx + d * next.ty + ty
            );
        }

        private Point inverse(double x, double y) {
            double determinant = a * d - b * c;
            if (Math.abs(determinant) < 1.0E-9) {
                return new Point(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
            double translatedX = x - tx;
            double translatedY = y - ty;
            return new Point(
                (d * translatedX - c * translatedY) / determinant,
                (-b * translatedX + a * translatedY) / determinant
            );
        }
    }
}
