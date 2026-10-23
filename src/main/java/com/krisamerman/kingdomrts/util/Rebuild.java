package com.krisamerman.kingdomrts.util;

import java.util.Objects;

import javax.annotation.Nullable;

// Remembers what something cached was built for, so it's rebuilt only when that changes: a
// preview's baked mesh is built for one building turned one way, and a site's for the blocks it
// still shows. Keys are compared with equals.
public final class Rebuild<K> {
    @Nullable
    private K builtFor;
    private boolean built;

    // Whether what's cached is missing or was built for something else.
    public boolean needed(K key) {
        return !built || !Objects.equals(builtFor, key);
    }

    public void done(K key) {
        builtFor = key;
        built = true;
    }

    // Forgets what was built, so the next check says it's needed.
    public void reset() {
        builtFor = null;
        built = false;
    }
}
