package com.xinyv.median;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Small JSON-backed registry for user-defined search engines. */
final class SearchEngineStore {
    static final class Engine {
        final String id;
        final String name;
        final String template;

        Engine(String id, String name, String template) {
            this.id = id;
            this.name = name;
            this.template = template;
        }
    }

    private static final String KEY = "custom_search_engines_v2";
    private final SharedPreferences prefs;
    private final ArrayList<Engine> custom = new ArrayList<Engine>();

    SearchEngineStore(SharedPreferences prefs) {
        this.prefs = prefs;
        load();
        migrateLegacy();
    }

    synchronized List<Engine> customEngines() {
        return new ArrayList<Engine>(custom);
    }

    synchronized Engine get(String id) {
        if (id == null) return null;
        for (Engine item : custom) if (item.id.equals(id)) return item;
        return null;
    }

    synchronized Engine add(String name, String template) {
        String id = "custom:" + UrlCleaner.stableId(name + "|" + template + "|" + System.nanoTime());
        Engine value = new Engine(id, cleanName(name), template.trim());
        custom.add(value);
        persist();
        return value;
    }

    synchronized Engine update(String id, String name, String template) {
        for (int i = 0; i < custom.size(); i++) {
            Engine current = custom.get(i);
            if (!current.id.equals(id)) continue;
            Engine value = new Engine(current.id, cleanName(name), template.trim());
            custom.set(i, value);
            persist();
            return value;
        }
        return null;
    }

    synchronized void delete(String id) {
        for (int i = custom.size() - 1; i >= 0; i--) if (custom.get(i).id.equals(id)) custom.remove(i);
        persist();
    }

    synchronized boolean contains(String id) {
        return isBuiltIn(id) || get(id) != null;
    }

    synchronized String label(String id) {
        if ("baidu".equals(id)) return "百度";
        if ("bing".equals(id)) return "Bing";
        if ("google".equals(id)) return "Google";
        Engine engine = get(id);
        return engine == null ? "Google" : engine.name;
    }

    synchronized String template(String id) {
        if ("baidu".equals(id)) return "https://www.baidu.com/s?wd=%s";
        if ("bing".equals(id)) return "https://www.bing.com/search?q=%s";
        if ("google".equals(id)) return "https://www.google.com/search?q=%s";
        Engine engine = get(id);
        return engine == null ? "https://www.google.com/search?q=%s" : engine.template;
    }

    synchronized String signature() {
        StringBuilder value = new StringBuilder();
        for (Engine item : custom) value.append('|').append(item.id).append(':').append(item.name).append(':').append(item.template);
        return value.toString();
    }

    synchronized JSONArray exportJson() {
        JSONArray result = new JSONArray();
        for (Engine item : custom) {
            try {
                result.put(new JSONObject().put("id", item.id).put("name", item.name).put("template", item.template));
            } catch (Exception ignored) {}
        }
        return result;
    }

    synchronized void replaceFromJson(JSONArray array, String legacyTemplate) {
        custom.clear();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String template = item.optString("template", "").trim();
                if (!validTemplate(template)) continue;
                String id = item.optString("id", "").trim();
                if (!id.startsWith("custom:") || get(id) != null)
                    id = "custom:" + UrlCleaner.stableId(template + "|" + i + "|" + System.nanoTime());
                custom.add(new Engine(id, cleanName(item.optString("name", "自定义")), template));
            }
        }
        if (custom.size() == 0 && validTemplate(legacyTemplate)) {
            custom.add(new Engine("custom:" + UrlCleaner.stableId(legacyTemplate), "自定义", legacyTemplate.trim()));
        }
        persist();
    }

    static boolean isBuiltIn(String id) {
        return "google".equals(id) || "baidu".equals(id) || "bing".equals(id);
    }

    static boolean validTemplate(String value) {
        if (value == null || !value.contains("%s") || value.length() >= 2048) return false;
        try {
            java.net.URL parsed = NetworkSecurity.parseHttpsUrl(value.replace("%s", "median-query"));
            return parsed.getRef() == null;
        } catch (Exception ignored) { return false; }
    }

    private void migrateLegacy() {
        if (custom.size() > 0) return;
        String legacy = prefs.getString("custom_search_template", "");
        if (!validTemplate(legacy)) return;
        Engine migrated = add("自定义", legacy);
        if ("custom".equals(prefs.getString("search_engine", "google")))
            prefs.edit().putString("search_engine", migrated.id).apply();
    }

    private void load() {
        custom.clear();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "").trim();
                String template = item.optString("template", "").trim();
                if (!id.startsWith("custom:") || !validTemplate(template) || get(id) != null) continue;
                custom.add(new Engine(id, cleanName(item.optString("name", "自定义")), template));
            }
        } catch (Exception ignored) {
            custom.clear();
        }
    }

    private void persist() {
        prefs.edit().putString(KEY, exportJson().toString()).apply();
    }

    private static String cleanName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.length() == 0) name = "自定义";
        return name.length() > 32 ? name.substring(0, 32) : name;
    }
}
