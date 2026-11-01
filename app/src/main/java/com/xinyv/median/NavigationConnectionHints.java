package com.xinyv.median;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebViewNavigationWarmupCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Owns learned connection hints and document registrations for one browser Activity. */
final class NavigationConnectionHints {
    static final String PREF_CONNECTION_HINTS = "connection_hints_v1";

    interface Host {
        boolean isCurrentPageCallback(WebView source, long sequence);
        boolean isAdBlockEnabled();
    }

    private final Handler uiHandler;
    private final SharedPreferences prefs;
    private final AdBlockEngine adBlock;
    private final Host host;
    private ConnectionHintsStore connectionHints = new ConnectionHintsStore();
    private long connectionHintSequence = -1L;
    private long connectionHintsGeneration;
    private boolean closed;
    private final ConcurrentHashMap<WebView, ScriptHandler> connectionHintScripts =
            new ConcurrentHashMap<WebView, ScriptHandler>();
    private final ConcurrentHashMap<WebView, String> connectionHintScriptIdentities =
            new ConcurrentHashMap<WebView, String>();

    NavigationConnectionHints(Handler uiHandler, SharedPreferences prefs,
                              AdBlockEngine adBlock, Host host) {
        this.uiHandler = uiHandler;
        this.prefs = prefs;
        this.adBlock = adBlock;
        this.host = host;
    }

    /** Called on the UI thread after the startup worker decodes the persisted snapshot. */
    boolean publishRestored(ConnectionHintsStore restored) {
        if (closed || connectionHintsGeneration != 0L || restored == null) return false;
        connectionHints = restored;
        return true;
    }

    void removeConnectionHintScript(WebView target) {
        connectionHintScriptIdentities.remove(target);
        ScriptHandler handler = connectionHintScripts.remove(target);
        if (handler != null) try { handler.remove(); } catch (RuntimeException ignored) {}
    }

    /** Register before loadUrl so Chromium can warm dependencies in this document's partition. */
    void installConnectionHintScript(WebView target, String url) {
        installConnectionHintScript(target, url, false);
    }

    void installConnectionHintScript(WebView target, String url, boolean applyToCurrentDocument) {
        if (closed) return;
        if (!target.getSettings().getJavaScriptEnabled() ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            removeConnectionHintScript(target);
            return;
        }
        String page = ConnectionHintsStore.origin(url);
        if (page.length() == 0) {
            removeConnectionHintScript(target);
            return;
        }
        List<String> learned = connectionHints.hintsFor(url, System.currentTimeMillis());
        String[] origins = WebViewNavigationWarmupCompat.preconnectOriginsForNavigation(url,
                learned.toArray(new String[learned.size()]), connectionOriginFilter(url));
        String script = ConnectionPreconnectScript.build(url, origins);
        if (script.length() == 0) {
            removeConnectionHintScript(target);
            return;
        }
        // Preserve a matching pre-navigation registration across startup and page callbacks.
        String identity = page + '\n' + script;
        try {
            if (!identity.equals(connectionHintScriptIdentities.get(target)) ||
                    !connectionHintScripts.containsKey(target)) {
                removeConnectionHintScript(target);
                ScriptHandler handler = WebViewCompat.addDocumentStartJavaScript(target, script,
                        Collections.singleton(page));
                connectionHintScripts.put(target, handler);
                connectionHintScriptIdentities.put(target, identity);
            }
            // POST/history/OEM navigations still need the idempotent current-document fallback.
            if (applyToCurrentDocument) target.evaluateJavascript(script, null);
        } catch (RuntimeException ignored) {
            // Native Profile hints remain available on providers without document-start support.
        }
    }

    private WebViewNavigationWarmupCompat.OriginFilter connectionOriginFilter(String url) {
        final String pageHost = hostOf(url);
        return new WebViewNavigationWarmupCompat.OriginFilter() {
            @Override public boolean allow(String origin) {
                return !host.isAdBlockEnabled() || !adBlock.shouldBlock(Uri.parse(origin + "/"), pageHost);
            }
        };
    }

    /** Waits for the actual document load event before learning completed render dependencies. */
    void collectConnectionHints(final WebView source, final String url, final long sequence) {
        if (closed || !host.isCurrentPageCallback(source, sequence) || !isNetworkPage(url) ||
                connectionHintSequence == sequence) return;
        connectionHintSequence = sequence;
        final long generation = connectionHintsGeneration;
        uiHandler.postDelayed(new Runnable() {
            private int pendingChecks;
            @Override public void run() {
                if (closed || !host.isCurrentPageCallback(source, sequence) || generation != connectionHintsGeneration ||
                        !navigationDocumentUrl(url).equals(navigationDocumentUrl(source.getUrl()))) return;
                // Retry only the bounded readiness query; enumerate resources once after load.
                String script = ConnectionHintsScript.collectScript();
                final Runnable retry = this;
                source.evaluateJavascript(script, new ValueCallback<String>() {
                    @Override public void onReceiveValue(String value) {
                        if (closed || !host.isCurrentPageCallback(source, sequence) || generation != connectionHintsGeneration ||
                                value == null || value.length() > 65536) return;
                        try {
                            Object decoded = new org.json.JSONTokener(value).nextValue();
                            if (!(decoded instanceof String)) return;
                            JSONObject result = new JSONObject((String) decoded);
                            if (!ConnectionHintsStore.origin(url).equals(result.optString("o"))) return;
                            if (result.optBoolean("p")) {
                                if (++pendingChecks < 24)
                                    uiHandler.postDelayed(retry, pendingChecks == 1 ? 500L : 2000L);
                                return;
                            }
                            JSONArray resources = result.optJSONArray("r");
                            if (resources == null) return;
                            ArrayList<ConnectionHintsStore.ResourceHint> hints =
                                    new ArrayList<ConnectionHintsStore.ResourceHint>();
                            String pageHost = hostOf(url);
                            for (int i = 0; i < resources.length() && i < 16; i++) {
                                JSONObject resource = resources.optJSONObject(i);
                                if (resource == null) continue;
                                String resourceUrl = resource.optString("u"), type = resource.optString("t");
                                String accept = "script".equals(type) ? "application/javascript" :
                                        "stylesheet".equals(type) ? "text/css" :
                                        "image".equals(type) ? "image/*" : "font/woff2";
                                if (host.isAdBlockEnabled() && adBlock.shouldBlock(Uri.parse(resourceUrl), pageHost, accept, false)) continue;
                                hints.add(new ConnectionHintsStore.ResourceHint(resourceUrl, type,
                                        resource.optDouble("e"), resource.optDouble("d")));
                            }
                            long now = System.currentTimeMillis();
                            connectionHints.observe(url, hints, now);
                            // An empty observation also supersedes a still-loading disk snapshot.
                            connectionHintsGeneration++;
                            prefs.edit().putString(PREF_CONNECTION_HINTS, connectionHints.snapshot(now)).apply();
                        } catch (Exception ignored) {}
                    }
                });
            }
        }, 250L);
    }

    void clearConnectionHints() {
        connectionHintsGeneration++;
        connectionHints.clear();
        removeAllConnectionHintScripts();
        prefs.edit().remove(PREF_CONNECTION_HINTS).apply();
    }

    void prepareNavigationConnections(String url) {
        if (closed) return;
        List<String> learnedOrigins = connectionHints.hintsFor(url, System.currentTimeMillis());
        WebViewNavigationWarmupCompat.preconnectForNavigation(url,
                learnedOrigins.toArray(new String[learnedOrigins.size()]), connectionOriginFilter(url));
    }

    /** Retires Activity-owned work and registrations without deleting the persisted cache. */
    void close() {
        if (closed) return;
        closed = true;
        connectionHintsGeneration++;
        removeAllConnectionHintScripts();
    }

    private void removeAllConnectionHintScripts() {
        for (WebView target : new ArrayList<WebView>(connectionHintScripts.keySet()))
            removeConnectionHintScript(target);
    }

    private static String hostOf(String url) {
        try {
            if (url == null) return "";
            String pageHost = Uri.parse(url).getHost();
            return pageHost == null || "median.invalid".equalsIgnoreCase(pageHost) ? "" : pageHost.toLowerCase(Locale.US);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static boolean isNetworkPage(String url) {
        if (url == null || !(url.regionMatches(true, 0, "https://", 0, 8) ||
                url.regionMatches(true, 0, "http://", 0, 7))) return false;
        try { return !"median.invalid".equalsIgnoreCase(Uri.parse(url).getHost()); }
        catch (RuntimeException ignored) { return false; }
    }

    private static String navigationDocumentUrl(String url) {
        if (url == null) return "";
        int fragment = url.indexOf('#');
        return fragment < 0 ? url : url.substring(0, fragment);
    }
}
