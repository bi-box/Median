package com.xinyv.median;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;
import android.view.WindowManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.os.Handler;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class MainActivitySecuritySmokeTest {
    private static final String HOME = "https://median.invalid/";
    private static final long STARTUP_TIMEOUT_MS = 30_000L;
    private static final long PAGE_TIMEOUT_MS = 15_000L;
    private static final String[] TEST_PREFERENCE_KEYS = {
            "community_notice_shown_v1", "home_open_mode", "restore_tabs",
            "home_custom_html_enabled", "home_show_search"
    };
    private final Map<String, Object> savedPreferences = new HashMap<String, Object>();
    private SharedPreferences preferences;

    @Before public void useDeterministicLocalHome() {
        Context context = ApplicationProvider.getApplicationContext();
        preferences = context.getSharedPreferences("median_browser_v2", Context.MODE_PRIVATE);
        Map<String, ?> previous = preferences.getAll();
        for (String key : TEST_PREFERENCE_KEYS) {
            if (previous.containsKey(key)) savedPreferences.put(key, previous.get(key));
        }
        assertTrue(preferences.edit()
                .putBoolean("community_notice_shown_v1", true)
                .putString("home_open_mode", "home")
                .putBoolean("restore_tabs", false)
                .putBoolean("home_custom_html_enabled", false)
                .putBoolean("home_show_search", true).commit());
    }

    @After public void restoreTestPreferences() {
        if (preferences == null) return;
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : TEST_PREFERENCE_KEYS) {
            Object value = savedPreferences.get(key);
            if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof String) editor.putString(key, (String) value);
            else editor.remove(key);
        }
        assertTrue(editor.commit());
    }

    @Test public void webViewSecurityDefaultsRemainHardened() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitBrowserReady(scenario);
            onUi(scenario, activity -> {
                WebSettings settings = webView(activity).getSettings();
                assertFalse(settings.getAllowFileAccess());
                assertFalse(settings.getAllowContentAccess());
                assertFalse(settings.getSaveFormData());
                assertTrue(settings.getMediaPlaybackRequiresUserGesture());
                assertFalse(settings.getJavaScriptCanOpenWindowsAutomatically());
                assertTrue(settings.getSafeBrowsingEnabled());
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, settings.getMixedContentMode());
            });
        }
    }

    @Test public void credentialAutofillScriptsRunAgainstRealWebViewForms() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitBrowserReady(scenario);
            awaitTrustedHome(scenario);
            String markup = "document.body.innerHTML=" + JSONObject.quote(
                    "<form id='subscribe'><input type='email' name='newsletter_email'></form>" +
                    "<form id='login'><input type='text' name='login_name' autocomplete='username'>" +
                    "<input type='password' name='password' autocomplete='current-password'>" +
                    "<input type='password' name='otp' autocomplete='one-time-code'>" +
                    "<input type='password' name='new_password' autocomplete='new-password'></form>");
            assertEquals("true", evaluate(scenario, "(function(){" + markup + ";return true;})()"));

            JSONObject detected = new JSONObject(decodeWebViewString(evaluate(scenario,
                    CredentialAutofill.detectScript())));
            assertTrue("Real WebView should identify the login form", detected.optBoolean("login"));
            assertTrue("Username in the password form should be selected", detected.optBoolean("username"));
            assertEquals("OTP and new-password fields must be excluded", 1, detected.optInt("passwords"));

            JSONObject filled = new JSONObject(decodeWebViewString(evaluate(scenario,
                    CredentialAutofill.fillScript("person@example.com", "test-secret"))));
            assertTrue(filled.optBoolean("filled"));
            assertTrue(filled.optBoolean("username"));
            assertEquals("person@example.com", decodeWebViewString(evaluate(scenario,
                    "document.querySelector('#login [autocomplete=username]').value")));
            assertEquals("test-secret", decodeWebViewString(evaluate(scenario,
                    "document.querySelector('#login [autocomplete=current-password]').value")));
            assertEquals("", decodeWebViewString(evaluate(scenario,
                    "document.querySelector('#subscribe input').value")));
            assertEquals("", decodeWebViewString(evaluate(scenario,
                    "document.querySelector('[autocomplete=one-time-code]').value")));
            assertEquals("", decodeWebViewString(evaluate(scenario,
                    "document.querySelector('[autocomplete=new-password]').value")));

            JSONArray captured = new JSONArray(decodeWebViewString(evaluate(scenario,
                    CredentialAutofill.captureFieldsScript())));
            assertEquals("person@example.com", captured.getString(0));
            assertEquals("test-secret", captured.getString(1));

            assertEquals("true", evaluate(scenario, "(function(){document.body.innerHTML=" +
                    JSONObject.quote("<input type='email' name='email'><input type='password' " +
                            "name='otp' autocomplete='one-time-code'>") + ";return true;})()"));
            JSONArray otpProbe = new JSONArray(decodeWebViewString(evaluate(scenario,
                    CredentialAutofill.captureFieldsScript())));
            assertEquals("OTP-only pages must not supply save-password credentials", "", otpProbe.getString(0));
            assertEquals("", otpProbe.getString(1));
        }
    }

    @Test public void adblockSeparatorCaretUsesAbpBoundaryOnAndroidRuntime() {
        AdBlockEngine engine = new AdBlockEngine();
        assertEquals(2, engine.updateCustomRules("||ads.example^\nfoo^bar"));
        assertTrue(engine.shouldBlock("https://ads.example/banner.js", "news.example"));
        assertFalse("Domain anchors must stop at a host boundary",
                engine.shouldBlock("https://ads.example.evil/banner.js", "news.example"));
        assertTrue("ABP ^ accepts a URL delimiter", engine.shouldBlock("https://cdn.example/foo/bar", "news.example"));
        assertTrue("ABP ^ accepts a query delimiter", engine.shouldBlock("https://cdn.example/foo?bar", "news.example"));
        assertFalse("ABP ^ must not match between adjacent alphanumeric characters",
                engine.shouldBlock("https://cdn.example/foobar", "news.example"));
        assertFalse("ABP ^ must consume exactly one separator",
                engine.shouldBlock("https://cdn.example/foo//bar", "news.example"));
    }

    @Test public void userscriptRequestCompletesAfterSpaRouteAndIsSuppressedAfterNavigation() throws Exception {
        try (LocalPages pages = new LocalPages();
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitBrowserReady(scenario);
            awaitTrustedHome(scenario);
            String page = pages.url("/one");
            onUi(scenario, activity -> invoke(activity, "loadInput", new Class<?>[] { String.class }, page));
            awaitPage(scenario, page, "Median fixture one");

            AtomicReference<WebView> viewRef = new AtomicReference<WebView>();
            onUi(scenario, activity -> viewRef.set(webView(activity)));
            WebView view = viewRef.get();
            String token = "instrumentation-token";
            String scriptId = "instrumentation-spa-xhr";
            String objectName = UserScriptStore.dispatchObjectName(token, scriptId);
            String dispatcher = "window.events=[];window[" + JSONObject.quote(objectName) +
                    "]={dispatch:function(t,i,e,p){if(t===" + JSONObject.quote(token) +
                    ")window.events.push({id:i,event:e,payload:p});}};true";
            assertEquals("true", evaluate(scenario, dispatcher));

            NetworkProbeHost host = new NetworkProbeHost(view, token, scriptId);
            UserScriptNetwork network = new UserScriptNetwork(new Handler(Looper.getMainLooper()), host);
            try {
                pages.gate("/xhr");
                assertTrue(new JSONObject(network.request(view, token, scriptId,
                        new JSONObject().put("u", pages.url("/xhr")).put("i", "spa-callback"),
                        page, host.generation)).optBoolean("ok"));
                pages.awaitGatedRequest();

                assertEquals("SPA route should stay in the same document",
                        pages.url("/spa-route"),
                        decodeWebViewString(evaluate(scenario,
                                "(function(){history.pushState({},'', '/spa-route');return location.href;})()")));
                pages.releaseGatedResponse();
                awaitUiValue(scenario, "SPA callback", "window.events.some(function(e){return e.id==='spa-callback'&&e.event==='load';})", "true");
                JSONObject spaEvent = new JSONObject(decodeWebViewString(evaluate(scenario,
                        "JSON.stringify(window.events.filter(function(e){return e.id==='spa-callback';})[0])")));
                assertEquals("load", spaEvent.getString("event"));
                assertEquals("spa-response", spaEvent.getJSONObject("payload").getString("responseText"));

                pages.gate("/xhr-stale");
                assertTrue(new JSONObject(network.request(view, token, scriptId,
                        new JSONObject().put("u", pages.url("/xhr-stale")).put("i", "stale-callback"),
                        pages.url("/spa-route"), host.generation)).optBoolean("ok"));
                pages.awaitGatedRequest();
                onUi(scenario, activity -> invoke(activity, "loadInput", new Class<?>[] { String.class }, pages.url("/two")));
                awaitPage(scenario, pages.url("/two"), "Median fixture two");
                host.invalidateDocument();
                assertEquals("true", evaluate(scenario, "window.events=[];" +
                        "window[" + JSONObject.quote(objectName) + "]={dispatch:function(t,i,e,p){window.events.push({id:i,event:e});}};true"));
                pages.releaseGatedResponse();
                assertTrue("The request worker should finish", host.awaitWorker(10, TimeUnit.SECONDS));
                SystemClock.sleep(250L);
                assertEquals("A response from the previous document must not dispatch",
                        "false", evaluate(scenario,
                                "window.events.some(function(e){return e.id==='stale-callback';})"));
            } finally {
                network.close();
                host.close();
            }
            pages.assertHealthy();
        }
    }

    private static String decodeWebViewString(String raw) throws Exception {
        return new JSONArray("[" + (raw == null ? "null" : raw) + "]").getString(0);
    }

    private static void awaitUiValue(ActivityScenario<MainActivity> scenario, String label,
                                     String expression, String expected) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + PAGE_TIMEOUT_MS;
        do {
            if (expected.equals(evaluate(scenario, expression))) return;
            SystemClock.sleep(50L);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Timed out waiting for " + label);
    }

    private static final class NetworkProbeHost implements UserScriptNetwork.Host, AutoCloseable {
        private final WebView source;
        private final String token;
        private final String scriptId;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private volatile long generation = 1L;
        private volatile boolean documentCurrent = true;
        private volatile CountDownLatch workerFinished = new CountDownLatch(0);

        NetworkProbeHost(WebView source, String token, String scriptId) {
            this.source = source;
            this.token = token;
            this.scriptId = scriptId;
        }

        @Override public boolean executeNetworkTask(Runnable task) {
            CountDownLatch finished = new CountDownLatch(1);
            workerFinished = finished;
            executor.execute(() -> {
                try { task.run(); } finally { finished.countDown(); }
            });
            return true;
        }

        @Override public boolean canConnect(String candidateScriptId, String url, String pageUrl) {
            return scriptId.equals(candidateScriptId);
        }

        @Override public boolean isCurrentRequestDocument(WebView candidate, String candidateToken,
                                                           String candidateScriptId, long requestGeneration) {
            return documentCurrent && candidate == source && token.equals(candidateToken) &&
                    scriptId.equals(candidateScriptId) && requestGeneration == generation;
        }

        @Override public void scheduleCookieFlush() {}

        void invalidateDocument() {
            documentCurrent = false;
            generation++;
        }

        boolean awaitWorker(long timeout, TimeUnit unit) throws InterruptedException {
            return workerFinished.await(timeout, unit);
        }

        @Override public void close() throws Exception {
            executor.shutdownNow();
            assertTrue("Network probe executor did not stop", executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test public void generatedHomeKeepsItsTrustedOriginAndDocumentToken() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitBrowserReady(scenario);
            awaitTrustedHome(scenario);
            onUi(scenario, activity -> {
                assertEquals(HOME, currentPageUrl(activity));
                assertEquals(HOME, webView(activity).getUrl());
                Set<?> trusted = (Set<?>) field(activity, "trustedHomeViews");
                assertTrue(trusted.contains(webView(activity)));
            });
            assertEquals("true", evaluate(scenario,
                    "document.querySelector('#form') !== null && document.querySelector('#q') !== null"));
        }
    }

    @Test public void exportedActivityRejectsNonHttpExplicitIntent() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String[] blocked = {
                "file:///sdcard/private.txt",
                "content://com.example.untrusted/private",
                "data:text/html,<title>unsafe-intent</title>",
                "javascript:document.documentElement.setAttribute('data-unsafe-intent','executed')"
        };
        Intent malicious = new Intent(Intent.ACTION_VIEW, Uri.parse(blocked[0]));
        malicious.setClass(context, MainActivity.class);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(malicious)) {
            AtomicReference<MainActivity> launched = new AtomicReference<MainActivity>();
            AtomicReference<Intent> launchIntent = new AtomicReference<Intent>();
            onUi(scenario, activity -> {
                launched.set(activity);
                launchIntent.set(new Intent(activity.getIntent()));
            });
            try {
                awaitBrowserReady(scenario);
                awaitTrustedHome(scenario);
                assertEquals("false", evaluate(scenario,
                        "document.documentElement.hasAttribute('data-unsafe-intent')"));

                for (String url : blocked) {
                    // Dispatch through Android from the existing Activity, retaining its task.
                    // The manifest's singleTask path must deliver onNewIntent to this same instance.
                    Intent repeated = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(url + (url.startsWith("javascript:") ? ";void(0)" : "#repeated")));
                    repeated.setClass(context, MainActivity.class);
                    repeated.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    onUi(scenario, activity -> activity.startActivity(repeated));
                    awaitUi(scenario, "onNewIntent delivery for " + url, PAGE_TIMEOUT_MS,
                            activity -> repeated.getDataString().equals(activity.getIntent().getDataString()));
                    onUi(scenario, activity -> assertSame("Intent must reuse the launched Activity", launched.get(), activity));
                    awaitTrustedHome(scenario);
                    assertEquals("false", evaluate(scenario,
                            "document.documentElement.hasAttribute('data-unsafe-intent')"));
                }
            } finally {
                // Production deliberately stores the latest Intent. Restore the launch identity
                // only for ActivityScenario cleanup so its lifecycle matcher can observe destroy.
                onUi(scenario, activity -> activity.setIntent(launchIntent.get()));
            }
        }
    }

    @Test public void localNavigationAndProductionStateCaptureSurviveActivityRecreation() throws Exception {
        try (LocalPages pages = new LocalPages();
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitBrowserReady(scenario);
            awaitTrustedHome(scenario);
            String first = pages.url("/one");
            String second = pages.url("/two");
            onUi(scenario, activity -> invoke(activity, "loadInput", new Class<?>[] { String.class }, first));
            awaitPage(scenario, first, "Median fixture one");
            onUi(scenario, activity -> invoke(activity, "loadInput", new Class<?>[] { String.class }, second));
            awaitPage(scenario, second, "Median fixture two");
            onUi(scenario, activity -> {
                assertTrue(webView(activity).canGoBack());
                webView(activity).goBack();
            });
            awaitPage(scenario, first, "Median fixture one");
            onUi(scenario, activity -> {
                assertTrue(webView(activity).canGoForward());
                webView(activity).goForward();
            });
            awaitPage(scenario, second, "Median fixture two");

            onUi(scenario, activity -> {
                Bundle captured = (Bundle) invoke(null, "captureWebViewState",
                        new Class<?>[] { WebView.class }, webView(activity));
                assertNotNull("Production state capture must preserve a small real history", captured);
                assertFalse(captured.isEmpty());
            });
            // This also runs the real onSaveInstanceState -> captureWebViewState -> restoreState path.
            scenario.recreate();
            awaitBrowserReady(scenario);
            awaitPage(scenario, second, "Median fixture two");
            onUi(scenario, activity -> {
                WebBackForwardList history = webView(activity).copyBackForwardList();
                assertEquals(second, history.getCurrentItem().getUrl());
                assertTrue(history.getCurrentIndex() > 0);
                assertEquals(first, history.getItemAtIndex(history.getCurrentIndex() - 1).getUrl());
                webView(activity).goBack();
            });
            awaitPage(scenario, first, "Median fixture one");
            onUi(scenario, activity -> {
                assertTrue(webView(activity).canGoForward());
                webView(activity).goForward();
            });
            awaitPage(scenario, second, "Median fixture two");
            pages.assertHealthy();
        }
    }

    @Test public void frozenTabsRestoreHistoryAndClosingCleansOnlyTheirScriptState() throws Exception {
        try (LocalPages pages = new LocalPages();
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitBrowserReady(scenario);
            awaitTrustedHome(scenario);
            String first = pages.url("/one");
            String second = pages.url("/two");
            onUi(scenario, activity -> invoke(activity, "loadInput", new Class<?>[] { String.class }, first));
            awaitPage(scenario, first, "Median fixture one");
            onUi(scenario, activity -> invoke(activity, "loadInput", new Class<?>[] { String.class }, second));
            awaitPage(scenario, second, "Median fixture two");

            AtomicReference<String> firstId = new AtomicReference<String>();
            AtomicReference<String> secondId = new AtomicReference<String>();
            onUi(scenario, activity -> {
                firstId.set(activeScriptTabId(activity));
                // Seed the real GM tab backing store; switching, freezing and cleanup below
                // exercise production lifecycle paths rather than a replacement WebViewClient.
                ScriptTabStateStore values = (ScriptTabStateStore) field(activity, "scriptTabValues");
                assertTrue(values.put("instrumentation|" + firstId.get(), "{\"tab\":\"first\"}"));
                invoke(activity, "openUrlInNewTab", new Class<?>[] { String.class, boolean.class }, first, true);
            });
            awaitPage(scenario, first, "Median fixture one");
            onUi(scenario, activity -> {
                assertEquals(2, tabs(activity).size());
                assertEquals(1, ((Integer) field(activity, "currentTabIndex")).intValue());
                secondId.set(activeScriptTabId(activity));
                assertFalse("Distinct tabs need distinct GM tab identities", firstId.get().equals(secondId.get()));
                ScriptTabStateStore values = (ScriptTabStateStore) field(activity, "scriptTabValues");
                assertTrue(values.put("instrumentation|" + secondId.get(), "{\"tab\":\"second\"}"));

                // This is the production memory-pressure freeze path. It also covers devices
                // whose one-renderer policy already froze the previous tab during activation.
                invoke(activity, "releaseInactiveTabStates", new Class<?>[0]);
                Object frozen = tabs(activity).get(0);
                assertNull("Background renderer must be released", tabField(frozen, "liveView"));
                assertNotNull("Small frozen history must remain restorable", tabField(frozen, "state"));
                assertEquals("{\"tab\":\"first\"}", values.get("instrumentation|" + firstId.get()));
                invoke(activity, "switchTab", new Class<?>[] { int.class }, 0);
            });
            awaitPage(scenario, second, "Median fixture two");
            onUi(scenario, activity -> {
                assertEquals(firstId.get(), activeScriptTabId(activity));
                WebBackForwardList history = webView(activity).copyBackForwardList();
                assertTrue(history.getCurrentIndex() > 0);
                assertEquals(first, history.getItemAtIndex(history.getCurrentIndex() - 1).getUrl());
                webView(activity).goBack();
            });
            awaitPage(scenario, first, "Median fixture one");
            onUi(scenario, activity -> webView(activity).goForward());
            awaitPage(scenario, second, "Median fixture two");
            onUi(scenario, activity -> invoke(activity, "closeCurrentTab", new Class<?>[0]));
            awaitPage(scenario, first, "Median fixture one");
            onUi(scenario, activity -> {
                assertEquals(1, tabs(activity).size());
                assertEquals(0, ((Integer) field(activity, "currentTabIndex")).intValue());
                assertEquals(secondId.get(), activeScriptTabId(activity));
                ScriptTabStateStore values = (ScriptTabStateStore) field(activity, "scriptTabValues");
                assertNull("Closing a tab must remove its GM tab state", values.get("instrumentation|" + firstId.get()));
                assertEquals("{\"tab\":\"second\"}", values.get("instrumentation|" + secondId.get()));
            });
            pages.assertHealthy();
        }
    }

    @Test public void multipleTabsAndSelectionSurviveActivityRecreation() throws Exception {
        try (LocalPages pages = new LocalPages();
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitBrowserReady(scenario);
            awaitTrustedHome(scenario);
            String first = pages.url("/one");
            String second = pages.url("/two");
            onUi(scenario, activity -> invoke(activity, "loadInput", new Class<?>[] { String.class }, first));
            awaitPage(scenario, first, "Median fixture one");
            onUi(scenario, activity -> {
                // Pinning is session metadata and must follow the tab through recreation.
                Field pinned = tabs(activity).get(0).getClass().getDeclaredField("pinned");
                pinned.setAccessible(true);
                pinned.setBoolean(tabs(activity).get(0), true);
                invoke(activity, "openUrlInNewTab", new Class<?>[] { String.class, boolean.class }, second, true);
            });
            awaitPage(scenario, second, "Median fixture two");
            onUi(scenario, activity -> {
                assertEquals(2, tabs(activity).size());
                assertEquals(1, ((Integer) field(activity, "currentTabIndex")).intValue());
            });

            // ActivityScenario invokes Android's actual save/destroy/create/restore sequence.
            // No preference change is needed: configuration recreation must retain open tabs
            // even when a fresh launcher start is configured to open the homepage.
            scenario.recreate();
            awaitBrowserReady(scenario);
            onUi(scenario, activity -> {
                assertEquals("Activity recreation must preserve every open tab", 2, tabs(activity).size());
                assertEquals("Activity recreation must retain the selected tab", 1,
                        ((Integer) field(activity, "currentTabIndex")).intValue());
                assertEquals(first, tabField(tabs(activity).get(0), "url"));
                assertEquals(second, tabField(tabs(activity).get(1), "url"));
                assertEquals(Boolean.TRUE, tabField(tabs(activity).get(0), "pinned"));
            });
            awaitPage(scenario, second, "Median fixture two");
            onUi(scenario, activity -> invoke(activity, "switchTab", new Class<?>[] { int.class }, 0));
            awaitPage(scenario, first, "Median fixture one");
            pages.assertHealthy();
        }
    }

    @Test public void metadataOnlyStateRestoresSelectedUrlWithoutLaunchRestorePreference() throws Exception {
        try (LocalPages pages = new LocalPages();
             ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitBrowserReady(scenario);
            String first = pages.url("/one");
            String second = pages.url("/two");
            onUi(scenario, activity -> invoke(activity, "loadInput", new Class<?>[] { String.class }, first));
            awaitPage(scenario, first, "Median fixture one");
            onUi(scenario, activity -> invoke(activity, "openUrlInNewTab",
                    new Class<?>[] { String.class, boolean.class }, second, true));
            awaitPage(scenario, second, "Median fixture two");
            Bundle saved = new Bundle();
            onUi(scenario, activity -> {
                activity.onSaveInstanceState(saved);
                assertNotNull(saved.getBundle("median.tabs.state"));
                // Model a Chromium history capture that returned null or exceeded its bound.
                saved.remove("median.webview.state");
                invoke(activity, "loadInput", new Class<?>[] { String.class }, first);
            });
            awaitPage(scenario, first, "Median fixture one");
            onUi(scenario, activity -> invoke(activity, "completeStartup",
                    new Class<?>[] { Bundle.class, String.class }, saved, ""));
            awaitPage(scenario, second, "Median fixture two");
            onUi(scenario, activity -> {
                assertEquals(2, tabs(activity).size());
                assertEquals(1, ((Integer) field(activity, "currentTabIndex")).intValue());
                assertEquals(first, tabField(tabs(activity).get(0), "url"));
                assertEquals(second, tabField(tabs(activity).get(1), "url"));
            });
            pages.assertHealthy();
        }
    }

    @Test public void tabMetadataKeeps64OrdinaryUrlsAndRejectsMalformedStateWithinParcelBound() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitBrowserReady(scenario);
            onUi(scenario, activity -> {
                List<?> actualTabs = tabs(activity);
                Class<?> tabType = actualTabs.get(0).getClass();
                java.lang.reflect.Constructor<?> constructor = tabType.getDeclaredConstructor();
                constructor.setAccessible(true);
                @SuppressWarnings("unchecked") List<Object> mutableTabs = (List<Object>) actualTabs;
                mutableTabs.clear();
                String padding = new String(new char[2048]).replace('\0', 'a');
                String selectedUrl = "https://example.com/63?" + padding;
                for (int i = 0; i < 64; i++) {
                    Object tab = constructor.newInstance();
                    setTabField(tab, "url", "https://example.com/" + i + "?" + padding);
                    setTabField(tab, "title", "标签 " + i);
                    setTabField(tab, "pinned", i % 2 == 0);
                    mutableTabs.add(tab);
                }
                Field selected = MainActivity.class.getDeclaredField("currentTabIndex");
                selected.setAccessible(true);
                selected.setInt(activity, 63);
                Bundle metadata = (Bundle) invoke(activity, "captureTabMetadata", new Class<?>[0]);
                assertNotNull(metadata);
                Parcel parcel = Parcel.obtain();
                try {
                    metadata.writeToParcel(parcel, 0);
                    assertTrue("Metadata has an independent 256 KiB bound", parcel.dataSize() <= 262144);
                } finally { parcel.recycle(); }
                Bundle saved = new Bundle();
                saved.putBundle("median.tabs.state", metadata);
                assertEquals(Boolean.TRUE, invoke(activity, "restoreTabMetadata", new Class<?>[] { Bundle.class }, saved));
                assertEquals(64, tabs(activity).size());
                assertEquals(63, selected.getInt(activity));
                for (int i = 0; i < 64; i++) {
                    assertEquals("https://example.com/" + i + "?" + padding, tabField(tabs(activity).get(i), "url"));
                    assertEquals(i % 2 == 0, tabField(tabs(activity).get(i), "pinned"));
                }

                byte[] corrupted = metadata.getByteArray("data").clone();
                ByteBuffer.wrap(corrupted).putInt(8, 64); // Index equals tab count: out of range.
                Bundle invalid = new Bundle();
                invalid.putByteArray("data", corrupted);
                saved.putBundle("median.tabs.state", invalid);
                assertEquals(Boolean.FALSE, invoke(activity, "restoreTabMetadata", new Class<?>[] { Bundle.class }, saved));
                ByteBuffer.wrap(corrupted).putInt(8, 63).putInt(4, 65);
                assertEquals(Boolean.FALSE, invoke(activity, "restoreTabMetadata", new Class<?>[] { Bundle.class }, saved));
                invalid.putByteArray("data", new byte[262144]);
                assertEquals(Boolean.FALSE, invoke(activity, "restoreTabMetadata", new Class<?>[] { Bundle.class }, saved));
                assertEquals("Rejected input must leave the current session intact", selectedUrl,
                        tabField(tabs(activity).get(63), "url"));

                // Exceed the aggregate budget with long but valid URLs. Every tab position and
                // the selected URL must survive, and the encoded state must remain bounded.
                String longPadding = new String(new char[15000]).replace('\0', 'b');
                for (int i = 0; i < 64; i++) setTabField(tabs(activity).get(i), "url",
                        "https://example.com/" + i + "?" + longPadding);
                metadata = (Bundle) invoke(activity, "captureTabMetadata", new Class<?>[0]);
                assertNotNull(metadata);
                assertTrue(metadata.getByteArray("data").length <= 262144 - 1024);
                saved.putBundle("median.tabs.state", metadata);
                assertEquals(Boolean.TRUE, invoke(activity, "restoreTabMetadata", new Class<?>[] { Bundle.class }, saved));
                assertEquals(64, tabs(activity).size());
                assertEquals(63, selected.getInt(activity));
                assertEquals("https://example.com/63?" + longPadding, tabField(tabs(activity).get(63), "url"));
            });
        }
    }

    private static void setTabField(Object tab, String name, Object value) throws Exception {
        Field target = tab.getClass().getDeclaredField(name);
        target.setAccessible(true);
        target.set(tab, value);
    }

    private interface UiAction { void run(MainActivity activity) throws Exception; }
    private interface UiCheck { boolean test(MainActivity activity) throws Exception; }

    private static void onUi(ActivityScenario<MainActivity> scenario, UiAction action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        scenario.onActivity(activity -> {
            try { action.run(activity); }
            catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void awaitUi(ActivityScenario<MainActivity> scenario, String description,
                                long timeoutMs, UiCheck check) throws Exception {
        assertFalse("Waiting must run off the UI thread", Looper.myLooper() == Looper.getMainLooper());
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        do {
            AtomicBoolean ready = new AtomicBoolean();
            onUi(scenario, activity -> ready.set(check.test(activity)));
            if (ready.get()) return;
            SystemClock.sleep(50L);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Timed out waiting for " + description + " after " + timeoutMs + " ms");
    }

    private static void awaitBrowserReady(ActivityScenario<MainActivity> scenario) throws Exception {
        onUi(scenario, activity -> activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        awaitUi(scenario, "asynchronous WebView and local startup data", STARTUP_TIMEOUT_MS,
                activity -> field(activity, "webView") != null &&
                        Boolean.TRUE.equals(field(activity, "startupReady")) &&
                        Boolean.TRUE.equals(field(activity, "startupDataReady")));
    }

    private static void awaitTrustedHome(ActivityScenario<MainActivity> scenario) throws Exception {
        awaitUi(scenario, "local homepage navigation", PAGE_TIMEOUT_MS,
                activity -> HOME.equals(currentPageUrl(activity)) &&
                        HOME.equals(webView(activity).getUrl()) && webView(activity).getProgress() == 100);
        AtomicReference<String> token = new AtomicReference<String>();
        onUi(scenario, activity -> token.set((String) invoke(activity, "homeToken", new Class<?>[0])));
        long deadline = SystemClock.elapsedRealtime() + PAGE_TIMEOUT_MS;
        String probe = "(function(){var m=document.querySelector('meta[name=median-home-token]');" +
                "return location.href===" + JSONObject.quote(HOME) + "&&!!m&&m.content===" +
                JSONObject.quote(token.get()) + ";})()";
        do {
            if ("true".equals(evaluate(scenario, probe))) {
                awaitUi(scenario, "native homepage trust verification", PAGE_TIMEOUT_MS,
                        activity -> ((Set<?>) field(activity, "trustedHomeViews")).contains(webView(activity)));
                return;
            }
            SystemClock.sleep(50L);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Generated home document did not retain its origin and native token");
    }

    private static void awaitPage(ActivityScenario<MainActivity> scenario, String url, String title)
            throws Exception {
        awaitUi(scenario, title + " at " + url, PAGE_TIMEOUT_MS,
                activity -> url.equals(webView(activity).getUrl()) &&
                        title.equals(webView(activity).getTitle()) && webView(activity).getProgress() == 100 &&
                        url.equals(currentPageUrl(activity)));
        assertEquals("true", evaluate(scenario,
                "document.readyState === 'complete' && document.title === " + JSONObject.quote(title)));
    }

    private static String evaluate(ActivityScenario<MainActivity> scenario, String script) throws Exception {
        assertFalse("JavaScript callback wait must run off the UI thread",
                Looper.myLooper() == Looper.getMainLooper());
        AtomicReference<String> result = new AtomicReference<String>();
        CountDownLatch complete = new CountDownLatch(1);
        onUi(scenario, activity -> webView(activity).evaluateJavascript(script, value -> {
            result.set(value);
            complete.countDown();
        }));
        assertTrue("WebView JavaScript callback timed out", complete.await(10L, TimeUnit.SECONDS));
        return result.get();
    }

    private static Object field(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static List<?> tabs(MainActivity activity) throws Exception {
        return (List<?>) field(activity, "tabs");
    }

    private static Object tabField(Object tab, String name) throws Exception {
        Field field = tab.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(tab);
    }

    private static String activeScriptTabId(MainActivity activity) throws Exception {
        Map<?, ?> identities = (Map<?, ?>) field(activity, "scriptTabIds");
        String id = (String) identities.get(webView(activity));
        assertNotNull("Active renderer must retain its browser tab identity", id);
        return id;
    }

    private static Object invoke(MainActivity activity, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(activity, args);
    }

    private static String currentPageUrl(MainActivity activity) throws Exception {
        return (String) field(activity, "currentPageUrl");
    }

    private static WebView webView(MainActivity activity) throws Exception {
        WebView value = (WebView) field(activity, "webView");
        assertNotNull(value);
        return value;
    }

    /** An HTTP fixture served inside the instrumented process; no external host or WebViewClient. */
    private static final class LocalPages implements AutoCloseable {
        private final ServerSocket server;
        private final Thread worker;
        private final ExecutorService requests = Executors.newCachedThreadPool();
        private final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        private volatile String gatedPath;
        private volatile CountDownLatch gatedRequest = new CountDownLatch(0);
        private volatile CountDownLatch gatedRelease = new CountDownLatch(0);
        private volatile boolean running = true;

        LocalPages() throws Exception {
            server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            worker = new Thread(() -> {
                while (running) {
                    try {
                        Socket socket = server.accept();
                        requests.execute(() -> serve(socket));
                    } catch (java.io.IOException ignored) {
                        // Closing the listening socket ends this accept loop.
                        if (running) failure.compareAndSet(null, ignored);
                    } catch (RuntimeException error) {
                        if (running) failure.compareAndSet(null, error);
                    }
                }
            }, "median-instrumentation-local-pages");
            worker.setDaemon(true);
            worker.start();
        }

        private void serve(Socket socket) {
            try (Socket owned = socket) {
                owned.setSoTimeout(5000);
                BufferedReader input = new BufferedReader(new InputStreamReader(
                        owned.getInputStream(), StandardCharsets.US_ASCII));
                String request = input.readLine();
                if (request == null) return;
                String line;
                int headerBytes = request.length();
                while ((line = input.readLine()) != null && !line.isEmpty()) {
                    headerBytes += line.length();
                    if (headerBytes > 8192) throw new IllegalStateException("Unexpected large fixture request");
                }
                String path = request.startsWith("GET ") ? request.substring(4, request.indexOf(' ', 5)) : "/";
                if (path.equals(gatedPath)) {
                    gatedRequest.countDown();
                    if (!gatedRelease.await(10L, TimeUnit.SECONDS))
                        throw new IllegalStateException("Timed out waiting to release " + path);
                }
                boolean xhr = path.startsWith("/xhr");
                String name = path.startsWith("/two") ? "two" : "one";
                byte[] body = (xhr ? (path.equals("/xhr") ? "spa-response" : "stale-response") :
                        "<!doctype html><html><head><meta charset='utf-8'><title>Median fixture " + name +
                                "</title></head><body><h1 id='marker'>" + name + "</h1></body></html>")
                        .getBytes(StandardCharsets.UTF_8);
                String contentType = xhr ? "text/plain; charset=utf-8" : "text/html; charset=utf-8";
                OutputStream output = owned.getOutputStream();
                output.write(("HTTP/1.1 200 OK\r\nContent-Type: " + contentType + "\r\n" +
                        "Cache-Control: no-store\r\nConnection: close\r\nContent-Length: " +
                        body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(body);
                output.flush();
            } catch (java.io.IOException ignored) {
                // Chromium may abandon speculative connections or cancel a favicon request.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException error) {
                if (running) failure.compareAndSet(null, error);
            }
        }

        String url(String path) { return "http://127.0.0.1:" + server.getLocalPort() + path; }

        void gate(String path) {
            gatedPath = path;
            gatedRequest = new CountDownLatch(1);
            gatedRelease = new CountDownLatch(1);
        }

        void awaitGatedRequest() throws InterruptedException {
            assertTrue("XHR did not reach local fixture", gatedRequest.await(10L, TimeUnit.SECONDS));
        }

        void releaseGatedResponse() {
            gatedRelease.countDown();
            gatedPath = null;
        }

        void assertHealthy() {
            if (failure.get() != null) throw new AssertionError("Local HTTP fixture failed", failure.get());
        }

        @Override public void close() throws Exception {
            running = false;
            server.close();
            worker.join(3000L);
            assertFalse("Local fixture worker did not stop", worker.isAlive());
            requests.shutdownNow();
            assertTrue("Local request handlers did not stop", requests.awaitTermination(3L, TimeUnit.SECONDS));
        }
    }
}
