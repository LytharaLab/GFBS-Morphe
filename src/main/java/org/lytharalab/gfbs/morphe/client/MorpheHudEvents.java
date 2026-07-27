package org.lytharalab.gfbs.morphe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lytharalab.gfbs.morphe.api.Morphe;

@Mod.EventBusSubscriber(modid = Morphe.MOD_ID, value = Dist.CLIENT)
public final class MorpheHudEvents {
    private MorpheHudEvents() {
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            if (!MorpheHudManager.get().isEmpty()) {
                MorpheHudManager.get().clear();
            }
            return;
        }
        MorpheHudManager.get().tick(minecraft);
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        MorpheHudManager.get().renderBeforeScreen(
            minecraft,
            event.getGuiGraphics(),
            event.getPartialTick()
        );
    }

    @SubscribeEvent
    public static void renderAboveScreen(ScreenEvent.Render.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        MorpheHudManager.get().renderAfterScreen(
            minecraft,
            event.getGuiGraphics(),
            event.getPartialTick()
        );
    }

    @Mod.EventBusSubscriber(modid = Morphe.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ReloadRegistration {
        private ReloadRegistration() {
        }

        @SubscribeEvent
        public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
                @Override
                protected Void prepare(ResourceManager resources, ProfilerFiller profiler) {
                    return null;
                }

                @Override
                protected void apply(Void ignored, ResourceManager resources, ProfilerFiller profiler) {
                    MinecraftUiCanvas.clearImageSizeCache();
                }
            });
        }
    }
}
