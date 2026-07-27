package org.lytharalab.gfbs.morphe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lytharalab.gfbs.morphe.script.MorpheLuaRuntime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class MorpheResourceLoader {
    private MorpheResourceLoader() {
    }

    static String readScript(ResourceLocation id) throws IOException {
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(id)
            .orElseThrow(() -> new IOException("Resource not found: " + id));
        try (InputStream input = resource.open()) {
            byte[] bytes = input.readNBytes(MorpheLuaRuntime.MAX_SCRIPT_BYTES + 1);
            if (bytes.length > MorpheLuaRuntime.MAX_SCRIPT_BYTES) {
                throw new IOException("Script exceeds " + MorpheLuaRuntime.MAX_SCRIPT_BYTES + " bytes");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
