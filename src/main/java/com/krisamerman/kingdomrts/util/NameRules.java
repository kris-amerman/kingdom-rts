package com.krisamerman.kingdomrts.util;

import javax.annotation.Nullable;

import com.mojang.brigadier.StringReader;

// Shared rules for names typed into commands (factions, settlements): 1-32 characters, using only
// what a command accepts as one unquoted word (letters, digits, _ - . +), so a name can always be
// typed and Tab-completed without quotes.
public final class NameRules {
    public static final int MAX_LENGTH = 32;

    private NameRules() {
    }

    // Returns why the name breaks the format rules, or null if it's fine. kind is used in the
    // message, e.g. "Faction" or "Settlement". Uniqueness is checked by the caller.
    @Nullable
    public static String formatProblem(String kind, String name) {
        if (name.isEmpty() || name.length() > MAX_LENGTH) {
            return kind + " names must be 1-" + MAX_LENGTH + " characters";
        }
        for (char c : name.toCharArray()) {
            if (!StringReader.isAllowedInUnquotedString(c)) {
                return kind + " names may only use letters, digits, _ - . +";
            }
        }
        return null;
    }

    // Turns arbitrary text into a name that passes formatProblem: disallowed characters become "_",
    // it's cut to fit (leaving room for a "_N" suffix), and empty text becomes the fallback.
    // Anything built on top of this (adding "_2", "_3", ...) only fails for being taken, so a loop
    // that adds suffixes until a name is free always ends.
    public static String sanitize(String text, String fallback) {
        StringBuilder name = new StringBuilder();
        for (char c : text.toCharArray()) {
            name.append(StringReader.isAllowedInUnquotedString(c) ? c : '_');
        }
        if (name.length() > MAX_LENGTH - 8) {
            name.setLength(MAX_LENGTH - 8);
        }
        return name.isEmpty() ? fallback : name.toString();
    }
}
