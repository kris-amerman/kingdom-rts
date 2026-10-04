package com.krisamerman.kingdomrts.unit;

import com.krisamerman.kingdomrts.KingdomRts;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

// Makes vanilla hostile mobs go after military units, the way zombies already go after villagers.
// Vanilla mobs only target a hardcoded list of classes (players, villagers, iron golems, ...), so
// rather than changing each mob class we add one extra targeting goal to every hostile mob
// as it enters the world.
@EventBusSubscriber(modid = KingdomRts.MODID)
public class HostileMobTargeting {
    // Same priority zombies give villagers: they still prefer a nearby player (priority 2).
    private static final int TARGET_PRIORITY = 3;

    // Fires whenever any entity is added to a world: freshly spawned or loaded from disk. Each
    // load creates a new entity object with fresh goals, so the goal is never added twice.
    @SubscribeEvent
    static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return; // AI only runs on the server
        }
        if (event.getEntity() instanceof Monster monster && MilitaryUnit.isHostileMob(monster)) {
            // true = must see the unit to start targeting it, like vanilla's villager targeting.
            monster.targetSelector.addGoal(TARGET_PRIORITY,
                    new NearestAttackableTargetGoal<>(monster, MilitaryUnit.class, true));
        }
    }
}
