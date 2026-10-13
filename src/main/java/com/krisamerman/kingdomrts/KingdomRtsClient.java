package com.krisamerman.kingdomrts;

import com.krisamerman.kingdomrts.settlement.TownHallScreen;
import com.krisamerman.kingdomrts.settlement.WorkplaceScreen;
import com.krisamerman.kingdomrts.settlement.WorkplaceStockScreen;
import com.krisamerman.kingdomrts.unit.UnitRenderer;
import com.krisamerman.kingdomrts.unit.UnitScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = KingdomRts.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = KingdomRts.MODID, value = Dist.CLIENT)
public class KingdomRtsClient {
    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(KingdomRts.UNIT.get(), UnitRenderer::new);
    }

    // Tells the client which screen to show when the server opens each menu type.
    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(KingdomRts.TOWN_HALL_MENU.get(), TownHallScreen::new);
        event.register(KingdomRts.UNIT_MENU.get(), UnitScreen::new);
        event.register(KingdomRts.WORKPLACE_MENU.get(), WorkplaceScreen::new);
        event.register(KingdomRts.WORKPLACE_STOCK_MENU.get(), WorkplaceStockScreen::new);
    }
}
