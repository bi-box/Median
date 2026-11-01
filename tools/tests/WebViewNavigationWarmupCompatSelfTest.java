package androidx.webkit;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.webkit.internal.WebViewGlueCommunicator;
import org.chromium.support_lib_glue.SupportLibReflectionUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Exercises privacy boundaries and initialization ordering through a synthetic real glue entry. */
public final class WebViewNavigationWarmupCompatSelfTest {
    private static int cases;
    private static final String[] FEATURES = { "ASYNC_WEBVIEW_STARTUP_V2", "ENQUEUE_PRECONNECT",
            "PRECONNECT", "MULTI_PROFILE", "MULTI_PROCESS_QUERY" };

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void done(String name) { cases++; System.out.println("PASS " + name); }
    private static void drain() {
        Runnable task;
        while ((task = Handler.QUEUE.poll()) != null) task.run();
    }
    private static void reset() {
        drain();
        WebViewNavigationWarmupCompat.cancelStartupPreconnect();
        WebViewGlueCommunicator.invalidate();
        Build.VERSION.SDK_INT = 36;
        Build.TYPE = "user";
        WebView.loads = 0;
        WebView.loadFailure = null;
        WebView.loader = WebView.class.getClassLoader();
        SupportLibReflectionUtil.features = FEATURES;
        SupportLibReflectionUtil.starts = 0;
        SupportLibReflectionUtil.throwOnStart = false;
        SupportLibReflectionUtil.featureEntered = null;
        SupportLibReflectionUtil.featureRelease = null;
        SupportLibReflectionUtil.multiProcess = true;
        SupportLibReflectionUtil.defaultProfileAvailable = true;
        SupportLibReflectionUtil.staticsReads = 0;
        SupportLibReflectionUtil.profileStoreReads = 0;
        SupportLibReflectionUtil.connectionFailure = null;
        SupportLibReflectionUtil.connectionLinkageFailure = null;
        SupportLibReflectionUtil.preconnections.clear();
        SupportLibReflectionUtil.enqueuedConnections.clear();
        SupportLibReflectionUtil.profileNames.clear();
    }
    private static void discoverOnWorker() throws Exception {
        Thread worker = new Thread(() -> {
            WebViewGlueCommunicator.getFactoryForStartup();
            WebViewNavigationWarmupCompat.onStartupBoundaryReady();
        });
        worker.start();
        worker.join(3000);
        check(!worker.isAlive(), "background discovery completed");
    }

    public static void main(String[] args) throws Exception {
        Looper.getMainLooper();
        reset();
        WebViewNavigationWarmupCompat.enqueueForStartup("https://GOOGLE.com:443/search?q=private#token");
        check(WebView.loads == 0 && SupportLibReflectionUtil.profileStoreReads == 0,
                "cold hint does not load provider or query profile store");
        check(!WebViewNavigationWarmupCompat.preconnectForNavigation("https://early.example/path"),
                "normal hint also never starts provider");
        WebViewNavigationWarmupCompat.getDiagnosticReport();
        check(WebView.loads == 0, "diagnostic does not initialize provider");
        discoverOnWorker();
        check(SupportLibReflectionUtil.enqueuedConnections.isEmpty(), "worker never invokes UI-only preconnect");
        drain();
        check(SupportLibReflectionUtil.enqueuedConnections.size() == 1
                && SupportLibReflectionUtil.enqueuedConnections.get(0).equals("https://google.com"),
                "dispatch contains only normalized origin");
        check(SupportLibReflectionUtil.profileNames.size() == 1
                && SupportLibReflectionUtil.profileNames.get(0).equals("Default"),
                "only existing default profile retrieved");
        check(!WebViewNavigationWarmupCompat.preconnectForNavigation("https://google.com/other?q=secret"),
                "touch/click duplicate shares warm connection");
        check(!WebViewNavigationWarmupCompat.getDiagnosticReport().contains("google"),
                "diagnostics do not retain origin");
        done("no initialization, UI dispatch, origin privacy, default profile and duplicate hint");

        reset();
        WebViewNavigationWarmupCompat.enqueueForStartup("https://older.example/full");
        WebViewNavigationWarmupCompat.enqueueForStartup("https://chosen.example/final?q=secret");
        discoverOnWorker(); drain();
        check(SupportLibReflectionUtil.enqueuedConnections.size() == 1
                && SupportLibReflectionUtil.enqueuedConnections.get(0).equals("https://chosen.example"),
                "only latest confirmed destination warms");
        reset();
        WebViewNavigationWarmupCompat.enqueueForStartup("https://cancelled.example/path");
        WebViewNavigationWarmupCompat.cancelStartupPreconnect();
        discoverOnWorker(); drain();
        check(SupportLibReflectionUtil.enqueuedConnections.isEmpty(), "cancelled hint does not connect");
        done("latest navigation and cancellation");

        reset();
        SupportLibReflectionUtil.features = new String[] { "PRECONNECT", "MULTI_PROFILE", "MULTI_PROCESS_QUERY" };
        WebViewNavigationWarmupCompat.enqueueForStartup("https://no-enqueue.example/path");
        discoverOnWorker(); drain();
        check(SupportLibReflectionUtil.profileStoreReads == 0
                && SupportLibReflectionUtil.preconnections.isEmpty(),
                "older provider must not sync-init via profile store fallback");
        check(WebViewNavigationWarmupCompat.preconnectForNavigation("https://initialized.example:8443/path?a=b"),
                "initialized provider can use PRECONNECT only");
        check(SupportLibReflectionUtil.preconnections.get(0).equals("https://initialized.example:8443"),
                "explicit nondefault port preserved");
        done("PRECONNECT-only provider skipped during startup");

        for (String[] features : new String[][] {
                { "ENQUEUE_PRECONNECT", "PRECONNECT", "MULTI_PROCESS_QUERY" },
                { "ENQUEUE_PRECONNECT", "PRECONNECT", "MULTI_PROFILE" },
                {} }) {
            reset(); SupportLibReflectionUtil.features = features;
            discoverOnWorker(); drain();
            check(!WebViewNavigationWarmupCompat.preconnectForNavigation("https://unsupported.example"), "missing requirement");
            check(SupportLibReflectionUtil.profileStoreReads == 0, "unsupported provider not queried");
        }
        reset(); SupportLibReflectionUtil.multiProcess = false;
        discoverOnWorker(); drain();
        check(!WebViewNavigationWarmupCompat.preconnectForNavigation("https://single-process.example"), "single process unsupported");
        check(SupportLibReflectionUtil.profileStoreReads == 0, "single process skips profile store");
        done("AndroidX multi-profile prerequisites checked before profile access");

        reset(); discoverOnWorker(); drain();
        SupportLibReflectionUtil.defaultProfileAvailable = false;
        check(!WebViewNavigationWarmupCompat.preconnectForNavigation("https://no-profile.example"), "no default profile safe");
        SupportLibReflectionUtil.defaultProfileAvailable = true;
        SupportLibReflectionUtil.connectionFailure = new IllegalArgumentException("unsupported");
        check(!WebViewNavigationWarmupCompat.preconnectForNavigation("https://rejected.example"), "runtime failure safe");
        SupportLibReflectionUtil.connectionFailure = null;
        SupportLibReflectionUtil.connectionLinkageFailure = new AbstractMethodError("old boundary");
        check(!WebViewNavigationWarmupCompat.preconnectForNavigation("https://incompatible.example"), "boundary mismatch safe");
        SupportLibReflectionUtil.connectionLinkageFailure = null;
        check(WebViewNavigationWarmupCompat.preconnectForNavigation("http://normal.example:80/path"), "navigation still usable after optional failures");
        done("missing profile, provider rejection and linkage failures leave navigation usable");

        reset();
        String[] invalid = { null, "", "https://user:secret@example.com/path", "https://user@example.com",
                "javascript:alert(1)", "data:text/html,secret", "file:///private", "about:blank",
                "//example.com/path", "https://", "https://host:65536/path", "https://host:0/path",
                "https://exa mple.com", "https://host/\nsecret", "https://*.example.com", "https://host/%zz",
                "https://example.com/?" + "a".repeat(16384) };
        for (String url : invalid) {
            check(WebViewNavigationWarmupCompat.originOf(url) == null, "reject malformed or nonnetwork origin");
            WebViewNavigationWarmupCompat.enqueueForStartup(url);
            check(!WebViewNavigationWarmupCompat.preconnectForNavigation(url), "invalid input never hinted");
        }
        check(WebView.loads == 0 && SupportLibReflectionUtil.profileStoreReads == 0,
                "invalid destinations never touch provider");
        check("https://[::1]:444".equals(WebViewNavigationWarmupCompat.originOf("https://[::1]:444/a?secret")), "IPv6 origin safe");
        done("credentials, local schemes, malformed addresses and private URL parts rejected or stripped");

        reset();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SupportLibReflectionUtil.featureEntered = entered;
        SupportLibReflectionUtil.featureRelease = release;
        Thread discovering = new Thread(WebViewGlueCommunicator::getFactoryForStartup);
        discovering.start();
        check(entered.await(2, TimeUnit.SECONDS), "provider discovery owns initialization lock");
        try {
            WebViewNavigationWarmupCompat.enqueueForStartup("https://parallel.example/path");
            check(WebViewNavigationWarmupCompat.getDiagnosticReport().contains("not-ready"),
                    "incomplete discovery not treated as ready");
            check(SupportLibReflectionUtil.profileStoreReads == 0, "main-thread hint returns while discovery lock held");
        } finally { release.countDown(); }
        discovering.join(3000);
        check(!discovering.isAlive(), "discovery released");
        WebViewNavigationWarmupCompat.onStartupBoundaryReady(); drain();
        check(SupportLibReflectionUtil.enqueuedConnections.size() == 1, "pending hint delivered after discovery");
        done("concurrent provider discovery never blocks UI hint");

        reset(); discoverOnWorker(); drain();
        Thread wrongThread = new Thread(() -> {
            WebViewNavigationWarmupCompat.enqueueForStartup("https://wrong-thread.example");
            check(!WebViewNavigationWarmupCompat.preconnectForNavigation("https://wrong-thread.example"), "wrong thread rejected");
        });
        wrongThread.start(); wrongThread.join(3000);
        check(SupportLibReflectionUtil.profileStoreReads == 0, "UI-only methods not called from worker");
        done("UI-only protocol thread enforced");

        reset();
        WebViewNavigationWarmupCompat.enqueueForStartup("https://startup-hook.example/path");
        WebViewStartupCompat.startUpWebView(task -> {
            Thread worker = new Thread(task); worker.start();
            try { worker.join(3000); } catch (InterruptedException e) { throw new AssertionError(e); }
        }, new WebViewStartupCompat.Callback() {
            public void onSuccess(WebViewStartupCompat.Result result) {}
            public void onFailure(Throwable error) { throw new AssertionError(error); }
        });
        drain();
        check(SupportLibReflectionUtil.starts == 1 && SupportLibReflectionUtil.enqueuedConnections.size() == 1,
                "actual startup facade flushes pending origin through enqueue protocol");
        done("startup integration hook");

        reset();
        WebViewNavigationWarmupCompat.enqueueForStartup("https://www.baidu.com/s?wd=private-search#private-fragment");
        check(WebView.loads == 0 && SupportLibReflectionUtil.profileStoreReads == 0,
                "resource batch does not trigger provider discovery");
        discoverOnWorker(); drain();
        check(SupportLibReflectionUtil.enqueuedConnections.size() == 1
                && SupportLibReflectionUtil.enqueuedConnections.get(0).equals("https://www.baidu.com"),
                "startup queues only accepted main origin before resource policies are loaded");
        check(SupportLibReflectionUtil.profileStoreReads == 1 && SupportLibReflectionUtil.staticsReads == 1,
                "batch obtains one Default profile and one mode query");
        check(WebViewNavigationWarmupCompat.preconnectForNavigation("https://www.baidu.com/s?wd=second-search",
                null, origin -> true), "ready browser connects permitted critical resource batch");
        check(SupportLibReflectionUtil.preconnections.size() == 3
                && SupportLibReflectionUtil.preconnections.get(0).equals("https://gss3.bdstatic.com")
                && SupportLibReflectionUtil.preconnections.get(1).equals("https://mms-static.cdn.bcebos.com")
                && SupportLibReflectionUtil.preconnections.get(2).equals("https://ms.bdstatic.com"),
                "main origin remains deduplicated while three permitted script/style origins connect");
        check(!WebViewNavigationWarmupCompat.preconnectForNavigation("https://www.baidu.com/s?wd=other-search"),
                "startup-to-navigation repetition deduplicated per origin");
        check(SupportLibReflectionUtil.profileStoreReads == 2,
                "fully recent batch skips profile access");
        check(WebViewNavigationWarmupCompat.preconnectForNavigation("https://interleaved.example/content"),
                "independent confirmed site can connect between batch calls");
        check(!WebViewNavigationWarmupCompat.preconnectForNavigation("https://www.baidu.com/s?wd=third-search"),
                "intervening origin does not evict four batch connections");
        check(SupportLibReflectionUtil.enqueuedConnections.size() == 1
                && SupportLibReflectionUtil.preconnections.size() == 4,
                "batch and intervening navigation do not duplicate any resource connection");
        done("bounded critical resource batch and independent origin deduplication");

        String[] noExtraSites = { "https://www.baidu.com/", "https://www.baidu.com/other",
                "https://www.baidu.com/s/", "https://www.baidu.com:8443/s", "http://www.baidu.com/s",
                "https://m.baidu.com/s", "https://www.baidu.com.evil.example/s", "https://evil.example/?next=https://www.baidu.com/s",
                "https://www.google.com/", "https://m.youtube.com/watch?v=private", "https://www.bing.com/other?q=private",
                "https://arbitrary.example/page" };
        for (String url : noExtraSites) {
            String[] origins = WebViewNavigationWarmupCompat.originsForNavigation(url);
            check(origins.length == 1 && origins[0].equals(WebViewNavigationWarmupCompat.originOf(url)),
                    "unobserved page/site gets only its confirmed main origin");
        }
        check(WebViewNavigationWarmupCompat.originsForNavigation("https://user:secret@www.baidu.com/s") == null,
                "credential-bearing Baidu URL gets no batch");
        done("static resource hint scope cannot spread to other pages, ports or lookalike domains");

        reset();
        SupportLibReflectionUtil.features = new String[] { "PRECONNECT", "MULTI_PROFILE", "MULTI_PROCESS_QUERY" };
        WebViewNavigationWarmupCompat.enqueueForStartup("https://www.baidu.com/s?wd=unsupported");
        discoverOnWorker(); drain();
        check(SupportLibReflectionUtil.enqueuedConnections.isEmpty()
                && SupportLibReflectionUtil.preconnections.isEmpty()
                && SupportLibReflectionUtil.profileStoreReads == 0,
                "unsupported enqueue skips complete resource batch without synchronous fallback");
        reset();
        WebViewNavigationWarmupCompat.enqueueForStartup("https://www.baidu.com/s?wd=cancelled");
        WebViewNavigationWarmupCompat.cancelStartupPreconnect();
        discoverOnWorker(); drain();
        check(SupportLibReflectionUtil.enqueuedConnections.isEmpty()
                && SupportLibReflectionUtil.profileStoreReads == 0,
                "cancellation removes main and all dependency connection hints");
        done("batch unsupported and cancellation paths keep zero connection side effects");

        reset(); discoverOnWorker(); drain();
        for (int i = 0; i < 24; i++) {
            String url = "https://bounded-" + i + ".example/confirmed";
            check(WebViewNavigationWarmupCompat.preconnectForNavigation(url), "new confirmed origin connects");
            check(!WebViewNavigationWarmupCompat.preconnectForNavigation(url), "latest origin deduplicated after capacity pressure");
        }
        check(SupportLibReflectionUtil.preconnections.size() == 24,
                "capacity pressure does not duplicate immediate navigation hints");
        done("bounded origin history retains duplicate protection under pressure");

        checkOrigins("https://www.google.com/search?q=private", "https://www.google.com",
                "https://www.gstatic.com", "https://encrypted-tbn0.gstatic.com", "https://ssl.gstatic.com");
        checkOrigins("https://m.youtube.com/?private=secret", "https://m.youtube.com",
                "https://i.ytimg.com", "https://yt3.ggpht.com", "https://fonts.gstatic.com",
                "https://fonts.googleapis.com");
        checkOrigins("https://www.bing.com/search?q=private", "https://www.bing.com", "https://th.bing.com");
        checkOrigins("https://www.baidu.com/s?wd=private", "https://www.baidu.com", "https://gss3.bdstatic.com",
                "https://mms-static.cdn.bcebos.com", "https://ms.bdstatic.com");
        checkOrigins("https://www.google.com:8443/search?q=private", "https://www.google.com:8443");
        checkOrigins("https://www.google.com.evil.example/search?q=private", "https://www.google.com.evil.example");
        checkOrigins("https://www.bing.com/search/", "https://www.bing.com");
        checkOrigins("https://m.youtube.com.evil.example/", "https://m.youtube.com.evil.example");
        done("only exact observed common entry points receive fixed render-resource batches");

        String[] learned = { null, "https://user:secret@bad.example", "https://bad.example/path.js",
                "https://bad.example/?secret=query", "https://bad.example/#secret", "http://mixed.example",
                "javascript:alert(1)", "//no-scheme.example", "https://bad.example:65536",
                "https://learned-main.example", "https://STATIC.example:443/", "https://static.example",
                "https://font.example", "https://image.example", "https://script.example", "https://over-limit.example" };
        String[] combined = WebViewNavigationWarmupCompat.originsForNavigation("https://learned-main.example/page?q=private", learned);
        check(combined.length == 5 && combined[0].equals("https://learned-main.example")
                && combined[1].equals("https://static.example") && combined[2].equals("https://font.example")
                && combined[3].equals("https://image.example") && combined[4].equals("https://script.example"),
                "learned origins are strict, same scheme, deduplicated, normalized and bounded with main origin");
        String[] fixedAndLearned = WebViewNavigationWarmupCompat.originsForNavigation("https://m.youtube.com/",
                new String[] { "https://i.ytimg.com", "https://one-extra.example", "https://too-many.example" });
        check(java.util.Arrays.equals(fixedAndLearned, new String[] { "https://m.youtube.com",
                "https://i.ytimg.com", "https://one-extra.example", "https://too-many.example",
                "https://yt3.ggpht.com" }),
                "completed learned dependencies have priority and fixed hints fill remaining slots only");
        check(WebViewNavigationWarmupCompat.originsForNavigation("https://user@bad.example/", learned) == null,
                "invalid navigation cannot connect learned origins");
        String[] sameHttp = WebViewNavigationWarmupCompat.originsForNavigation("http://http-main.example/",
                new String[] { "https://mixed.example", "http://plain.example:80/", "http://[::1]:8080" });
        check(sameHttp.length == 4 && sameHttp[1].equals("https://mixed.example")
                && sameHttp[2].equals("http://plain.example")
                && sameHttp[3].equals("http://[::1]:8080"), "HTTP navigation permits HTTPS dependencies and valid IPv6 port");
        String[] noDowngrade = WebViewNavigationWarmupCompat.originsForNavigation("https://secure-main.example/",
                new String[] { "http://insecure-static.example", "https://secure-static.example" });
        check(noDowngrade.length == 2 && noDowngrade[1].equals("https://secure-static.example"),
                "HTTPS navigation never connects insecure dependency");
        done("learned resource-origin boundary strips no private URLs and caps combined batch at five");

        String[] observedYoutube = { "https://yt3.ggpht.com", "https://www.google.com", "https://i.ytimg.com" };
        String[] expectedYoutube = { "https://m.youtube.com", "https://yt3.ggpht.com",
                "https://www.google.com", "https://i.ytimg.com", "https://fonts.gstatic.com" };
        check(java.util.Arrays.equals(WebViewNavigationWarmupCompat.originsForNavigation(
                "https://m.youtube.com/", observedYoutube), expectedYoutube),
                "observed YouTube image origin survives fixed dependency capacity pressure");
        check(java.util.Arrays.equals(WebViewNavigationWarmupCompat.preconnectOriginsForNavigation(
                "https://m.youtube.com/", observedYoutube, origin -> true), expectedYoutube),
                "document preconnect helper shares learned-first priority and five-origin bound");
        check(java.util.Arrays.equals(WebViewNavigationWarmupCompat.preconnectOriginsForNavigation(
                "https://m.youtube.com/", new String[] { "https://denied.example", "https://yt3.ggpht.com",
                        "https://www.google.com", "https://i.ytimg.com" },
                origin -> !origin.equals("https://denied.example")), expectedYoutube),
                "denied learned origin leaves capacity for the first allowed fixed dependency");
        done("completed YouTube image dependencies receive native and document hint slots before defaults");

        reset(); discoverOnWorker(); drain();
        check(WebViewNavigationWarmupCompat.preconnectForNavigation("https://learned-send.example/page?q=private",
                new String[] { "https://learned-static.example", "https://learned-font.example" }),
                "public overload dispatches actual observed resources");
        check(SupportLibReflectionUtil.preconnections.size() == 3
                && SupportLibReflectionUtil.profileStoreReads == 1, "combined batch uses one profile lookup");
        check(!WebViewNavigationWarmupCompat.preconnectForNavigation("https://learned-send.example/other",
                new String[] { "https://learned-static.example", "https://learned-font.example" }),
                "learned batch receives per-origin deduplication");
        done("public learned-origin overload shares optional dispatch and duplicate protection");

        java.util.List<String> policyChecked = new java.util.ArrayList<>();
        String[] policySelected = WebViewNavigationWarmupCompat.originsForNavigation("https://www.google.com/search?q=private",
                new String[] { "https://learned-blocked.example", "https://learned-allowed.example" }, origin -> {
                    policyChecked.add(origin);
                    return !(origin.equals("https://www.gstatic.com") || origin.equals("https://learned-blocked.example"));
                });
        check(java.util.Arrays.equals(policySelected, new String[] { "https://www.google.com",
                "https://learned-allowed.example", "https://encrypted-tbn0.gstatic.com", "https://ssl.gstatic.com" }),
                "current resource policy filters fixed and learned dependencies before connection");
        check(policyChecked.size() == 5 && !policyChecked.contains("https://www.google.com"),
                "only extra origins pass policy callback, accepted main origin preserved");
        String[] rejectedPolicy = WebViewNavigationWarmupCompat.originsForNavigation("https://m.youtube.com/",
                new String[] { "https://extra-policy.example" }, origin -> { throw new IllegalStateException("unavailable policy"); });
        check(rejectedPolicy.length == 1 && rejectedPolicy[0].equals("https://m.youtube.com"),
                "unavailable policy rejects optional origins and keeps confirmed main navigation");
        reset(); discoverOnWorker(); drain();
        check(WebViewNavigationWarmupCompat.preconnectForNavigation("https://www.bing.com/search?q=private",
                new String[] { "https://learned-denied.example" }, origin -> false),
                "public filtered overload keeps accepted main connection");
        check(SupportLibReflectionUtil.preconnections.size() == 1
                && SupportLibReflectionUtil.preconnections.get(0).equals("https://www.bing.com"),
                "denied extras never reach actual provider dispatch");
        done("browser ad and HTTPS policy gates every fixed/learned extra without changing accepted navigation");
        System.out.println("PASS " + cases + " connection warmup cases");
    }

    private static void checkOrigins(String url, String... expected) {
        String[] actual = WebViewNavigationWarmupCompat.originsForNavigation(url);
        check(java.util.Arrays.equals(actual, expected), "exact common origin/path mapping: " + url);
    }
}
