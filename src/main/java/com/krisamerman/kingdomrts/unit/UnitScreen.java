package com.krisamerman.kingdomrts.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.client.DisplayNames;
import com.krisamerman.kingdomrts.client.GuiDraw;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

// Client only. Draws the unit info panel in the style of vanilla's container screens: the unit
// itself on the left (turning to follow the mouse, like the player in their inventory), its details
// beside it as label/value rows, and, for the unit's own faction, its inventory and then the
// player's inventory below, where every container screen puts them.
public class UnitScreen extends AbstractContainerScreen<UnitMenu> {
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int LABEL_COLOR = 0xFF707070;
    private static final int PANEL_WIDTH = 176;
    // The black box the unit stands in, and the rows beside it.
    private static final int MODEL_X = 8;
    private static final int MODEL_Y = 18;
    private static final int MODEL_WIDTH = 44;
    private static final int ROWS_X = MODEL_X + MODEL_WIDTH + 6;
    private static final int ROWS_RIGHT = PANEL_WIDTH - 8;
    private static final int ROWS_Y = 21;
    private static final int ROW_HEIGHT = 10;

    // Label/value rows, built once when the screen opens. Health is the only live value, so it's
    // drawn separately.
    private record Row(String label, String value) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final int modelHeight;

    public UnitScreen(UnitMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        UnitInfo info = menu.getInfo();
        rows.add(new Row("Faction", info.factionName()));
        rows.add(new Row("Job", DisplayNames.occupation(info.occupation())));
        rows.add(new Row("Health", "")); // value drawn live in renderLabels
        rows.add(new Row("Damage", number(info.attackDamage())));
        rows.add(new Row("Range", number(info.attackRange())));
        rows.add(new Row("Speed", number(info.movementSpeed())));
        UnitInfo.Details details = info.details();
        if (details != null) {
            rows.add(new Row("Settlement", details.settlementName().isEmpty() ? "None" : details.settlementName()));
            rows.add(new Row("Home", position(details.home())));
            rows.add(new Row("Post", position(details.post())));
        }
        imageWidth = PANEL_WIDTH;
        modelHeight = rows.size() * ROW_HEIGHT;
        if (info.isFull()) {
            imageHeight = UnitMenu.HOTBAR_Y + 16 + 8;
            inventoryLabelY = UnitMenu.PLAYER_SLOTS_Y - 11;
        } else {
            imageHeight = MODEL_Y + modelHeight + 8;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY); // item tooltips over the slots
        renderValueTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiDraw.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            GuiDraw.slot(graphics, leftPos + slot.x, topPos + slot.y);
        }
        int x1 = leftPos + MODEL_X;
        int y1 = topPos + MODEL_Y;
        GuiDraw.entityBox(graphics, x1, y1, MODEL_WIDTH, modelHeight);
        if (minecraft.level.getEntity(menu.getUnitEntityId()) instanceof LivingEntity unit) {
            // Same call vanilla's inventory screen uses to draw the player.
            int scale = Math.min(30, modelHeight * 3 / 10);
            // Hide its name tag while drawing it here (the title already shows the name).
            boolean nameVisible = unit.isCustomNameVisible();
            unit.setCustomNameVisible(false);
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x1 + 1, y1 + 1, x1 + MODEL_WIDTH - 1, y1 + modelHeight - 1,
                    scale, 0.0625f, mouseX, mouseY, unit);
            unit.setCustomNameVisible(nameVisible);
        }
    }

    // Text; coordinates are relative to the panel's top-left corner.
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Plain text: a unit's name tag may carry its own colour, which would ignore TEXT_COLOR.
        graphics.drawString(font, title.getString(), titleLabelX, titleLabelY, TEXT_COLOR, false);
        int y = ROWS_Y;
        for (Row row : rows) {
            String value = row.label().equals("Health")
                    ? menu.getHealth() + "/" + (int) Math.ceil(menu.getInfo().maxHealth())
                    : row.value();
            graphics.drawString(font, row.label(), ROWS_X, y, LABEL_COLOR, false);
            String shown = fit(row.label(), value);
            graphics.drawString(font, shown, ROWS_RIGHT - font.width(shown), y, TEXT_COLOR, false);
            y += ROW_HEIGHT;
        }
        if (!menu.getInfo().isFull()) {
            return;
        }
        graphics.drawString(font, "Carrying", UnitMenu.SLOTS_X, UnitMenu.UNIT_SLOTS_Y - 11, TEXT_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_COLOR, false);
    }

    // A value too long for its row is cut short with "..."; hovering the row shows it in full.
    private void renderValueTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = mouseX - leftPos;
        int row = (mouseY - topPos - ROWS_Y + 1) / ROW_HEIGHT;
        if (x < ROWS_X || x > ROWS_RIGHT || mouseY - topPos < ROWS_Y - 1 || row >= rows.size()) {
            return;
        }
        Row hovered = rows.get(row);
        if (!fit(hovered.label(), hovered.value()).equals(hovered.value())) {
            graphics.renderTooltip(font, Component.literal(hovered.value()), mouseX, mouseY);
        }
    }

    private String fit(String label, String value) {
        int room = ROWS_RIGHT - ROWS_X - font.width(label) - 4;
        if (font.width(value) <= room) {
            return value;
        }
        return font.plainSubstrByWidth(value, room - font.width("...")) + "...";
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "");
    }

    private static String position(@Nullable BlockPos pos) {
        return pos != null ? pos.getX() + ", " + pos.getY() + ", " + pos.getZ() : "None";
    }
}
