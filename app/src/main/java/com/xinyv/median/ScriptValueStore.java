package com.xinyv.median;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.HashMap;
import java.util.Iterator;

/** Persistent, origin-independent userscript storage. */
final class ScriptValueStore {
    private static final String PREFS = "median_script_values_v1";
    private final SharedPreferences prefs;
    private final HashMap<String, JSONObject> cache = new HashMap<String, JSONObject>();

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
        if (!validKey(key)) return false;
        String storageKey = prefKey(scriptId);
        JSONObject values = read(scriptId);
        boolean existed = values.has(key);
        Object previous = values.opt(key);
        try {
            Object value = new JSONTokener(normalizeJson(valueJson, "null")).nextValue();
            values.put(key, value);
            String encoded = values.toString();
            persistAsync(storageKey, encoded);
            return true;
        } catch (Exception ignored) {
            if (existed) try { values.put(key, previous); } catch (Exception restoreIgnored) {}
            else values.remove(key);
            return false;
        }
    }

    synchronized boolean delete(String scriptId, String key) {
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
        while (keys.hasNext()) result.put(keys.next());
        return result.toString();
    }

    synchronized void clearScript(String scriptId) {
        final String storageKey = prefKey(scriptId);
        cache.remove(storageKey);
        prefs.edit().remove(storageKey).apply();
    }

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
        try { value = new JSONObject(prefs.getString(storageKey, "{}")); }
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
        return key != null && key.length() > 0;
    }

    private static String normalizeJson(String raw, String fallback) {
        if (raw == null || raw.length() == 0) return fallback;
        return raw;
    }
}
