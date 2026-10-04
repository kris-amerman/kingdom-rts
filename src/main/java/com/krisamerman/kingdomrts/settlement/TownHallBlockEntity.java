package com.krisamerman.kingdomrts.settlement;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.unit.MilitaryUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Per-town-hall data: the capture control meter (0-100, 100 = fully held by the owner).
public class TownHallBlockEntity extends BlockEntity {
    // Capture tuning. All of these will need playtesting.
    public static final int CHECK_INTERVAL_TICKS = 20;   // how often the meter updates (20 ticks = 1 second)
    public static final double PRESENCE_RADIUS = 16.0;   // blocks from the town hall's center
    public static final int CAPTURE_MARGIN = 2;          // meter drains when attackers - defenders >= this
    public static final int DRAIN_PER_CHECK = 5;         // 100 -> 0 in 20 checks of sustained pressure
    public static final int REGEN_PER_CHECK = 2;         // 0 -> 100 in 50 checks when not under pressure
    public static final int MIN_CONTROL = 0;
    public static final int MAX_CONTROL = 100;

    private static final String CONTROL_TAG = "control";
    private static final String FACTION_TAG = "faction";

    private int control = MAX_CONTROL;
    // Owning faction id. Placeholder until a real faction data model exists; "" means unowned.
    private String faction = "";

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(KingdomRts.TOWN_HALL_BLOCK_ENTITY.get(), pos, state);
    }

    public int getControl() {
        return control;
    }

    public String getFaction() {
        return faction;
    }

    public void setFaction(String faction) {
        this.faction = faction;
        setChanged();
    }

    // Called every server tick by the ticker from TownHallBlock#getTicker.
    public static void serverTick(Level level, BlockPos pos, BlockState state, TownHallBlockEntity townHall) {
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        Vec3 center = Vec3.atCenterOf(pos);
        AABB area = new AABB(pos).inflate(PRESENCE_RADIUS);
        double radiusSqr = PRESENCE_RADIUS * PRESENCE_RADIUS;

        // Attackers: always 0 for now, so the meter only ever regenerates. This is intentional.
        // Capture must only reflect real faction conflict, never monster activity near a town
        // hall, so hostile mobs deliberately don't count. Nothing else can attack yet: factions
        // aren't hostile to each other by default, and the war mechanism that would make another
        // faction's military units count here doesn't exist yet. Once it does, count those units
        // here the same way defenders are counted below.
        int attackers = 0;
        // Defenders: military units of the town hall's own faction. An unowned hall has none.
        String faction = townHall.faction;
        int defenders = faction.isEmpty() ? 0 : level.getEntitiesOfClass(MilitaryUnit.class, area,
                e -> e.isAlive() && faction.equals(e.getFaction()) && withinRadius(e, center, radiusSqr)).size();

        int oldControl = townHall.control;
        int newControl = oldControl;
        if (attackers - defenders >= CAPTURE_MARGIN) {
            newControl -= DRAIN_PER_CHECK;
        } else if (newControl < MAX_CONTROL) {
            newControl += REGEN_PER_CHECK;
        }
        newControl = Mth.clamp(newControl, MIN_CONTROL, MAX_CONTROL);

        if (newControl != oldControl) {
            townHall.control = newControl;
            townHall.setChanged(); // marks the chunk as needing to be saved
        }

        KingdomRts.LOGGER.info("Town hall at {}: faction={}, attackers={}, defenders={}, control={}",
                pos.toShortString(), faction.isEmpty() ? "none" : faction, attackers, defenders, newControl);
    }

    private static boolean withinRadius(Entity entity, Vec3 center, double radiusSqr) {
        return entity.distanceToSqr(center) <= radiusSqr;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(CONTROL_TAG, control);
        tag.putString(FACTION_TAG, faction);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(CONTROL_TAG)) {
            control = Mth.clamp(tag.getInt(CONTROL_TAG), MIN_CONTROL, MAX_CONTROL);
        }
        faction = tag.getString(FACTION_TAG); // "" if missing
    }
}
