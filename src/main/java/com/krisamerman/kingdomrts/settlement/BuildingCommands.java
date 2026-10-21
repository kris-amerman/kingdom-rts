package com.krisamerman.kingdomrts.settlement;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.building.BuildingRules;
import com.krisamerman.kingdomrts.building.BuildingType;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionCommands;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.unit.Unit;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.TemplateRotationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;

// /kingdomrts building ...: commissioning buildings and checking on their sites, until the build
// tool exists.
//   commission <type> <pos> [<rotation>] [faction <name>]: for the given faction, or the player's
//   list: the sites of the settlement you're standing in
//   remaining <pos>: what's left to do at the site containing pos
//   cancel <pos>: forget the site and take its construction site block away (placed blocks stay)
//   demolish <pos>: remove the building containing pos, built or not: its blocks and its record
//   assign <pos> <units> / unassign <pos> <units>: put builders on the site containing pos, or take them off
//   finish <pos>: a cheat for testing: build it instantly, with no builder and no materials
public final class BuildingCommands {
    // How many materials "remaining" lists before saying "and N more".
    private static final int MATERIALS_SHOWN = 12;

    private BuildingCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("building")
                .then(Commands.literal("commission")
                        .then(Commands.argument("type", ResourceLocationArgument.id())
                                .suggests(BuildingCommands::suggestTypes)
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> commission(ctx, Rotation.NONE, null))
                                        .then(factionOption(Rotation.NONE))
                                        .then(Commands.argument("rotation", TemplateRotationArgument.templateRotation())
                                                .executes(ctx -> commission(ctx, TemplateRotationArgument.getRotation(ctx, "rotation"), null))
                                                .then(Commands.literal("faction")
                                                        .then(FactionCommands.factionArgument("faction")
                                                                .executes(ctx -> commission(ctx, TemplateRotationArgument.getRotation(ctx, "rotation"),
                                                                        FactionCommands.getFaction(ctx, "faction")))))))))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("remaining")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> remaining(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> cancel(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))))
                .then(Commands.literal("assign")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("units", EntityArgument.entities())
                                        .executes(ctx -> assign(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"),
                                                EntityArgument.getEntities(ctx, "units"), true)))))
                .then(Commands.literal("unassign")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("units", EntityArgument.entities())
                                        .executes(ctx -> assign(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"),
                                                EntityArgument.getEntities(ctx, "units"), false)))))
                .then(Commands.literal("demolish")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> demolish(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"), null))
                                .then(Commands.literal("faction")
                                        .then(FactionCommands.factionArgument("faction")
                                                .executes(ctx -> demolish(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"),
                                                        FactionCommands.getFaction(ctx, "faction")))))))
                .then(Commands.literal("finish")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> finish(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> factionOption(Rotation rotation) {
        return Commands.literal("faction")
                .then(FactionCommands.factionArgument("faction")
                        .executes(ctx -> commission(ctx, rotation, FactionCommands.getFaction(ctx, "faction"))));
    }

    // Tab-completes the building types loaded from datapacks.
    private static CompletableFuture<Suggestions> suggestTypes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(
                ctx.getSource().registryAccess().registryOrThrow(BuildingType.REGISTRY_KEY).keySet(), builder);
    }

    private static int commission(CommandContext<CommandSourceStack> ctx, Rotation rotation, @Nullable Faction faction) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ResourceLocation type = ResourceLocationArgument.getId(ctx, "type");
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        if (faction == null) {
            ServerPlayer player = source.getPlayer();
            faction = player != null ? FactionData.get(source.getServer()).factionOf(player.getUUID()) : null;
            if (faction == null) {
                source.sendFailure(Component.literal("You're not in a faction: join one, or add faction <name>"));
                return 0;
            }
        }
        BuildingOps.CommissionResult result = BuildingOps.commission(source.getLevel(), faction, type, pos, rotation);
        if (result.building() == null) {
            source.sendFailure(Component.literal(result.problem()));
            return 0;
        }
        String factionName = faction.name();
        source.sendSuccess(() -> Component.literal("Commissioned " + typeName(source.getLevel(), type) + " at " + pos.toShortString()
                + " for " + factionName + " (" + describe(result.building()) + ")"), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Settlement settlement = SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(BlockPos.containing(source.getPosition())));
        if (settlement == null) {
            source.sendFailure(Component.literal("You're not in a settlement's claim"));
            return 0;
        }
        if (settlement.buildings().isEmpty()) {
            source.sendSuccess(() -> Component.literal(settlement.name() + " has no buildings commissioned"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(settlement.name() + "'s buildings:"), false);
        for (Building building : settlement.buildings()) {
            BuildingOps.RemainingResult result = BuildingOps.remaining(level, building);
            String progress = building.demolishing() ? "being demolished"
                    : building.isBeingClearedOut() ? "built on day " + (building.built() / 24000 + 1) + ", its site's stock being cleared out"
                    : building.isBuilt() ? "built on day " + (building.built() / 24000 + 1)
                    : result.remaining() == null ? result.problem()
                    : result.remaining().isDone() ? "finished, waiting to be completed"
                    : result.remaining().missing().size() + " blocks to place";
            source.sendSuccess(() -> Component.literal("- " + typeName(level, building.type()) + " at " + building.origin().toShortString()
                    + ": " + progress), false);
        }
        return settlement.buildings().size();
    }

    private static int remaining(CommandSourceStack source, BlockPos pos) {
        Building building = buildingAt(source, pos);
        if (building == null) {
            return 0;
        }
        String name = typeName(source.getLevel(), building.type());
        if (building.isBuilt()) {
            source.sendSuccess(() -> Component.literal("The " + name + " is built"), false);
            return 0;
        }
        BuildingOps.RemainingResult result = BuildingOps.remaining(source.getLevel(), building);
        if (result.remaining() == null) {
            source.sendFailure(Component.literal(result.problem()));
            return 0;
        }
        BuildingRules.Remaining remaining = result.remaining();
        if (remaining.isDone()) {
            source.sendSuccess(() -> Component.literal("The " + name + " is finished"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("The " + name + ": " + remaining.missing().size() + " blocks to place, "
                + remaining.obstructions().size() + " in the way"), false);
        if (!remaining.materials().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Materials: " + materials(remaining.materials())), false);
        }
        if (!remaining.unplaceable().isEmpty()) {
            source.sendSuccess(() -> Component.literal(remaining.unplaceable().size() + " blocks have no item to place them with"), false);
        }
        return remaining.missing().size();
    }

    private static int cancel(CommandSourceStack source, BlockPos pos) {
        Building building = buildingAt(source, pos);
        if (building == null) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        if (building.demolishing()) {
            BuildingOps.stopDemolition(level, settlementOf(level, building), building);
            source.sendSuccess(() -> Component.literal("Stopped demolishing the " + typeName(level, building.type()) + " at "
                    + building.origin().toShortString()), true);
            return 1;
        }
        if (building.isBuilt()) {
            source.sendFailure(Component.literal("The " + typeName(level, building.type()) + " is already built"));
            return 0;
        }
        BuildingOps.cancel(level, settlementOf(level, building), building);
        source.sendSuccess(() -> Component.literal("Cancelled the " + typeName(level, building.type()) + " at "
                + building.origin().toShortString()), true);
        return 1;
    }

    // Assigns builders to (or unassigns them from) the site containing pos, one by one, reporting each refusal.
    private static int assign(CommandSourceStack source, BlockPos pos, Collection<? extends Entity> entities, boolean assign) {
        Building building = buildingAt(source, pos);
        if (building == null) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        Settlement settlement = settlementOf(level, building);
        String name = typeName(level, building.type());
        int changed = 0;
        for (Entity entity : entities) {
            if (!(entity instanceof Unit unit)) {
                continue;
            }
            if (assign) {
                String problem = BuilderOps.assign(level, settlement, building, unit.getUUID());
                if (problem != null) {
                    source.sendFailure(Component.literal(problem));
                    continue;
                }
            } else if (!BuilderOps.unassign(level, settlement, building, unit.getUUID())) {
                source.sendFailure(Component.literal(unit.getName().getString() + " isn't working on the " + name));
                continue;
            }
            changed++;
        }
        if (changed > 0) {
            int total = changed;
            source.sendSuccess(() -> Component.literal((assign ? "Assigned " : "Unassigned ") + total + " builder(s) " + (assign ? "to" : "from")
                    + " the " + name), true);
        }
        return changed;
    }

    // Orders a building demolished, for the player's faction or the one named: its builders take it
    // apart (assign them as to a site).
    private static int demolish(CommandSourceStack source, BlockPos pos, @Nullable Faction faction) {
        Building building = buildingAt(source, pos);
        if (building == null) {
            return 0;
        }
        if (faction == null) {
            ServerPlayer player = source.getPlayer();
            faction = player != null ? FactionData.get(source.getServer()).factionOf(player.getUUID()) : null;
            if (faction == null) {
                source.sendFailure(Component.literal("You're not in a faction: join one, or add faction <name>"));
                return 0;
            }
        }
        ServerLevel level = source.getLevel();
        String problem = BuildingOps.orderDemolition(level, faction, settlementOf(level, building), building);
        if (problem != null) {
            source.sendFailure(Component.literal(problem));
            return 0;
        }
        String name = typeName(level, building.type());
        source.sendSuccess(() -> Component.literal("Ordered the " + name + " at " + building.origin().toShortString()
                + " demolished: assign builders to take it apart"), true);
        return 1;
    }

    private static int finish(CommandSourceStack source, BlockPos pos) {
        Building building = buildingAt(source, pos);
        if (building == null) {
            return 0;
        }
        if (building.isBuilt()) {
            source.sendFailure(Component.literal("The " + typeName(source.getLevel(), building.type()) + " is already built"));
            return 0;
        }
        int changed = BuildingOps.finish(source.getLevel(), settlementOf(source.getLevel(), building), building);
        if (changed < 0) {
            source.sendFailure(Component.literal(BuildingOps.remaining(source.getLevel(), building).problem()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Finished the " + typeName(source.getLevel(), building.type()) + " (" + changed + " blocks set)"), true);
        return changed;
    }

    // The building whose site contains pos, or null (after telling the source).
    @Nullable
    private static Building buildingAt(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        Settlement settlement = SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(pos));
        Building building = settlement != null ? settlement.buildingContaining(pos) : null;
        if (building == null) {
            source.sendFailure(Component.literal("No building is commissioned at " + pos.toShortString()));
        }
        return building;
    }

    private static Settlement settlementOf(ServerLevel level, Building building) {
        return SettlementData.get(level).claimAt(level.dimension(), new ChunkPos(building.origin()));
    }

    private static String typeName(ServerLevel level, ResourceLocation type) {
        return BuildingType.get(level, type).map(BuildingType::name).orElse(type.toString());
    }

    private static String describe(Building building) {
        var box = building.footprint();
        return box.getXSpan() + "x" + box.getYSpan() + "x" + box.getZSpan() + ", turned " + building.rotation().getSerializedName();
    }

    // "12 Oak Planks, 4 Cobblestone, ..." (most needed first).
    static String materials(Map<Item, Integer> materials) {
        String shown = materials.entrySet().stream()
                .sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
                .limit(MATERIALS_SHOWN)
                .map(entry -> entry.getValue() + " " + entry.getKey().getDescription().getString())
                .collect(Collectors.joining(", "));
        int more = materials.size() - MATERIALS_SHOWN;
        return more > 0 ? shown + ", and " + more + " more" : shown;
    }
}
