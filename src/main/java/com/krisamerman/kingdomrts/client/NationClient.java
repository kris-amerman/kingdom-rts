package com.krisamerman.kingdomrts.client;

import org.lwjgl.glfw.GLFW;

import com.krisamerman.kingdomrts.KingdomRts;
import com.krisamerman.kingdomrts.nation.NationOverview;
import com.krisamerman.kingdomrts.nation.NationPayloads;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

// Client only. Opening the nation management interface: the key that asks the server for it, and
// showing the screen when the server answers.
@EventBusSubscriber(modid = KingdomRts.MODID, value = Dist.CLIENT)
public final class NationClient {
    // The "Open nation management" key, N by default, in a "Kingdom RTS" section of the Controls
    // screen, where players can rebind it. IN_GAME means it only counts while no screen is open.
    public static final KeyMapping OPEN_NATION_KEY = new KeyMapping("key.kingdomrts.open_nation", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.kingdomrts");

    private NationClient() {
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_NATION_KEY);
    }

    // Once per client tick: each press since the last tick asks the server to open the interface.
    // The server decides what it shows (NationRequests.open).
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_NATION_KEY.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen == null && minecraft.getConnection() != null) {
                PacketDistributor.sendToServer(new NationPayloads.OpenNation());
            }
        }
    }

    // The server sent the interface's first screen (from the key or /kingdomrts nation).
    public static void show(NationOverview overview) {
        Minecraft.getInstance().setScreen(new NationScreen(overview));
    }
}
