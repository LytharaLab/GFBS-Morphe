package org.lytharalab.gfbs.morphe.api;

import net.minecraft.resources.ResourceLocation;
import org.lytharalab.gfbs.morphe.MorpheBuildInfo;
import org.lytharalab.gfbs.morphe.core.UiDocument;
import org.lytharalab.gfbs.morphe.core.UiElement;
import org.lytharalab.gfbs.morphe.widget.ButtonElement;
import org.lytharalab.gfbs.morphe.widget.CheckboxElement;
import org.lytharalab.gfbs.morphe.widget.ImageElement;
import org.lytharalab.gfbs.morphe.widget.ItemElement;
import org.lytharalab.gfbs.morphe.widget.PanelElement;
import org.lytharalab.gfbs.morphe.widget.ProgressElement;
import org.lytharalab.gfbs.morphe.widget.ScrollElement;
import org.lytharalab.gfbs.morphe.widget.SliderElement;
import org.lytharalab.gfbs.morphe.widget.TextElement;
import org.lytharalab.gfbs.morphe.widget.TextFieldElement;
import org.lytharalab.gfbs.morphe.widget.VideoElement;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stable, side-neutral entry point for extending the Morphe widget system.
 */
public final class Morphe {
    public static final String MOD_ID = "gfbs_morphe";
    public static final String VERSION = MorpheBuildInfo.VERSION;
    public static final int SCRIPT_API_VERSION = 1;

    private static final Morphe INSTANCE = new Morphe();

    private final WidgetRegistry widgets = new WidgetRegistry();
    private final EffectRegistry effects = new EffectRegistry();
    private final ScriptModuleRegistry scriptModules = new ScriptModuleRegistry();
    private final SystemExtensionRegistry systemExtensions = new SystemExtensionRegistry();
    private final AtomicBoolean initialized = new AtomicBoolean();

    private Morphe() {
    }

    public static Morphe get() {
        return INSTANCE;
    }

    public void initialize() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        widgets.register("panel", PanelElement::new);
        widgets.register("text", TextElement::new);
        widgets.register("button", ButtonElement::new);
        widgets.register("image", ImageElement::new);
        widgets.register("checkbox", CheckboxElement::new);
        widgets.register("slider", SliderElement::new);
        widgets.register("progress", ProgressElement::new);
        widgets.register("input", TextFieldElement::new);
        widgets.register("scroll", ScrollElement::new);
        widgets.register("video", VideoElement::new);
        widgets.register("item", ItemElement::new);

        widgets.alias("frame", "panel");
        widgets.alias("label", "text");
        widgets.alias("text_label", "text");
        widgets.alias("text_button", "button");
        widgets.alias("image_label", "image");
        widgets.alias("text_field", "input");
        widgets.alias("scroll_view", "scroll");
        widgets.alias("progress_bar", "progress");
        widgets.alias("animated_image", "video");
        widgets.alias("movie", "video");
        widgets.alias("item_stack", "item");
    }

    public WidgetRegistry widgets() {
        initialize();
        return widgets;
    }

    public EffectRegistry effects() {
        return effects;
    }

    public ScriptModuleRegistry scriptModules() {
        return scriptModules;
    }

    public SystemExtensionRegistry systemExtensions() {
        return systemExtensions;
    }

    public UiDocument createDocument() {
        UiDocument document = new UiDocument();
        systemExtensions.attachAll(document);
        return document;
    }

    public UiElement create(String type) {
        return widgets().create(type);
    }

    public void registerWidget(String type, WidgetFactory factory) {
        widgets().register(type, factory);
    }

    public void registerWidget(ResourceLocation id, WidgetFactory factory) {
        registerWidget(id.toString(), factory);
    }

    public Set<String> widgetTypes() {
        return widgets().types();
    }
}
