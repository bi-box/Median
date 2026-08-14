package com.xinyv.median;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Bounded local MHTML archive index. Page bytes never leave the device. */
final class OfflinePageStore {
    interface Callback { void onComplete(Entry entry, Exception error); }

    static final class Entry implements Cloneable {
        String file;
        String title;
        String url;
        long createdAt;
        long size;

        Entry copy() {
            try { return (Entry) super.clone(); }
            catch (CloneNotSupportedException impossible) { throw new AssertionError(impossible); }
        }
    }

    private static final int MAX_ENTRIES = 30;
    private static final long MAX_BYTES = 128L * 1024L * 1024L;
    private static final String PREFS = "median_offline_v1";
    private final SharedPreferences prefs;
    private final File root;
    private final ArrayList<Entry> entries = new ArrayList<Entry>();

    OfflinePageStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        root = new File(context.getFilesDir(), "offline");
        if (!root.isDirectory()) root.mkdirs();
        load();
    }

    void save(WebView view, String title, String url, final Callback callback) {
        if (view == null) { if (callback != null) callback.onComplete(null, new IllegalArgumentException("没有活动页面")); return; }
        final Entry entry = new Entry();
        entry.file = "page-" + System.currentTimeMillis() + '-' + Long.toHexString(System.nanoTime()) + ".mht";
        entry.title = safe(title, 180);
        entry.url = safe(url, 4096);
        entry.createdAt = System.currentTimeMillis();
        final File target = new File(root, entry.file);
        view.saveWebArchive(target.getAbsolutePath(), false, new ValueCallback<String>() {
            @Override public void onReceiveValue(String path) {
                if (path == null || !target.isFile() || target.length() == 0L) {
                    target.delete();
                    if (callback != null) callback.onComplete(null, new IllegalStateException("系统 WebView 无法保存此页面"));
                    return;
                }
                if (target.length() > MAX_BYTES) {
                    target.delete();
                    if (callback != null) callback.onComplete(null, new IllegalStateException("单个离线页面超过 128 MB"));
                    return;
                }
                entry.size = target.length();
                synchronized (OfflinePageStore.this) {
                    entries.add(0, entry);
                    trim();
                    persist();
                }
                if (callback != null) callback.onComplete(copy(entry), null);
            }
        });
    }

    synchronized List<Entry> getAll() {
        ArrayList<Entry> result = new ArrayList<Entry>();
        for (Entry entry : entries) result.add(copy(entry));
        return result;
    }

    synchronized void remove(String file) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).file.equals(file)) {
                new File(root, entries.get(i).file).delete();
                entries.remove(i);
            }
        }
        persist();
    }

    Uri uriFor(Entry entry) {
        return new Uri.Builder().scheme("content").authority(OfflineContentProvider.AUTHORITY)
                .appendPath(entry == null ? "" : entry.file).build();
    }

    private void load() {
        try {
            JSONArray array = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < array.length() && entries.size() < MAX_ENTRIES; i++) {
                JSONObject value = array.optJSONObject(i);
                if (value == null) continue;
                Entry entry = new Entry();
                entry.file = value.optString("file", "");
                entry.title = value.optString("title", "离线页面");
                entry.url = value.optString("url", "");
                entry.createdAt = value.optLong("createdAt", 0L);
                File file = new File(root, entry.file);
                if (!entry.file.matches("[A-Za-z0-9._-]+") || !entry.file.endsWith(".mht") || !file.isFile()) continue;
                entry.size = file.length();
                entries.add(entry);
            }
            trim();
            HashSet<String> indexed = new HashSet<String>();
            for (Entry entry : entries) indexed.add(entry.file);
            File[] files = root.listFiles();
            if (files != null) for (File file : files) if (file.isFile() && file.getName().endsWith(".mht") &&
                    !indexed.contains(file.getName())) file.delete();
            persist();
        } catch (Exception ignored) { entries.clear(); }
    }

    private void trim() {
        long total = 0L;
        for (Entry entry : entries) total += entry.size;
        while (entries.size() > MAX_ENTRIES || total > MAX_BYTES) {
            Entry removed = entries.remove(entries.size() - 1);
            total -= removed.size;
            new File(root, removed.file).delete();
        }
    }

    private void persist() {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            JSONObject value = new JSONObject();
            try {
                value.put("file", entry.file);
                value.put("title", entry.title);
                value.put("url", entry.url);
                value.put("createdAt", entry.createdAt);
                array.put(value);
            } catch (Exception ignored) {}
        }
        prefs.edit().putString("items", array.toString()).apply();
    }

    private static Entry copy(Entry source) {
        return source.copy();
    }

    private static String safe(String value, int max) {
        String result = value == null ? "" : value.trim();
        return result.length() > max ? result.substring(0, max) : result;
    }
}
