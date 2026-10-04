package com.krisamerman.kingdomrts.unit;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.KingdomRts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// A unit with a military occupation. It belongs to a faction and holds position near a post.
// Its combat stats come from a MilitaryStats datapack entry (see statsId), not from this class.
// It attacks hostile mobs within its attack range without leaving its post, and never attacks
// other units of any faction (factions aren't hostile to each other until a war mechanism exists).
public class MilitaryUnit extends PathfinderMob {
    // How far from its post the unit may wander. Behavior tuning, not a per-unit-type stat.
    public static final int HOLD_RADIUS = 6;

    private static final String FACTION_TAG = "faction";
    private static final String POST_TAG = "post";
    private static final String STATS_TAG = "stats";

    // Faction id. Placeholder until a real faction data model exists; "" means no faction.
    private String faction = "";
    @Nullable
    private BlockPos post;
    private ResourceLocation statsId = MilitaryStats.DEFAULT_ID;
    // Not a vanilla attribute, so it's kept here. Used both to pick targets and as melee reach.
    private double attackRange;

    public MilitaryUnit(EntityType<? extends MilitaryUnit> type, Level level) {
        super(type, level);
        setPersistenceRequired(); // never despawn
    }

    // Attributes every MilitaryUnit has, registered at game startup (before datapacks load).
    // These values are only fallbacks: applyStats overwrites them from the JSON entry.
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    // Goals are the vanilla mob AI building blocks; lower number = higher priority.
    // goalSelector = what the unit does; targetSelector = who it's fighting (sets getTarget()).
    //
    // restrictTo(post, HOLD_RADIUS) does the "hold position" work: random strolls stay inside
    // the restriction, and MoveTowardsRestrictionGoal walks the unit back if it ends up outside
    // (e.g. knocked back during a fight). Once a fight ends (target dead or out of range, see
    // HostileInRangeTargetGoal), the attack goal stops and these goals take over again.
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new HoldPostAttackGoal(this));
        goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HostileInRangeTargetGoal(this));
    }

    // What a military unit treats as a hostile mob. Neutral mobs (endermen, zombified piglins, ...)
    // are left alone so units don't start fights with them. Units themselves are never hostile.
    public static boolean isHostileMob(Entity entity) {
        return entity instanceof Enemy && !(entity instanceof NeutralMob);
    }

    public boolean isInAttackRange(LivingEntity target) {
        return distanceToSqr(target) <= attackRange * attackRange;
    }

    // Called once when the unit is created (e.g. by the spawn command), before it's added to the world.
    public void setup(String faction, BlockPos post, ResourceLocation statsId) {
        this.faction = faction;
        this.post = post.immutable();
        this.statsId = statsId;
        restrictTo(this.post, HOLD_RADIUS);
        applyStats();
        setHealth(getMaxHealth());
    }

    // Copies the datapack stats onto the entity's attributes.
    private void applyStats() {
        MilitaryStats.get(level(), statsId).ifPresentOrElse(stats -> {
            setBaseValue(Attributes.MAX_HEALTH, stats.maxHealth());
            setBaseValue(Attributes.MOVEMENT_SPEED, stats.movementSpeed());
            setBaseValue(Attributes.ATTACK_DAMAGE, stats.attackDamage());
            attackRange = stats.attackRange();
        }, () -> KingdomRts.LOGGER.warn("No military stats entry '{}'; unit keeps fallback stats", statsId));
    }

    private void setBaseValue(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public String getFaction() {
        return faction;
    }

    @Nullable
    public BlockPos getPost() {
        return post;
    }

    public double getAttackRange() {
        return attackRange;
    }

    // Advances the arm-swing animation timer each tick (on both server and client). Vanilla only
    // does this for Monster and Player, so without it swing() starts an animation that never plays.
    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
    }

    // Units are tracked individually and must not despawn like ordinary mobs.
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // Leashing replaces the mob's restriction, which would break its post.
    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(FACTION_TAG, faction);
        tag.putString(STATS_TAG, statsId.toString());
        if (post != null) {
            tag.put(POST_TAG, NbtUtils.writeBlockPos(post));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        faction = tag.getString(FACTION_TAG);
        ResourceLocation savedStats = ResourceLocation.tryParse(tag.getString(STATS_TAG));
        statsId = savedStats != null ? savedStats : MilitaryStats.DEFAULT_ID;
        post = NbtUtils.readBlockPos(tag, POST_TAG).orElse(null);
        // Vanilla doesn't save the restriction, so rebuild it from the post.
        if (post != null) {
            restrictTo(post, HOLD_RADIUS);
        }
        // Re-apply stats so JSON tuning reaches existing units; keep current health (capped).
        applyStats();
        setHealth(Math.min(getHealth(), getMaxHealth()));
    }
}
