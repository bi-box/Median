package com.xinyv.median;

import android.content.Context;
import android.os.Handler;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * In-memory browser library with coalesced, atomic background persistence.
 * Reads never touch disk after construction and navigation never waits for a write.
 */
final class BrowserDataStore {
    static final class Bookmark {
        final String title;
        final String url;
        final long createdAt;

        Bookmark(String title, String url, long createdAt) {
            this.title = title;
            this.url = url;
            this.createdAt = createdAt;
        }
    }

    static final class HistoryItem {
        final String title;
        final String url;
        final long visitedAt;
        final int visits;

        HistoryItem(String title, String url, long visitedAt, int visits) {
            this.title = title;
            this.url = url;
            this.visitedAt = visitedAt;
            this.visits = visits;
        }
    }

    static final class SessionTab {
        final String title;
        final String url;
        final boolean pinned;

        SessionTab(String title, String url, boolean pinned) {
            this.title = title;
            this.url = url;
            this.pinned = pinned;
        }
    }

    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int MAX_HISTORY = 1200;
    private static final int MAX_SESSION_TABS = 64;
    private static final int MAX_BOOKMARKS = 1500;
    private static final int MAX_LIBRARY_BYTES = 8 * 1024 * 1024;
    private static final long WRITE_DELAY_MS = 650L;
    private static final long WRITE_RETRY_MS = 5000L;
    private static final String FILE_NAME = "browser-library-v1.json";
    private static final String HOME_URL = "https://median.invalid/";

    private final AtomicFile file;
    private final AtomicFile backup;
    private final Object lock = new Object();
    private final ArrayList<Bookmark> bookmarks = new ArrayList<Bookmark>();
    private final ArrayList<HistoryItem> history = new ArrayList<HistoryItem>();
    private final ArrayList<SessionTab> session = new ArrayList<SessionTab>();
    private final Handler io;
    private int sessionIndex;
    private volatile boolean closed;
    private boolean dirty;
    private boolean retryPending;

    private final Runnable writer = new Runnable() {
        @Override public void run() { writeSnapshot(); }
    };

    BrowserDataStore(Context context) {
        File directory = context.getFilesDir();
        file = new AtomicFile(new File(directory, FILE_NAME));
        backup = new AtomicFile(new File(directory, FILE_NAME + ".good"));
        readBlocking();
        io = LocalDataIo.acquire();
    }

    List<Bookmark> bookmarks() {
        synchronized (lock) { return new ArrayList<Bookmark>(bookmarks); }
    }

    boolean isBookmarked(String url) {
        String normalized = normalizeUrl(url);
        synchronized (lock) {
            for (Bookmark item : bookmarks) if (sameNormalizedUrl(item.url, normalized)) return true;
        }
        return false;
    }

    boolean toggleBookmark(String title, String url) {
        String normalized = normalizeUrl(url);
        if (!isWebUrl(normalized)) return false;
        boolean added = true;
        synchronized (lock) {
            for (int i = bookmarks.size() - 1; i >= 0; i--) {
                if (sameNormalizedUrl(bookmarks.get(i).url, normalized)) {
                    bookmarks.remove(i);
                    added = false;
                }
            }
            if (added) {
                bookmarks.add(0, new Bookmark(safeTitle(title, normalized), normalized, System.currentTimeMillis()));
                while (bookmarks.size() > MAX_BOOKMARKS) bookmarks.remove(bookmarks.size() - 1);
            }
        }
        scheduleWrite();
        return added;
    }

    Bookmark bookmark(String url) {
        String normalized = normalizeUrl(url);
        synchronized (lock) {
            for (Bookmark item : bookmarks) if (sameNormalizedUrl(item.url, normalized)) return item;
        }
        return null;
    }

    Bookmark addBookmark(String title, String url) {
        String normalized = normalizeUrl(url);
        if (!isWebUrl(normalized)) return null;
        Bookmark added;
        synchronized (lock) {
            for (Bookmark item : bookmarks) if (sameNormalizedUrl(item.url, normalized)) return null;
            added = new Bookmark(safeTitle(title, normalized), normalized, System.currentTimeMillis());
            bookmarks.add(0, added);
            while (bookmarks.size() > MAX_BOOKMARKS) bookmarks.remove(bookmarks.size() - 1);
        }
        scheduleWrite();
        return added;
    }

    boolean updateBookmark(String originalUrl, String title, String url) {
        String original = normalizeUrl(originalUrl);
        String normalized = normalizeUrl(url);
        if (!isWebUrl(normalized)) return false;
        boolean changed = false;
        synchronized (lock) {
            int found = -1;
            for (int i = 0; i < bookmarks.size(); i++) {
                Bookmark item = bookmarks.get(i);
                if (sameNormalizedUrl(item.url, original)) found = i;
                else if (sameNormalizedUrl(item.url, normalized)) return false;
            }
            if (found < 0) return false;
            Bookmark before = bookmarks.get(found);
            String safe = safeTitle(title, normalized);
            if (!before.url.equals(normalized) || !before.title.equals(safe)) {
                bookmarks.set(found, new Bookmark(safe, normalized, before.createdAt));
                changed = true;
            }
        }
        if (changed) scheduleWrite();
        return true;
    }

    void removeBookmark(String url) {
        String normalized = normalizeUrl(url);
        boolean changed = false;
        synchronized (lock) {
            for (int i = bookmarks.size() - 1; i >= 0; i--) {
                if (sameNormalizedUrl(bookmarks.get(i).url, normalized)) {
                    bookmarks.remove(i);
                    changed = true;
                }
            }
        }
        if (changed) scheduleWrite();
    }

    void recordVisit(String title, String url) {
        String normalized = normalizeUrl(url);
        if (!isWebUrl(normalized)) return;
        long now = System.currentTimeMillis();
        synchronized (lock) {
            HistoryItem previous = history.size() == 0 ? null : history.get(0);
            if (previous != null && sameNormalizedUrl(previous.url, normalized) && now - previous.visitedAt < 90_000L) {
                history.set(0, new HistoryItem(safeTitle(title, normalized), normalized, now, previous.visits + 1));
            } else {
                int visits = 1;
                for (int i = history.size() - 1; i >= 0; i--) {
                    HistoryItem item = history.get(i);
                    if (sameNormalizedUrl(item.url, normalized)) {
                        visits = item.visits + 1;
                        history.remove(i);
                        break;
                    }
                }
                history.add(0, new HistoryItem(safeTitle(title, normalized), normalized, now, visits));
                while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
            }
        }
        scheduleWrite();
    }

    List<HistoryItem> recentHistory(int limit, String query) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        ArrayList<HistoryItem> result = new ArrayList<HistoryItem>();
        synchronized (lock) {
            for (HistoryItem item : history) {
                if (needle.length() > 0 && !item.title.toLowerCase(Locale.US).contains(needle) &&
                        !item.url.toLowerCase(Locale.US).contains(needle)) continue;
                result.add(new HistoryItem(item.title, item.url, item.visitedAt, item.visits));
                if (result.size() >= safeLimit) break;
            }
        }
        return result;
    }

    void clearHistory() {
        boolean changed;
        synchronized (lock) {
            changed = !history.isEmpty();
            if (changed) history.clear();
        }
        if (changed) scheduleWrite();
    }

    void saveSession(List<SessionTab> tabs, int selectedIndex) {
        ArrayList<SessionTab> next = new ArrayList<SessionTab>();
        int count = Math.min(MAX_SESSION_TABS, tabs == null ? 0 : tabs.size());
        for (int i = 0; i < count; i++) {
            SessionTab tab = tabs.get(i);
            if (tab == null) continue;
            String url = normalizeSessionUrl(tab.url);
            if (isWebUrl(url) || isHomePage(url)) next.add(new SessionTab(safeTitle(tab.title, url), url, tab.pinned));
        }
        int nextIndex = next.size() == 0 ? 0 : Math.max(0, Math.min(selectedIndex, next.size() - 1));
        boolean changed;
        synchronized (lock) {
            changed = sessionIndex != nextIndex || session.size() != next.size();
            if (!changed) for (int i = 0; i < next.size(); i++) {
                SessionTab before = session.get(i);
                SessionTab after = next.get(i);
                if (before.pinned != after.pinned || !before.url.equals(after.url) || !before.title.equals(after.title)) {
                    changed = true;
                    break;
                }
            }
            if (!changed) return;
            session.clear();
            session.addAll(next);
            sessionIndex = nextIndex;
        }
        scheduleWrite();
    }

    List<SessionTab> restoreSession() {
        synchronized (lock) { return new ArrayList<SessionTab>(session); }
    }

    int restoredSessionIndex() {
        synchronized (lock) { return sessionIndex; }
    }

    String exportJson() {
        ArrayList<Bookmark> snapshot;
        synchronized (lock) { snapshot = new ArrayList<Bookmark>(bookmarks); }
        JSONObject root = new JSONObject();
        JSONArray bookmarkArray = new JSONArray();
        try {
            for (Bookmark item : snapshot) {
                JSONObject value = new JSONObject();
                value.put("title", item.title);
                value.put("url", item.url);
                value.put("createdAt", item.createdAt);
                bookmarkArray.put(value);
            }
            root.put("version", 2);
            root.put("type", "median-bookmarks");
            root.put("bookmarks", bookmarkArray);
        } catch (Exception ignored) {}
        return root.toString();
    }

    JSONObject exportPortable() { return snapshotJson(); }

    int importPortable(JSONObject root) throws Exception {
        if (root == null) throw new IllegalArgumentException("备份中没有浏览数据");
        JSONArray savedBookmarks = root.optJSONArray("bookmarks");
        if (savedBookmarks == null) throw new IllegalArgumentException("备份中没有书签数据");
        ArrayList<Bookmark> loadedBookmarks = new ArrayList<Bookmark>();
        ArrayList<HistoryItem> loadedHistory = new ArrayList<HistoryItem>();
        ArrayList<SessionTab> loadedSession = new ArrayList<SessionTab>();
        HashSet<String> known = new HashSet<String>();
        for (int i = 0; i < savedBookmarks.length() && loadedBookmarks.size() < MAX_BOOKMARKS; i++) {
            JSONObject value = savedBookmarks.optJSONObject(i);
            if (value == null) continue;
            String url = normalizeUrl(value.optString("url", ""));
            if (isWebUrl(url) && known.add(url)) loadedBookmarks.add(new Bookmark(
                    safeTitle(value.optString("title", ""), url), url, value.optLong("createdAt", 0L)));
        }
        JSONArray savedHistory = root.optJSONArray("history");
        if (savedHistory != null) for (int i = 0; i < savedHistory.length() && loadedHistory.size() < MAX_HISTORY; i++) {
            JSONObject value = savedHistory.optJSONObject(i);
            if (value == null) continue;
            String url = normalizeUrl(value.optString("url", ""));
            if (isWebUrl(url)) loadedHistory.add(new HistoryItem(safeTitle(value.optString("title", ""), url), url,
                    value.optLong("visitedAt", 0L), Math.max(1, value.optInt("visits", 1))));
        }
        JSONArray savedSession = root.optJSONArray("session");
        if (savedSession != null) for (int i = 0; i < savedSession.length() && loadedSession.size() < MAX_SESSION_TABS; i++) {
            JSONObject value = savedSession.optJSONObject(i);
            if (value == null) continue;
            String url = normalizeSessionUrl(value.optString("url", ""));
            if (isWebUrl(url) || isHomePage(url)) loadedSession.add(new SessionTab(
                    safeTitle(value.optString("title", ""), url), url, value.optBoolean("pinned", false)));
        }
        synchronized (lock) {
            bookmarks.clear(); bookmarks.addAll(loadedBookmarks);
            history.clear(); history.addAll(loadedHistory);
            session.clear(); session.addAll(loadedSession);
            sessionIndex = Math.max(0, Math.min(root.optInt("sessionIndex", 0), Math.max(0, session.size() - 1)));
        }
        scheduleWrite();
        return loadedBookmarks.size();
    }

    int importJson(String raw) throws Exception {
        if (raw == null || raw.getBytes(UTF8).length > 4 * 1024 * 1024) throw new IllegalArgumentException("备份超过 4 MB");
        JSONObject object = new JSONObject(raw);
        JSONArray incoming = object.optJSONArray("bookmarks");
        if (incoming == null) throw new IllegalArgumentException("备份中没有书签数据");
        int imported = 0;
        synchronized (lock) {
            HashSet<String> known = new HashSet<String>();
            for (Bookmark current : bookmarks) known.add(normalizeUrl(current.url));
            for (int i = 0; i < incoming.length() && bookmarks.size() < MAX_BOOKMARKS; i++) {
                JSONObject value = incoming.optJSONObject(i);
                if (value == null) continue;
                String url = normalizeUrl(value.optString("url", ""));
                if (!isWebUrl(url)) continue;
                if (known.add(url)) {
                    bookmarks.add(new Bookmark(safeTitle(value.optString("title", ""), url), url,
                            value.optLong("createdAt", System.currentTimeMillis())));
                    imported++;
                }
            }
            Collections.sort(bookmarks, new Comparator<Bookmark>() {
                @Override public int compare(Bookmark a, Bookmark b) { return a.createdAt == b.createdAt ? 0 : (a.createdAt > b.createdAt ? -1 : 1); }
            });
        }
        if (imported > 0) scheduleWrite();
        return imported;
    }

    void flush() {
        synchronized (lock) {
            if (closed || !dirty) return;
            retryPending = false;
        }
        io.removeCallbacks(writer);
        io.post(writer);
    }

    void close() {
        boolean shouldWrite;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            retryPending = false;
            shouldWrite = dirty;
        }
        io.removeCallbacks(writer);
        if (shouldWrite) io.post(writer);
        LocalDataIo.release();
    }

    private void scheduleWrite() {
        synchronized (lock) {
            if (closed) return;
            dirty = true;
            retryPending = false;
        }
        io.removeCallbacks(writer);
        io.postDelayed(writer, WRITE_DELAY_MS);
    }

    private JSONObject snapshotJson() {
        ArrayList<Bookmark> bookmarkSnapshot;
        ArrayList<HistoryItem> historySnapshot;
        ArrayList<SessionTab> sessionSnapshot;
        int selectedIndex;
        synchronized (lock) {
            bookmarkSnapshot = new ArrayList<Bookmark>(bookmarks);
            historySnapshot = new ArrayList<HistoryItem>(history);
            sessionSnapshot = new ArrayList<SessionTab>(session);
            selectedIndex = sessionIndex;
        }
        JSONObject root = new JSONObject();
        JSONArray bookmarkArray = new JSONArray();
        JSONArray historyArray = new JSONArray();
        JSONArray sessionArray = new JSONArray();
        try {
            for (Bookmark item : bookmarkSnapshot) {
                JSONObject value = new JSONObject();
                value.put("title", item.title);
                value.put("url", item.url);
                value.put("createdAt", item.createdAt);
                bookmarkArray.put(value);
            }
            for (HistoryItem item : historySnapshot) {
                JSONObject value = new JSONObject();
                value.put("title", item.title);
                value.put("url", item.url);
                value.put("visitedAt", item.visitedAt);
                value.put("visits", item.visits);
                historyArray.put(value);
            }
            for (SessionTab item : sessionSnapshot) {
                JSONObject value = new JSONObject();
                value.put("title", item.title);
                value.put("url", item.url);
                value.put("pinned", item.pinned);
                sessionArray.put(value);
            }
            root.put("version", 1);
            root.put("bookmarks", bookmarkArray);
            root.put("history", historyArray);
            root.put("session", sessionArray);
            root.put("sessionIndex", selectedIndex);
        } catch (Exception ignored) {}
        return root;
    }

    private void readBlocking() {
        if (readFile(file)) return;
        readFile(backup);
    }

    private boolean readFile(AtomicFile source) {
        try {
            String raw = AtomicTextFile.read(source, MAX_LIBRARY_BYTES);
            if (raw == null) return false;
            JSONObject root = new JSONObject(raw);
            JSONArray savedBookmarks = root.optJSONArray("bookmarks");
            JSONArray savedHistory = root.optJSONArray("history");
            JSONArray savedSession = root.optJSONArray("session");
            ArrayList<Bookmark> loadedBookmarks = new ArrayList<Bookmark>();
            ArrayList<HistoryItem> loadedHistory = new ArrayList<HistoryItem>();
            ArrayList<SessionTab> loadedSession = new ArrayList<SessionTab>();
            if (savedBookmarks != null) for (int i = 0; i < savedBookmarks.length() && loadedBookmarks.size() < MAX_BOOKMARKS; i++) {
                JSONObject value = savedBookmarks.optJSONObject(i);
                if (value == null) continue;
                String url = normalizeUrl(value.optString("url", ""));
                if (isWebUrl(url)) loadedBookmarks.add(new Bookmark(safeTitle(value.optString("title", ""), url), url,
                        value.optLong("createdAt", 0L)));
            }
            if (savedHistory != null) for (int i = 0; i < savedHistory.length() && loadedHistory.size() < MAX_HISTORY; i++) {
                JSONObject value = savedHistory.optJSONObject(i);
                if (value == null) continue;
                String url = normalizeUrl(value.optString("url", ""));
                if (isWebUrl(url)) loadedHistory.add(new HistoryItem(safeTitle(value.optString("title", ""), url), url,
                        value.optLong("visitedAt", 0L), Math.max(1, value.optInt("visits", 1))));
            }
            if (savedSession != null) for (int i = 0; i < savedSession.length() && loadedSession.size() < MAX_SESSION_TABS; i++) {
                JSONObject value = savedSession.optJSONObject(i);
                if (value == null) continue;
                String url = normalizeSessionUrl(value.optString("url", ""));
                if (isWebUrl(url) || isHomePage(url)) {
                    loadedSession.add(new SessionTab(safeTitle(value.optString("title", ""), url), url, value.optBoolean("pinned", false)));
                }
            }
            bookmarks.clear();
            history.clear();
            session.clear();
            bookmarks.addAll(loadedBookmarks);
            history.addAll(loadedHistory);
            session.addAll(loadedSession);
            sessionIndex = Math.max(0, Math.min(root.optInt("sessionIndex", 0), Math.max(0, session.size() - 1)));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void writeSnapshot() {
        synchronized (lock) {
            retryPending = false;
            if (!dirty) return;
            dirty = false;
        }
        String raw = snapshotJson().toString();
        boolean saved = AtomicTextFile.write(file, raw);
        if (saved) AtomicTextFile.write(backup, raw);
        boolean retry = false;
        if (!saved) {
            synchronized (lock) {
                dirty = true;
                if (!closed && !retryPending) {
                    retryPending = true;
                    retry = true;
                }
            }
        }
        if (retry) io.postDelayed(writer, WRITE_RETRY_MS);
    }

    private static boolean sameNormalizedUrl(String stored, String normalized) {
        if (stored == null) return normalized == null || normalized.length() == 0;
        if (stored.equals(normalized)) return true;
        return normalizeUrl(stored).equals(normalized);
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String value = url.trim();
        while (value.endsWith("/") && value.length() > "https://a/".length()) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String normalizeSessionUrl(String url) {
        String value = normalizeUrl(url);
        if (isHomePage(value) || UrlCleaner.isInternalPage(value, "median.home")) return HOME_URL;
        return value;
    }

    private static boolean isHomePage(String url) {
        return UrlCleaner.isInternalPage(url, "median.invalid");
    }

    private static boolean isWebUrl(String url) {
        if (url == null || url.length() > 2048) return false;
        try {
            NetworkSecurity.parseHttpUrl(url);
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static String safeTitle(String title, String url) {
        String value = title == null ? "" : title.trim();
        if (value.length() == 0) value = url;
        return value.length() > 160 ? value.substring(0, 160) : value;
    }
}
