package com.krisamerman.kingdomrts.settlement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.unit.MilitaryUnit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Per-town-hall data: which settlement this hall belongs to (none = an unclaimed hall) and the
// capture control meter (1-100, 100 = fully held by the owner). The settlement itself, including
// its owner and claim, lives in SettlementData. Control never sits at 0: a drain that would take
// it below 1 passes the settlement to a new owner instead.
public class TownHallBlockEntity extends BlockEntity {
    // Capture tuning. All of these will need playtesting.
    public static final int CHECK_INTERVAL_TICKS = 20;   // how often the meter updates (20 ticks = 1 second)
    public static final double PRESENCE_RADIUS = 16.0;   // blocks from the town hall's center
    // How much control changes per check, the same in both directions: a hall at 100 flips on the
    // 20th check of sustained hostile majority, and a new owner at 1 gets back to 100 in 20 checks.
    public static final int CONTROL_CHANGE_PER_CHECK = 5;
    public static final int MIN_CONTROL = 1;             // also the new owner's control right after a capture
    public static final int MAX_CONTROL = 100;
    // After an "under attack" alert, a hall won't alert again for this long (1200 ticks = 60 seconds),
    // so units stepping in and out of the capture radius don't spam the owner.
    public static final int ALERT_COOLDOWN_TICKS = 1200;

    private static final String CONTROL_TAG = "control";
    private static final String SETTLEMENT_TAG = "settlement";

    private int control = MAX_CONTROL;
    // The settlement this hall is the town hall of (see SettlementData), or null if unclaimed.
    @Nullable
    private UUID settlementId;

    // Capture meter UI state, server only. Not saved: after a reload the bar and alerts start fresh.
    @Nullable
    private CaptureBossBar bossBar; // created on first use, so client-side copies never make one
    private boolean wasDraining;
    private long lastAlertTick = -ALERT_COOLDOWN_TICKS;

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(KingdomRts.TOWN_HALL_BLOCK_ENTITY.get(), pos, state);
    }

    public int getControl() {
        return control;
    }

    @Nullable
    public UUID getSettlementId() {
        return settlementId;
    }

    // Server only: this hall's settlement, or null if the hall is unclaimed.
    @Nullable
    public Settlement getSettlement() {
        return level instanceof ServerLevel serverLevel ? SettlementData.get(serverLevel).byId(settlementId) : null;
    }

    // Server only: the faction that owns this hall's settlement, or null if the hall is unclaimed.
    @Nullable
    public UUID getOwner() {
        Settlement settlement = getSettlement();
        return settlement != null ? settlement.owner() : null;
    }

    // Called by SettlementOps.found once a settlement exists for this hall: full control.
    void settlementFounded(UUID settlementId) {
        this.settlementId = settlementId;
        ownerChanged(MAX_CONTROL);
    }

    // The settlement has a new owner (founded or captured): reset the meter to the given control.
    private void ownerChanged(int control) {
        this.control = Mth.clamp(control, MIN_CONTROL, MAX_CONTROL);
        // A new owner gets their own "under attack" alert, even if the old owner was just alerted.
        this.wasDraining = false;
        this.lastAlertTick = -ALERT_COOLDOWN_TICKS;
        setChanged(); // marks the chunk as needing to be saved
    }

    // A player claiming this unclaimed hall founds a settlement for their faction. A factionless
    // player founds a faction named after them first, but only once the settlement is sure to fit,
    // so a failed claim never leaves a stray faction behind.
    public SettlementOps.FoundResult claim(ServerPlayer player) {
        ServerLevel serverLevel = player.serverLevel();
        String problem = SettlementOps.foundingProblem(serverLevel, worldPosition);
        if (problem != null) {
            return SettlementOps.FoundResult.failed(problem);
        }
        FactionData factions = FactionData.get(serverLevel);
        Faction faction = factions.factionOf(player.getUUID());
        if (faction == null) {
            faction = factions.foundFactionFor(player);
            player.sendSystemMessage(Component.literal("You founded the faction " + faction.name()
                    + ". Rename it with /kingdomrts faction rename."));
        }
        return SettlementOps.found(serverLevel, worldPosition, faction);
    }

    // Called every server tick by the ticker from TownHallBlock#getTicker.
    public static void serverTick(Level level, BlockPos pos, BlockState state, TownHallBlockEntity townHall) {
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0 || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        FactionData factions = FactionData.get(serverLevel);
        Settlement settlement = townHall.getSettlement();

        // Unclaimed halls are claimed (the Claim button, see claim()), not captured: unit presence
        // has no effect and the meter never drains.
        if (settlement == null) {
            if (townHall.settlementId != null) {
                townHall.settlementId = null; // its settlement no longer exists
                townHall.setChanged();
            }
            if (townHall.bossBar != null) {
                townHall.bossBar.hide();
            }
            KingdomRts.LOGGER.debug("Town hall at {}: unclaimed", pos.toShortString());
            return;
        }
        UUID owner = settlement.owner();

        // Presence: only military units that belong to a faction. Mobs never count, factionless
        // units never count, and units of factions not at war with the owner count as neither side.
        Vec3 center = Vec3.atCenterOf(pos);
        AABB area = new AABB(pos).inflate(PRESENCE_RADIUS);
        double radiusSqr = PRESENCE_RADIUS * PRESENCE_RADIUS;
        int defenders = 0;
        Map<UUID, Integer> attackersByFaction = new HashMap<>();
        for (MilitaryUnit unit : level.getEntitiesOfClass(MilitaryUnit.class, area,
                e -> e.isAlive() && e.getFactionId() != null && withinRadius(e, center, radiusSqr))) {
            UUID faction = unit.getFactionId();
            if (owner.equals(faction)) {
                defenders++;
            } else if (factions.isHostile(owner, faction)) {
                attackersByFaction.merge(faction, 1, Integer::sum);
            }
        }
        // Each hostile faction is compared with the defenders on its own; hostile factions are not
        // added together (they may well be at war with each other too). Only the strongest one matters.
        int strongestAttackers = attackersByFaction.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        // Majority rule: the strongest hostile faction outnumbers the defenders -> drain; the
        // defenders outnumber it, or there are no hostile units at all -> regenerate; equal -> hold.
        //
        // Capture: when a drain would take control below 1, the hall changes owner on this check,
        // to the hostile faction with the most units present, at control 1 in its favor. From the
        // next check on, the same rule applies relative to the new owner, so it has to keep the
        // majority to push control back up; a hostile majority can flip it again. On a tie for most
        // attackers, control holds at 1 until one faction leads. Units don't change allegiance.
        int oldControl = townHall.control;
        int newControl = oldControl;
        UUID capturedBy = null;
        boolean tiedForCapture = false;
        if (strongestAttackers > defenders) {
            newControl -= CONTROL_CHANGE_PER_CHECK;
            if (newControl < MIN_CONTROL) {
                newControl = MIN_CONTROL;
                capturedBy = strongest(attackersByFaction);
                tiedForCapture = capturedBy == null;
            }
        } else if (strongestAttackers < defenders || strongestAttackers == 0) {
            newControl = Math.min(newControl + CONTROL_CHANGE_PER_CHECK, MAX_CONTROL);
        }

        String outcome = "";
        if (capturedBy != null) {
            SettlementOps.transfer(serverLevel, settlement, capturedBy); // its claim goes with it
            townHall.ownerChanged(MIN_CONTROL);
            outcome = ", captured by " + factions.nameOf(capturedBy);
        } else if (newControl != oldControl) {
            townHall.control = newControl;
            townHall.setChanged();
        }
        if (tiedForCapture) {
            outcome = ", holding: tie for most attackers";
        }

        // Counts are relative to the owner at the start of this check.
        KingdomRts.LOGGER.debug("{} (town hall at {}): owner={}, attackers={} strongest={}, defenders={}, control={}{}",
                settlement.name(), pos.toShortString(), factions.nameOf(owner), describe(factions, attackersByFaction), strongestAttackers,
                defenders, newControl, outcome);

        // Hostile faction names, strongest first (ties in alphabetical order), for the bar and alerts.
        List<String> attackerNames = attackersByFaction.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()
                        .thenComparing(e -> factions.nameOf(e.getKey())))
                .map(e -> factions.nameOf(e.getKey()))
                .toList();
        boolean draining = strongestAttackers > defenders;

        if (capturedBy != null) {
            String message = settlement.name() + " was captured by " + factions.nameOf(capturedBy)
                    + " (from " + factions.nameOf(owner) + ")";
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
            // Show the new owner at 1 right away; the next check works out the attackers relative to them.
            townHall.bossBar().update(serverLevel, pos, factions.nameOf(capturedBy), settlement.name(), MIN_CONTROL,
                    CaptureBossBar.Trend.HOLDING, List.of());
            return;
        }

        CaptureBossBar.Trend trend = draining ? CaptureBossBar.Trend.DRAINING
                : strongestAttackers == defenders && strongestAttackers > 0 ? CaptureBossBar.Trend.HOLDING
                : CaptureBossBar.Trend.RISING;
        townHall.bossBar().update(serverLevel, pos, factions.nameOf(owner), settlement.name(), newControl, trend, attackerNames);

        // Alert the owner's members the moment the hall starts draining (not on every draining check).
        if (draining && !townHall.wasDraining && level.getGameTime() - townHall.lastAlertTick >= ALERT_COOLDOWN_TICKS) {
            townHall.lastAlertTick = level.getGameTime();
            alertUnderAttack(serverLevel, factions, settlement, attackerNames.get(0));
        }
        townHall.wasDraining = draining;
    }

    private CaptureBossBar bossBar() {
        if (bossBar == null) {
            bossBar = new CaptureBossBar();
        }
        return bossBar;
    }

    // Tells every online member of the owning faction, wherever they are, with a chat line and a bell.
    private static void alertUnderAttack(ServerLevel level, FactionData factions, Settlement settlement, String attackerName) {
        Faction ownerFaction = factions.byId(settlement.owner());
        if (ownerFaction == null) {
            return;
        }
        Component message = Component.literal(settlement.name() + " is under attack by " + attackerName + "!")
                .withStyle(ChatFormatting.RED);
        for (UUID member : factions.members(ownerFaction)) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(message);
                // Played to this player only, at their own position, so they hear it anywhere.
                // MASTER so it isn't silenced by the block-sounds volume slider.
                player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.MASTER, 1.0f, 1.0f);
            }
        }
    }

    // Called when the hall is broken or its chunk unloads: take the bar off everyone's screen.
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (bossBar != null) {
            bossBar.hide();
        }
    }

    // The faction with the most units, or null if there's a tie for most (or no one).
    @Nullable
    private static UUID strongest(Map<UUID, Integer> countsByFaction) {
        UUID best = null;
        int bestCount = 0;
        boolean tied = false;
        for (Map.Entry<UUID, Integer> entry : countsByFaction.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
                tied = false;
            } else if (entry.getValue() == bestCount) {
                tied = true;
            }
        }
        return tied ? null : best;
    }

    // e.g. "{Red=3, Green=1}"
    private static String describe(FactionData factions, Map<UUID, Integer> countsByFaction) {
        return countsByFaction.entrySet().stream()
                .map(e -> factions.nameOf(e.getKey()) + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static boolean withinRadius(Entity entity, Vec3 center, double radiusSqr) {
        return entity.distanceToSqr(center) <= radiusSqr;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(CONTROL_TAG, control);
        if (settlementId != null) {
            tag.putUUID(SETTLEMENT_TAG, settlementId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(CONTROL_TAG)) {
            control = Mth.clamp(tag.getInt(CONTROL_TAG), MIN_CONTROL, MAX_CONTROL);
        }
        // Halls saved before settlements existed stored an "owner" instead; they load unclaimed
        // and can be claimed again.
        settlementId = tag.hasUUID(SETTLEMENT_TAG) ? tag.getUUID(SETTLEMENT_TAG) : null;
    }
}
