// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Median Browser contributors

package com.xinyv.median;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded in-process backing for GM_getTab/GM_saveTab.
 *
 * Tab values intentionally disappear when a tab closes. Keeping an access-ordered global bound
 * also prevents one broken userscript from retaining hundreds of megabytes until the Activity is
 * destroyed. Individual API payload limits remain enforced by the bridge.
 */
final class ScriptTabStateStore {
    static final int MAX_CHARS = 4 * 1024 * 1024;
    private static final int MAX_ENTRIES = 256;

    private final LinkedHashMap<String, String> values =
            new LinkedHashMap<String, String>(16, .75f, true);
    private int chars;

    synchronized String get(String key) { return values.get(key); }

    synchronized boolean put(String key, String value) {
        if (key == null || key.length() == 0 || value == null || value.length() > MAX_CHARS) return false;
        String previous = values.put(key, value);
        if (previous != null) chars -= previous.length();
        chars += value.length();
        trimTo(MAX_CHARS, MAX_ENTRIES);
        return values.containsKey(key);
    }

    synchronized List<Map.Entry<String, String>> entries() {
        ArrayList<Map.Entry<String, String>> copy = new ArrayList<Map.Entry<String, String>>(values.size());
        for (Map.Entry<String, String> entry : values.entrySet())
            copy.add(new AbstractMap.SimpleImmutableEntry<String, String>(entry.getKey(), entry.getValue()));
        return copy;
    }

    synchronized void removeTab(String tabId) {
        if (tabId == null || tabId.length() == 0) return;
        String suffix = "|" + tabId;
        Iterator<Map.Entry<String, String>> iterator = values.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (!entry.getKey().endsWith(suffix)) continue;
            chars -= entry.getValue().length();
            iterator.remove();
        }
    }

    synchronized void trimMemory() { trimTo(MAX_CHARS / 4, MAX_ENTRIES / 4); }

    synchronized void clear() {
        values.clear();
        chars = 0;
    }

    synchronized int size() { return values.size(); }
    synchronized int charCount() { return chars; }

    private void trimTo(int charLimit, int entryLimit) {
        Iterator<Map.Entry<String, String>> iterator = values.entrySet().iterator();
        while ((chars > charLimit || values.size() > entryLimit) && iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            chars -= entry.getValue().length();
            iterator.remove();
        }
        if (chars < 0) chars = 0;
    }
}
