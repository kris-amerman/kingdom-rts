package com.krisamerman.kingdomrts.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.settlement.SettlementOps;
import com.krisamerman.kingdomrts.settlement.WorkplaceOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// A unit: an NPC tracked individually, unlike a generic mob. Everything about it is optional and
// independent: it may have a faction, a settlement, a home, items in its inventory and an
// occupation, in any combination. What it does comes from its occupation (see Occupation), which
// is a datapack entry; a unit with no occupation just idles near its post.
public class Unit extends PathfinderMob implements InventoryCarrier {
    // How far from its post the unit may wander. Behavior tuning, not a per-occupation stat.
    public static final int HOLD_RADIUS = 6;
    public static final int INVENTORY_SIZE = 9;
    // How often an unemployed settler looks for work (10 seconds), each unit at its own moment.
    public static final int JOB_SEEK_INTERVAL_TICKS = 200;
    // How often a unit looks for items on the ground to pick up (vanilla mobs look every tick).
    private static final int PICKUP_INTERVAL_TICKS = 10;

    private static final String FACTION_TAG = "faction_id";
    private static final String SETTLEMENT_TAG = "settlement";
    private static final String SEEN_OWNER_CHANGES_TAG = "seen_owner_changes";
    private static final String HOME_TAG = "home";
    private static final String POST_TAG = "post";
    private static final String OCCUPATION_TAG = "occupation";
    private static final String INVENTORY_TAG = "inventory";
    private static final String WORKPLACE_TAG = "workplace";

    // The faction this unit belongs to (see FactionData), or null for a factionless unit
    // (a mercenary, say). A factionless unit is never part of any faction's presence or wars.
    @Nullable
    private UUID factionId;
    // The settlement this unit belongs to (it's also in that settlement's unit list). Changed only
    // through SettlementOps, which keeps both sides in step.
    @Nullable
    private UUID settlementId;
    // How many owner changes of its settlement this unit has caught up with. If the settlement was
    // captured while this unit was unloaded, the numbers differ when it loads, and it catches up
    // then (see SettlementOps#catchUp).
    private int seenOwnerChanges;
    // Where it lives, independent of its settlement.
    @Nullable
    private BlockPos home;
    // The workplace it's employed at (see WorkplaceOps), or null if it has no job there. Changed
    // only through WorkplaceOps, which keeps the workplace's worker list in step.
    @Nullable
    private BlockPos workplace;
    // Where it stays: random strolls keep within HOLD_RADIUS of it.
    @Nullable
    private BlockPos post;
    @Nullable
    private ResourceLocation occupationId;
    // The occupation entry for occupationId, looked up when it's set or loaded. Null if the unit
    // has none, or its entry no longer exists.
    @Nullable
    private Occupation occupation;
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    // Goals added by the current occupation, so they can be removed if it changes.
    private final List<Goal> occupationGoals = new ArrayList<>();
    // Not a vanilla attribute, so it's kept here. Used both to pick targets and as melee reach.
    // Zero unless the occupation sets it.
    private double attackRange;

    public Unit(EntityType<? extends Unit> type, Level level) {
        super(type, level);
        setPersistenceRequired(); // never despawn
    }

    // Attributes every unit has, registered at game startup (before datapacks load). These values
    // are only fallbacks: an occupation can overwrite them from its JSON entry.
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                // Player attributes for breaking blocks, so tools and their enchantments (Efficiency)
                // work for units the way they do for players (see BreakTime).
                .add(Attributes.MINING_EFFICIENCY)
                .add(Attributes.BLOCK_BREAK_SPEED);
    }

    // Goals every unit has, whatever its occupation (lower number = higher priority). The
    // occupation adds its own around these. Vanilla only calls this on the server.
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    // Called once when the unit is created (e.g. by the spawn command), before it's added to the
    // world. occupationId may be null for a unit with no occupation.
    public void setup(@Nullable UUID factionId, BlockPos post, @Nullable ResourceLocation occupationId) {
        giveNameIfNone();
        this.factionId = factionId;
        setPost(post);
        this.occupationId = occupationId;
        applyOccupation();
        setHealth(getMaxHealth());
    }

    // ---- Occupation ----

    // Use WorkplaceOps (hiring, firing) for jobs; this just swaps the occupation and its goals.
    public void setOccupation(@Nullable ResourceLocation occupationId) {
        this.occupationId = occupationId;
        applyOccupation();
    }

    // Looks up the occupation entry and lets it set up the unit's stats and goals.
    private void applyOccupation() {
        occupationGoals.forEach(goal -> {
            goalSelector.removeGoal(goal);
            targetSelector.removeGoal(goal);
        });
        occupationGoals.clear();
        attackRange = 0;
        setPost(post); // back to keeping near its post, unless the occupation says otherwise
        occupation = occupationId != null ? Occupation.get(level(), occupationId).orElse(null) : null;
        if (occupationId != null && occupation == null) {
            KingdomRts.LOGGER.warn("No occupation entry '{}'; unit has no occupation", occupationId);
        }
        if (occupation != null) {
            occupation.applyTo(this);
        }
    }

    // For occupations: adds an AI goal that's removed again if the occupation changes.
    // target = true for target-selection goals (who to fight), false for what to do.
    public void addOccupationGoal(boolean target, int priority, Goal goal) {
        if (level().isClientSide) {
            return; // AI only runs on the server
        }
        (target ? targetSelector : goalSelector).addGoal(priority, goal);
        occupationGoals.add(goal);
    }

    // For occupations: sets an attribute's base value (e.g. max health from the JSON entry).
    public void setBaseValue(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    void setAttackRange(double attackRange) {
        this.attackRange = attackRange;
    }

    @Nullable
    public ResourceLocation getOccupationId() {
        return occupationId;
    }

    @Nullable
    public Occupation getOccupation() {
        return occupation;
    }

    // Whether this unit has a military occupation (see Occupation#isMilitary).
    public boolean isMilitary() {
        return occupation != null && occupation.isMilitary();
    }

    // ---- Combat ----

    // What a unit treats as a hostile mob. Neutral mobs (endermen, zombified piglins, ...)
    // are left alone so units don't start fights with them. Units themselves are never hostile.
    public static boolean isHostileMob(Entity entity) {
        return entity instanceof Enemy && !(entity instanceof NeutralMob);
    }

    // Who this unit fights: hostile mobs, and units of factions at war with its own. A factionless
    // unit only fights hostile mobs, since FactionData.isHostile is false whenever either side is null.
    public boolean isValidTarget(LivingEntity target) {
        if (isHostileMob(target)) {
            return true;
        }
        return target instanceof Unit other && level() instanceof ServerLevel serverLevel
                && FactionData.get(serverLevel).isHostile(factionId, other.factionId);
    }

    public boolean isInAttackRange(LivingEntity target) {
        return distanceToSqr(target) <= attackRange * attackRange;
    }

    public double getAttackRange() {
        return attackRange;
    }

    // ---- Faction, settlement, home, post, inventory ----

    @Nullable
    public UUID getFactionId() {
        return factionId;
    }

    @Nullable
    public UUID getSettlementId() {
        return settlementId;
    }

    public int getSeenOwnerChanges() {
        return seenOwnerChanges;
    }

    // Use SettlementOps.setUnitSettlement, which also updates the settlement's unit list.
    public void setSettlement(@Nullable UUID settlementId, int seenOwnerChanges) {
        this.settlementId = settlementId;
        this.seenOwnerChanges = seenOwnerChanges;
    }

    // Use SettlementOps (capture, catching up), which applies the rules for who changes faction.
    public void setFactionId(@Nullable UUID factionId) {
        this.factionId = factionId;
    }

    @Nullable
    public BlockPos getWorkplace() {
        return workplace;
    }

    // Use WorkplaceOps.hire / fire, which also update the workplace.
    public void setWorkplace(@Nullable BlockPos workplace) {
        this.workplace = workplace != null ? workplace.immutable() : null;
    }

    @Nullable
    public BlockPos getHome() {
        return home;
    }

    public void setHome(@Nullable BlockPos home) {
        this.home = home != null ? home.immutable() : null;
    }

    @Nullable
    public BlockPos getPost() {
        return post;
    }

    private void setPost(@Nullable BlockPos post) {
        this.post = post != null ? post.immutable() : null;
        if (this.post != null) {
            restrictTo(this.post, HOLD_RADIUS);
        } else {
            clearRestriction();
        }
    }

    // (Also InventoryCarrier's: vanilla's shared code for mobs that carry items, like villagers.)
    @Override
    public SimpleContainer getInventory() {
        return inventory;
    }

    // ---- Picking up items ----

    // Units pick up items lying on the ground nearby, as long as they have room. Vanilla's Mob
    // checks this each tick and then looks for items within reach; answering yes only every few
    // ticks keeps that search cheap. (Like other mobs, units don't pick up items when the
    // mobGriefing game rule is off.) This will likely need rules later, so units don't hoard trash.
    @Override
    public boolean canPickUpLoot() {
        return tickCount % PICKUP_INTERVAL_TICKS == 0;
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return inventory.canAddItem(stack) && (occupation == null || occupation.wantsToPickUp(stack));
    }

    // Into the inventory, rather than vanilla's default of equipping the item.
    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        InventoryCarrier.pickUpItem(this, this, itemEntity);
    }

    // Every unit has a name (see UnitNames), kept as vanilla's custom name, so it's saved with the
    // unit and shown when a player looks at it (CustomNameVisible stays off: no names floating over
    // every unit in a crowd).
    private void giveNameIfNone() {
        if (!hasCustomName()) {
            setCustomName(Component.literal(UnitNames.pick()));
        }
    }

    // ---- Vanilla behavior ----

    // Advances the arm-swing animation timer each tick (on both server and client). Vanilla only
    // does this for Monster and Player, so without it swing() starts an animation that never plays.
    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
        // Job seeking (see WorkplaceOps#seekJob). The offset spreads units' checks across ticks.
        if (occupationId == null && settlementId != null && level() instanceof ServerLevel serverLevel
                && (tickCount + Math.floorMod(getUUID().hashCode(), JOB_SEEK_INTERVAL_TICKS)) % JOB_SEEK_INTERVAL_TICKS == 0) {
            WorkplaceOps.seekJob(serverLevel, this);
        }
    }

    // Units are tracked individually and must not despawn like ordinary mobs.
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // Right-clicking a unit opens its info panel (see UnitMenu for who sees what).
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            UnitMenu.open(serverPlayer, this);
        }
        // On the client, SUCCESS plays the hand-swing; the server opens the menu.
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // Leashing replaces the mob's restriction, which would break its post.
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    // Server: catches up with anything that happened to its settlement while it was unloaded
    // (a capture, or the settlement dissolving). Runs when the unit is spawned or loaded.
    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (level() instanceof ServerLevel serverLevel) {
            giveNameIfNone(); // units from before names existed
            SettlementOps.catchUp(serverLevel, this);
        }
    }

    // When a unit dies or is discarded (not when its chunk unloads), its settlement forgets it.
    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (reason.shouldDestroy() && level() instanceof ServerLevel serverLevel) {
            SettlementOps.unitRemoved(serverLevel, this);
        }
    }

    // A unit's inventory is real items, so they drop where it dies.
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        Containers.dropContents(level, this, inventory);
    }

    // ---- Saving ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (factionId != null) {
            tag.putUUID(FACTION_TAG, factionId);
        }
        if (settlementId != null) {
            tag.putUUID(SETTLEMENT_TAG, settlementId);
            tag.putInt(SEEN_OWNER_CHANGES_TAG, seenOwnerChanges);
        }
        if (home != null) {
            tag.put(HOME_TAG, NbtUtils.writeBlockPos(home));
        }
        if (workplace != null) {
            tag.put(WORKPLACE_TAG, NbtUtils.writeBlockPos(workplace));
        }
        if (post != null) {
            tag.put(POST_TAG, NbtUtils.writeBlockPos(post));
        }
        if (occupationId != null) {
            tag.putString(OCCUPATION_TAG, occupationId.toString());
        }
        // ContainerHelper keeps each item in its slot (SimpleContainer's own createTag doesn't).
        tag.put(INVENTORY_TAG, ContainerHelper.saveAllItems(new CompoundTag(), inventory.getItems(), registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        factionId = tag.hasUUID(FACTION_TAG) ? tag.getUUID(FACTION_TAG) : null;
        settlementId = tag.hasUUID(SETTLEMENT_TAG) ? tag.getUUID(SETTLEMENT_TAG) : null;
        seenOwnerChanges = tag.getInt(SEEN_OWNER_CHANGES_TAG);
        home = NbtUtils.readBlockPos(tag, HOME_TAG).orElse(null);
        workplace = NbtUtils.readBlockPos(tag, WORKPLACE_TAG).orElse(null);
        // Vanilla doesn't save the restriction, so setPost rebuilds it.
        setPost(NbtUtils.readBlockPos(tag, POST_TAG).orElse(null));
        occupationId = tag.contains(OCCUPATION_TAG, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(OCCUPATION_TAG)) : null;
        inventory.clearContent();
        ContainerHelper.loadAllItems(tag.getCompound(INVENTORY_TAG), inventory.getItems(), registryAccess());
        // Re-apply the occupation so JSON tuning reaches existing units; keep current health (capped).
        applyOccupation();
        setHealth(Math.min(getHealth(), getMaxHealth()));
    }
}
