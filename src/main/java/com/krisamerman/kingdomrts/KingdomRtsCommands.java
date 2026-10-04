package com.krisamerman.kingdomrts;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.settlement.TownHallBlockEntity;
import com.krisamerman.kingdomrts.unit.MilitaryStats;
import com.krisamerman.kingdomrts.unit.MilitaryUnit;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// Testing commands. Commands are built with Brigadier, Minecraft's command library: a tree of
// literal words and typed arguments, with executes(...) marking where a command can end.
@EventBusSubscriber(modid = KingdomRts.MODID)
public class KingdomRtsCommands {
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        // /kingdomrts spawnunit [townhall <pos>] [faction <faction>]
        event.getDispatcher().register(Commands.literal(KingdomRts.MODID)
                .requires(source -> source.hasPermission(2)) // ops / cheats enabled
                .then(Commands.literal("spawnunit")
                        .executes(ctx -> spawnUnit(ctx, null, null))
                        .then(factionArgument(null))
                        .then(Commands.literal("townhall")
                                .then(Commands.argument("townHall", BlockPosArgument.blockPos())
                                        .executes(ctx -> spawnUnit(ctx, townHallPos(ctx), null))
                                        .then(factionArgument(KingdomRtsCommands::townHallPos))))));
    }

    // The "faction <faction>" part, which can follow "spawnunit" or "spawnunit townhall <pos>".
    // townHallPos is null when no town hall was given.
    private static LiteralArgumentBuilder<CommandSourceStack> factionArgument(@Nullable TownHallPosGetter townHallPos) {
        return Commands.literal("faction")
                .then(Commands.argument("faction", StringArgumentType.word())
                        .executes(ctx -> spawnUnit(ctx,
                                townHallPos != null ? townHallPos.get(ctx) : null,
                                StringArgumentType.getString(ctx, "faction"))));
    }

    // A function that reads the town hall position out of a parsed command. (An interface with a
    // single method can be implemented by a lambda or method reference like KingdomRtsCommands::townHallPos.)
    private interface TownHallPosGetter {
        BlockPos get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    private static BlockPos townHallPos(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return BlockPosArgument.getLoadedBlockPos(ctx, "townHall");
    }

    // Spawns a military unit at the command user's position (run it via /execute positioned ...
    // to spawn somewhere else). Town hall and faction are both optional and independent:
    // - town hall given: the unit's post is the town hall; otherwise its post is where it spawned.
    // - faction given: the unit gets that faction; otherwise the town hall's faction, if any.
    private static int spawnUnit(CommandContext<CommandSourceStack> ctx, @Nullable BlockPos townHallPos,
            @Nullable String factionArg) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Vec3 spawnPos = source.getPosition();

        String faction = factionArg != null ? factionArg : "";
        BlockPos post = BlockPos.containing(spawnPos);
        if (townHallPos != null) {
            if (!(level.getBlockEntity(townHallPos) instanceof TownHallBlockEntity townHall)) {
                source.sendFailure(Component.literal("No town hall at " + townHallPos.toShortString()));
                return 0;
            }
            post = townHallPos;
            if (factionArg == null) {
                faction = townHall.getFaction();
            }
        }

        MilitaryUnit unit = KingdomRts.MILITARY_UNIT.get().create(level);
        if (unit == null) {
            return 0;
        }
        unit.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, source.getRotation().y, 0.0f);
        unit.setup(faction, post, MilitaryStats.DEFAULT_ID);
        level.addFreshEntity(unit);

        String factionText = faction.isEmpty() ? "no faction" : "faction " + faction;
        String postText = townHallPos != null ? "town hall at " + post.toShortString() : post.toShortString();
        source.sendSuccess(() -> Component.literal("Spawned military unit (" + factionText + "), posted at " + postText), true);
        return 1;
    }
}
