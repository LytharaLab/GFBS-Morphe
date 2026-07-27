package org.lytharalab.gfbs.morphe.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UiAnimator {
    public enum Easing {
        LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT,
        IN_SINE, OUT_SINE, IN_OUT_SINE,
        IN_CUBIC, OUT_CUBIC, IN_OUT_CUBIC,
        IN_QUART, OUT_QUART, IN_OUT_QUART,
        IN_EXPO, OUT_EXPO, IN_OUT_EXPO,
        BACK_IN, BACK_OUT, BACK_IN_OUT,
        ELASTIC_OUT, BOUNCE_OUT, SPRING;

        public static Easing parse(String value) {
            String key = UiStyle.normalize(value == null ? "ease_out" : value).toUpperCase(Locale.ROOT);
            return switch (key) {
                case "QUAD_IN", "IN_QUAD" -> EASE_IN;
                case "QUAD_OUT", "OUT_QUAD" -> EASE_OUT;
                case "QUAD_IN_OUT", "IN_OUT_QUAD" -> EASE_IN_OUT;
                case "CUBIC_IN" -> IN_CUBIC;
                case "CUBIC_OUT" -> OUT_CUBIC;
                case "CUBIC_IN_OUT" -> IN_OUT_CUBIC;
                case "SINE_IN" -> IN_SINE;
                case "SINE_OUT" -> OUT_SINE;
                case "SINE_IN_OUT" -> IN_OUT_SINE;
                case "ELASTIC", "OUT_ELASTIC" -> ELASTIC_OUT;
                case "BOUNCE", "OUT_BOUNCE" -> BOUNCE_OUT;
                default -> valueOf(key);
            };
        }
    }

    public record Spec(double duration, double delay, Easing easing, int repeat, boolean yoyo, Runnable onComplete) {
        public Spec {
            if (!Double.isFinite(duration) || duration < 0 || !Double.isFinite(delay) || delay < 0) {
                throw new IllegalArgumentException("Animation duration/delay must be finite and non-negative");
            }
            if (repeat < -1 || repeat > 1_000_000) {
                throw new IllegalArgumentException("Animation repeat must be -1 or 0..1000000");
            }
            easing = easing == null ? Easing.EASE_OUT : easing;
        }

        public static Spec tween(double duration, Easing easing) {
            return new Spec(duration, 0, easing, 0, false, null);
        }
    }

    private final UiElement target;
    private final List<Tween> tweens = new ArrayList<>();
    private boolean paused;

    UiAnimator(UiElement target) {
        this.target = target;
    }

    public void animate(Map<String, ?> targets, double durationSeconds, Easing easing) {
        animate(targets, Spec.tween(durationSeconds, easing));
    }

    public void animate(Map<String, ?> targets, Spec spec) {
        Map<String, ValuePair> values = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : targets.entrySet()) {
            String property = UiStyle.normalize(entry.getKey());
            Object start = target.getProperty(property);
            Object end = normalizeTarget(start, entry.getValue());
            if (!canInterpolate(start, end)) {
                throw new IllegalArgumentException("Property is not animatable: " + property);
            }
            values.put(property, new ValuePair(start, end));
        }
        tweens.removeIf(tween -> tween.properties.keySet().stream().anyMatch(values::containsKey));
        if (spec.duration == 0 && spec.delay == 0) {
            apply(values, 1);
            complete(spec);
        } else if (!values.isEmpty()) {
            tweens.add(new Tween(values, spec));
        }
    }

    public void cancelAll() {
        tweens.clear();
    }

    public void cancel(String property) {
        String key = UiStyle.normalize(property);
        tweens.removeIf(tween -> tween.properties.containsKey(key));
    }

    public void finishAll() {
        for (Tween tween : List.copyOf(tweens)) {
            apply(tween.properties, tween.spec.yoyo && tween.spec.repeat % 2 == 1 ? 0 : 1);
            complete(tween.spec);
        }
        tweens.clear();
    }

    public void paused(boolean value) {
        paused = value;
    }

    public boolean paused() {
        return paused;
    }

    public int activeCount() {
        return tweens.size();
    }

    void tick(double deltaSeconds) {
        if (paused || tweens.isEmpty()) {
            return;
        }
        double delta = Math.max(0, deltaSeconds);
        var iterator = tweens.iterator();
        while (iterator.hasNext()) {
            Tween tween = iterator.next();
            double remaining = delta;
            if (tween.delayRemaining > 0) {
                double consumed = Math.min(remaining, tween.delayRemaining);
                tween.delayRemaining -= consumed;
                remaining -= consumed;
                if (remaining <= 0) {
                    continue;
                }
            }
            if (tween.spec.duration == 0) {
                apply(tween.properties, directionProgress(tween, 1));
                iterator.remove();
                complete(tween.spec);
                continue;
            }
            tween.elapsed += remaining;
            boolean finished = false;
            while (tween.elapsed >= tween.spec.duration) {
                apply(tween.properties, directionProgress(tween, 1));
                tween.elapsed -= tween.spec.duration;
                if (tween.spec.repeat >= 0 && tween.completedRepeats >= tween.spec.repeat) {
                    finished = true;
                    break;
                }
                tween.completedRepeats++;
            }
            if (finished) {
                iterator.remove();
                complete(tween.spec);
                continue;
            }
            double progress = tween.elapsed / tween.spec.duration;
            apply(tween.properties, directionProgress(tween, ease(progress, tween.spec.easing)));
        }
    }

    private double directionProgress(Tween tween, double progress) {
        return tween.spec.yoyo && (tween.completedRepeats & 1) == 1 ? 1 - progress : progress;
    }

    private void apply(Map<String, ValuePair> values, double progress) {
        for (Map.Entry<String, ValuePair> entry : values.entrySet()) {
            ValuePair pair = entry.getValue();
            target.setProperty(entry.getKey(), interpolate(pair.start, pair.end, progress));
        }
    }

    private static void complete(Spec spec) {
        if (spec.onComplete != null) {
            spec.onComplete.run();
        }
    }

    private static Object normalizeTarget(Object start, Object requested) {
        if (start instanceof UiLength length && requested instanceof Number number) {
            return new UiLength(length.unit(), number.doubleValue());
        }
        if (start instanceof UiColor && !(requested instanceof UiColor)) {
            return UiColor.parse(requested.toString());
        }
        return requested;
    }

    private static boolean canInterpolate(Object start, Object end) {
        return start instanceof Number && end instanceof Number
            || start instanceof UiColor && end instanceof UiColor
            || start instanceof UiLength a && end instanceof UiLength b && a.unit() == b.unit();
    }

    private static Object interpolate(Object start, Object end, double progress) {
        if (start instanceof Number a && end instanceof Number b) {
            return a.doubleValue() + (b.doubleValue() - a.doubleValue()) * progress;
        }
        if (start instanceof UiColor a && end instanceof UiColor b) {
            return UiColor.lerp(a, b, progress);
        }
        if (start instanceof UiLength a && end instanceof UiLength b) {
            return new UiLength(a.unit(), a.value() + (b.value() - a.value()) * progress);
        }
        return progress >= 1 ? end : start;
    }

    private static double ease(double t, Easing easing) {
        return switch (easing) {
            case LINEAR -> t;
            case EASE_IN -> t * t;
            case EASE_OUT -> 1 - (1 - t) * (1 - t);
            case EASE_IN_OUT -> t < .5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            case IN_SINE -> 1 - Math.cos(t * Math.PI / 2);
            case OUT_SINE -> Math.sin(t * Math.PI / 2);
            case IN_OUT_SINE -> -(Math.cos(Math.PI * t) - 1) / 2;
            case IN_CUBIC -> t * t * t;
            case OUT_CUBIC -> 1 - Math.pow(1 - t, 3);
            case IN_OUT_CUBIC -> t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            case IN_QUART -> Math.pow(t, 4);
            case OUT_QUART -> 1 - Math.pow(1 - t, 4);
            case IN_OUT_QUART -> t < .5 ? 8 * Math.pow(t, 4) : 1 - Math.pow(-2 * t + 2, 4) / 2;
            case IN_EXPO -> t == 0 ? 0 : Math.pow(2, 10 * t - 10);
            case OUT_EXPO -> t == 1 ? 1 : 1 - Math.pow(2, -10 * t);
            case IN_OUT_EXPO -> t == 0 || t == 1 ? t : t < .5
                ? Math.pow(2, 20 * t - 10) / 2
                : (2 - Math.pow(2, -20 * t + 10)) / 2;
            case BACK_IN -> 2.70158 * t * t * t - 1.70158 * t * t;
            case BACK_OUT -> 1 + 2.70158 * Math.pow(t - 1, 3) + 1.70158 * Math.pow(t - 1, 2);
            case BACK_IN_OUT -> {
                double c = 1.70158 * 1.525;
                yield t < .5 ? Math.pow(2 * t, 2) * ((c + 1) * 2 * t - c) / 2
                    : (Math.pow(2 * t - 2, 2) * ((c + 1) * (2 * t - 2) + c) + 2) / 2;
            }
            case ELASTIC_OUT -> t == 0 || t == 1 ? t
                : Math.pow(2, -10 * t) * Math.sin((10 * t - .75) * 2 * Math.PI / 3) + 1;
            case BOUNCE_OUT -> bounce(t);
            case SPRING -> 1 - Math.exp(-7 * t) * Math.cos(12 * t);
        };
    }

    private static double bounce(double t) {
        double n = 7.5625;
        double d = 2.75;
        if (t < 1 / d) return n * t * t;
        if (t < 2 / d) {
            double x = t - 1.5 / d;
            return n * x * x + .75;
        }
        if (t < 2.5 / d) {
            double x = t - 2.25 / d;
            return n * x * x + .9375;
        }
        double x = t - 2.625 / d;
        return n * x * x + .984375;
    }

    private record ValuePair(Object start, Object end) {
    }

    private static final class Tween {
        private final Map<String, ValuePair> properties;
        private final Spec spec;
        private double delayRemaining;
        private double elapsed;
        private int completedRepeats;

        private Tween(Map<String, ValuePair> properties, Spec spec) {
            this.properties = properties;
            this.spec = spec;
            delayRemaining = spec.delay;
        }
    }
}
