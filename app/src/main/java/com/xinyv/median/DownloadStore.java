package com.xinyv.median;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Persistent index shared by Android DownloadManager and Median's adaptive downloader. */
final class DownloadStore {
    static final String ENGINE_SYSTEM = "system";
    static final String ENGINE_ADAPTIVE = "adaptive";
    static final String STATUS_PENDING = "pending";
    static final String STATUS_WAITING = "waiting";
    static final String STATUS_DOWNLOADING = "downloading";
    static final String STATUS_PAUSED = "paused";
    static final String STATUS_COMPLETED = "completed";
    static final String STATUS_FAILED = "failed";
    static final String STATUS_CANCELLED = "cancelled";

    static final class Item implements Cloneable {
        long id;
        String url;
        String filename;
        String mime;
        long createdAt;
        long updatedAt;
        long completedAt;
        String engine;
        String status;
        String reason;
        String localUri;
        long downloadedBytes;
        long totalBytes;
        long bytesPerSecond;
        long etaSeconds;
        int retryCount;
        String userAgent;
        String headersJson;
        boolean wifiOnly;
        boolean allowRoaming;
        boolean chargingOnly;
        boolean publicOnly;

        boolean isAdaptive() { return ENGINE_ADAPTIVE.equals(engine); }
        boolean isActive() {
            return STATUS_PENDING.equals(status) || STATUS_WAITING.equals(status) || STATUS_DOWNLOADING.equals(status);
        }
        boolean isTerminal() {
            return STATUS_COMPLETED.equals(status) || STATUS_FAILED.equals(status) || STATUS_CANCELLED.equals(status);
        }

        Item copy() {
            try { return (Item) super.clone(); }
            catch (CloneNotSupportedException impossible) { throw new AssertionError(impossible); }
        }
    }

    private static final String PREFS = "median_downloads_v3";
    private static final String LEGACY_PREFS = "median_downloads_v2";
    private static final String KEY = "items";
    private static final String NEXT_CUSTOM_ID = "next_custom_id";
    private static final int MAX_ITEMS = 500;
    private static final long TELEMETRY_WRITE_INTERVAL_MS = 5000L;
    private static final Object LOCK = new Object();

    private static final ArrayList<Item> items = new ArrayList<Item>();
    private static boolean loaded;
    private static long lastTelemetryWriteAt;
    private final SharedPreferences prefs;

    DownloadStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        synchronized (LOCK) {
            migrateLegacyLocked(context);
            if (!loaded) {
                loadLocked();
                loaded = true;
            }
        }
    }

    long addAdaptive(String url, String filename, String mime) {
        return addAdaptive(url, filename, mime, "", "{}", false, false, false, false);
    }

    long addAdaptive(String url, String filename, String mime, String userAgent,
                     String headersJson, boolean wifiOnly, boolean allowRoaming, boolean chargingOnly,
                     boolean publicOnly) {
        return addAdaptive(url, filename, mime, userAgent, headersJson, wifiOnly, allowRoaming,
                chargingOnly, publicOnly, 0L);
    }

    long addAdaptive(String url, String filename, String mime, String userAgent,
                     String headersJson, boolean wifiOnly, boolean allowRoaming, boolean chargingOnly,
                     boolean publicOnly, long expectedTotalBytes) {
        synchronized (LOCK) {
            long id = prefs.getLong(NEXT_CUSTOM_ID, -1L);
            if (id >= 0L) id = -1L;
            prefs.edit().putLong(NEXT_CUSTOM_ID, id - 1L).apply();
            Item item = baseItem(id, url, filename, mime);
            item.engine = ENGINE_ADAPTIVE;
            item.status = STATUS_PENDING;
            item.userAgent = value(userAgent);
            item.headersJson = value(headersJson);
            item.wifiOnly = wifiOnly;
            item.allowRoaming = allowRoaming;
            item.chargingOnly = chargingOnly;
            item.publicOnly = publicOnly;
            item.totalBytes = Math.max(0L, expectedTotalBytes);
            addLocked(item);
            return id;
        }
    }

    void updateAdaptive(long id, String status, String reason, long downloadedBytes,
                        long totalBytes, long bytesPerSecond, String localUri) {
        synchronized (LOCK) {
            Item item = findLocked(id);
            if (item == null || !item.isAdaptive()) return;
            applyProgress(item, status, reason, downloadedBytes, totalBytes, bytesPerSecond, localUri,
                    item.etaSeconds, item.retryCount);
            persistLocked();
        }
    }

    void updateAdaptiveTelemetry(long id, String status, String reason, long downloadedBytes,
                                 long totalBytes, long bytesPerSecond, String localUri,
                                 long etaSeconds, int retryCount) {
        synchronized (LOCK) {
            Item item = findLocked(id);
            if (item == null || !item.isAdaptive()) return;
            applyProgress(item, status, reason, downloadedBytes, totalBytes, bytesPerSecond, localUri,
                    etaSeconds, retryCount);
            long now = SystemClock.elapsedRealtime();
            boolean urgent = !STATUS_DOWNLOADING.equals(status) || localUri != null;
            if (urgent || now - lastTelemetryWriteAt >= TELEMETRY_WRITE_INTERVAL_MS) persistLocked();
        }
    }

    void updateMetadata(long id, String filename, String mime) {
        synchronized (LOCK) {
            Item item = findLocked(id);
            if (item == null) return;
            item.filename = value(filename);
            item.mime = value(mime);
            item.updatedAt = System.currentTimeMillis();
            persistLocked();
        }
    }

    List<Item> getAll() {
        synchronized (LOCK) {
            ArrayList<Item> result = new ArrayList<Item>();
            for (Item item : items) result.add(copy(item));
            return result;
        }
    }

    Item get(long id) {
        synchronized (LOCK) {
            Item item = findLocked(id);
            return item == null ? null : copy(item);
        }
    }

    Item findBlockingDuplicate(String url, long failedCooldownMs) {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            for (Item item : items) {
                if (!item.isAdaptive() || !value(url).equals(item.url)) continue;
                if ((item.isActive() && now - item.updatedAt < 30000L) || STATUS_PAUSED.equals(item.status))
                    return copy(item);
                if (STATUS_FAILED.equals(item.status) && now - item.updatedAt < Math.max(0L, failedCooldownMs))
                    return copy(item);
            }
            return null;
        }
    }

    void remove(long id) {
        synchronized (LOCK) {
            for (int i = items.size() - 1; i >= 0; i--) if (items.get(i).id == id) items.remove(i);
            persistLocked();
        }
    }

    int removeAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        synchronized (LOCK) {
            int removed = 0;
            for (int i = items.size() - 1; i >= 0; i--) {
                long itemId = items.get(i).id;
                boolean matched = false;
                for (Long id : ids) if (id != null && id.longValue() == itemId) { matched = true; break; }
                if (!matched) continue;
                items.remove(i);
                removed++;
            }
            if (removed > 0) persistLocked();
            return removed;
        }
    }

    void clear() {
        synchronized (LOCK) {
            items.clear();
            persistLocked();
        }
    }

    private void applyProgress(Item item, String status, String reason, long downloadedBytes,
                               long totalBytes, long bytesPerSecond, String localUri,
                               long etaSeconds, int retryCount) {
        item.status = value(status);
        item.reason = value(reason);
        item.downloadedBytes = Math.max(0L, downloadedBytes);
        item.totalBytes = STATUS_COMPLETED.equals(status) ? Math.max(item.downloadedBytes, Math.max(0L, totalBytes)) :
                DownloadCenterPolicy.resolvedTotal(item.totalBytes, totalBytes);
        item.bytesPerSecond = Math.max(0L, bytesPerSecond);
        item.etaSeconds = Math.max(0L, etaSeconds);
        item.retryCount = Math.max(0, retryCount);
        item.updatedAt = System.currentTimeMillis();
        if (STATUS_COMPLETED.equals(item.status)) item.completedAt = item.updatedAt;
        if (localUri != null) item.localUri = value(localUri);
    }

    private Item baseItem(long id, String url, String filename, String mime) {
        Item item = new Item();
        item.id = id;
        item.url = value(url);
        item.filename = value(filename);
        item.mime = value(mime);
        item.createdAt = System.currentTimeMillis();
        item.updatedAt = item.createdAt;
        item.engine = ENGINE_SYSTEM;
        item.status = STATUS_PENDING;
        item.reason = "";
        item.localUri = "";
        item.userAgent = "";
        item.headersJson = "{}";
        return item;
    }

    private void addLocked(Item item) {
        for (int i = items.size() - 1; i >= 0; i--) if (items.get(i).id == item.id) items.remove(i);
        items.add(0, item);
        while (items.size() > MAX_ITEMS) items.remove(items.size() - 1);
        persistLocked();
    }

    private Item findLocked(long id) {
        for (Item item : items) if (item.id == id) return item;
        return null;
    }

    private void migrateLegacyLocked(Context context) {
        if (prefs.contains(KEY)) return;
        SharedPreferences legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        String legacyItems = legacy.getString(KEY, null);
        if (legacyItems == null) return;
        prefs.edit().putString(KEY, legacyItems)
                .putLong(NEXT_CUSTOM_ID, legacy.getLong(NEXT_CUSTOM_ID, -1L)).apply();
    }

    private void loadLocked() {
        items.clear();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < array.length() && items.size() < MAX_ITEMS; i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                Item item = new Item();
                item.id = object.optLong("id", 0L);
                item.url = object.optString("url", "");
                item.filename = object.optString("filename", "");
                item.mime = object.optString("mime", "");
                item.createdAt = object.optLong("createdAt", 0L);
                item.updatedAt = object.optLong("updatedAt", item.createdAt);
                item.completedAt = object.optLong("completedAt", 0L);
                item.engine = object.optString("engine", item.id < 0L ? ENGINE_ADAPTIVE : ENGINE_SYSTEM);
                item.status = object.optString("status", STATUS_PENDING);
                item.reason = object.optString("reason", "");
                item.localUri = object.optString("localUri", "");
                item.downloadedBytes = object.optLong("downloadedBytes", 0L);
                item.totalBytes = object.optLong("totalBytes", 0L);
                item.bytesPerSecond = object.optLong("bytesPerSecond", 0L);
                item.etaSeconds = object.optLong("etaSeconds", 0L);
                item.retryCount = object.optInt("retryCount", 0);
                item.userAgent = object.optString("userAgent", "");
                item.headersJson = object.optString("headersJson", "{}");
                item.wifiOnly = object.optBoolean("wifiOnly", false);
                item.allowRoaming = object.optBoolean("allowRoaming", false);
                item.chargingOnly = object.optBoolean("chargingOnly", false);
                item.publicOnly = object.optBoolean("publicOnly", false);
                if (item.id != 0L) items.add(item);
            }
        } catch (Exception ignored) {
            items.clear();
        }
    }

    private void persistLocked() {
        JSONArray array = new JSONArray();
        for (Item item : items) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", item.id);
                object.put("url", item.url);
                object.put("filename", item.filename);
                object.put("mime", item.mime);
                object.put("createdAt", item.createdAt);
                object.put("updatedAt", item.updatedAt);
                object.put("completedAt", item.completedAt);
                object.put("engine", item.engine);
                object.put("status", item.status);
                object.put("reason", item.reason);
                object.put("localUri", item.localUri);
                object.put("downloadedBytes", item.downloadedBytes);
                object.put("totalBytes", item.totalBytes);
                object.put("bytesPerSecond", item.bytesPerSecond);
                object.put("etaSeconds", item.etaSeconds);
                object.put("retryCount", item.retryCount);
                object.put("userAgent", item.userAgent);
                object.put("headersJson", item.headersJson);
                object.put("wifiOnly", item.wifiOnly);
                object.put("allowRoaming", item.allowRoaming);
                object.put("chargingOnly", item.chargingOnly);
                object.put("publicOnly", item.publicOnly);
                array.put(object);
            } catch (Exception ignored) {}
        }
        prefs.edit().putString(KEY, array.toString()).apply();
        lastTelemetryWriteAt = SystemClock.elapsedRealtime();
    }

    private static Item copy(Item source) {
        return source.copy();
    }

    private static String value(String value) { return value == null ? "" : value; }
}
