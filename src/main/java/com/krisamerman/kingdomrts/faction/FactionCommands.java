package com.krisamerman.kingdomrts.faction;

import java.util.UUID;
import java.util.stream.Collectors;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// The "/kingdomrts faction ..." commands, for creating and inspecting factions while testing.
public final class FactionCommands {
    private static final DynamicCommandExceptionType UNKNOWN_FACTION =
            new DynamicCommandExceptionType(name -> Component.literal("Unknown faction: " + name));

    private FactionCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("faction")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("join")
                        .then(factionArgument("name").executes(ctx -> join(ctx.getSource(), getFaction(ctx, "name")))))
                .then(Commands.literal("leave")
                        .executes(ctx -> leave(ctx.getSource())))
                .then(Commands.literal("rename")
                        .then(factionArgument("name")
                                .then(Commands.argument("newName", StringArgumentType.word())
                                        .executes(ctx -> rename(ctx.getSource(), getFaction(ctx, "name"),
                                                StringArgumentType.getString(ctx, "newName"))))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("war")
                        .then(factionArgument("a").then(factionArgument("b")
                                .executes(ctx -> setHostile(ctx.getSource(), getFaction(ctx, "a"), getFaction(ctx, "b"), true)))))
                .then(Commands.literal("peace")
                        .then(factionArgument("a").then(factionArgument("b")
                                .executes(ctx -> setHostile(ctx.getSource(), getFaction(ctx, "a"), getFaction(ctx, "b"), false)))));
    }

    // A faction-name argument that Tab-completes existing faction names.
    public static RequiredArgumentBuilder<CommandSourceStack, String> factionArgument(String argName) {
        return Commands.argument(argName, StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        FactionData.get(ctx.getSource().getServer()).names(), builder));
    }

    // Reads a faction-name argument and looks it up, failing the command if there's no such faction.
    public static Faction getFaction(CommandContext<CommandSourceStack> ctx, String argName) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, argName);
        Faction faction = FactionData.get(ctx.getSource().getServer()).byName(name);
        if (faction == null) {
            throw UNKNOWN_FACTION.create(name);
        }
        return faction;
    }

    private static int create(CommandSourceStack source, String name) {
        FactionData factions = FactionData.get(source.getServer());
        String problem = factions.nameProblem(name);
        if (problem != null) {
            source.sendFailure(Component.literal(problem));
            return 0;
        }
        Faction faction = factions.create(name, null);
        source.sendSuccess(() -> Component.literal("Created faction " + faction.name()), true);
        return 1;
    }

    private static int join(CommandSourceStack source, Faction faction) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FactionData.get(source.getServer()).join(player.getUUID(), faction);
        source.sendSuccess(() -> Component.literal("You joined " + faction.name()), true);
        return 1;
    }

    private static int leave(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Faction old = FactionData.get(source.getServer()).leave(player.getUUID());
        if (old == null) {
            source.sendFailure(Component.literal("You aren't in a faction"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("You left " + old.name()), true);
        return 1;
    }

    private static int rename(CommandSourceStack source, Faction faction, String newName) {
        FactionData factions = FactionData.get(source.getServer());
        String problem = factions.nameProblem(newName);
        if (problem != null) {
            source.sendFailure(Component.literal(problem));
            return 0;
        }
        String oldName = faction.name();
        factions.rename(faction, newName);
        source.sendSuccess(() -> Component.literal("Renamed " + oldName + " to " + newName), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        FactionData factions = FactionData.get(server);
        if (factions.all().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No factions yet"), false);
        }
        for (Faction faction : factions.all()) {
            String members = factions.members(faction).stream().map(p -> playerName(server, p)).collect(Collectors.joining(", "));
            String enemies = factions.enemiesOf(faction).stream().map(Faction::name).collect(Collectors.joining(", "));
            String line = faction.name()
                    + " | leader: " + (faction.leader() != null ? playerName(server, faction.leader()) : "none")
                    + " | members: " + (members.isEmpty() ? "none" : members)
                    + " | at war with: " + (enemies.isEmpty() ? "nobody" : enemies);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        ServerPlayer player = source.getPlayer(); // null if run from the server console
        if (player != null) {
            Faction own = factions.factionOf(player.getUUID());
            source.sendSuccess(() -> Component.literal("You are in: " + (own != null ? own.name() : "no faction")), false);
        }
        return factions.all().size();
    }

    private static int setHostile(CommandSourceStack source, Faction a, Faction b, boolean hostile) {
        if (a == b) {
            source.sendFailure(Component.literal("A faction can't be at war with itself"));
            return 0;
        }
        boolean changed = FactionData.get(source.getServer()).setHostile(a, b, hostile);
        String state = hostile ? "at war" : "at peace";
        if (!changed) {
            source.sendFailure(Component.literal(a.name() + " and " + b.name() + " are already " + state));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(a.name() + " and " + b.name() + " are now " + state), true);
        return 1;
    }

    // A player's name if they're online or known to the server, otherwise their UUID.
    private static String playerName(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(player).map(GameProfile::getName).orElse(player.toString());
        }
        return player.toString();
    }
}
