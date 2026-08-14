package com.xinyv.median;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/** Compact per-origin preferences. A copy is returned so request threads never share mutable state. */
final class SiteSettingsStore {
    static final int INHERIT = 0;
    static final int ALLOW = 1;
    static final int BLOCK = 2;
    static final int JAVASCRIPT = 0;
    static final int IMAGES = 2;
    static final int THIRD_PARTY_COOKIES = 4;
    static final int DESKTOP = 6;
    static final int DARK = 8;
    static final int POPUPS = 10;
    static final int AUTOPLAY = 12;
    static final int LOCATION = 14;
    static final int CAMERA = 16;
    static final int MICROPHONE = 18;
    static final int TRACKING_PROTECTION = 20;
    private static final int COMPATIBILITY = 1 << 22;
    private static final int TEXT_ZOOM_SHIFT = 23;
    private static final int TEXT_ZOOM_MASK = 255 << TEXT_ZOOM_SHIFT;
    private static final String[] STATE_KEYS = { "javascript", "images", "thirdPartyCookies",
            "desktop", "dark", "popups", "autoplay", "location", "camera", "microphone",
            "trackingProtection" };

    static final class SiteSettings {
        private int packed;

        boolean isDefault() {
            return packed == 0;
        }

        SiteSettings copy() {
            SiteSettings result = new SiteSettings();
            result.packed = packed;
            return result;
        }

        boolean sameAs(SiteSettings other) {
            return other != null && packed == other.packed;
        }

        int get(int shift) { return (packed >>> shift) & 3; }
        void set(int shift, int value) { packed = (packed & ~(3 << shift)) | (value << shift); }
        boolean compatibilityMode() { return (packed & COMPATIBILITY) != 0; }
        void compatibilityMode(boolean enabled) {
            packed = enabled ? packed | COMPATIBILITY : packed & ~COMPATIBILITY;
        }
        int textZoom() { int value = (packed >>> TEXT_ZOOM_SHIFT) & 255; return value == 0 ? 100 : value; }
        void textZoom(int value) {
            packed = (packed & ~TEXT_ZOOM_MASK) | (value == 100 ? 0 : value << TEXT_ZOOM_SHIFT);
        }
        int packedStates() { return packed; }
    }

    private static final String PREFS = "median_sites_v1";
    private static final String KEY = "sites";
    private static final int MAX_SITES = 2000;
    private static final long WRITE_DELAY_MS = 180L;
    private final SharedPreferences prefs;
    private final HashMap<String, SiteSettings> cache = new HashMap<String, SiteSettings>();
    private final Handler io;
    private boolean dirty;
    private boolean closed;
    private long generation;
    private final Runnable writer = new Runnable() {
        @Override public void run() { writeSnapshot(); }
    };

    SiteSettingsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        io = LocalDataIo.acquire();
    }

    synchronized SiteSettings forHost(String host) {
        SiteSettings value = cache.get(normalize(host));
        return value == null ? new SiteSettings() : value.copy();
    }

    synchronized void save(String host, SiteSettings settings) {
        String key = normalize(host);
        if (!validHost(key) || settings == null) return;
        if (!cache.containsKey(key) && cache.size() >= MAX_SITES) return;
        SiteSettings before = cache.get(key);
        if (settings.isDefault()) {
            if (before == null) return;
            cache.remove(key);
        } else {
            if (before != null && before.sameAs(settings)) return;
            cache.put(key, settings.copy());
        }
        schedulePersistLocked();
    }

    synchronized void clear(String host) {
        if (cache.remove(normalize(host)) != null) schedulePersistLocked();
    }

    synchronized void clearAll() {
        if (cache.isEmpty()) return;
        cache.clear();
        schedulePersistLocked();
    }

    synchronized int configuredSiteCount() { return cache.size(); }

    synchronized String exportJson() { return serialize(snapshotLocked()); }

    void importJson(String raw) throws Exception {
        if (raw == null || raw.length() > 1024 * 1024) throw new IllegalArgumentException("网站设置超过限制");
        HashMap<String, SiteSettings> restored = parse(raw);
        synchronized (this) {
            if (closed) throw new IllegalStateException("网站设置存储已关闭");
            io.removeCallbacks(writer);
            generation++;
            if (!prefs.edit().putString(KEY, serialize(restored)).commit())
                throw new IllegalStateException("网站设置保存失败");
            cache.clear();
            cache.putAll(restored);
            dirty = false;
        }
    }

    synchronized void flush() {
        if (closed || !dirty) return;
        io.removeCallbacks(writer);
        io.post(writer);
    }

    synchronized void close() {
        if (closed) return;
        closed = true;
        io.removeCallbacks(writer);
        if (dirty) io.post(writer);
        LocalDataIo.release();
    }

    private synchronized void load() {
        try {
            cache.clear();
            cache.putAll(parse(prefs.getString(KEY, "{}")));
        } catch (Exception ignored) { cache.clear(); }
    }

    private synchronized void schedulePersistLocked() {
        if (closed) return;
        generation++;
        dirty = true;
        io.removeCallbacks(writer);
        io.postDelayed(writer, WRITE_DELAY_MS);
    }

    private void writeSnapshot() {
        HashMap<String, SiteSettings> snapshot;
        long snapshotGeneration;
        synchronized (this) {
            if (!dirty) return;
            snapshot = snapshotLocked();
            snapshotGeneration = generation;
        }
        String json = serialize(snapshot);
        synchronized (this) {
            if (snapshotGeneration != generation) return;
            prefs.edit().putString(KEY, json).apply();
            dirty = false;
        }
    }

    private HashMap<String, SiteSettings> snapshotLocked() {
        HashMap<String, SiteSettings> result = new HashMap<String, SiteSettings>(cache.size());
        for (Map.Entry<String, SiteSettings> entry : cache.entrySet())
            result.put(entry.getKey(), entry.getValue().copy());
        return result;
    }

    private static HashMap<String, SiteSettings> parse(String raw) throws Exception {
        HashMap<String, SiteSettings> result = new HashMap<String, SiteSettings>();
        JSONObject root = new JSONObject(raw == null ? "{}" : raw);
        Iterator<String> names = root.keys();
        while (names.hasNext() && result.size() < MAX_SITES) {
            String rawHost = names.next();
            String host = normalize(rawHost);
            JSONObject value = root.optJSONObject(rawHost);
            if (!validHost(host) || value == null) continue;
            SiteSettings settings = new SiteSettings();
            for (int i = 0; i < STATE_KEYS.length; i++)
                settings.set(i << 1, clamp(value.optInt(STATE_KEYS[i], INHERIT), INHERIT, BLOCK));
            settings.compatibilityMode(value.optBoolean("compatibilityMode", false));
            settings.textZoom(clamp(value.optInt("textZoom", 100), 50, 200));
            if (!settings.isDefault()) result.put(host, settings);
        }
        return result;
    }

    private static String serialize(Map<String, SiteSettings> values) {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, SiteSettings> entry : values.entrySet()) {
                SiteSettings settings = entry.getValue();
                JSONObject value = new JSONObject();
                for (int i = 0; i < STATE_KEYS.length; i++) value.put(STATE_KEYS[i], settings.get(i << 1));
                value.put("compatibilityMode", settings.compatibilityMode());
                value.put("textZoom", settings.textZoom());
                root.put(entry.getKey(), value);
            }
        } catch (Exception ignored) {}
        return root.toString();
    }

    private static String normalize(String host) {
        if (host == null) return "";
        String value = host.trim().toLowerCase(Locale.US);
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static boolean validHost(String host) {
        return host != null && host.length() > 0 && host.length() <= 253 && host.matches("[a-z0-9._:-]+");
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
