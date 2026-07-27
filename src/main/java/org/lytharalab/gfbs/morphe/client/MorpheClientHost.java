package org.lytharalab.gfbs.morphe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.lytharalab.gfbs.morphe.api.MorpheViewOptions;
import org.lytharalab.gfbs.morphe.core.UiHost;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

final class MorpheClientHost implements UiHost {
    private final MorpheViewOptions options;
    private final MorpheEnvironment environment = new MorpheEnvironment();
    private final Runnable closeAction;
    private final Consumer<Boolean> interactionAction;
    private final Runnable optionsChanged;
    private boolean interactive;
    private double delta;

    MorpheClientHost(
        MorpheViewOptions options,
        Runnable closeAction,
        Consumer<Boolean> interactionAction,
        Runnable optionsChanged
    ) {
        this.options = Objects.requireNonNull(options, "options");
        this.closeAction = closeAction == null ? () -> {
        } : closeAction;
        this.interactionAction = interactionAction == null ? ignored -> {
        } : interactionAction;
        this.optionsChanged = optionsChanged == null ? () -> {
        } : optionsChanged;
    }

    void delta(double value) {
        delta = Math.max(0, value);
    }

    void gameTick() {
        environment.gameTick();
    }

    MorpheViewOptions options() {
        return options;
    }

    @Override
    public Map<String, ?> environment() {
        return environment.capture(Minecraft.getInstance(), options, interactive, delta);
    }

    @Override
    public void configure(Map<String, ?> values) {
        options.apply(values);
        if (options.inputMode() == MorpheViewOptions.InputMode.PASSIVE && interactive) {
            setInteractive(false);
        }
        optionsChanged.run();
    }

    @Override
    public void setInteractive(boolean value) {
        boolean next = value && options.inputMode() != MorpheViewOptions.InputMode.PASSIVE;
        if (interactive != next) {
            interactive = next;
            interactionAction.accept(next);
        }
    }

    void setInteractiveFromManager(boolean value) {
        interactive = value;
    }

    @Override
    public boolean interactive() {
        return interactive;
    }

    @Override
    public void playSound(String sound, float volume, float pitch) {
        ResourceLocation id;
        try {
            id = new ResourceLocation(sound);
        } catch (RuntimeException ignored) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
            SoundEvent.createVariableRangeEvent(id),
            Math.max(0.01F, Math.min(4F, pitch)),
            Math.max(0F, Math.min(4F, volume))
        ));
    }

    @Override
    public void closeView() {
        closeAction.run();
    }
}
