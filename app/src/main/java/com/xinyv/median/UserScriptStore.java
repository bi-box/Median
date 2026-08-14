package com.xinyv.median;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class UserScriptStore {
    static final class Script {
        static final class Resource {
            String name = "";
            String url = "";
            String mime = "application/octet-stream";
            String base64 = "";
        }

        String id = "";
        String name = "未命名脚本";
        String version = "";
        String namespace = "";
        String description = "";
        String author = "";
        String homepage = "";
        String sourceUrl = "";
        String updateUrl = "";
        String downloadUrl = "";
        String runAt = "document-end";
        String code = "";
        String requireCode = "";
        boolean noFrames;
        boolean enabled = true;
        boolean quarantined;
        String disabledReason = "";
        int riskScore;
        String riskSummary = "低风险";
        long installedAt;
        long updatedAt;
        long lastUpdateCheck;
        final ArrayList<String> matches = new ArrayList<String>();
        final ArrayList<String> excludes = new ArrayList<String>();
        final ArrayList<String> grants = new ArrayList<String>();
        final ArrayList<String> requires = new ArrayList<String>();
        final ArrayList<String> connects = new ArrayList<String>();
        final ArrayList<Resource> resources = new ArrayList<Resource>();
        final ArrayList<Pattern> compiledMatches = new ArrayList<Pattern>();
        final ArrayList<Pattern> compiledExcludes = new ArrayList<Pattern>();
    }

    private static final String PREFS = "median_scripts_v2";
    private static final String KEY = "scripts";
    private static final String FILE = "userscripts-v3.json";
    private static final long WRITE_DELAY_MS = 280L;
    private final SharedPreferences prefs;
    private final AtomicFile file;
    private final AtomicFile backup;
    private final ArrayList<Script> cache = new ArrayList<Script>();
    private final LinkedHashMap<String, List<Script>> matchCache = new LinkedHashMap<String, List<Script>>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<Script>> eldest) { return size() > 32; }
    };
    private final Handler io;
    private boolean dirty;
    private boolean closed;
    private long generation;
    private String documentStartTemplate;
    private String templateMarker;
    private final Runnable writer = new Runnable() {
        @Override public void run() { writeSnapshot(); }
    };

    UserScriptStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        File directory = context.getFilesDir();
        file = new AtomicFile(new File(directory, FILE));
        backup = new AtomicFile(new File(directory, FILE + ".good"));
        boolean migrate = loadCache();
        io = LocalDataIo.acquire();
        if (migrate) persist();
    }

    synchronized List<Script> getAll() {
        return new ArrayList<Script>(cache);
    }

    synchronized boolean hasEnabledScripts() {
        for (Script script : cache) {
            if (script.enabled && !script.quarantined && script.code.length() > 0) return true;
        }
        return false;
    }

    synchronized Script getById(String id) {
        for (Script script : cache) if (script.id.equals(id)) return script;
        return null;
    }

    synchronized void save(Script script) {
        if (script == null || script.code == null) throw new IllegalArgumentException("脚本内容无效");
        prepare(script);
        boolean replaced = false;
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).id.equals(script.id)) {
                cache.set(i, script);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            cache.add(script);
        }
        persist();
    }

    synchronized void saveBatch(List<Script> scripts) {
        if (scripts == null) return;
        for (Script script : scripts) {
            if (script == null || script.code == null) continue;
            prepare(script);
            boolean replaced = false;
            for (int i = 0; i < cache.size(); i++) {
                if (cache.get(i).id.equals(script.id)) {
                    cache.set(i, script);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) cache.add(script);
        }
        persist();
    }

    synchronized void refreshAnalysis(Script script) {
        if (script != null) prepare(script);
    }

    synchronized String exportJson() { return serializeCacheLocked(); }

    synchronized int importJson(String raw) throws Exception {
        if (raw == null) throw new IllegalArgumentException("脚本备份为空");
        new JSONArray(raw);
        if (!decodeCache(raw)) throw new IllegalArgumentException("脚本备份损坏");
        persist();
        return cache.size();
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

    synchronized void setEnabled(String id, boolean enabled) {
        Script script = getById(id);
        if (script != null) {
            script.enabled = enabled;
            if (enabled) {
                script.quarantined = false;
                script.disabledReason = "";
            }
            persist();
        }
    }

    synchronized void delete(String id) {
        for (int i = cache.size() - 1; i >= 0; i--) {
            if (cache.get(i).id.equals(id)) cache.remove(i);
        }
        persist();
    }

    synchronized List<Script> matching(String url) {
        if (!isEligiblePageUrl(url)) return Collections.emptyList();
        List<Script> remembered = matchCache.get(url);
        if (remembered != null) return remembered;
        ArrayList<Script> result = new ArrayList<Script>();
        for (Script script : cache) {
            if (!script.enabled || script.quarantined || script.code.length() == 0) continue;
            boolean included = script.matches.size() == 0;
            for (Pattern pattern : script.compiledMatches) {
                if (pattern.matcher(url).find()) {
                    included = true;
                    break;
                }
            }
            if (!included) continue;
            boolean excluded = false;
            for (Pattern pattern : script.compiledExcludes) {
                if (pattern.matcher(url).find()) {
                    excluded = true;
                    break;
                }
            }
            if (!excluded) result.add(script);
        }
        List<Script> stable = Collections.unmodifiableList(result);
        matchCache.put(url, stable);
        return stable;
    }

    Script parseUserScript(String source, String sourceUrl) throws IllegalArgumentException {
        if (source == null || source.length() == 0) throw new IllegalArgumentException("脚本内容为空");
        int start = source.indexOf("==UserScript==");
        int end = source.indexOf("==/UserScript==");
        if (start < 0 || end <= start) throw new IllegalArgumentException("不是有效的 UserScript");

        Script script = new Script();
        script.sourceUrl = sourceUrl == null ? "" : sourceUrl;
        script.installedAt = System.currentTimeMillis();
        script.updatedAt = script.installedAt;
        script.code = source;
        script.id = stableId(script.sourceUrl.length() > 0 ? script.sourceUrl : String.valueOf(System.currentTimeMillis()));

        String fallbackName = "";
        String localizedName = "";
        String anyLocalizedName = "";
        String fallbackDescription = "";
        String localizedDescription = "";
        String anyLocalizedDescription = "";
        String[] lines = source.substring(start, end).split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            int at = trimmed.indexOf('@');
            if (at < 0) continue;
            String meta = trimmed.substring(at + 1).trim();
            int space = meta.indexOf(' ');
            if (space < 0) space = meta.indexOf('\t');
            String key = space < 0 ? meta : meta.substring(0, space).trim();
            String value = space < 0 ? "" : meta.substring(space + 1).trim();
            String lowerKey = key.toLowerCase(Locale.US);
            if (lowerKey.equals("name")) fallbackName = value;
            else if (lowerKey.startsWith("name:")) {
                if (anyLocalizedName.length() == 0) anyLocalizedName = value;
                if (lowerKey.equals("name:zh-cn") || lowerKey.equals("name:zh-hans") ||
                        (lowerKey.equals("name:zh") && localizedName.length() == 0)) localizedName = value;
            } else if (lowerKey.equals("version")) script.version = value;
            else if (lowerKey.equals("namespace")) script.namespace = value;
            else if (lowerKey.equals("description")) fallbackDescription = value;
            else if (lowerKey.startsWith("description:")) {
                if (anyLocalizedDescription.length() == 0) anyLocalizedDescription = value;
                if (lowerKey.equals("description:zh-cn") || lowerKey.equals("description:zh-hans") ||
                        (lowerKey.equals("description:zh") && localizedDescription.length() == 0)) localizedDescription = value;
            } else if (lowerKey.equals("author")) script.author = value;
            else if (lowerKey.equals("homepage") || lowerKey.equals("homepageurl") || lowerKey.equals("website") || lowerKey.equals("websiteurl")) script.homepage = value;
            else if (lowerKey.equals("match") || lowerKey.equals("include")) addUnique(script.matches, value);
            else if (lowerKey.equals("exclude") || lowerKey.equals("exclude-match")) addUnique(script.excludes, value);
            else if (lowerKey.equals("run-at")) script.runAt = normalizeRunAt(value);
            else if (lowerKey.equals("grant")) addUnique(script.grants, value);
            else if (lowerKey.equals("require")) addUnique(script.requires, value);
            else if (lowerKey.equals("connect")) addUnique(script.connects, value);
            else if (lowerKey.equals("resource")) parseResource(value, script.resources);
            else if (lowerKey.equals("noframes")) script.noFrames = true;
            else if (lowerKey.equals("downloadurl")) script.downloadUrl = value;
            else if (lowerKey.equals("updateurl")) script.updateUrl = value;
        }
        script.name = localizedName.length() > 0 ? localizedName : (fallbackName.length() > 0 ? fallbackName :
                (anyLocalizedName.length() > 0 ? anyLocalizedName : "未命名脚本"));
        script.description = localizedDescription.length() > 0 ? localizedDescription :
                (fallbackDescription.length() > 0 ? fallbackDescription : anyLocalizedDescription);
        if (script.matches.size() == 0) script.matches.add("*://*/*");
        prepare(script);
        return script;
    }

    /** Returns one self-contained fallback payload for WebViews without document-start support. */
    String buildInjection(String url, boolean documentStart, String bridgeToken) {
        List<Script> scripts = matching(url);
        if (scripts.size() == 0) return "";
        StringBuilder out = new StringBuilder(8192);
        out.append("(function(){window.__medianInstalled=window.__medianInstalled||{};");
        for (Script script : scripts) if (hasNativeGrants(script)) {
            appendCompatibilityRuntime(out);
            break;
        }
        boolean included = false;
        for (Script script : scripts) {
            boolean isStart = "document-start".equalsIgnoreCase(script.runAt) ||
                    "document-body".equalsIgnoreCase(script.runAt);
            if (isStart != documentStart) continue;
            included = true;
            String key = jsQuote(script.id);
            out.append("(function(){if(window.__medianInstalled[").append(key).append("])return;window.__medianInstalled[").append(key).append("]=1;try{(function(){\n");
            if (script.noFrames || hasBridgeGrants(script)) out.append("if(window.top!==window.self)return;\n");
            appendCompatibilityApi(out, script, bridgeToken, dispatchObjectName(bridgeToken, script.id));
            if (script.requireCode != null && script.requireCode.length() > 0) {
                out.append("\n/* Median resolved @require */\n").append(script.requireCode).append("\n");
            }
            if ("document-idle".equalsIgnoreCase(script.runAt)) {
                out.append("\nsetTimeout(function(){try{(function(){\n").append(script.code)
                        .append("\n}).call(window);}catch(e){console.error('Median userscript idle ")
                        .append(escapeForSingle(script.name)).append("',e);}},0);\n");
            } else {
                out.append("\n/* Median userscript */\n").append(script.code).append("\n");
            }
            out.append("}).call(window);}catch(e){console.error('Median userscript ")
                    .append(escapeForSingle(script.name)).append("',e);}})();");
        }
        out.append("})();");
        return included ? out.toString() : "";
    }

    /**
     * Combines every enabled userscript into one document-start registration per WebView. Each
     * script remains isolated in its own closure while provider reflection and origin registration
     * happen only once.
     */
    synchronized String buildDocumentStartScript(String bridgeToken) {
        if (bridgeToken == null || bridgeToken.length() < 32) return "";
        if (documentStartTemplate != null) return documentStartTemplate.replace(templateMarker, bridgeToken);
        templateMarker = UrlCleaner.randomToken();
        StringBuilder out = new StringBuilder(8192);
        out.append("(function(){'use strict';if((location.protocol!=='http:'&&location.protocol!=='https:')||String(location.hostname||'').toLowerCase()==='median.invalid')return;var __mu=String(location.href||'');window.__medianInstalled=window.__medianInstalled||{};");
        for (Script script : cache) if (script.enabled && !script.quarantined &&
                script.code.length() > 0 && hasNativeGrants(script)) {
            appendCompatibilityRuntime(out);
            break;
        }
        for (Script script : cache) {
            if (!script.enabled || script.quarantined || script.code.length() == 0) continue;
            out.append("(function(){if(!(")
                    .append(jsPatternTest(script.compiledMatches, "__mu"))
                    .append(")||(").append(jsPatternTest(script.compiledExcludes, "__mu"))
                    .append("))return;var __medianKey=").append(jsQuote(script.id))
                    .append(";if(window.__medianInstalled[__medianKey])return;window.__medianInstalled[__medianKey]=1;var __medianRun=function(){try{(function(){\n");
            if (script.noFrames || hasBridgeGrants(script)) out.append("if(window.top!==window.self)return;\n");
            appendCompatibilityApi(out, script, templateMarker, dispatchObjectName(templateMarker, script.id));
            if (script.requireCode != null && script.requireCode.length() > 0) {
                out.append("\n/* Median resolved @require */\n").append(script.requireCode).append("\n");
            }
            out.append("\n/* Median userscript */\n").append(script.code)
                    .append("\n}).call(window);}catch(e){try{console.error('Median userscript ")
                    .append(escapeForSingle(script.name)).append("',e);}catch(_){}}};");
            if ("document-start".equalsIgnoreCase(script.runAt)) {
                out.append("__medianRun();");
            } else if ("document-body".equalsIgnoreCase(script.runAt)) {
                out.append("if(document.body)__medianRun();else{var __medianBody=function(){if(!document.body)return;document.removeEventListener('DOMContentLoaded',__medianBody);__medianRun();};document.addEventListener('DOMContentLoaded',__medianBody,{once:true});}");
            } else if ("document-idle".equalsIgnoreCase(script.runAt)) {
                out.append("var __medianIdle=function(){setTimeout(__medianRun,0);};if(document.readyState==='complete')__medianIdle();else window.addEventListener('load',__medianIdle,{once:true});");
            } else {
                out.append("if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',__medianRun,{once:true});else __medianRun();");
            }
            out.append("})();");
        }
        documentStartTemplate = out.append("})();").toString();
        return documentStartTemplate.replace(templateMarker, bridgeToken);
    }

    synchronized boolean matchesUrl(String scriptId, String url) {
        Script script = getById(scriptId);
        if (script == null || !script.enabled || script.quarantined || !isEligiblePageUrl(url)) return false;
        boolean included = false;
        for (Pattern pattern : script.compiledMatches) if (pattern.matcher(url).find()) { included = true; break; }
        if (!included) return false;
        for (Pattern pattern : script.compiledExcludes) if (pattern.matcher(url).find()) return false;
        return true;
    }

    static String dispatchObjectName(String bridgeToken, String scriptId) {
        return "__medianDispatch_" + UrlCleaner.stableId(scriptId == null ? "" : scriptId);
    }

    private static boolean hasNativeGrants(Script script) {
        if (script == null || script.grants.size() == 0) return false;
        for (String grant : script.grants) if (grant != null && grant.trim().length() > 0 && !"none".equalsIgnoreCase(grant.trim())) return true;
        return false;
    }

    private static boolean hasBridgeGrants(Script script) {
        if (script == null) return false;
        for (String raw : script.grants) {
            String grant = normalizeGrant(raw);
            if (grant.startsWith("gm_cookie") || grant.equals("gm_getvalue") || grant.equals("gm_getvalues") ||
                    grant.equals("gm_setvalue") || grant.equals("gm_setvalues") || grant.equals("gm_deletevalue") ||
                    grant.equals("gm_deletevalues") || grant.equals("gm_listvalues") || grant.equals("gm_openintab") ||
                    grant.equals("gm_setclipboard") || grant.equals("gm_notification") || grant.equals("gm_download") ||
                    grant.equals("gm_xmlhttprequest") || grant.equals("gm_registermenucommand")) return true;
        }
        return false;
    }

    private static boolean isEligiblePageUrl(String url) {
        try {
            java.net.URL parsed = NetworkSecurity.parseHttpUrl(url);
            return !"median.invalid".equals(NetworkSecurity.normalizedHost(parsed));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String jsPatternTest(List<Pattern> patterns, String valueExpression) {
        if (patterns == null || patterns.size() == 0) return "false";
        StringBuilder out = new StringBuilder();
        for (Pattern pattern : patterns) {
            if (out.length() > 0) out.append("||");
            String flags = (pattern.flags() & Pattern.CASE_INSENSITIVE) != 0 ? "i" : "";
            out.append("(new RegExp(").append(jsQuote(pattern.pattern())).append(',').append(jsQuote(flags))
                    .append(").test(").append(valueExpression).append("))");
        }
        return out.toString();
    }

    synchronized boolean canConnect(String scriptId, String targetUrl, String pageUrl) {
        Script script = getById(scriptId);
        if (script == null || !script.enabled || script.quarantined || !matchesUrl(scriptId, pageUrl)) return false;
        try {
            java.net.URL target = NetworkSecurity.parseHttpUrl(targetUrl);
            java.net.URL page = NetworkSecurity.parseHttpUrl(pageUrl);
            String targetHost = NetworkSecurity.normalizedHost(target);
            String pageHost = NetworkSecurity.normalizedHost(page);
            boolean localTarget = NetworkSecurity.isObviouslyLocalHost(targetHost);
            if (script.connects.size() == 0) return NetworkSecurity.sameOrigin(target, page);
            for (String raw : script.connects) {
                String value = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
                if ("*".equals(value)) {
                    if (!localTarget) return true; // Wildcards never silently grant native access to local networks.
                    continue;
                }
                if ("self".equals(value) && NetworkSecurity.sameOrigin(target, page)) return true;
                if (value.contains("://")) {
                    try {
                        java.net.URL allowed = NetworkSecurity.parseHttpUrl(value.endsWith("/") ? value : value + "/");
                        if (NetworkSecurity.sameOrigin(target, allowed)) return true;
                    } catch (Exception ignored) {}
                    continue;
                }
                if (value.startsWith("*.")) {
                    String suffix = value.substring(2);
                    if (!localTarget && targetHost.endsWith("." + suffix)) return true;
                } else if (targetHost.equals(value)) {
                    return !localTarget || value.equals(pageHost) || NetworkSecurity.isObviouslyLocalHost(value);
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    synchronized boolean isRunnable(String scriptId) {
        Script script = getById(scriptId);
        return script != null && script.enabled && !script.quarantined;
    }

    synchronized boolean allowsApi(String scriptId, String action) {
        Script script = getById(scriptId);
        if (script == null || !script.enabled || script.quarantined) return false;
        if (script.grants.size() == 0) return false; // Missing @grant is treated as least privilege.
        for (String raw : script.grants) {
            String grant = normalizeGrant(raw);
            if ("none".equals(grant)) return false;
            if ((action.equals("getValue") && ("gm_getvalue".equals(grant) || "gm_getvalues".equals(grant))) ||
                    (action.equals("setValue") && ("gm_setvalue".equals(grant) || "gm_setvalues".equals(grant))) ||
                    (action.equals("deleteValue") && ("gm_deletevalue".equals(grant) || "gm_deletevalues".equals(grant))) ||
                    (action.equals("listValues") && ("gm_listvalues".equals(grant) || "gm_getvalues".equals(grant))) ||
                    (action.equals("openTab") && "gm_openintab".equals(grant)) ||
                    (action.equals("clipboard") && "gm_setclipboard".equals(grant)) ||
                    (action.equals("notification") && "gm_notification".equals(grant)) ||
                    (action.equals("download") && "gm_download".equals(grant)) ||
                    (action.equals("cookie") && grant.startsWith("gm_cookie")) ||
                    ((action.equals("xhr") || action.equals("xhrAbort")) && "gm_xmlhttprequest".equals(grant))) return true;
        }
        return false;
    }

    private static String normalizeGrant(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.US).replace('.', '_');
        while (value.contains("__")) value = value.replace("__", "_");
        return value;
    }

    /** Loads the atomic primary, then last-known-good, then the legacy preference blob. */
    private boolean loadCache() {
        String raw = AtomicTextFile.read(file, 64 * 1024 * 1024);
        if (decodeCache(raw)) return false;
        raw = AtomicTextFile.read(backup, 64 * 1024 * 1024);
        if (decodeCache(raw)) return true;
        raw = prefs.getString(KEY, null);
        if (raw == null) raw = prefs.getString("scripts", null);
        if (raw == null) {
            decodeCache("[]");
            return false;
        }
        return decodeCache(raw);
    }

    private boolean decodeCache(String raw) {
        cache.clear();
        matchCache.clear();
        documentStartTemplate = null;
        templateMarker = null;
        if (raw == null) return false;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                Script script = new Script();
                script.id = object.optString("id", String.valueOf(i));
                script.name = object.optString("name", "未命名脚本");
                script.version = object.optString("version", "");
                script.namespace = object.optString("namespace", "");
                script.description = object.optString("description", "");
                script.author = object.optString("author", "");
                script.homepage = object.optString("homepage", "");
                script.sourceUrl = object.optString("sourceUrl", "");
                script.updateUrl = object.optString("updateUrl", "");
                script.downloadUrl = object.optString("downloadUrl", "");
                script.runAt = normalizeRunAt(object.optString("runAt", "document-end"));
                script.code = object.optString("code", "");
                script.requireCode = object.optString("requireCode", "");
                script.noFrames = object.optBoolean("noFrames", false);
                script.enabled = object.optBoolean("enabled", true);
                boolean previouslyQuarantined = object.optBoolean("quarantined", false);
                script.quarantined = false;
                script.disabledReason = "";
                if (previouslyQuarantined) script.enabled = true;
                script.installedAt = object.optLong("installedAt", 0L);
                script.updatedAt = object.optLong("updatedAt", script.installedAt);
                script.lastUpdateCheck = object.optLong("lastUpdateCheck", 0L);
                copyArray(object.optJSONArray("matches"), script.matches);
                copyArray(object.optJSONArray("excludes"), script.excludes);
                copyArray(object.optJSONArray("grants"), script.grants);
                copyArray(object.optJSONArray("requires"), script.requires);
                copyArray(object.optJSONArray("connects"), script.connects);
                copyResources(object.optJSONArray("resources"), script.resources);
                if (script.matches.size() == 0) script.matches.add("*://*/*");
                prepare(script);
                cache.add(script);
            }
            return true;
        } catch (JSONException ignored) {
            cache.clear();
            return false;
        }
    }

    private synchronized void persist() {
        matchCache.clear();
        documentStartTemplate = null;
        templateMarker = null;
        if (closed) return;
        generation++;
        dirty = true;
        io.removeCallbacks(writer);
        io.postDelayed(writer, WRITE_DELAY_MS);
    }

    private String serializeCacheLocked() {
        JSONArray array = new JSONArray();
        for (Script script : cache) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", script.id);
                object.put("name", script.name);
                object.put("version", script.version);
                object.put("namespace", script.namespace);
                object.put("description", script.description);
                object.put("author", script.author);
                object.put("homepage", script.homepage);
                object.put("sourceUrl", script.sourceUrl);
                object.put("updateUrl", script.updateUrl);
                object.put("downloadUrl", script.downloadUrl);
                object.put("runAt", script.runAt);
                object.put("code", script.code);
                object.put("requireCode", script.requireCode);
                object.put("noFrames", script.noFrames);
                object.put("enabled", script.enabled);
                object.put("quarantined", script.quarantined);
                object.put("disabledReason", script.disabledReason);
                object.put("installedAt", script.installedAt);
                object.put("updatedAt", script.updatedAt);
                object.put("lastUpdateCheck", script.lastUpdateCheck);
                object.put("matches", new JSONArray(script.matches));
                object.put("excludes", new JSONArray(script.excludes));
                object.put("grants", new JSONArray(script.grants));
                object.put("requires", new JSONArray(script.requires));
                object.put("connects", new JSONArray(script.connects));
                JSONArray resources = new JSONArray();
                for (Script.Resource resource : script.resources) {
                    JSONObject value = new JSONObject();
                    value.put("name", resource.name);
                    value.put("url", resource.url);
                    value.put("mime", resource.mime);
                    value.put("base64", resource.base64);
                    resources.put(value);
                }
                object.put("resources", resources);
                array.put(object);
            } catch (JSONException ignored) {
            }
        }
        return array.toString();
    }

    private void writeSnapshot() {
        final String raw;
        final long snapshotGeneration;
        synchronized (this) {
            if (!dirty) return;
            raw = serializeCacheLocked();
            snapshotGeneration = generation;
        }
        boolean saved = AtomicTextFile.write(file, raw);
        if (saved) {
            AtomicTextFile.write(backup, raw);
            prefs.edit().remove(KEY).remove("scripts").apply();
        }
        synchronized (this) {
            if (saved && snapshotGeneration == generation) dirty = false;
            else if (!saved && !closed) {
                dirty = true;
                io.removeCallbacks(writer);
                io.postDelayed(writer, 3000L);
            }
        }
    }

    private static void prepare(Script script) {
        script.runAt = normalizeRunAt(script.runAt);
        preparePatterns(script);
        analyzeRisk(script);
    }

    private static String normalizeRunAt(String value) {
        String runAt = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if ("document-start".equals(runAt) || "document-body".equals(runAt) ||
                "document-end".equals(runAt) || "document-idle".equals(runAt)) return runAt;
        return "document-end";
    }

    private static void analyzeRisk(Script script) {
        String combined = (script.requireCode == null ? "" : script.requireCode) + "\n" + (script.code == null ? "" : script.code);
        String lower = combined.toLowerCase(Locale.US);
        int score = 0;
        ArrayList<String> reasons = new ArrayList<String>();
        if (script.matches.contains("*://*/*") || script.matches.contains("http*://*/*")) { score += 2; reasons.add("全站运行"); }
        if (script.requires.size() > 0) { score += Math.min(4, script.requires.size() * 2); reasons.add("外部依赖"); }
        if (script.resources.size() > 0) { score += Math.min(2, script.resources.size()); reasons.add("外部资源"); }
        if (script.connects.contains("*")) { score += 2; reasons.add("任意网络域名"); }
        for (String grant : script.grants) {
            if (!"none".equalsIgnoreCase(grant)) score += 1;
            if (grant.toLowerCase(Locale.US).contains("xmlhttprequest") || grant.toLowerCase(Locale.US).contains("download")) score += 1;
        }
        if (lower.contains("unsafewindow")) { score += 2; reasons.add("网页全局访问"); }
        if (lower.contains("eval(") || lower.contains("new function")) { score += 3; reasons.add("动态代码"); }
        if (lower.contains("while(true)") || lower.contains("while (true)") || lower.contains("for(;;)")) { score += 5; reasons.add("疑似无限循环"); }
        if (lower.contains("setinterval(")) { score += 1; reasons.add("高频定时器"); }
        if (lower.contains("mutationobserver")) { score += 1; reasons.add("持续 DOM 监听"); }
        if (lower.contains("xmlhttprequest") || lower.contains("fetch(") || lower.contains("websocket")) { score += 2; reasons.add("网络访问"); }
        if (lower.contains("document.cookie") || lower.contains("localstorage")) { score += 1; reasons.add("网站数据访问"); }
        if (lower.contains("navigator.clipboard") || lower.contains("execcommand('copy") || lower.contains("execcommand(\"copy")) { score += 1; reasons.add("剪贴板访问"); }
        if (lower.contains("sendbeacon") || lower.contains("rtcpeerconnection")) { score += 2; reasons.add("后台通信"); }
        if (script.code != null && script.code.length() > 262144) { score += 2; reasons.add("大型脚本"); }
        script.riskScore = score;
        if (score >= 8) script.riskSummary = "高风险 · " + join(reasons);
        else if (score >= 4) script.riskSummary = "中风险 · " + join(reasons);
        else if (score > 0) script.riskSummary = "低风险 · " + join(reasons);
        else script.riskSummary = "低风险 · 未发现明显危险特征";
    }

    private static String join(List<String> values) {
        if (values.size() == 0) return "需人工检查";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size() && i < 4; i++) {
            if (i > 0) out.append("、");
            out.append(values.get(i));
        }
        return out.toString();
    }

    private static void copyArray(JSONArray array, List<String> target) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) addUnique(target, array.optString(i));
    }

    private static void parseResource(String value, List<Script.Resource> target) {
        if (value == null) return;
        String trimmed = value.trim();
        int split = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) { split = i; break; }
        }
        if (split <= 0) return;
        String name = trimmed.substring(0, split).trim();
        String url = trimmed.substring(split + 1).trim();
        if (name.length() == 0 || url.length() == 0) return;
        for (Script.Resource existing : target) if (existing.name.equals(name)) return;
        Script.Resource resource = new Script.Resource();
        resource.name = name;
        resource.url = url;
        target.add(resource);
    }

    private static void copyResources(JSONArray array, List<Script.Resource> target) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) continue;
            Script.Resource resource = new Script.Resource();
            resource.name = object.optString("name", "");
            resource.url = object.optString("url", "");
            resource.mime = object.optString("mime", "application/octet-stream");
            resource.base64 = object.optString("base64", "");
            if (resource.name.length() > 0) target.add(resource);
        }
    }

    private static void addUnique(List<String> list, String value) {
        if (value == null) return;
        String v = value.trim();
        if (v.length() > 0 && !list.contains(v)) list.add(v);
    }

    private static void preparePatterns(Script script) {
        script.compiledMatches.clear();
        script.compiledExcludes.clear();
        for (String value : script.matches) {
            Pattern pattern = compilePattern(value);
            if (pattern != null) script.compiledMatches.add(pattern);
        }
        for (String value : script.excludes) {
            Pattern pattern = compilePattern(value);
            if (pattern != null) script.compiledExcludes.add(pattern);
        }
    }

    private static Pattern compilePattern(String pattern) {
        if (pattern == null || pattern.length() == 0 || pattern.equals("*")) return Pattern.compile(".*");
        String p = pattern.trim();
        if ("<all_urls>".equalsIgnoreCase(p)) return Pattern.compile("^(?:https?|file)://.*$", Pattern.CASE_INSENSITIVE);
        if (p.startsWith("/") && p.length() > 2) {
            int last = p.lastIndexOf('/');
            if (last > 0) {
                int flags = 0;
                String suffix = p.substring(last + 1);
                if (suffix.indexOf('i') >= 0) flags |= Pattern.CASE_INSENSITIVE;
                if (suffix.indexOf('m') >= 0) flags |= Pattern.MULTILINE;
                try { return Pattern.compile(p.substring(1, last), flags); }
                catch (RuntimeException ignored) { return null; }
            }
        }
        StringBuilder regex = new StringBuilder("^");
        int schemeEnd = p.indexOf("://");
        int pathStart = schemeEnd < 0 ? -1 : p.indexOf('/', schemeEnd + 3);
        for (int i = 0; i < p.length(); i++) {
            if (i == 0 && schemeEnd >= 0) regex.append("(?i:");
            if (i == pathStart && schemeEnd >= 0) regex.append(')');
            char c = p.charAt(i);
            if (c == '*' && i == 0 && schemeEnd == 1) regex.append("https?");
            else if (c == '*' && i == schemeEnd + 3 && i + 1 < p.length() && p.charAt(i + 1) == '.') {
                regex.append("(?:[^/]+\\.)?");
                i++;
            }
            else if (c == '*') regex.append(".*");
            else if ("\\.[]{}()+-^$|?".indexOf(c) >= 0) regex.append('\\').append(c);
            else regex.append(c);
        }
        if (schemeEnd >= 0 && pathStart < 0) regex.append(')');
        regex.append('$');
        try { return Pattern.compile(regex.toString()); }
        catch (RuntimeException ignored) { return null; }
    }

    private static String stableId(String value) {
        return "script-" + UrlCleaner.stableId(value);
    }

    private static void appendCompatibilityRuntime(StringBuilder out) {
        out.append("var __medianApiFactory=function(C){var __msid=C.i,__mbt=C.t,__mb=!!C.b,__mp=(typeof window.prompt==='function'?window.prompt.bind(window):null);"
                + "var __mraw=function(a,p){try{if(!__mb||!__mp||__mbt.length<32)return null;var r=__mp('__MEDIAN_BRIDGE__'+JSON.stringify({t:__mbt,s:__msid,a:a,p:p||{}}),'');return r?JSON.parse(r):null;}catch(e){return null;}};"
                + "var unsafeWindow=window,__mk=__msid+':',__mcall=__mraw,__mcb=Object.create(null),__mmenu=Object.create(null),__mmc=1;"
                + "var GM_info={script:{name:C.n,version:C.v,namespace:C.ns,description:C.de,author:C.a,homepage:C.h,homepageURL:C.h,source:C.so,downloadURL:C.du,updateURL:C.uu,runAt:C.ra},scriptHandler:'Median',version:'2.3.0',isIncognito:false,platform:{browserName:'Median',os:'android'}};"
                + "var __mdecode=function(p,rt){if(!p)return p;rt=String(rt||'').toLowerCase();try{if((rt==='arraybuffer'||rt==='blob')&&typeof p.response==='string'){var b=atob(p.response),a=new Uint8Array(b.length);for(var j=0;j<b.length;j++)a[j]=b.charCodeAt(j);p.response=rt==='blob'&&typeof Blob==='function'?new Blob([a],{type:String(p.contentType||'application/octet-stream')}):a.buffer;}else if(rt==='json'&&typeof p.response==='string'){p.response=p.response.length?JSON.parse(p.response):null;}else if(rt==='document'&&typeof p.response==='string'){p.response=(new DOMParser()).parseFromString(p.response,p.contentType&&p.contentType.indexOf('xml')>=0?'application/xml':'text/html');}}catch(_){if(rt==='json')p.response=null;}return p;};"
                + "var __mdispatch=function(t,i,e,p){var x=__mcb[i];if(!x||t!==__mbt)return;p=__mdecode(p,x.o.responseType);if(p&&typeof p==='object')p.context=x.o.context;if(e==='progress'){if(x.o.onprogress)x.o.onprogress(p);return;}try{if(p&&typeof p==='object')p.readyState=4;if(x.o.onreadystatechange)x.o.onreadystatechange(p);if(e==='load'&&x.o.onload)x.o.onload(p);else if(e==='error'&&x.o.onerror)x.o.onerror(p);else if(e==='timeout'&&x.o.ontimeout)x.o.ontimeout(p);else if(e==='abort'&&x.o.onabort)x.o.onabort(p);}finally{if(x.o.onloadend)x.o.onloadend(p);delete __mcb[i];}};"
                + "var __mlist=function(t){if(t!==__mbt)return[];return Object.keys(__mmenu).map(function(i){var x=__mmenu[i];return{id:i,caption:x.c,script:C.n};});};"
                + "var __mrun=function(t,i){if(t!==__mbt||!__mmenu[i])return false;try{__mmenu[i].f();return true;}catch(e){try{console.error('Median script command',e);}catch(_){}return false;}};"
                + "try{Object.defineProperty(window,C.d,{value:Object.freeze({dispatch:__mdispatch,menus:__mlist,runMenu:__mrun}),writable:false,configurable:false,enumerable:false});}catch(_){try{Object.defineProperty(window,C.d,{value:Object.freeze({dispatch:__mdispatch,menus:__mlist,runMenu:__mrun}),writable:false,configurable:false});}catch(__){}};"
                + "var __mres=C.r||{};"
                + "var GM_log=function(){try{console.log.apply(console,arguments);}catch(e){}};"
                + "var __msoon=function(f,v){if(f)setTimeout(function(){f(v);},0);};"
                + "var GM_addStyle=function(c){var s=document.createElement('style');s.textContent=String(c);(document.head||document.documentElement).appendChild(s);return s;};"
                + "var GM_getValue=function(k,d){var j;try{j=JSON.stringify(d);}catch(e){j=undefined;}var r=__mcall('getValue',{k:String(k),d:j===undefined?'null':j});if(r&&r.ok){if(r.exists===false)return d;try{return JSON.parse(r.v);}catch(e){}}try{var v=localStorage.getItem(__mk+k);return v===null?d:JSON.parse(v);}catch(e){return d;}};"
                + "var __mvl={},__mvli=1,__mvd=function(k,o,n,r){Object.keys(__mvl).forEach(function(i){var x=__mvl[i];if(x.k===k)try{x.f(k,o,n,!!r);}catch(e){}});};"
                + "var GM_setValue=function(k,v){var o=GM_getValue(k,undefined),j=JSON.stringify(v),r=__mcall('setValue',{k:String(k),v:j});if(!(r&&r.ok))try{localStorage.setItem(__mk+k,j);}catch(e){}__mvd(String(k),o,v,false);};"
                + "var GM_deleteValue=function(k){var o=GM_getValue(k,undefined),r=__mcall('deleteValue',{k:String(k)});if(!(r&&r.ok))try{localStorage.removeItem(__mk+k);}catch(e){}__mvd(String(k),o,undefined,false);};"
                + "var GM_listValues=function(){var r=__mcall('listValues',{});if(r&&r.ok)return r.v||[];var a=[];try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(k&&k.indexOf(__mk)===0)a.push(k.slice(__mk.length));}}catch(e){}return a;};"
                + "var GM_getValues=function(q){var o={},a;if(Array.isArray(q))a=q;else if(q&&typeof q==='object')a=Object.keys(q);else a=GM_listValues();a.forEach(function(k){o[k]=GM_getValue(k,q&& !Array.isArray(q)?q[k]:undefined);});return o;},GM_setValues=function(o){Object.keys(o||{}).forEach(function(k){GM_setValue(k,o[k]);});},GM_deleteValues=function(a){(a||[]).forEach(GM_deleteValue);};"
                + "var GM_addValueChangeListener=function(k,f){var i=__mvli++;__mvl[i]={k:String(k),f:f};return i;},GM_removeValueChangeListener=function(i){delete __mvl[i];};"
                + "var GM_addElement=function(a,b,c){var p=document.documentElement,t=a,x=b;if(a&&a.nodeType){p=a;t=b;x=c;}var e=document.createElement(t);x=x||{};Object.keys(x).forEach(function(k){var v=x[k];if(k==='textContent'||k==='innerHTML'||k==='className'||k==='htmlFor')e[k]=v;else if(k==='style'&&v&&typeof v==='object')Object.assign(e.style,v);else if(/^on/.test(k)&&typeof v==='function')e.addEventListener(k.slice(2),v);else if(k in e&&typeof v!=='object')try{e[k]=v;}catch(_){e.setAttribute(k,v);}else e.setAttribute(k,v);});p.appendChild(e);return e;};"
                + "var __murl=function(u){try{return new URL(String(u||''),location.href).href;}catch(e){return String(u||'');}};var GM_openInTab=function(u,o){u=__murl(u);var r=__mcall('openTab',{u:u,active:!(o&&o.active===false)}),w=null;if(!(r&&r.ok)&&!__mb)try{w=window.open(u,'_blank');}catch(e){}return{close:function(){try{if(w)w.close();}catch(e){}},get closed(){return w?!!w.closed:!(r&&r.ok);}};};"
                + "var GM_setClipboard=function(v){var r=__mcall('clipboard',{v:String(v)});if(r&&r.ok)return;v=String(v);var t=document.createElement('textarea');t.value=v;document.documentElement.appendChild(t);t.select();try{document.execCommand('copy');}finally{t.remove();}};"
                + "var GM_notification=function(o){o=typeof o==='string'?{text:o}:(o||{});var r=__mcall('notification',{title:String(o.title||'Median'),text:String(o.text||'')});if(!(r&&r.ok))GM_log(o.text||'');};"
                + "var GM_registerMenuCommand=function(c,f){if(typeof f!=='function')return'';var i=__msid+'-'+(__mmc++);__mmenu[i]={c:String(c).slice(0,160),f:f};return i;};var GM_unregisterMenuCommand=function(i){delete __mmenu[String(i)];};"
                + "var GM_getResourceURL=function(n){var r=__mres[n];return r&&r.b?'data:'+(r.m||'application/octet-stream')+';base64,'+r.b:(r?r.u:undefined);};"
                + "var GM_getResourceUrl=GM_getResourceURL;"
                + "var GM_getResourceText=function(n){var r=__mres[n];if(!r||!r.b)return undefined;try{var s=atob(r.b),a=new Uint8Array(s.length);for(var i=0;i<s.length;i++)a[i]=s.charCodeAt(i);if(typeof TextDecoder==='function')return new TextDecoder('utf-8').decode(a);var q='';for(i=0;i<a.length;i++)q+='%'+('0'+a[i].toString(16)).slice(-2);return decodeURIComponent(q);}catch(e){return undefined;}};"
                + "var GM_download=function(o,n){if(typeof o==='string')o={url:o,name:n};o=o||{};var r=__mcall('download',{u:__murl(o.url),n:String(o.name||''),h:o.headers||{}}),a={abort:function(){}};if(r&&r.ok){__msoon(o.onload,{queued:true});return a;}__msoon(o.onerror,{error:'download rejected'});return a;};"
                + "var GM_cookie={list:function(d,c){d=d||{};var r=__mcall('cookie',{op:'list',d:d}),a=r&&r.ok?(r.v||[]):[];if(!r&&!__mb)try{a=document.cookie.split(/;\\s*/).filter(Boolean).map(function(x){var i=x.indexOf('=');return{name:i<0?x:x.slice(0,i),value:i<0?'':x.slice(i+1),domain:location.hostname,path:'/',secure:location.protocol==='https:'};}).filter(function(x){return!d.name||x.name===d.name;});}catch(e){}__msoon(c,a);return a;},set:function(d,c){d=d||{};var r=__mcall('cookie',{op:'set',d:d});if(!r&&!__mb)try{document.cookie=encodeURIComponent(d.name)+'='+encodeURIComponent(d.value==null?'':d.value)+'; path='+(d.path||'/')+(d.domain?'; domain='+d.domain:'')+(d.secure?'; secure':'')+(d.expirationDate?'; expires='+new Date(d.expirationDate*1000).toUTCString():'');r={ok:true};}catch(e){}__msoon(c,r&&r.ok?null:{error:'cookie rejected'});return!!(r&&r.ok);},delete:function(d,c){d=d||{};var r=__mcall('cookie',{op:'delete',d:d});if(!r&&!__mb)try{document.cookie=encodeURIComponent(d.name)+'=; Max-Age=0; path='+(d.path||'/')+(d.domain?'; domain='+d.domain:'');r={ok:true};}catch(e){}__msoon(c,r&&r.ok?null:{error:'cookie rejected'});return!!(r&&r.ok);}};"
                + "var __mpagexhr=function(o){var x=new XMLHttpRequest(),u=String(o.url||''),m=String(o.method||'GET');x.open(m,u,true,o.user||undefined,o.password||undefined);try{x.timeout=Number(o.timeout||0);if(o.responseType)x.responseType=o.responseType;if(o.overrideMimeType)x.overrideMimeType(o.overrideMimeType);x.withCredentials=!o.anonymous;Object.keys(o.headers||{}).forEach(function(k){x.setRequestHeader(k,o.headers[k]);});}catch(e){}var p=function(){var r={readyState:x.readyState,status:x.status,statusText:x.statusText,finalUrl:x.responseURL||u,responseHeaders:x.getAllResponseHeaders(),response:x.response,context:o.context};try{r.responseText=x.responseText;}catch(e){r.responseText='';}return r;};x.onreadystatechange=function(){if(o.onreadystatechange)o.onreadystatechange(p());};x.onloadstart=function(){if(o.onloadstart)o.onloadstart(p());};x.onprogress=function(e){if(o.onprogress){var r=p();r.loaded=e.loaded;r.total=e.total;r.lengthComputable=e.lengthComputable;o.onprogress(r);}};var z=function(n){return function(){var r=p();if(o[n])o[n](r);if(o.onloadend)o.onloadend(r);};};x.onload=z('onload');x.onerror=z('onerror');x.ontimeout=z('ontimeout');x.onabort=z('onabort');x.send(o.data==null?null:o.data);return x;};"
                + "var GM_xmlhttpRequest=function(o){o=o||{};o.url=__murl(o.url);if(!__mb)return __mpagexhr(o);var i='x'+Date.now().toString(36)+Math.random().toString(36).slice(2),d=o.data,b=false;if(d instanceof ArrayBuffer||ArrayBuffer.isView(d)){var a=d instanceof ArrayBuffer?new Uint8Array(d):new Uint8Array(d.buffer,d.byteOffset,d.byteLength),z='';for(var j=0;j<a.length;j+=8192)z+=String.fromCharCode.apply(null,a.subarray(j,j+8192));d=btoa(z);b=true;}else if(d!=null&&typeof d!=='string'){try{d=typeof URLSearchParams==='function'&&d instanceof URLSearchParams?String(d):JSON.stringify(d);}catch(_){d=String(d);}}var q={i:i,u:o.url,m:String(o.method||'GET'),h:o.headers||{},d:d==null?'':d,b64:b,rt:String(o.responseType||'text'),to:Number(o.timeout||0),anon:!!o.anonymous};__mcb[i]={o:o};try{var st={readyState:1,finalUrl:q.u,context:o.context};if(o.onloadstart)o.onloadstart(st);if(o.onreadystatechange)o.onreadystatechange(st);}catch(_){}var r=__mcall('xhr',q);if(!(r&&r.ok)){delete __mcb[i];setTimeout(function(){var e={error:(r&&r.error)||'request rejected',readyState:4,context:o.context};try{if(o.onreadystatechange)o.onreadystatechange(e);if(o.onerror)o.onerror(e);}finally{if(o.onloadend)o.onloadend(e);}},0);}return{abort:function(){if(!__mcb[i])return;__mcall('xhrAbort',{i:i});}};};"
                + "var __mpr=function(f){return function(){return Promise.resolve(f.apply(null,arguments));};};var GM={info:GM_info,log:GM_log,addStyle:GM_addStyle,addElement:GM_addElement,getValue:__mpr(GM_getValue),setValue:__mpr(GM_setValue),deleteValue:__mpr(GM_deleteValue),listValues:__mpr(GM_listValues),getValues:__mpr(GM_getValues),setValues:__mpr(GM_setValues),deleteValues:__mpr(GM_deleteValues),addValueChangeListener:GM_addValueChangeListener,removeValueChangeListener:GM_removeValueChangeListener,openInTab:GM_openInTab,setClipboard:__mpr(GM_setClipboard),notification:__mpr(GM_notification),registerMenuCommand:GM_registerMenuCommand,unregisterMenuCommand:GM_unregisterMenuCommand,getResourceText:__mpr(GM_getResourceText),getResourceURL:__mpr(GM_getResourceURL),getResourceUrl:__mpr(GM_getResourceURL),download:GM_download,xmlHttpRequest:function(o){var q=o||{},x,l=q.onload,e=q.onerror,t=q.ontimeout,a=q.onabort,p=new Promise(function(resolve,reject){x=GM_xmlhttpRequest(Object.assign({},q,{onload:function(r){if(l)l(r);resolve(r);},onerror:function(r){if(e)e(r);reject(r);},ontimeout:function(r){if(t)t(r);reject(r);},onabort:function(r){if(a)a(r);reject(r);}}));});p.abort=function(){if(x&&x.abort)x.abort();};return p;},xmlhttpRequest:GM_xmlhttpRequest,cookie:{list:__mpr(GM_cookie.list),set:__mpr(GM_cookie.set),delete:__mpr(GM_cookie.delete)}};\n"
                + "return{w:unsafeWindow,i:GM_info,l:GM_log,s:GM_addStyle,gv:GM_getValue,sv:GM_setValue,dv:GM_deleteValue,lv:GM_listValues,gvs:GM_getValues,svs:GM_setValues,dvs:GM_deleteValues,av:GM_addValueChangeListener,rv:GM_removeValueChangeListener,ae:GM_addElement,ot:GM_openInTab,sc:GM_setClipboard,no:GM_notification,rm:GM_registerMenuCommand,um:GM_unregisterMenuCommand,ru:GM_getResourceURL,rt:GM_getResourceText,dl:GM_download,ck:GM_cookie,xr:GM_xmlhttpRequest,g:GM};};");
    }

    private static void appendCompatibilityApi(StringBuilder out, Script script, String bridgeToken, String dispatcherName) {
        String name = jsQuote(script.name);
        String version = jsQuote(script.version);
        String namespace = jsQuote(script.namespace);
        String description = jsQuote(script.description);
        String author = jsQuote(script.author);
        String homepage = jsQuote(script.homepage);
        if (!hasNativeGrants(script)) {
            out.append("var GM_info={script:{name:").append(name).append(",version:").append(version)
                    .append(",namespace:").append(namespace).append(",description:").append(description)
                    .append(",author:").append(author).append(",homepage:").append(homepage)
                    .append(",homepageURL:").append(homepage).append(",source:").append(jsQuote(script.sourceUrl))
                    .append(",downloadURL:").append(jsQuote(script.downloadUrl)).append(",updateURL:").append(jsQuote(script.updateUrl))
                    .append(",runAt:").append(jsQuote(script.runAt))
                    .append("},scriptHandler:'Median',version:'2.3.0',isIncognito:false,platform:{browserName:'Median',os:'android'}};");
            return;
        }
        boolean bridgeAvailable = hasBridgeGrants(script) && bridgeToken != null && bridgeToken.length() >= 32;
        out.append("var __ma=__medianApiFactory({i:").append(jsQuote(script.id))
                .append(",n:").append(name).append(",v:").append(version).append(",ns:").append(namespace)
                .append(",de:").append(description).append(",a:").append(author).append(",h:").append(homepage)
                .append(",so:").append(jsQuote(script.sourceUrl)).append(",du:").append(jsQuote(script.downloadUrl))
                .append(",uu:").append(jsQuote(script.updateUrl)).append(",ra:").append(jsQuote(script.runAt))
                .append(",t:").append(jsQuote(bridgeToken == null ? "" : bridgeToken))
                .append(",b:").append(bridgeAvailable ? "true" : "false")
                .append(",d:").append(jsQuote(dispatcherName)).append(",r:{");
        for (int i = 0; i < script.resources.size(); i++) {
            Script.Resource resource = script.resources.get(i);
            if (i > 0) out.append(',');
            out.append(jsQuote(resource.name)).append(":{b:").append(jsQuote(resource.base64))
                    .append(",m:").append(jsQuote(resource.mime)).append(",u:").append(jsQuote(resource.url)).append("}");
        }
        out.append("}}),unsafeWindow=__ma.w,GM_info=__ma.i,GM_log=__ma.l,GM_addStyle=__ma.s,GM_getValue=__ma.gv,GM_setValue=__ma.sv,GM_deleteValue=__ma.dv,GM_listValues=__ma.lv,GM_getValues=__ma.gvs,GM_setValues=__ma.svs,GM_deleteValues=__ma.dvs,GM_addValueChangeListener=__ma.av,GM_removeValueChangeListener=__ma.rv,GM_addElement=__ma.ae,GM_openInTab=__ma.ot,GM_setClipboard=__ma.sc,GM_notification=__ma.no,GM_registerMenuCommand=__ma.rm,GM_unregisterMenuCommand=__ma.um,GM_getResourceURL=__ma.ru,GM_getResourceUrl=__ma.ru,GM_getResourceText=__ma.rt,GM_download=__ma.dl,GM_cookie=__ma.ck,GM_xmlhttpRequest=__ma.xr,GM=__ma.g;");
    }

    private static String jsQuote(String value) {
        String input = value == null ? "" : value;
        StringBuilder out = new StringBuilder(input.length() + 2);
        out.append('"');
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c == '\b') out.append("\\b");
            else if (c == '\f') out.append("\\f");
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else if (c < 0x20 || c == '\u2028' || c == '\u2029') {
                String hex = Integer.toHexString(c);
                out.append("\\u");
                for (int pad = hex.length(); pad < 4; pad++) out.append('0');
                out.append(hex);
            } else out.append(c);
        }
        return out.append('"').toString();
    }

    private static String escapeForSingle(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ");
    }
}
