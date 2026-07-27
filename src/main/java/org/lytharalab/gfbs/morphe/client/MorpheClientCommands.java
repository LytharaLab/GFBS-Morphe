package org.lytharalab.gfbs.morphe.client;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lytharalab.gfbs.morphe.api.Morphe;

public final class MorpheClientCommands {
    private MorpheClientCommands() {
    }

    @Mod.EventBusSubscriber(modid = Morphe.MOD_ID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("morpheui")
                .then(Commands.literal("open")
                    .then(Commands.argument("document", ResourceLocationArgument.id())
                        .executes(MorpheClientCommands::open)))
                .then(Commands.literal("open_transparent")
                    .then(Commands.argument("document", ResourceLocationArgument.id())
                        .executes(MorpheClientCommands::openTransparent)))
                .then(Commands.literal("hud")
                    .then(Commands.argument("layer", ResourceLocationArgument.id())
                        .then(Commands.argument("document", ResourceLocationArgument.id())
                            .executes(MorpheClientCommands::hud))))
                .then(Commands.literal("hud_interactive")
                    .then(Commands.argument("layer", ResourceLocationArgument.id())
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                            .executes(MorpheClientCommands::hudInteractive))))
                .then(Commands.literal("hud_hide")
                    .then(Commands.argument("layer", ResourceLocationArgument.id())
                        .executes(MorpheClientCommands::hudHide)))
                .then(Commands.literal("reload")
                    .executes(MorpheClientCommands::reload))
                .then(Commands.literal("inspect")
                    .executes(MorpheClientCommands::inspect))
                .then(Commands.literal("widgets")
                    .executes(MorpheClientCommands::widgets))
            );
        }
    }

    private static int open(CommandContext<CommandSourceStack> context) {
        ResourceLocation document = ResourceLocationArgument.getId(context, "document");
        MorpheClient.open(document);
        context.getSource().sendSuccess(
            () -> Component.translatable("gfbs_morphe.command.opened", document),
            false
        );
        return 1;
    }

    private static int openTransparent(CommandContext<CommandSourceStack> context) {
        ResourceLocation document = ResourceLocationArgument.getId(context, "document");
        MorpheClient.openTransparent(document, new CompoundTag());
        context.getSource().sendSuccess(
            () -> Component.literal("Opened transparent Morphe screen " + document),
            false
        );
        return 1;
    }

    private static int hud(CommandContext<CommandSourceStack> context) {
        ResourceLocation layer = ResourceLocationArgument.getId(context, "layer");
        ResourceLocation document = ResourceLocationArgument.getId(context, "document");
        MorpheClient.showHud(
            layer,
            document,
            new CompoundTag(),
            org.lytharalab.gfbs.morphe.api.MorpheViewOptions.interactiveHud()
        );
        context.getSource().sendSuccess(
            () -> Component.literal("Showing Morphe HUD " + layer + " from " + document),
            false
        );
        return 1;
    }

    private static int hudInteractive(CommandContext<CommandSourceStack> context) {
        ResourceLocation layer = ResourceLocationArgument.getId(context, "layer");
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        MorpheClient.setHudInteractive(layer, enabled);
        context.getSource().sendSuccess(
            () -> Component.literal("Morphe HUD " + layer + " interaction: " + enabled),
            false
        );
        return 1;
    }

    private static int hudHide(CommandContext<CommandSourceStack> context) {
        ResourceLocation layer = ResourceLocationArgument.getId(context, "layer");
        MorpheClient.hideHud(layer);
        context.getSource().sendSuccess(
            () -> Component.literal("Hidden Morphe HUD " + layer),
            false
        );
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        if (!MorpheClient.reloadCurrent()) {
            context.getSource().sendFailure(Component.translatable("gfbs_morphe.command.no_screen"));
            return 0;
        }
        context.getSource().sendSuccess(
            () -> Component.translatable("gfbs_morphe.command.reloaded"),
            false
        );
        return 1;
    }

    private static int inspect(CommandContext<CommandSourceStack> context) {
        if (!MorpheClient.toggleInspector()) {
            context.getSource().sendFailure(Component.translatable("gfbs_morphe.command.no_screen"));
            return 0;
        }
        MorpheScreen screen = MorpheClient.currentScreen();
        boolean enabled = screen != null && screen.document() != null && screen.document().debug();
        context.getSource().sendSuccess(
            () -> Component.translatable("gfbs_morphe.command.inspect", enabled ? "ON" : "OFF"),
            false
        );
        return 1;
    }

    private static int widgets(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
            () -> Component.literal("Morphe widgets: " + String.join(", ", Morphe.get().widgetTypes())),
            false
        );
        return Morphe.get().widgetTypes().size();
    }
}
