package com.xinyv.median;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Hierarchical bookmark folders; bookmark content remains in BrowserDataStore. */
final class BookmarkFolderStore {
    static final String ROOT = "";

    static final class Folder {
        final String id;
        final String name;
        final String parentId;
        final boolean showOnHome;
        final long createdAt;

        Folder(String id, String name, String parentId, boolean showOnHome, long createdAt) {
            this.id = id;
            this.name = name;
            this.parentId = parentId;
            this.showOnHome = showOnHome;
            this.createdAt = createdAt;
        }
    }

    private static final String PREFS = "median_bookmark_folders_v1";
    private static final String KEY = "tree";
    private final SharedPreferences prefs;
    private final LinkedHashMap<String, Folder> folders = new LinkedHashMap<String, Folder>();
    private final HashMap<String, String> bookmarkParents = new HashMap<String, String>();

    BookmarkFolderStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load(prefs.getString(KEY, "{}"));
    }

    /** In-memory store used by the JVM tree-behavior self-test. */
    BookmarkFolderStore() { prefs = null; }

    BookmarkFolderStore(String raw) {
        prefs = null;
        load(raw);
    }

    synchronized List<Folder> foldersIn(String parentId) {
        String parent = validFolder(parentId) ? parentId : ROOT;
        ArrayList<Folder> result = new ArrayList<Folder>();
        for (Folder folder : folders.values()) if (folder.parentId.equals(parent)) result.add(folder);
        Collections.sort(result, new Comparator<Folder>() {
            @Override public int compare(Folder a, Folder b) {
                return a.createdAt == b.createdAt ? a.name.compareToIgnoreCase(b.name) : (a.createdAt < b.createdAt ? -1 : 1);
            }
        });
        return result;
    }

    synchronized List<Folder> allFolders() { return new ArrayList<Folder>(folders.values()); }

    synchronized Folder folder(String id) { return folders.get(safeId(id)); }

    synchronized Folder create(String name, String parentId, boolean showOnHome) {
        String title = cleanName(name);
        if (title.length() == 0) return null;
        String parent = validFolder(parentId) ? parentId : ROOT;
        String id = "f-" + UUID.randomUUID().toString();
        Folder folder = new Folder(id, title, parent, showOnHome, System.currentTimeMillis());
        folders.put(id, folder);
        persist();
        return folder;
    }

    synchronized boolean update(String id, String name, String parentId, boolean showOnHome) {
        Folder current = folders.get(safeId(id));
        String title = cleanName(name);
        if (current == null || title.length() == 0) return false;
        String parent = validFolder(parentId) ? parentId : ROOT;
        if (current.id.equals(parent) || isDescendant(parent, current.id)) return false;
        Folder next = new Folder(current.id, title, parent, showOnHome, current.createdAt);
        if (same(current, next)) return true;
        folders.put(next.id, next);
        persist();
        return true;
    }

    synchronized boolean remove(String id) {
        String target = safeId(id);
        if (!folders.containsKey(target)) return false;
        HashSet<String> removed = new HashSet<String>();
        removed.add(target);
        boolean changed;
        do {
            changed = false;
            for (Folder folder : folders.values()) if (removed.contains(folder.parentId) && removed.add(folder.id)) changed = true;
        } while (changed);
        for (String folderId : removed) folders.remove(folderId);
        for (Map.Entry<String, String> item : new ArrayList<Map.Entry<String, String>>(bookmarkParents.entrySet()))
            if (removed.contains(item.getValue())) bookmarkParents.remove(item.getKey());
        persist();
        return true;
    }

    synchronized int descendantFolderCount(String id) {
        String target = safeId(id);
        if (!folders.containsKey(target)) return 0;
        int count = 0;
        for (Folder folder : folders.values())
            if (!target.equals(folder.id) && isDescendant(folder.id, target)) count++;
        return count;
    }

    synchronized int bookmarkCountInTree(String id) {
        String target = safeId(id);
        if (!folders.containsKey(target)) return 0;
        int count = 0;
        for (String parent : bookmarkParents.values())
            if (target.equals(parent) || isDescendant(parent, target)) count++;
        return count;
    }

    synchronized String parentForUrl(String url) {
        String parent = bookmarkParents.get(normalizeUrl(url));
        return validFolder(parent) ? parent : ROOT;
    }

    synchronized void setParentForUrl(String url, String folderId) {
        String key = normalizeUrl(url);
        if (key.length() == 0) return;
        String parent = validFolder(folderId) ? folderId : ROOT;
        if (parent.length() == 0) bookmarkParents.remove(key); else bookmarkParents.put(key, parent);
        persist();
    }

    synchronized void updateUrl(String oldUrl, String newUrl) {
        String oldKey = normalizeUrl(oldUrl);
        String nextKey = normalizeUrl(newUrl);
        String parent = bookmarkParents.remove(oldKey);
        if (parent != null && nextKey.length() > 0 && validFolder(parent)) bookmarkParents.put(nextKey, parent);
        persist();
    }

    synchronized void removeUrl(String url) {
        if (bookmarkParents.remove(normalizeUrl(url)) != null) persist();
    }

    synchronized List<Folder> homeFolders() {
        ArrayList<Folder> result = new ArrayList<Folder>();
        for (Folder folder : folders.values()) if (folder.showOnHome) result.add(folder);
        return result;
    }

    synchronized String path(String id) {
        Folder current = folders.get(safeId(id));
        if (current == null) return "全部收藏";
        ArrayList<String> parts = new ArrayList<String>();
        HashSet<String> seen = new HashSet<String>();
        while (current != null && seen.add(current.id)) {
            parts.add(current.name);
            current = folders.get(current.parentId);
        }
        Collections.reverse(parts);
        StringBuilder value = new StringBuilder("全部收藏");
        for (String part : parts) value.append(" / ").append(part);
        return value.toString();
    }

    synchronized String exportJson() {
        JSONObject root = new JSONObject();
        JSONArray list = new JSONArray();
        JSONObject parents = new JSONObject();
        try {
            for (Folder folder : folders.values()) {
                JSONObject item = new JSONObject();
                item.put("id", folder.id).put("name", folder.name).put("parentId", folder.parentId)
                        .put("showOnHome", folder.showOnHome).put("createdAt", folder.createdAt);
                list.put(item);
            }
            for (Map.Entry<String, String> item : bookmarkParents.entrySet()) parents.put(item.getKey(), item.getValue());
            root.put("version", 1).put("folders", list).put("bookmarkParents", parents);
        } catch (Exception ignored) {}
        return root.toString();
    }

    synchronized void importJson(String raw) throws Exception {
        State parsed = parse(raw);
        folders.clear(); folders.putAll(parsed.folders);
        bookmarkParents.clear(); bookmarkParents.putAll(parsed.parents);
        persist();
    }

    synchronized void mergeJson(String raw) throws Exception {
        State parsed = parse(raw);
        for (Folder folder : parsed.folders.values()) if (!folders.containsKey(folder.id)) folders.put(folder.id, folder);
        for (Map.Entry<String, String> item : parsed.parents.entrySet())
            if (folders.containsKey(item.getValue())) bookmarkParents.put(item.getKey(), item.getValue());
        persist();
    }

    private void load(String raw) {
        try {
            State state = parse(raw);
            folders.clear(); folders.putAll(state.folders);
            bookmarkParents.clear(); bookmarkParents.putAll(state.parents);
        } catch (Exception ignored) {
            folders.clear(); bookmarkParents.clear();
        }
    }

    private static State parse(String raw) throws Exception {
        if (raw == null || raw.length() > 2 * 1024 * 1024) throw new IllegalArgumentException("收藏文件夹数据过大");
        JSONObject root = new JSONObject(raw.length() == 0 ? "{}" : raw);
        LinkedHashMap<String, Folder> parsedFolders = new LinkedHashMap<String, Folder>();
        JSONArray list = root.optJSONArray("folders");
        if (list != null) for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            String id = safeId(item.optString("id", ""));
            String name = cleanName(item.optString("name", ""));
            if (id.length() == 0 || name.length() == 0 || parsedFolders.containsKey(id)) continue;
            parsedFolders.put(id, new Folder(id, name, safeId(item.optString("parentId", "")),
                    item.optBoolean("showOnHome", false), item.optLong("createdAt", i)));
        }
        // Break missing-parent links and cycles without dropping the folder itself.
        for (Folder folder : new ArrayList<Folder>(parsedFolders.values())) {
            String parent = parsedFolders.containsKey(folder.parentId) ? folder.parentId : ROOT;
            if (folder.id.equals(parent) || descendantIn(parsedFolders, parent, folder.id)) parent = ROOT;
            if (!parent.equals(folder.parentId)) parsedFolders.put(folder.id,
                    new Folder(folder.id, folder.name, parent, folder.showOnHome, folder.createdAt));
        }
        HashMap<String, String> parents = new HashMap<String, String>();
        JSONObject savedParents = root.optJSONObject("bookmarkParents");
        if (savedParents != null) {
            java.util.Iterator<String> names = savedParents.keys();
            while (names.hasNext()) {
                String rawUrl = names.next();
                String url = normalizeUrl(rawUrl);
                String parent = safeId(savedParents.optString(rawUrl, ""));
                if (url.length() > 0 && parsedFolders.containsKey(parent)) parents.put(url, parent);
            }
        }
        return new State(parsedFolders, parents);
    }

    private static boolean descendantIn(Map<String, Folder> values, String candidate, String ancestor) {
        HashSet<String> seen = new HashSet<String>();
        String current = candidate;
        while (current.length() > 0 && seen.add(current)) {
            if (ancestor.equals(current)) return true;
            Folder folder = values.get(current);
            current = folder == null ? ROOT : folder.parentId;
        }
        return false;
    }

    private boolean isDescendant(String candidate, String ancestor) { return descendantIn(folders, candidate, ancestor); }
    private boolean validFolder(String id) { return id != null && id.length() > 0 && folders.containsKey(id); }
    private static boolean same(Folder a, Folder b) { return a.name.equals(b.name) && a.parentId.equals(b.parentId) && a.showOnHome == b.showOnHome; }
    private static String safeId(String value) { return value == null ? ROOT : value.trim(); }
    private static String cleanName(String value) { String v = value == null ? "" : value.trim(); return v.length() > 80 ? v.substring(0, 80) : v; }
    private static String normalizeUrl(String value) {
        String url = value == null ? "" : value.trim();
        while (url.endsWith("/") && url.length() > "https://a/".length()) url = url.substring(0, url.length() - 1);
        return url;
    }
    private void persist() { if (prefs != null) prefs.edit().putString(KEY, exportJson()).apply(); }

    private static final class State {
        final LinkedHashMap<String, Folder> folders;
        final HashMap<String, String> parents;
        State(LinkedHashMap<String, Folder> folders, HashMap<String, String> parents) {
            this.folders = folders; this.parents = parents;
        }
    }
}
