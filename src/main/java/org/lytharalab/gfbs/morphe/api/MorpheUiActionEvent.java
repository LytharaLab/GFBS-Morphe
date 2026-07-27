package org.lytharalab.gfbs.morphe.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public final class MorpheUiActionEvent extends Event {
    private final ServerPlayer player;
    private final MorpheUiAction action;

    public MorpheUiActionEvent(ServerPlayer player, MorpheUiAction action) {
        this.player = player;
        this.action = action;
    }

    public ServerPlayer player() {
        return player;
    }

    public MorpheUiAction action() {
        return action;
    }
}
