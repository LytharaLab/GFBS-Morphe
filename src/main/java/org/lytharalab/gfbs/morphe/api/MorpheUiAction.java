package org.lytharalab.gfbs.morphe.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record MorpheUiAction(
    UUID sessionId,
    ResourceLocation document,
    String action,
    CompoundTag payload
) {
    public MorpheUiAction {
        payload = payload == null ? new CompoundTag() : payload.copy();
    }
}
