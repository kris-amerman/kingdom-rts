package com.krisamerman.kingdomrts.gametest;

import static com.krisamerman.kingdomrts.gametest.TestSupport.FLOOR_Y;
import static com.krisamerman.kingdomrts.gametest.TestSupport.TEMPLATE;
import static com.krisamerman.kingdomrts.gametest.TestSupport.faction;
import static com.krisamerman.kingdomrts.gametest.TestSupport.setWar;
import static com.krisamerman.kingdomrts.gametest.TestSupport.unit;

import java.util.concurrent.atomic.AtomicReference;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.faction.Faction;
import com.krisamerman.kingdomrts.unit.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Units of different factions next to each other: they fight only while their factions are at war.
@GameTestHolder(KingdomRts.MODID)
@PrefixGameTestTemplate(false)
public class CombatGameTests {
    private static final BlockPos A = new BlockPos(7, FLOOR_Y + 1, 8);
    private static final BlockPos B = new BlockPos(9, FLOOR_Y + 1, 8); // 2 blocks apart, inside attack range

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void unitsAtWarFight(GameTestHelper helper) {
        Faction red = faction(helper, "Red");
        Faction blue = faction(helper, "Blue");
        setWar(helper, red, blue, true);
        Unit a = unit(helper, A, red, true);
        Unit b = unit(helper, B, blue, true);
        helper.succeedWhen(() -> helper.assertTrue(hurt(a) || hurt(b), "Expected units at war to fight"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void unitsAtPeaceDontFight(GameTestHelper helper) {
        Faction red = faction(helper, "Red");
        Faction blue = faction(helper, "Blue");
        Unit a = unit(helper, A, red, true);
        Unit b = unit(helper, B, blue, true);
        helper.startSequence()
                .thenIdle(100)
                .thenExecute(() -> helper.assertFalse(hurt(a) || hurt(b), "Units of factions at peace attacked each other: "
                        + describe(a) + " / " + describe(b)))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void peaceEndsFight(GameTestHelper helper) {
        Faction red = faction(helper, "Red");
        Faction blue = faction(helper, "Blue");
        setWar(helper, red, blue, true);
        Unit a = unit(helper, A, red, true);
        Unit b = unit(helper, B, blue, true);
        AtomicReference<float[]> healthAtPeace = new AtomicReference<>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(hurt(a) || hurt(b), "Waiting for the fight to start"))
                .thenExecute(() -> setWar(helper, red, blue, false))
                .thenIdle(10) // let a swing that was already underway land
                .thenExecute(() -> healthAtPeace.set(new float[] {a.getHealth(), b.getHealth()}))
                .thenIdle(80)
                .thenExecute(() -> helper.assertTrue(a.getHealth() == healthAtPeace.get()[0] && b.getHealth() == healthAtPeace.get()[1],
                        "Units kept fighting after peace was made"))
                .thenSucceed();
    }

    private static String describe(Unit unit) {
        return unit.getHealth() + "/" + unit.getMaxHealth() + " last damage: "
                + (unit.getLastDamageSource() != null ? unit.getLastDamageSource().getMsgId() + " by " + unit.getLastDamageSource().getEntity() : "none");
    }

    private static boolean hurt(Unit unit) {
        return unit.getHealth() < unit.getMaxHealth();
    }
}
