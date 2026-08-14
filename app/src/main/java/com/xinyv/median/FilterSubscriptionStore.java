package com.xinyv.median;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** HTTPS filter subscriptions with bounded downloads and atomic on-disk replacement. */
final class FilterSubscriptionStore {
    static final class Subscription implements Cloneable {
        String id;
        String name;
        String url;
        boolean enabled;
        long updatedAt;
        int ruleCount;
        String etag = "";
        String lastModified = "";
        String error = "";

        Subscription copy() {
            try { return (Subscription) super.clone(); }
            catch (CloneNotSupportedException impossible) { throw new AssertionError(impossible); }
        }
    }

    interface Callback {
        void onComplete(int updated, int unchanged, int failed, String message);
    }

    private static final String PREFS = "median_filter_subscriptions_v1";
    private static final String KEY = "subscriptions";
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private static final int MAX_TOTAL_BYTES = 28 * 1024 * 1024;
    private static final int MAX_SUBSCRIPTIONS = 32;
    private static final long AUTO_UPDATE_INTERVAL = 7L * 24L * 60L * 60L * 1000L;

    private final SharedPreferences prefs;
    private final File directory;
    private final ArrayList<Subscription> subscriptions = new ArrayList<Subscription>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
            1, 1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1));
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean updateInFlight = new AtomicBoolean();
    private final AtomicBoolean manualUpdatePending = new AtomicBoolean();
    private volatile boolean automaticUpdateInFlight;
    private volatile boolean closed;

    FilterSubscriptionStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        directory = new File(context.getFilesDir(), "filter-subscriptions");
        if (!directory.isDirectory()) directory.mkdirs();
        worker.allowCoreThreadTimeOut(true);
        load();
    }

    synchronized List<Subscription> getAll() {
        ArrayList<Subscription> copy = new ArrayList<Subscription>();
        for (Subscription item : subscriptions) copy.add(item.copy());
        return copy;
    }

    synchronized int enabledCount() {
        int count = 0;
        for (Subscription item : subscriptions) if (item.enabled) count++;
        return count;
    }

    synchronized String exportJson() { return prefs.getString(KEY, "[]"); }

    synchronized int importJson(String raw) throws Exception {
        if (raw == null || raw.length() > 256 * 1024) throw new IllegalArgumentException("过滤订阅设置超过限制");
        JSONArray array = new JSONArray(raw);
        if (array.length() > MAX_SUBSCRIPTIONS) throw new IllegalArgumentException("过滤订阅超过 32 个");
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null || !isHttps(item.optString("url", "")) || item.optString("url", "").length() > 4096)
                throw new IllegalArgumentException("过滤订阅地址无效");
        }
        if (!prefs.edit().putString(KEY, raw).commit()) throw new IllegalStateException("无法保存过滤订阅");
        File[] oldFiles = directory.listFiles();
        if (oldFiles != null) for (File file : oldFiles) if (file.isFile() &&
                (file.getName().endsWith(".txt") || file.getName().endsWith(".tmp"))) file.delete();
        load();
        persist();
        return subscriptions.size();
    }

    synchronized void setEnabled(String id, boolean enabled) {
        for (Subscription item : subscriptions) if (item.id.equals(id)) item.enabled = enabled;
        persist();
    }

    synchronized void add(String name, String url) {
        url = url == null ? "" : url.trim();
        if (!isHttps(url) || url.length() > 4096) throw new IllegalArgumentException("订阅地址必须使用 HTTPS");
        if (subscriptions.size() >= MAX_SUBSCRIPTIONS) throw new IllegalStateException("最多允许 32 个过滤订阅");
        for (Subscription item : subscriptions) if (item.url.equals(url)) throw new IllegalArgumentException("订阅已存在");
        Subscription item = new Subscription();
        item.id = canonicalId(url);
        item.name = cleanName(name, hostOf(url));
        item.url = url;
        item.enabled = true;
        subscriptions.add(item);
        persist();
    }

    synchronized void remove(String id) {
        for (int i = subscriptions.size() - 1; i >= 0; i--) {
            if (!subscriptions.get(i).id.equals(id)) continue;
            fileFor(subscriptions.get(i)).delete();
            subscriptions.remove(i);
        }
        persist();
    }

    List<String> readEnabledRuleSources() {
        ArrayList<Subscription> enabled = new ArrayList<Subscription>();
        synchronized (this) {
            for (Subscription item : subscriptions) if (item.enabled) enabled.add(item.copy());
        }
        ArrayList<String> result = new ArrayList<String>();
        int total = 0;
        for (Subscription item : enabled) {
            File file = fileFor(item);
            if (!file.isFile() || file.length() <= 0L) continue;
            if (file.length() > MAX_BYTES || total + file.length() > MAX_TOTAL_BYTES) continue;
            try {
                byte[] bytes = readFile(file, MAX_BYTES);
                total += bytes.length;
                result.add(new String(bytes, UTF8));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    void updateEnabled(final boolean automatic, final Callback callback) {
        if (closed || worker.isShutdown()) return;
        // Keep the optimized bounded queue, but do not lose a user-triggered manual
        // refresh that arrives while a cheaper automatic refresh is already running.
        if (!updateInFlight.compareAndSet(false, true)) {
            if (!automatic && automaticUpdateInFlight) {
                manualUpdatePending.set(true);
                postResult(callback, 0, 0, 0, "过滤订阅正在自动更新，已排队完整更新");
            } else {
                postResult(callback, 0, 0, 0, "过滤订阅正在更新");
            }
            return;
        }
        manualUpdatePending.set(false);
        try {
            worker.execute(new Runnable() {
                @Override public void run() {
                    int updated = 0;
                    int unchanged = 0;
                    int failed = 0;
                    String lastError = "";
                    boolean runAutomatic = automatic;
                    try {
                        try { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); } catch (RuntimeException ignored) {}
                        while (true) {
                            automaticUpdateInFlight = runAutomatic;
                            ArrayList<Subscription> targets = collectUpdateTargets(runAutomatic);
                            for (Subscription target : targets) {
                                try {
                                    int state = updateOne(target);
                                    if (state > 0) updated++; else unchanged++;
                                } catch (Exception error) {
                                    failed++;
                                    lastError = ErrorText.message(error, 0);
                                    updateMetadata(target.id, null, -1, null, null, lastError);
                                }
                            }
                            if (!manualUpdatePending.getAndSet(false)) break;
                            runAutomatic = false;
                        }
                    } catch (RuntimeException error) {
                        failed++;
                        lastError = ErrorText.message(error, 0);
                    } finally {
                        automaticUpdateInFlight = false;
                        updateInFlight.set(false);
                        postResult(callback, updated, unchanged, failed, lastError);
                    }
                }
            });
        } catch (RejectedExecutionException error) {
            automaticUpdateInFlight = false;
            manualUpdatePending.set(false);
            updateInFlight.set(false);
            postResult(callback, 0, 0, 1, "过滤订阅服务正忙");
        }
    }

    private ArrayList<Subscription> collectUpdateTargets(boolean automatic) {
        ArrayList<Subscription> targets = new ArrayList<Subscription>();
        long now = System.currentTimeMillis();
        synchronized (FilterSubscriptionStore.this) {
            for (Subscription item : subscriptions) {
                if (!item.enabled) continue;
                if (!automatic || item.updatedAt == 0L || now - item.updatedAt >= AUTO_UPDATE_INTERVAL)
                    targets.add(item.copy());
            }
        }
        return targets;
    }

    void close() {
        closed = true;
        worker.shutdownNow();
        manualUpdatePending.set(false);
        automaticUpdateInFlight = false;
        updateInFlight.set(false);
        main.removeCallbacksAndMessages(null);
    }

    private void postResult(final Callback callback, final int updated, final int unchanged,
                            final int failed, final String message) {
        if (callback == null || closed) return;
        main.post(new Runnable() {
            @Override public void run() { callback.onComplete(updated, unchanged, failed, message); }
        });
    }

    private int updateOne(Subscription target) throws Exception {
        if (!isHttps(target.url)) throw new IllegalArgumentException("非 HTTPS 订阅已拒绝");
        HttpURLConnection connection = null;
        try {
            HashMap<String, String> headers = new HashMap<String, String>();
            headers.put("Accept", "text/plain, application/octet-stream;q=0.8, */*;q=0.2");
            headers.put("User-Agent", "MedianBrowser/2.0 filter updater");
            if (target.etag.length() > 0) headers.put("If-None-Match", target.etag);
            if (target.lastModified.length() > 0) headers.put("If-Modified-Since", target.lastModified);
            connection = NetworkSecurity.openPublicHttpsGetFollowingRedirects(new URL(target.url), 12000, 25000, headers);
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                updateMetadata(target.id, System.currentTimeMillis(), target.ruleCount, target.etag, target.lastModified, "");
                return 0;
            }
            if (status < 200 || status >= 300) throw new IllegalArgumentException("HTTP " + status);
            int length = connection.getContentLength();
            if (length > MAX_BYTES) throw new IllegalArgumentException("订阅超过 12 MB");
            byte[] bytes = NetworkSecurity.readBounded(connection.getInputStream(), MAX_BYTES,
                    "下载内容超过大小限制");
            String text = new String(bytes, UTF8);
            int count = plausibleRuleCount(text);
            if (count < 10) throw new IllegalArgumentException("订阅内容不像有效规则列表");
            File targetFile = fileFor(target);
            File temporary = new File(directory, targetFile.getName() + ".tmp");
            FileOutputStream output = new FileOutputStream(temporary);
            try {
                output.write(bytes);
                output.flush();
                output.getFD().sync();
            } finally {
                output.close();
            }
            if (!temporary.renameTo(targetFile)) {
                FileOutputStream direct = new FileOutputStream(targetFile);
                try { direct.write(bytes); } finally { direct.close(); }
                temporary.delete();
            }
            updateMetadata(target.id, System.currentTimeMillis(), count,
                    value(connection.getHeaderField("ETag")), value(connection.getHeaderField("Last-Modified")), "");
            return 1;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private synchronized void updateMetadata(String id, Long updatedAt, int ruleCount, String etag, String modified, String error) {
        for (Subscription item : subscriptions) {
            if (!item.id.equals(id)) continue;
            if (updatedAt != null) item.updatedAt = updatedAt;
            if (ruleCount >= 0) item.ruleCount = ruleCount;
            if (etag != null) item.etag = etag;
            if (modified != null) item.lastModified = modified;
            item.error = error == null ? "" : error;
        }
        persist();
    }

    private void load() {
        subscriptions.clear();
        String raw = prefs.getString(KEY, "");
        if (raw.length() > 0) {
            try {
                JSONArray array = new JSONArray(raw);
                HashSet<String> ids = new HashSet<String>();
                for (int i = 0; i < array.length() && subscriptions.size() < MAX_SUBSCRIPTIONS; i++) {
                    JSONObject object = array.optJSONObject(i);
                    String url = object == null ? "" : object.optString("url", "").trim();
                    if (!isHttps(url) || url.length() > 4096) continue;
                    Subscription item = new Subscription();
                    item.id = canonicalId(url);
                    if (!ids.add(item.id)) continue;
                    String oldId = object.optString("id", "");
                    if (oldId.length() > 0 && !oldId.equals(item.id)) {
                        Subscription legacy = new Subscription();
                        legacy.id = oldId;
                        File oldFile = fileFor(legacy);
                        File currentFile = fileFor(item);
                        if (!currentFile.isFile() && oldFile.isFile()) oldFile.renameTo(currentFile);
                    }
                    item.name = cleanName(object.optString("name", ""), hostOf(url));
                    item.url = url;
                    item.enabled = object.optBoolean("enabled", true);
                    item.updatedAt = object.optLong("updatedAt", 0L);
                    item.ruleCount = Math.max(0, object.optInt("ruleCount", 0));
                    item.etag = bounded(object.optString("etag", ""), 512);
                    item.lastModified = bounded(object.optString("lastModified", ""), 512);
                    item.error = bounded(object.optString("error", ""), 512);
                    subscriptions.add(item);
                }
            } catch (Exception ignored) {
                subscriptions.clear();
            }
        }
        if (subscriptions.size() == 0) {
            subscriptions.add(defaultSubscription("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt", true));
            subscriptions.add(defaultSubscription("easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt", true));
            boolean chinese = Locale.getDefault().getLanguage().toLowerCase(Locale.US).startsWith("zh");
            subscriptions.add(defaultSubscription("easylistchina", "EasyList China", "https://easylist-downloads.adblockplus.org/easylistchina.txt", chinese));
            persist();
        }
    }

    private synchronized void persist() {
        JSONArray array = new JSONArray();
        for (Subscription item : subscriptions) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", item.id);
                object.put("name", item.name);
                object.put("url", item.url);
                object.put("enabled", item.enabled);
                object.put("updatedAt", item.updatedAt);
                object.put("ruleCount", item.ruleCount);
                object.put("etag", item.etag);
                object.put("lastModified", item.lastModified);
                object.put("error", item.error);
                array.put(object);
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(KEY, array.toString()).apply();
    }

    private File fileFor(Subscription item) {
        return new File(directory, item.id.replaceAll("[^a-zA-Z0-9._-]", "_") + ".txt");
    }

    private static Subscription defaultSubscription(String id, String name, String url, boolean enabled) {
        Subscription item = new Subscription();
        item.id = id;
        item.name = name;
        item.url = url;
        item.enabled = enabled;
        return item;
    }

    private static int plausibleRuleCount(String text) {
        int count = 0;
        int start = 0;
        int lines = 0;
        while (start < text.length() && lines < 500000) {
            int end = text.indexOf('\n', start);
            if (end < 0) end = text.length();
            String line = text.substring(start, end).trim();
            if (line.length() >= 3 && !line.startsWith("!") && !line.startsWith("[")) count++;
            lines++;
            start = end + 1;
        }
        return count;
    }

    private static byte[] readFile(File file, int maxBytes) throws Exception {
        return NetworkSecurity.readBounded(new FileInputStream(file), maxBytes, "下载内容超过大小限制");
    }

    private static boolean isHttps(String url) {
        try {
            URL parsed = new URL(url == null ? "" : url.trim());
            return "https".equalsIgnoreCase(parsed.getProtocol()) && parsed.getHost().length() > 0 && parsed.getUserInfo() == null;
        } catch (Exception ignored) { return false; }
    }

    private static String canonicalId(String url) {
        if ("https://easylist.to/easylist/easylist.txt".equals(url)) return "easylist";
        if ("https://easylist.to/easylist/easyprivacy.txt".equals(url)) return "easyprivacy";
        if ("https://easylist-downloads.adblockplus.org/easylistchina.txt".equals(url)) return "easylistchina";
        return "custom-" + UrlCleaner.stableId(url);
    }

    private static String bounded(String value, int max) {
        String result = value == null ? "" : value;
        return result.length() > max ? result.substring(0, max) : result;
    }

    private static String hostOf(String url) {
        try { return new URL(url).getHost(); } catch (Exception ignored) { return "自定义订阅"; }
    }

    private static String cleanName(String name, String fallback) {
        String value = name == null ? "" : name.trim();
        if (value.length() == 0) value = fallback;
        if (value.length() == 0) value = "自定义订阅";
        return value.length() > 80 ? value.substring(0, 80) : value;
    }

    private static String value(String value) { return value == null ? "" : value; }

}
