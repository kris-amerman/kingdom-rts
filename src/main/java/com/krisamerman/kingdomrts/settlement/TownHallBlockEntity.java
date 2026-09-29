package com.krisamerman.kingdomrts.settlement;

import com.krisamerman.kingdomrts.KingdomRts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
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

    private int control = MAX_CONTROL;

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(KingdomRts.TOWN_HALL_BLOCK_ENTITY.get(), pos, state);
    }

    public int getControl() {
        return control;
    }

    // Called every server tick by the ticker from TownHallBlock#getTicker.
    public static void serverTick(Level level, BlockPos pos, BlockState state, TownHallBlockEntity townHall) {
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        Vec3 center = Vec3.atCenterOf(pos);
        AABB area = new AABB(pos).inflate(PRESENCE_RADIUS);
        double radiusSqr = PRESENCE_RADIUS * PRESENCE_RADIUS;

        // PLACEHOLDER until real faction data exists: hostile mobs stand in for an attacking
        // faction, and villagers and tamed animals stand in for the owner's defenders.
        int attackers = level.getEntitiesOfClass(Monster.class, area,
                e -> e.isAlive() && withinRadius(e, center, radiusSqr)).size();
        int defenders = level.getEntitiesOfClass(Villager.class, area,
                e -> e.isAlive() && withinRadius(e, center, radiusSqr)).size()
                + level.getEntitiesOfClass(TamableAnimal.class, area,
                e -> e.isAlive() && e.isTame() && withinRadius(e, center, radiusSqr)).size();

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

        KingdomRts.LOGGER.info("Town hall at {}: attackers={}, defenders={}, control={}",
                pos.toShortString(), attackers, defenders, newControl);
    }

    private static boolean withinRadius(Entity entity, Vec3 center, double radiusSqr) {
        return entity.distanceToSqr(center) <= radiusSqr;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(CONTROL_TAG, control);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(CONTROL_TAG)) {
            control = Mth.clamp(tag.getInt(CONTROL_TAG), MIN_CONTROL, MAX_CONTROL);
        }
    }
}
