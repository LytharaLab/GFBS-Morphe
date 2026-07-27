package org.lytharalab.gfbs.morphe.core;

import net.minecraft.resources.ResourceLocation;
import org.lytharalab.gfbs.morphe.api.Morphe;
import org.lytharalab.gfbs.morphe.api.MorpheAPI;
import org.lytharalab.gfbs.morphe.api.UiEffect;
import org.lytharalab.gfbs.morphe.api.UiSystemExtension;
import org.lytharalab.gfbs.morphe.widget.ButtonElement;
import org.lytharalab.gfbs.morphe.widget.PanelElement;
import org.lytharalab.gfbs.morphe.widget.ScrollElement;
import org.lytharalab.gfbs.morphe.widget.VideoElement;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Framework-free smoke test for the renderer-independent core.
 */
public final class CoreSmokeTest {
    private CoreSmokeTest() {
    }

    public static void main(String[] args) {
        lengthParsing();
        flexLayout();
        inputRouting();
        animation();
        advancedAnimation();
        transformedInput();
        soundRouting();
        inheritedWidgetProperties();
        videoPlayback();
        renderTraversal();
        effectLifecycle();
        systemExtensionPipeline();
        externalRegistries();
        System.out.println("GFBS Morphe core smoke tests passed.");
    }

    private static void lengthParsing() {
        require(UiLength.parse("25%").equals(UiLength.percent(25)), "percentage parsing");
        require(UiLength.parse("12px").equals(UiLength.px(12)), "pixel parsing");
        require(UiLength.parse("auto").isAuto(), "auto parsing");
        require(UiColor.parse("#26C6DA").argb() == 0xFF26C6DA, "color parsing");
    }

    private static void flexLayout() {
        UiDocument document = new UiDocument();
        document.resize(400, 200);
        document.root().setProperty("layout", "row");
        document.root().setProperty("gap", 10);

        PanelElement fixed = new PanelElement();
        fixed.setProperty("width", 100);
        fixed.setProperty("height", "100%");
        PanelElement flexible = new PanelElement();
        flexible.setProperty("flex", 1);
        flexible.setProperty("height", "100%");
        document.root().add(fixed);
        document.root().add(flexible);
        document.tick(0);

        require(fixed.bounds().width() == 100, "fixed flex item width");
        require(flexible.bounds().width() == 290, "flex item receives remaining width");
        require(flexible.bounds().x() == 110, "flex gap");
        document.close();
    }

    private static void inputRouting() {
        UiDocument document = new UiDocument();
        document.resize(200, 100);
        ButtonElement button = new ButtonElement();
        button.setProperty("x", 10);
        button.setProperty("y", 10);
        button.setProperty("width", 80);
        button.setProperty("height", 24);
        document.root().add(button);
        document.tick(0);

        AtomicInteger clicks = new AtomicInteger();
        button.on("click", event -> clicks.incrementAndGet());
        require(document.input().pointerDown(20, 20, 0), "button captures pointer");
        require(document.input().pointerUp(20, 20, 0), "button releases pointer");
        require(clicks.get() == 1, "button click");
        button.setProperty("enabled", false);
        require(!document.input().keyDown(257, 0, 0), "disabled focused button ignores keys");
        require(clicks.get() == 1, "disabled button does not click");
        document.close();
    }

    private static void animation() {
        PanelElement panel = new PanelElement();
        panel.setProperty("opacity", 0);
        panel.animator().animate(Map.of("opacity", 1), 1, UiAnimator.Easing.LINEAR);
        panel.tick(1);
        require(
            ((Number) panel.getProperty("opacity")).doubleValue() == 0,
            "logic ticks do not drive visual animation"
        );
        panel.frame(0.5);
        double opacity = ((Number) panel.getProperty("opacity")).doubleValue();
        require(Math.abs(opacity - 0.5) < 0.0001, "linear animation midpoint");
        panel.frame(0.5);
        require(panel.animator().activeCount() == 0, "animation completion");
        panel.animator().animate(Map.of("opacity", 0.25), 0, UiAnimator.Easing.LINEAR);
        require(
            Math.abs(((Number) panel.getProperty("opacity")).doubleValue() - 0.25) < 0.0001,
            "zero-duration animation"
        );
    }

    private static void advancedAnimation() {
        PanelElement panel = new PanelElement();
        panel.setProperty("opacity", 0);
        panel.animator().animate(
            Map.of("opacity", 1),
            new UiAnimator.Spec(0.1, 0.1, UiAnimator.Easing.OUT_CUBIC, 1, true, null)
        );
        panel.frame(0.05);
        require(((Number) panel.getProperty("opacity")).doubleValue() == 0, "animation delay");
        panel.frame(0.15);
        require(
            Math.abs(((Number) panel.getProperty("opacity")).doubleValue() - 1) < 0.0001,
            "animation yoyo apex"
        );
        panel.frame(0.1);
        require(
            Math.abs(((Number) panel.getProperty("opacity")).doubleValue()) < 0.0001,
            "animation yoyo return"
        );
    }

    private static void transformedInput() {
        UiDocument document = new UiDocument();
        document.resize(200, 100);
        ButtonElement button = new ButtonElement();
        button.setProperty("width", 20);
        button.setProperty("height", 20);
        button.setProperty("translate_x", 50);
        document.root().add(button);
        document.tick(0);
        AtomicInteger clicks = new AtomicInteger();
        button.on("click", event -> clicks.incrementAndGet());
        document.input().pointerDown(55, 5, 0);
        document.input().pointerUp(55, 5, 0);
        require(clicks.get() == 1, "transformed hit testing");
        document.close();
    }

    private static void soundRouting() {
        UiDocument document = new UiDocument();
        document.resize(100, 50);
        ButtonElement button = new ButtonElement();
        button.setProperty("width", 40);
        button.setProperty("height", 20);
        document.root().add(button);
        document.tick(0);
        AtomicInteger sounds = new AtomicInteger();
        document.soundSink((sound, volume, pitch) -> sounds.incrementAndGet());
        document.input().pointerDown(5, 5, 0);
        document.input().pointerUp(5, 5, 0);
        require(sounds.get() == 1, "button sound routing");
        document.close();
    }

    private static void videoPlayback() {
        VideoElement video = new VideoElement();
        video.setProperty("frames", List.of("test:a.png", "test:b.png"));
        video.setProperty("fps", 10);
        video.tick(1);
        require(((Number) video.getProperty("frame")).intValue() == 0, "logic ticks do not drive video");
        video.frame(0.11);
        require(((Number) video.getProperty("frame")).intValue() == 1, "video frame playback");
        VideoElement atlas = new VideoElement();
        atlas.setProperty("columns", 4);
        atlas.setProperty("rows", 2);
        require(((Number) atlas.getProperty("frame_count")).intValue() == 8, "atlas auto frame count");
    }

    private static void inheritedWidgetProperties() {
        ScrollElement scroll = new ScrollElement();
        scroll.setProperty("background", "#FF102030");
        require(scroll.style().background().argb() == 0xFF102030, "scroll inherited style property");
    }

    private static void renderTraversal() {
        Morphe.get().initialize();
        UiDocument document = new UiDocument();
        document.resize(100, 100);
        document.root().add(Morphe.get().create("panel"));
        document.tick(0);
        CountingCanvas canvas = new CountingCanvas();
        document.render(canvas, 0, 0, 0);
        require(canvas.transforms == 2, "root and child render traversal");
        document.close();
    }

    private static void effectLifecycle() {
        UiDocument document = new UiDocument();
        document.resize(100, 50);
        ButtonElement button = new ButtonElement();
        button.setProperty("width", 40);
        button.setProperty("height", 20);
        AtomicInteger attach = new AtomicInteger();
        AtomicInteger detach = new AtomicInteger();
        AtomicInteger frames = new AtomicInteger();
        AtomicInteger events = new AtomicInteger();
        button.addEffect("test:pulse", new UiEffect() {
            @Override
            public void onAttach(UiElement element) {
                attach.incrementAndGet();
            }

            @Override
            public void onDetach(UiElement element) {
                detach.incrementAndGet();
            }

            @Override
            public void onFrame(UiElement element, double deltaSeconds) {
                frames.incrementAndGet();
            }

            @Override
            public void onEvent(UiElement element, UiEvent event) {
                events.incrementAndGet();
            }
        });
        document.root().add(button);
        document.tick(0);
        document.frame(1.0 / 60.0);
        document.input().pointerDown(5, 5, 0);
        document.input().pointerUp(5, 5, 0);
        require(attach.get() == 1, "effect attach");
        require(frames.get() == 1, "effect frame hook");
        require(events.get() > 0, "effect event hook");
        document.close();
        require(detach.get() == 1, "effect detach on document close");
    }

    private static void systemExtensionPipeline() {
        UiDocument document = new UiDocument();
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger frames = new AtomicInteger();
        AtomicInteger layouts = new AtomicInteger();
        AtomicInteger renders = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        document.addSystemExtension("test:pipeline", new UiSystemExtension() {
            @Override
            public void beforeTick(UiDocument owner, double deltaSeconds) {
                ticks.incrementAndGet();
            }

            @Override
            public void beforeFrame(UiDocument owner, double deltaSeconds) {
                frames.incrementAndGet();
            }

            @Override
            public void beforeLayout(UiDocument owner) {
                layouts.incrementAndGet();
            }

            @Override
            public void beforeRender(UiDocument owner, UiCanvas canvas, UiFrame frame) {
                renders.incrementAndGet();
            }

            @Override
            public void onClose(UiDocument owner) {
                closes.incrementAndGet();
            }
        });
        document.resize(80, 40);
        document.tick(0.05);
        document.frame(0.05);
        document.render(new CountingCanvas(), 0, 0, 0);
        require(ticks.get() == 1, "system tick hook");
        require(frames.get() == 1, "system frame hook");
        require(layouts.get() == 1, "system layout hook");
        require(renders.get() == 1, "system render hook");
        document.close();
        require(closes.get() == 1, "system close hook");
    }

    private static void externalRegistries() {
        ResourceLocation systemId = new ResourceLocation("smoke", "system");
        ResourceLocation widgetId = new ResourceLocation("smoke", "widget");
        ResourceLocation nestedEffectId = new ResourceLocation("smoke", "nested_effect");
        AtomicInteger attaches = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        MorpheAPI.registerSystemExtension(systemId, document ->
            new org.lytharalab.gfbs.morphe.api.UiSystemExtension() {
                @Override
                public void onAttach(UiDocument owner) {
                    attaches.incrementAndGet();
                }

                @Override
                public void onClose(UiDocument owner) {
                    closes.incrementAndGet();
                }
            });
        MorpheAPI.registerWidget(widgetId, () -> {
            // A factory may safely register another extension: no registry
            // lock is held while dependent-mod code executes.
            if (!MorpheAPI.effectTypes().contains(nestedEffectId.toString())) {
                MorpheAPI.registerEffect(nestedEffectId, options -> new UiEffect() {
                });
            }
            return new PanelElement();
        });

        UiElement created = MorpheAPI.create(widgetId.toString());
        require(created != null, "namespaced external widget");
        require(MorpheAPI.effectTypes().contains(nestedEffectId.toString()), "nested registration");
        UiDocument document = MorpheAPI.createDocument();
        require(attaches.get() == 1, "registered system attached to document");
        document.close();
        require(closes.get() == 1, "registered system closed with document");

        require(MorpheAPI.unregisterWidget(widgetId), "external widget unregister");
        require(MorpheAPI.unregisterEffect(nestedEffectId), "external effect unregister");
        require(MorpheAPI.unregisterSystemExtension(systemId), "external system unregister");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Failed: " + message);
        }
    }

    private static final class CountingCanvas implements UiCanvas {
        private int transforms;

        @Override public void pushTransform(UiRect bounds, double rotationDegrees) { transforms++; }
        @Override public void popTransform() { }
        @Override public void pushClip(UiRect bounds) { }
        @Override public void popClip() { }
        @Override public void fill(UiRect bounds, UiColor color, double radius) { }
        @Override public void stroke(UiRect bounds, UiColor color, double width, double radius) { }
        @Override public void text(String text, UiRect bounds, UiColor color, int fontSize, UiStyle.TextAlign horizontal, UiStyle.VerticalAlign vertical, boolean wrap, boolean shadow) { }
        @Override public void image(String resource, UiRect bounds, UiColor tint, UiStyle.ImageFit fit) { }
        @Override public int textWidth(String text, int fontSize) { return text.length() * fontSize; }
        @Override public int lineHeight(int fontSize) { return fontSize; }
        @Override public List<String> wrapText(String text, int maxWidth, int fontSize) { return List.of(text); }
    }
}
