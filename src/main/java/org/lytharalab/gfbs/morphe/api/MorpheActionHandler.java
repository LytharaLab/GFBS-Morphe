package org.lytharalab.gfbs.morphe.api;

import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface MorpheActionHandler {
    void handle(ServerPlayer player, MorpheUiAction action);
}
