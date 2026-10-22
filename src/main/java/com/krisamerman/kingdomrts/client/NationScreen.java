package com.krisamerman.kingdomrts.client;

import java.util.List;

import com.krisamerman.kingdomrts.nation.NationOverview;
import com.krisamerman.kingdomrts.nation.NationPayloads;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// Client only. The nation management interface's first screen, in the same panel style as the
// town hall and hut panels: your faction, its settlements, and the interface's actions: Build
// (the catalog), and Demolish and Borders, which later stories fill in. A player without a faction
// sees how to get one instead.
//
// Unlike the panels, this is a plain Screen rather than a menu: it isn't tied to a block, so the
// server just sends what to show (NationOverview) and the screen sends requests back.
public class NationScreen extends Screen {
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int LABEL_COLOR = 0xFF707070;
    private static final int DIVIDER_COLOR = 0xFF9A9A9A;
    private static final int WIDTH = 176;
    private static final int HEIGHT = 160;
    private static final int LEFT = 8;
    private static final int RIGHT = WIDTH - 8;
    private static final int TOP = 20;
    private static final int ROW = 12;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTON_Y = HEIGHT - 8 - BUTTON_HEIGHT;
    private static final int LIST_TOP = TOP + ROW + 2;
    private static final int MAX_ROWS = (BUTTON_Y - 4 - LIST_TOP) / ROW;

    private final NationOverview overview;
    private int leftPos;
    private int topPos;

    public NationScreen(NationOverview overview) {
        super(Component.translatable("screen.kingdomrts.nation"));
        this.overview = overview;
    }

    @Override
    protected void init() {
        leftPos = (width - WIDTH) / 2;
        topPos = (height - HEIGHT) / 2;
        if (!overview.hasFaction()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                    .bounds(leftPos + LEFT, topPos + BUTTON_Y, WIDTH - 2 * LEFT, BUTTON_HEIGHT).build());
            return;
        }
        // Build asks the server for the catalog. Demolish and Borders come in later stories; until
        // then their buttons are shown but greyed out.
        List<String> actions = List.of("Build", "Demolish", "Borders");
        int buttonWidth = (WIDTH - 2 * LEFT - (actions.size() - 1) * BUTTON_GAP) / actions.size();
        for (int i = 0; i < actions.size(); i++) {
            boolean build = i == 0;
            Button.Builder builder = Button.builder(Component.literal(actions.get(i)), b -> {
                        if (build) {
                            PacketDistributor.sendToServer(new NationPayloads.RequestCatalog());
                        }
                    })
                    .bounds(leftPos + LEFT + i * (buttonWidth + BUTTON_GAP), topPos + BUTTON_Y, buttonWidth, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.literal(build ? "Pick a building and see where it would go" : "Coming soon")));
            addRenderableWidget(builder.build()).active = build;
        }
    }

    // The game keeps running behind this screen, as it does behind the panels.
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Draws the panel over the usual dimmed background, before the buttons go on top.
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        GuiDraw.panel(graphics, leftPos, topPos, WIDTH, HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos, topPos, 0);
        graphics.drawString(font, title, LEFT, 6, TEXT_COLOR, false);
        if (overview.hasFaction()) {
            renderFaction(graphics);
        } else {
            renderNoFaction(graphics);
        }
        graphics.pose().popPose();
        hoveredSettlementTooltip(graphics, mouseX, mouseY);
    }

    private void renderNoFaction(GuiGraphics graphics) {
        graphics.drawString(font, "You aren't in a faction.", LEFT, TOP, TEXT_COLOR, false);
        graphics.drawWordWrap(font, Component.literal("Join or found a faction first. To found one, claim a town hall."), LEFT, TOP + 2 * ROW,
                RIGHT - LEFT, LABEL_COLOR);
    }

    private void renderFaction(GuiGraphics graphics) {
        String role = overview.leader() ? "Leader" : "Member";
        graphics.drawString(font, role, RIGHT - font.width(role), 6, LABEL_COLOR, false);
        graphics.drawString(font, fit(overview.factionName(), RIGHT - LEFT), LEFT, TOP, TEXT_COLOR, false);
        List<NationOverview.SettlementLine> settlements = overview.settlements();
        graphics.fill(LEFT, LIST_TOP - 3, RIGHT, LIST_TOP - 2, DIVIDER_COLOR);
        if (settlements.isEmpty()) {
            graphics.drawString(font, "No settlements yet", LEFT, LIST_TOP, LABEL_COLOR, false);
            return;
        }
        int shown = settlements.size() > MAX_ROWS ? MAX_ROWS - 1 : settlements.size();
        for (int i = 0; i < shown; i++) {
            NationOverview.SettlementLine settlement = settlements.get(i);
            int y = LIST_TOP + i * ROW;
            String people = settlement.population() + (settlement.population() == 1 ? " person" : " people");
            graphics.drawString(font, fit(settlement.name(), RIGHT - LEFT - font.width(people) - 6), LEFT, y, TEXT_COLOR, false);
            graphics.drawString(font, people, RIGHT - font.width(people), y, LABEL_COLOR, false);
        }
        if (shown < settlements.size()) {
            graphics.drawString(font, "and " + (settlements.size() - shown) + " more", LEFT, LIST_TOP + shown * ROW, LABEL_COLOR, false);
        }
    }

    // Hovering a settlement shows where its town hall is and how much it claims.
    private void hoveredSettlementTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!overview.hasFaction()) {
            return;
        }
        int x = mouseX - leftPos;
        int row = Math.floorDiv(mouseY - topPos - LIST_TOP, ROW);
        int shown = overview.settlements().size() > MAX_ROWS ? MAX_ROWS - 1 : overview.settlements().size();
        if (x < LEFT || x >= RIGHT || row < 0 || row >= shown) {
            return;
        }
        NationOverview.SettlementLine settlement = overview.settlements().get(row);
        graphics.renderComponentTooltip(font, List.of(Component.literal(settlement.name()),
                Component.literal("Town hall at " + settlement.townHall().toShortString() + " (" + DisplayNames.dimension(settlement.dimension()) + ")"),
                Component.literal("Claim: " + settlement.claimedChunks() + (settlement.claimedChunks() == 1 ? " chunk" : " chunks"))), mouseX, mouseY);
    }

    // Text cut short with "..." if it doesn't fit.
    private String fit(String text, int room) {
        return font.width(text) <= room ? text : font.plainSubstrByWidth(text, room - font.width("...")) + "...";
    }
}
