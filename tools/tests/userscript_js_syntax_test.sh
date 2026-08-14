#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
command -v node >/dev/null 2>&1 || { echo 'node is required for userscript syntax checks' >&2; exit 1; }
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/android/content" "$TMP/src/android/os" "$TMP/src/android/util" "$TMP/src/org/json" "$TMP/src/com/xinyv/median" "$TMP/classes"
cat > "$TMP/src/android/content/Context.java" <<'JAVA'
package android.content;
import java.io.File;
public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public abstract SharedPreferences getSharedPreferences(String name, int mode);
    public File getFilesDir() { return new File("."); }
}
JAVA
cat > "$TMP/src/android/content/SharedPreferences.java" <<'JAVA'
package android.content;
public interface SharedPreferences {
    String getString(String key, String defValue);
    Editor edit();
    interface Editor {
        Editor putString(String key, String value);
        Editor remove(String key);
        void apply();
        boolean commit();
    }
}
JAVA
cat > "$TMP/src/android/util/AtomicFile.java" <<'JAVA'
package android.util;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
public final class AtomicFile {
    private final File file;
    public AtomicFile(File file) { this.file = file; }
    public byte[] readFully() throws IOException { throw new FileNotFoundException(); }
    public FileInputStream openRead() throws FileNotFoundException { return new FileInputStream(file); }
    public FileOutputStream startWrite() throws IOException { return new FileOutputStream(file); }
    public void finishWrite(FileOutputStream output) throws IOException { output.close(); }
    public void failWrite(FileOutputStream output) { try { output.close(); } catch (Exception ignored) {} }
}
JAVA
cat > "$TMP/src/android/os/Looper.java" <<'JAVA'
package android.os;
public final class Looper {}
JAVA
cat > "$TMP/src/android/os/HandlerThread.java" <<'JAVA'
package android.os;
public final class HandlerThread {
    private final Looper looper = new Looper();
    public HandlerThread(String name, int priority) {}
    public void start() {}
    public Looper getLooper() { return looper; }
    public boolean quitSafely() { return true; }
}
JAVA
cat > "$TMP/src/android/os/Handler.java" <<'JAVA'
package android.os;
public final class Handler {
    public Handler(Looper looper) {}
    public void removeCallbacks(Runnable task) {}
    public boolean post(Runnable task) { return true; }
    public boolean postDelayed(Runnable task, long delayMillis) { return true; }
}
JAVA
cat > "$TMP/src/android/os/Process.java" <<'JAVA'
package android.os;
public final class Process {
    public static final int THREAD_PRIORITY_BACKGROUND = 10;
}
JAVA
cat > "$TMP/src/org/json/JSONException.java" <<'JAVA'
package org.json;
public class JSONException extends Exception {
    public JSONException() { super(); }
    public JSONException(String message) { super(message); }
}
JAVA
cat > "$TMP/src/org/json/JSONArray.java" <<'JAVA'
package org.json;
import java.util.Collection;
public class JSONArray {
    public JSONArray() {}
    public JSONArray(String raw) throws JSONException {}
    public JSONArray(Collection<?> values) {}
    public int length() { return 0; }
    public JSONObject optJSONObject(int index) { return null; }
    public String optString(int index) { return ""; }
    public JSONArray put(Object value) { return this; }
    @Override public String toString() { return "[]"; }
}
JAVA
cat > "$TMP/src/org/json/JSONObject.java" <<'JAVA'
package org.json;
public class JSONObject {
    public JSONObject() {}
    public JSONObject(String raw) throws JSONException {}
    public JSONObject put(String key, Object value) throws JSONException { return this; }
    public String optString(String key, String fallback) { return fallback; }
    public boolean optBoolean(String key, boolean fallback) { return fallback; }
    public double optDouble(String key, double fallback) { return fallback; }
    public int optInt(String key, int fallback) { return fallback; }
    public long optLong(String key, long fallback) { return fallback; }
    public JSONArray optJSONArray(String key) { return null; }
    @Override public String toString() { return "{}"; }
}
JAVA
cat > "$TMP/src/org/json/JSONTokener.java" <<'JAVA'
package org.json;
public class JSONTokener {
    public JSONTokener(String raw) {}
    public Object nextValue() throws JSONException { return null; }
}
JAVA
cat > "$TMP/src/com/xinyv/median/UrlCleaner.java" <<'JAVA'
package com.xinyv.median;
final class UrlCleaner {
    static String stableId(String value) { return Integer.toUnsignedString(value == null ? 0 : value.hashCode(), 36); }
    static String randomToken() { return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"; }
}
JAVA
cat > "$TMP/src/com/xinyv/median/UserScriptGeneratedJsSelfTest.java" <<'JAVA'
package com.xinyv.median;
import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.regex.Pattern;

public final class UserScriptGeneratedJsSelfTest {
    private static final class MemoryPreferences implements SharedPreferences, SharedPreferences.Editor {
        public String getString(String key, String fallback) { return fallback; }
        public Editor edit() { return this; }
        public Editor putString(String key, String value) { return this; }
        public Editor remove(String key) { return this; }
        public void apply() {}
        public boolean commit() { return true; }
    }
    private static final class MemoryContext extends Context {
        private final MemoryPreferences preferences = new MemoryPreferences();
        public SharedPreferences getSharedPreferences(String name, int mode) { return preferences; }
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        UserScriptStore store = new UserScriptStore(new MemoryContext());
        UserScriptStore.Script script = new UserScriptStore.Script();
        script.id = "syntax-test";
        script.name = "Generated JS syntax test";
        script.version = "1.0";
        script.namespace = "median.test";
        script.description = "Exercises every compatibility API";
        script.author = "Median";
        script.homepage = "https://example.com/";
        script.runAt = "document-start";
        script.code = "window.__medianRunCount=(window.__medianRunCount||0)+1; window.__medianTestGM=GM;\n" +
                "GM_registerMenuCommand('Test', function(){ GM_setValue('x', 1); });\n" +
                "GM_xmlhttpRequest({url:'https://example.com/data',responseType:'arraybuffer',onload:function(r){console.log(r.status);}});";
        script.requireCode = "const requiredValue = 1;";
        script.enabled = true;
        script.matches.add("https://example.com/*");
        script.compiledMatches.add(Pattern.compile("^https://example\\.com/.*$"));
        script.grants.add("GM_registerMenuCommand");
        script.grants.add("GM_setValue");
        script.grants.add("GM.getValues");
        script.grants.add("GM_xmlhttpRequest");
        script.grants.add("GM_download");
        script.grants.add("GM_cookie");
        script.connects.add("example.com");
        UserScriptStore.Script.Resource resource = new UserScriptStore.Script.Resource();
        resource.name = "sample";
        resource.url = "https://example.com/sample.txt";
        resource.mime = "text/plain";
        resource.base64 = "aGVsbG8=";
        script.resources.add(resource);

        Field field = UserScriptStore.class.getDeclaredField("cache");
        field.setAccessible(true);
        ((ArrayList<UserScriptStore.Script>) field.get(store)).add(script);
        if (!store.allowsApi(script.id, "xhr") || !store.allowsApi(script.id, "download"))
            throw new AssertionError("declared grants were not recognized");
        if (!store.allowsApi(script.id, "cookie"))
            throw new AssertionError("GM_cookie grant was not recognized");
        if (!store.allowsApi(script.id, "getValue") || !store.allowsApi(script.id, "listValues"))
            throw new AssertionError("bulk value grants must authorize their primitive bridge operations");
        script.grants.add("GM_notDownloadButContainsTheWord");
        script.grants.remove("GM_download");
        if (store.allowsApi(script.id, "download"))
            throw new AssertionError("substring grant accidentally authorized download");
        script.grants.add("GM_download");
        UserScriptStore.Script second = new UserScriptStore.Script();
        second.id = "syntax-test-2";
        second.name = "Second shared-runtime script";
        second.runAt = "document-start";
        second.code = "GM_addStyle('html{scroll-behavior:auto}');";
        second.enabled = true;
        second.matches.add("https://example.com/*");
        second.compiledMatches.add(Pattern.compile("^https://example\\.com/.*$"));
        second.grants.add("GM_addStyle");
        ((ArrayList<UserScriptStore.Script>) field.get(store)).add(second);
        String payload = store.buildDocumentStartScript("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        if (payload.length() == 0) throw new AssertionError("expected one combined userscript registration");
        if (!payload.contains("location.hostname||'').toLowerCase()==='median.invalid'"))
            throw new AssertionError("internal home-page guard missing");
        if (!payload.contains("if(window.top!==window.self)return;"))
            throw new AssertionError("native-grant scripts must be top-frame only");
        for (String api : new String[] { "GM_getValues", "GM_setValues", "GM_deleteValues", "GM_getResourceUrl", "GM_cookie", "xmlHttpRequest" })
            if (!payload.contains(api)) throw new AssertionError("missing compatibility API: " + api);
        String runtimeMarker = "var __medianApiFactory=";
        if (payload.indexOf(runtimeMarker) < 0 || payload.indexOf(runtimeMarker) != payload.lastIndexOf(runtimeMarker))
            throw new AssertionError("multiple scripts must share exactly one compatibility runtime");
        if (!payload.contains("p.abort=function") || !payload.contains("ontimeout:function") || !payload.contains("onabort:function"))
            throw new AssertionError("modern GM.xmlHttpRequest must expose abort and reject terminal failures");
        if (!payload.contains("downloadURL:") || !payload.contains("updateURL:") || !payload.contains("isIncognito:false"))
            throw new AssertionError("common GM_info compatibility metadata is missing");
        if (!payload.contains("var __mpr=") || !payload.contains("var __msoon=") ||
                !payload.contains("var z=function(n)"))
            throw new AssertionError("shared Promise/callback helpers were not emitted");
        if (!payload.contains("window.__medianInstalled") || !payload.contains("r.exists===false") ||
                !payload.contains("new URL(String(u||''),location.href)"))
            throw new AssertionError("OEM recovery or modern value/URL compatibility missing");
        if (payload.contains("?'_blank':'_blank'"))
            throw new AssertionError("redundant openInTab target branch returned");
        String fallback = store.buildInjection("https://example.com/page", true, "");
        if (fallback.length() == 0 || !fallback.contains("b:false") || !fallback.contains("__mpagexhr"))
            throw new AssertionError("old-WebView fallback must execute granted scripts with same-origin APIs");
        if (fallback.indexOf(runtimeMarker) != fallback.lastIndexOf(runtimeMarker))
            throw new AssertionError("fallback scripts must also share one compatibility runtime per payload");
        if (store.matchesUrl(script.id, "https://median.invalid/"))
            throw new AssertionError("internal home page must not match user scripts");
        UserScriptStore.Script localized = store.parseUserScript("// ==UserScript==\n// @name Base\n// @name:zh 中文名\n// @description Base description\n// @description:zh-Hans 中文描述\n// @match https://*.Example.COM/*\n// @grant none\n// ==/UserScript==\n", "https://example.com/test.user.js");
        if (!"中文名".equals(localized.name) || !"中文描述".equals(localized.description))
            throw new AssertionError("localized metadata selection failed");
        store.save(localized);
        if (!store.matchesUrl(localized.id, "https://example.com/path"))
            throw new AssertionError("@match wildcard host should include the bare host case-insensitively");
        UserScriptStore.Script body = store.parseUserScript("// ==UserScript==\n// @name Body\n// @match https://Example.COM/Path/*\n// @run-at document-body\n// @grant none\n// ==/UserScript==\n", "https://example.com/body.user.js");
        store.save(body);
        if (!"document-body".equals(body.runAt) || !store.matchesUrl(body.id, "https://example.com/Path/a") ||
                store.matchesUrl(body.id, "https://example.com/path/a"))
            throw new AssertionError("document-body or host/path match semantics failed");
        UserScriptStore.Script regex = store.parseUserScript("// ==UserScript==\n// @name Regex\n// @include /https:\\/\\/example\\.com\\/CASE/i\n// @grant none\n// ==/UserScript==\n", "https://example.com/regex.user.js");
        store.save(regex);
        if (!store.matchesUrl(regex.id, "https://example.com/case"))
            throw new AssertionError("explicit regular-expression flags were ignored");
        UserScriptStore localStore = new UserScriptStore(new MemoryContext());
        UserScriptStore.Script localOnly = localStore.parseUserScript("// ==UserScript==\n// @name Frame style\n// @match https://example.com/*\n// @grant GM_addStyle\n// ==/UserScript==\nGM_addStyle('body{}');", "https://example.com/style.user.js");
        localStore.save(localOnly);
        String localPayload = localStore.buildDocumentStartScript("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        if (localPayload.contains("if(window.top!==window.self)return;"))
            throw new AssertionError("local-only grants should retain default frame compatibility");
        System.out.print(payload);
    }
}
JAVA

javac --release 17 -d "$TMP/classes" \
  "$TMP/src/android/content/Context.java" \
  "$TMP/src/android/content/SharedPreferences.java" \
  "$TMP/src/android/util/AtomicFile.java" \
  "$TMP/src/android/os/Looper.java" \
  "$TMP/src/android/os/HandlerThread.java" \
  "$TMP/src/android/os/Handler.java" \
  "$TMP/src/android/os/Process.java" \
  "$TMP/src/org/json/JSONException.java" \
  "$TMP/src/org/json/JSONArray.java" \
  "$TMP/src/org/json/JSONObject.java" \
  "$TMP/src/org/json/JSONTokener.java" \
  "$TMP/src/com/xinyv/median/UrlCleaner.java" \
  app/src/main/java/com/xinyv/median/LocalDataIo.java \
  app/src/main/java/com/xinyv/median/AtomicTextFile.java \
  app/src/main/java/com/xinyv/median/NetworkSecurity.java \
  app/src/main/java/com/xinyv/median/UserScriptStore.java \
  "$TMP/src/com/xinyv/median/UserScriptGeneratedJsSelfTest.java"
java -cp "$TMP/classes" com.xinyv.median.UserScriptGeneratedJsSelfTest > "$TMP/generated-userscript.js"
node --check "$TMP/generated-userscript.js" >/dev/null
node tools/tests/userscript_behavior_test.js "$TMP/generated-userscript.js"
echo 'Generated userscript JavaScript syntax passed'
