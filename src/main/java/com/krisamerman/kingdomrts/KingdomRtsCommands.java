package com.krisamerman.kingdomrts;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionCommands;
import com.krisamerman.kingdomrts.settlement.SettlementCommands;
import com.krisamerman.kingdomrts.settlement.TownHallBlockEntity;
import com.krisamerman.kingdomrts.unit.Occupation;
import com.krisamerman.kingdomrts.unit.Unit;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        event.getDispatcher().register(Commands.literal(KingdomRts.MODID)
                .requires(source -> source.hasPermission(2)) // ops / cheats enabled
                // /kingdomrts faction create|join|leave|rename|list|war|peace ...
                .then(FactionCommands.build())
                // /kingdomrts settlement list|here|rename|found ...
                .then(SettlementCommands.build())
                // /kingdomrts spawnunit [occupation <id>|none] [townhall <pos>] [faction <name>]
                .then(spawnOptions(Commands.literal("spawnunit"), ctx -> Occupation.SOLDIER)
                        .then(Commands.literal("occupation")
                                .then(spawnOptions(Commands.literal("none"), ctx -> null))
                                .then(spawnOptions(Commands.argument("occupation", ResourceLocationArgument.id())
                                        .suggests(KingdomRtsCommands::suggestOccupations),
                                        ctx -> ResourceLocationArgument.getId(ctx, "occupation"))))));
    }

    // Adds everything that can follow "spawnunit" or "spawnunit occupation <id>": nothing, or
    // "faction <faction>", or "townhall <pos>" optionally followed by "faction <faction>".
    // (T is the builder's own type, so the result can keep chaining .then(...) calls.)
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T spawnOptions(T builder, OccupationGetter occupation) {
        return builder
                .executes(ctx -> spawnUnit(ctx, occupation.get(ctx), null, null))
                .then(factionArgument(occupation, null))
                .then(Commands.literal("townhall")
                        .then(Commands.argument("townHall", BlockPosArgument.blockPos())
                                .executes(ctx -> spawnUnit(ctx, occupation.get(ctx), townHallPos(ctx), null))
                                .then(factionArgument(occupation, KingdomRtsCommands::townHallPos))));
    }

    // The "faction <faction>" part. townHallPos is null when no town hall was given.
    private static LiteralArgumentBuilder<CommandSourceStack> factionArgument(OccupationGetter occupation,
            @Nullable TownHallPosGetter townHallPos) {
        return Commands.literal("faction")
                .then(FactionCommands.factionArgument("faction")
                        .executes(ctx -> spawnUnit(ctx, occupation.get(ctx),
                                townHallPos != null ? townHallPos.get(ctx) : null,
                                FactionCommands.getFaction(ctx, "faction"))));
    }

    // Tab-completes the occupation ids loaded from datapacks.
    private static CompletableFuture<Suggestions> suggestOccupations(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(
                ctx.getSource().registryAccess().registryOrThrow(Occupation.REGISTRY_KEY).keySet(), builder);
    }

    // Functions that read a value out of a parsed command. (An interface with a single method can
    // be implemented by a lambda or a method reference like KingdomRtsCommands::townHallPos.)
    private interface TownHallPosGetter {
        BlockPos get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    private interface OccupationGetter {
        @Nullable
        ResourceLocation get(CommandContext<CommandSourceStack> ctx);
    }

    private static BlockPos townHallPos(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return BlockPosArgument.getLoadedBlockPos(ctx, "townHall");
    }

    // Spawns a unit at the command user's position (run it via /execute positioned ... to spawn
    // somewhere else). Occupation defaults to kingdomrts:soldier; "none" gives a unit with no
    // occupation. Town hall and faction are both optional and independent:
    // - town hall given: the unit's post is the town hall; otherwise its post is where it spawned.
    // - faction given: the unit belongs to it; otherwise the unit is factionless. (It does not
    //   inherit the town hall's owner.)
    private static int spawnUnit(CommandContext<CommandSourceStack> ctx, @Nullable ResourceLocation occupation,
            @Nullable BlockPos townHallPos, @Nullable Faction faction) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Vec3 spawnPos = source.getPosition();
        if (occupation != null && Occupation.get(level, occupation).isEmpty()) {
            source.sendFailure(Component.literal("Unknown occupation: " + occupation));
            return 0;
        }

        BlockPos post = BlockPos.containing(spawnPos);
        if (townHallPos != null) {
            if (!(level.getBlockEntity(townHallPos) instanceof TownHallBlockEntity)) {
                source.sendFailure(Component.literal("No town hall at " + townHallPos.toShortString()));
                return 0;
            }
            post = townHallPos;
        }

        Unit unit = KingdomRts.UNIT.get().create(level);
        if (unit == null) {
            return 0;
        }
        unit.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, source.getRotation().y, 0.0f);
        unit.setup(faction != null ? faction.id() : null, post, occupation);
        // Testing aid: every unit has the same skin, so show its faction and occupation as a name
        // tag. It isn't updated if the faction is renamed later.
        String factionText = faction != null ? faction.name() : "no faction";
        String occupationText = occupation != null ? occupation.getPath() : "no occupation";
        unit.setCustomName(Component.literal(factionText + " " + occupationText)
                .withStyle(faction != null ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        unit.setCustomNameVisible(true);
        level.addFreshEntity(unit);

        String postText = townHallPos != null ? "town hall at " + post.toShortString() : post.toShortString();
        source.sendSuccess(() -> Component.literal("Spawned unit (" + factionText + ", " + occupationText + "), posted at " + postText), true);
        return 1;
    }
}
