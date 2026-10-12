package com.krisamerman.kingdomrts.unit;

import java.util.Collection;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementCommands;
import com.krisamerman.kingdomrts.settlement.SettlementData;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

// The "/kingdomrts unit ..." commands, for staging and inspecting units while testing. Units are
// picked with entity selectors, e.g. @e[type=kingdomrts:unit,distance=..5].
public final class UnitCommands {
    private UnitCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("unit")
                // /kingdomrts unit join <units> <settlement>
                .then(Commands.literal("join")
                        .then(Commands.argument("units", EntityArgument.entities())
                                .then(SettlementCommands.settlementArgument("settlement")
                                        .executes(ctx -> join(ctx.getSource(), EntityArgument.getEntities(ctx, "units"),
                                                SettlementCommands.getSettlement(ctx, "settlement"))))))
                // /kingdomrts unit leave <units>
                .then(Commands.literal("leave")
                        .then(Commands.argument("units", EntityArgument.entities())
                                .executes(ctx -> join(ctx.getSource(), EntityArgument.getEntities(ctx, "units"), null))))
                // /kingdomrts unit info <units>
                .then(Commands.literal("info")
                        .then(Commands.argument("units", EntityArgument.entities())
                                .executes(ctx -> info(ctx.getSource(), EntityArgument.getEntities(ctx, "units")))));
    }

    // Makes units belong to a settlement, or to none (settlement = null). Doesn't change their faction.
    private static int join(CommandSourceStack source, Collection<? extends Entity> entities, @Nullable Settlement settlement) {
        int count = 0;
        for (Entity entity : entities) {
            if (entity instanceof Unit unit) {
                SettlementOps.setUnitSettlement((ServerLevel) unit.level(), unit, settlement);
                count++;
            }
        }
        if (count == 0) {
            source.sendFailure(Component.literal("No units selected"));
            return 0;
        }
        int total = count;
        String where = settlement != null ? "joined " + settlement.name() : "left their settlement";
        source.sendSuccess(() -> Component.literal(total + " unit(s) " + where), true);
        return count;
    }

    private static int info(CommandSourceStack source, Collection<? extends Entity> entities) {
        FactionData factions = FactionData.get(source.getServer());
        SettlementData settlements = SettlementData.get(source.getServer());
        int count = 0;
        for (Entity entity : entities) {
            if (entity instanceof Unit unit) {
                Settlement settlement = settlements.byId(unit.getSettlementId());
                String line = unit.getName().getString()
                        + " | faction: " + (unit.getFactionId() != null ? factions.nameOf(unit.getFactionId()) : "none")
                        + " | occupation: " + (unit.getOccupationId() != null ? unit.getOccupationId() : "none")
                        + " | settlement: " + (settlement != null ? settlement.name() : "none")
                        + " | health: " + Math.round(unit.getHealth()) + "/" + Math.round(unit.getMaxHealth());
                source.sendSuccess(() -> Component.literal(line), false);
                count++;
            }
        }
        if (count == 0) {
            source.sendFailure(Component.literal("No units selected"));
        }
        return count;
    }
}
