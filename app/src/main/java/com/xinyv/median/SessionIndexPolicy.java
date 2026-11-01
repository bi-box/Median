package com.xinyv.median;

/** Preserves the selected tab when malformed or unsupported session entries are filtered out. */
final class SessionIndexPolicy {
    static int remap(boolean[] accepted, int selectedIndex) {
        if (accepted == null || accepted.length == 0) return 0;
        int selected = Math.max(0, Math.min(selectedIndex, accepted.length - 1));
        int kept = 0;
        int before = 0;
        for (int i = 0; i < accepted.length; i++) {
            if (!accepted[i]) continue;
            if (i < selected) before++;
            kept++;
        }
        if (kept == 0) return 0;
        // If the selected entry was rejected, prefer the next surviving tab; when there is no
        // next tab, fall back to the nearest survivor on the left.
        return Math.min(before, kept - 1);
    }

    private SessionIndexPolicy() {}
}
