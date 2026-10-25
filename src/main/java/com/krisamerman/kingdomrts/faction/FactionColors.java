package com.krisamerman.kingdomrts.faction;

import java.util.UUID;

import javax.annotation.Nullable;

// A faction's colour, for the borders view: worked out from its id, so it never changes (renaming
// doesn't change it) and needs nothing saved. Bright and fairly saturated, so it shows on grass,
// sand and snow alike. Choosing colours (and emblems) is for later.
public final class FactionColors {
    // A settlement with no owner.
    public static final int UNOWNED = 0xA0A0A0;
    private static final float SATURATION = 0.75f;
    private static final float BRIGHTNESS = 1.0f;
    // Spreads hues out (the golden ratio), so ids that hash close together still look different.
    private static final double GOLDEN = 0.6180339887498949;

    private FactionColors() {
    }

    // The faction's colour as 0xRRGGBB.
    public static int of(@Nullable UUID faction) {
        if (faction == null) {
            return UNOWNED;
        }
        double hue = (faction.hashCode() * GOLDEN) % 1.0;
        return hsvToRgb((float) (hue < 0 ? hue + 1.0 : hue), SATURATION, BRIGHTNESS);
    }

    static int hsvToRgb(float hue, float saturation, float value) {
        int sector = (int) (hue * 6.0f) % 6;
        float f = hue * 6.0f - (int) (hue * 6.0f);
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - f * saturation);
        float t = value * (1.0f - (1.0f - f) * saturation);
        float[] rgb = switch (sector) {
            case 0 -> new float[] {value, t, p};
            case 1 -> new float[] {q, value, p};
            case 2 -> new float[] {p, value, t};
            case 3 -> new float[] {p, q, value};
            case 4 -> new float[] {t, p, value};
            default -> new float[] {value, p, q};
        };
        return channel(rgb[0]) << 16 | channel(rgb[1]) << 8 | channel(rgb[2]);
    }

    private static int channel(float value) {
        return Math.round(Math.max(0.0f, Math.min(1.0f, value)) * 255.0f);
    }
}
