// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Median Browser contributors

package com.xinyv.median;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent, origin-independent userscript storage. */
final class ScriptValueStore {
    private static final String PREFS = "median_script_values_v1";
    static final int MAX_KEY_LENGTH = 256;
    static final int MAX_VALUE_CHARS = 1024 * 1024;
    static final int MAX_TOTAL_CHARS = 4 * 1024 * 1024;
    static final int MAX_ENTRIES = 256;
    private final SharedPreferences prefs;
    private final LinkedHashMap<String, JSONObject> cache =
            new LinkedHashMap<String, JSONObject>(16, .75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, JSONObject> eldest) {
                    return size() > 64;
                }
            };

    ScriptValueStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized String getJson(String scriptId, String key, String fallbackJson) {
        JSONObject values = read(scriptId);
        if (!values.has(key)) return normalizeJson(fallbackJson, "null");
        Object value = values.opt(key);
        return encodeJsonValue(value);
    }

    synchronized boolean contains(String scriptId, String key) {
        return validKey(key) && read(scriptId).has(key);
    }

    synchronized boolean setJson(String scriptId, String key, String valueJson) {
        if (!validKey(key) || valueJson == null || valueJson.length() > MAX_VALUE_CHARS) return false;
        String storageKey = prefKey(scriptId);
        JSONObject values = read(scriptId);
        boolean existed = values.has(key);
        Object previous = values.opt(key);
        try {
            Object value = new JSONTokener(normalizeJson(valueJson, "null")).nextValue();
            String encodedValue = encodeJsonValue(value);
            if (encodedValue.length() > MAX_VALUE_CHARS) return false;
            if (!existed && values.length() >= MAX_ENTRIES) return false;
            values.put(key, value);
            String encoded = values.toString();
            if (encoded.length() > MAX_TOTAL_CHARS) {
                if (existed) values.put(key, previous);
                else values.remove(key);
                return false;
            }
            persistAsync(storageKey, encoded);
            return true;
        } catch (Exception ignored) {
            if (existed) try { values.put(key, previous); } catch (Exception restoreIgnored) {}
            else values.remove(key);
            return false;
        }
    }

    synchronized boolean delete(String scriptId, String key) {
        if (!validKey(key)) return false;
        String storageKey = prefKey(scriptId);
        JSONObject values = read(scriptId);
        values.remove(key);
        persistAsync(storageKey, values.toString());
        return true;
    }

    synchronized String listJson(String scriptId) {
        JSONObject values = read(scriptId);
        JSONArray result = new JSONArray();
        Iterator<String> keys = values.keys();
        int count = 0;
        while (keys.hasNext() && count++ < MAX_ENTRIES) result.put(keys.next());
        return result.toString();
    }

    synchronized void clearScript(String scriptId) {
        final String storageKey = prefKey(scriptId);
        cache.remove(storageKey);
        prefs.edit().remove(storageKey).apply();
    }

    synchronized void trimMemory() { cache.clear(); }

    void shutdown() {}

    private void persistAsync(final String storageKey, final String encoded) {
        // SharedPreferences.apply() updates the in-memory snapshot immediately and
        // performs the disk write asynchronously. An extra executor delayed the
        // in-memory visibility of recent GM values and could make backups miss them.
        prefs.edit().putString(storageKey, encoded).apply();
    }

    private JSONObject read(String scriptId) {
        String storageKey = prefKey(scriptId);
        JSONObject value = cache.get(storageKey);
        if (value != null) return value;
        try {
            String raw = prefs.getString(storageKey, "{}");
            value = raw != null && raw.length() <= MAX_TOTAL_CHARS ? new JSONObject(raw) : new JSONObject();
        }
        catch (Exception ignored) { value = new JSONObject(); }
        cache.put(storageKey, value);
        return value;
    }

    private static String prefKey(String scriptId) {
        return "s_" + UrlCleaner.stableId(scriptId == null ? "" : scriptId);
    }

    private static String encodeJsonValue(Object value) {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof String) return JSONObject.quote((String) value);
        if (value instanceof Number || value instanceof Boolean || value instanceof JSONObject || value instanceof JSONArray)
            return String.valueOf(value);
        return JSONObject.quote(String.valueOf(value));
    }

    private static boolean validKey(String key) {
        if (key == null || key.length() == 0 || key.length() > MAX_KEY_LENGTH) return false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isISOControl(c)) return false;
        }
        return true;
    }

    private static String normalizeJson(String raw, String fallback) {
        if (raw == null || raw.length() == 0 || raw.length() > MAX_VALUE_CHARS) return fallback;
        return raw;
    }
}
