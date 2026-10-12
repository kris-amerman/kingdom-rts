package com.krisamerman.kingdomrts.settlement;

import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.FactionData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;

// "Entering Oakhaven (Dev)" / "Leaving Oakhaven" in a player's action bar as they cross a claim's
// edge. NeoForge fires EntityEvent.EnteringSection only when an entity moves into a new 16x16x16
// section, so this runs on chunk crossings rather than every tick, and needs no per-player state:
// the event says which chunk the player left and which they entered.
@EventBusSubscriber(modid = KingdomRts.MODID)
public final class TerritoryMessages {
    private TerritoryMessages() {
    }

    @SubscribeEvent
    static void onEnteringSection(EntityEvent.EnteringSection event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.didChunkChange()) {
            return; // only players, and only when the chunk (not just the height) changed
        }
        ServerLevel level = player.serverLevel();
        SettlementData settlements = SettlementData.get(level);
        Settlement from = settlements.claimAt(level.dimension(), chunkOf(event.getOldPos()));
        Settlement to = settlements.claimAt(level.dimension(), chunkOf(event.getNewPos()));
        String message = message(from, to, FactionData.get(level)::nameOf);
        if (message != null) {
            player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GOLD), true);
        }
    }

    // What to show when moving from one claim (or none) to another (or none), or null if nothing
    // changed. Moving straight from one settlement's claim into another's just says "Entering".
    @Nullable
    static String message(@Nullable Settlement from, @Nullable Settlement to, Function<UUID, String> factionName) {
        if (from == to) {
            return null;
        }
        if (to != null) {
            return "Entering " + to.name() + " (" + factionName.apply(to.owner()) + ")";
        }
        return "Leaving " + from.name();
    }

    private static ChunkPos chunkOf(SectionPos section) {
        return new ChunkPos(section.x(), section.z());
    }
}
