package org.lytharalab.gfbs.morphe.api;

import net.minecraft.resources.ResourceLocation;
import org.lytharalab.gfbs.morphe.core.UiDocument;
import org.lytharalab.gfbs.morphe.core.UiElement;

import java.util.Set;

/**
 * Static convenience facade for Java integrations.
 */
public final class MorpheAPI {
    private MorpheAPI() {
    }

    public static String version() {
        return Morphe.VERSION;
    }

    public static int scriptApiVersion() {
        return Morphe.SCRIPT_API_VERSION;
    }

    public static UiElement create(String type) {
        return Morphe.get().create(type);
    }

    public static UiDocument createDocument() {
        return Morphe.get().createDocument();
    }

    public static void registerWidget(String type, WidgetFactory factory) {
        Morphe.get().registerWidget(type, factory);
    }

    public static void registerWidget(ResourceLocation id, WidgetFactory factory) {
        Morphe.get().registerWidget(id, factory);
    }

    public static void replaceWidget(ResourceLocation id, WidgetFactory factory) {
        Morphe.get().widgets().replace(id.toString(), factory);
    }

    public static void registerWidgetAlias(ResourceLocation alias, ResourceLocation target) {
        Morphe.get().widgets().alias(alias.toString(), target.toString());
    }

    public static boolean unregisterWidget(ResourceLocation id) {
        return Morphe.get().widgets().unregister(id.toString());
    }

    public static Set<String> widgetTypes() {
        return Morphe.get().widgetTypes();
    }

    public static void registerScriptModule(MorpheScriptModule module) {
        Morphe.get().scriptModules().register(module);
    }

    public static void replaceScriptModule(MorpheScriptModule module) {
        Morphe.get().scriptModules().replace(module);
    }

    public static boolean unregisterScriptModule(ResourceLocation id) {
        return Morphe.get().scriptModules().unregister(id);
    }

    public static Set<ResourceLocation> scriptModules() {
        return Morphe.get().scriptModules().ids();
    }

    public static void registerEffect(ResourceLocation id, UiEffectFactory factory) {
        Morphe.get().effects().register(id, factory);
    }

    public static void replaceEffect(ResourceLocation id, UiEffectFactory factory) {
        Morphe.get().effects().replace(id, factory);
    }

    public static boolean unregisterEffect(ResourceLocation id) {
        return Morphe.get().effects().unregister(id);
    }

    public static Set<String> effectTypes() {
        return Morphe.get().effects().types();
    }

    public static void registerSystemExtension(
        ResourceLocation id,
        UiSystemExtensionFactory factory
    ) {
        Morphe.get().systemExtensions().register(id, factory);
    }

    public static void replaceSystemExtension(
        ResourceLocation id,
        UiSystemExtensionFactory factory
    ) {
        Morphe.get().systemExtensions().replace(id, factory);
    }

    public static boolean unregisterSystemExtension(ResourceLocation id) {
        return Morphe.get().systemExtensions().unregister(id);
    }

    public static Set<ResourceLocation> systemExtensions() {
        return Morphe.get().systemExtensions().ids();
    }
}
