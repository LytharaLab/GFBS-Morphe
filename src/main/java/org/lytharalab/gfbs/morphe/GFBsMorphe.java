package org.lytharalab.gfbs.morphe;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import org.lytharalab.gfbs.morphe.api.Morphe;
import org.lytharalab.gfbs.morphe.network.MorpheNetwork;
import org.lytharalab.gfbs.morphe.server.MorpheCommands;
import org.lytharalab.gfbs.morphe.server.MorpheSessionManager;
import org.slf4j.Logger;

@Mod(Morphe.MOD_ID)
public final class GFBsMorphe {
    private static final Logger LOGGER = LogUtils.getLogger();

    public GFBsMorphe() {
        Morphe.get().initialize();
        MorpheNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::playerLoggedOut);
        LOGGER.info(
            "GFBS Morphe {} initialized with {} widgets",
            Morphe.VERSION,
            Morphe.get().widgetTypes().size()
        );
    }

    private void registerCommands(RegisterCommandsEvent event) {
        MorpheCommands.register(event.getDispatcher());
    }

    private void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            MorpheSessionManager.closeAll(player);
        }
    }
}
