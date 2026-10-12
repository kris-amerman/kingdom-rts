package com.krisamerman.kingdomrts.settlement;

import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionCommands;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

// The "/kingdomrts settlement ..." commands, for inspecting and staging settlements while testing.
public final class SettlementCommands {
    private static final DynamicCommandExceptionType UNKNOWN_SETTLEMENT =
            new DynamicCommandExceptionType(name -> Component.literal("Unknown settlement: " + name));

    private SettlementCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("settlement")
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("here")
                        .executes(ctx -> here(ctx.getSource())))
                .then(Commands.literal("rename")
                        .then(settlementArgument("name")
                                .then(Commands.argument("newName", StringArgumentType.word())
                                        .executes(ctx -> rename(ctx.getSource(), getSettlement(ctx, "name"),
                                                StringArgumentType.getString(ctx, "newName"))))))
                // Founds a settlement for any faction: the same operation the Claim button uses,
                // handy for staging a rival's settlement without joining their faction.
                .then(Commands.literal("found")
                        .then(Commands.argument("townHall", BlockPosArgument.blockPos())
                                .then(FactionCommands.factionArgument("faction")
                                        .executes(ctx -> found(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "townHall"),
                                                FactionCommands.getFaction(ctx, "faction"))))));
    }

    // A settlement-name argument that Tab-completes existing settlement names.
    public static RequiredArgumentBuilder<CommandSourceStack, String> settlementArgument(String argName) {
        return Commands.argument(argName, StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        SettlementData.get(ctx.getSource().getServer()).names(), builder));
    }

    public static Settlement getSettlement(CommandContext<CommandSourceStack> ctx, String argName) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, argName);
        Settlement settlement = SettlementData.get(ctx.getSource().getServer()).byName(name);
        if (settlement == null) {
            throw UNKNOWN_SETTLEMENT.create(name);
        }
        return settlement;
    }

    private static int list(CommandSourceStack source) {
        SettlementData settlements = SettlementData.get(source.getServer());
        FactionData factions = FactionData.get(source.getServer());
        if (settlements.all().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No settlements yet"), false);
        }
        for (Settlement settlement : settlements.all()) {
            String line = settlement.name()
                    + " | owner: " + factions.nameOf(settlement.owner())
                    + " | town hall: " + settlement.townHall().toShortString()
                    + " in " + settlement.dimension().location()
                    + " | claim: " + settlement.chunks().size() + " chunks"
                    + " | units: " + settlement.units().size();
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return settlements.all().size();
    }

    private static int here(CommandSourceStack source) {
        ChunkPos chunk = new ChunkPos(BlockPos.containing(source.getPosition()));
        Settlement settlement = SettlementData.get(source.getServer()).claimAt(source.getLevel().dimension(), chunk);
        if (settlement == null) {
            source.sendSuccess(() -> Component.literal("Chunk " + chunk.x + ", " + chunk.z + " is unclaimed"), false);
            return 0;
        }
        String owner = FactionData.get(source.getServer()).nameOf(settlement.owner());
        source.sendSuccess(() -> Component.literal("Chunk " + chunk.x + ", " + chunk.z + " belongs to "
                + settlement.name() + " (" + owner + "), " + settlement.units().size() + " units"), false);
        return 1;
    }

    private static int rename(CommandSourceStack source, Settlement settlement, String newName) {
        String oldName = settlement.name();
        String problem = SettlementOps.rename(source.getLevel(), settlement, newName);
        if (problem != null) {
            source.sendFailure(Component.literal(problem));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Renamed " + oldName + " to " + newName), true);
        return 1;
    }

    private static int found(CommandSourceStack source, BlockPos townHall, Faction faction) {
        SettlementOps.FoundResult result = SettlementOps.found(source.getLevel(), townHall, faction);
        if (result.settlement() == null) {
            source.sendFailure(Component.literal(result.problem()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Founded " + result.settlement().name() + " for " + faction.name()), true);
        return 1;
    }
}
