package org.lytharalab.gfbs.morphe.server;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.lytharalab.gfbs.morphe.api.MorpheServer;

import java.util.Collection;

public final class MorpheCommands {
    private MorpheCommands() {
    }

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("morphe")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("open")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("document", ResourceLocationArgument.id())
                        .executes(MorpheCommands::open))))
            .then(Commands.literal("close")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(MorpheCommands::close)))
        );
    }

    private static int open(CommandContext<CommandSourceStack> context) {
        Collection<ServerPlayer> players;
        ResourceLocation document;
        try {
            players = EntityArgument.getPlayers(context, "targets");
            document = ResourceLocationArgument.getId(context, "document");
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        for (ServerPlayer player : players) {
            MorpheServer.open(player, document);
        }
        context.getSource().sendSuccess(
            () -> Component.literal("Opened " + document + " for " + players.size() + " player(s)"),
            true
        );
        return players.size();
    }

    private static int close(CommandContext<CommandSourceStack> context) {
        Collection<ServerPlayer> players;
        try {
            players = EntityArgument.getPlayers(context, "targets");
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        for (ServerPlayer player : players) {
            MorpheServer.close(player);
        }
        context.getSource().sendSuccess(
            () -> Component.literal("Closed " + players.size() + " Morphe session(s)"),
            true
        );
        return players.size();
    }
}
