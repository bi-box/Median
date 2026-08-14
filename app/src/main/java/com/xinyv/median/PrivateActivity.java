package com.xinyv.median;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Isolated-process private browser for Android 9+. Its WebView profile never shares the normal
 * cookie/database directory and is erased when the task closes.
 */
public final class PrivateActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 501;
    private static final String HOME = "https://median-private.invalid/";
    private static final String HOME_TOKEN = UrlCleaner.randomToken();
    private static final long INITIAL_NAVIGATION_ACK_MS = 450L;
    private static final byte[] EMPTY = new byte[0];
    private static boolean privateDataDirectoryConfigured;
    private final AdBlockEngine adBlock = new AdBlockEngine();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable cookieResetFallback = new Runnable() {
        @Override public void run() { markCookieResetComplete(); }
    };
    private ExecutorService startupExecutor;
    private FilterSubscriptionStore filterStore;
    private FrameLayout webContainer;
    private WebView webView;
    private EditText address;
    private ProgressBar progress;
    private volatile String pageHost = "";
    private boolean trustedHome;
    private boolean activityResumed;
    private volatile boolean activityDestroyed;
    private final StartupReadiness startupReadiness = new StartupReadiness(
            StartupReadiness.VIEW | StartupReadiness.FILTERS |
                    StartupReadiness.COOKIES | StartupReadiness.RESUMED);
    private final InitialNavigationGuard initialNavigationGuard = new InitialNavigationGuard();
    private boolean filterRulesReady;
    private boolean cookieResetStarted;
    private boolean cookieResetComplete;
    private boolean privateProfileReady;
    private String pendingInput;
    private String pendingEngine = "google";
    private Object predictiveBackCallback;
    private ValueCallback<Uri[]> fileChooserCallback;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (android.os.Build.VERSION.SDK_INT < 28) { finish(); return; }
        if (!ensurePrivateDataDirectory()) {
            Toast.makeText(this, "无法建立隔离的 WebView 数据目录，隐私窗口已关闭", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        getWindow().setStatusBarColor(Color.rgb(30, 32, 36));
        getWindow().setNavigationBarColor(Color.rgb(30, 32, 36));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        startupExecutor = BackgroundExecutor.create(2, 4, "median-private-startup", false);
        beginFilterRuleLoad();
        buildUi();
        registerPredictiveBack();
        initializePrivateWebView();
    }

    private static synchronized boolean ensurePrivateDataDirectory() {
        if (privateDataDirectoryConfigured) return true;
        try {
            WebView.setDataDirectorySuffix("median_private");
            privateDataDirectoryConfigured = true;
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private void beginFilterRuleLoad() {
        try {
            startupExecutor.execute(new Runnable() {
                @Override public void run() {
                    FilterSubscriptionStore loadedStore = null;
                    String customRules = getSharedPreferences("median_browser_v2", MODE_PRIVATE)
                            .getString("custom_filter_rules", "");
                    try {
                        loadedStore = new FilterSubscriptionStore(getApplicationContext());
                        adBlock.updateRules(customRules, loadedStore.readEnabledRuleSources());
                    } catch (RuntimeException loadError) {
                        if (loadedStore != null) loadedStore.close();
                        loadedStore = null;
                        adBlock.updateRules(customRules, Collections.<String>emptyList());
                    }
                    final FilterSubscriptionStore completedStore = loadedStore;
                    if (activityDestroyed) {
                        if (completedStore != null) completedStore.close();
                        return;
                    }
                    handler.post(new Runnable() {
                        @Override public void run() {
                            if (activityDestroyed || isFinishing()) {
                                if (completedStore != null) completedStore.close();
                                return;
                            }
                            filterStore = completedStore;
                            filterRulesReady = true;
                            startupReadiness.set(StartupReadiness.FILTERS, true);
                            completePrivateProfileReset();
                        }
                    });
                }
            });
        } catch (RejectedExecutionException ignored) {
            if (!activityDestroyed) finishAndRemoveTask();
        }
    }

    /** Creates the private WebView directly, without an optional provider-callback gate. */
    private void initializePrivateWebView() {
        if (activityDestroyed || isFinishing() || webView != null || webContainer == null) return;
        WebView created = null;
        try {
            created = new WebView(this);
            created.setBackgroundColor(Color.rgb(18, 20, 23));
            created.setFocusable(true);
            created.setFocusableInTouchMode(true);
            created.setClickable(true);
            created.setVerticalScrollBarEnabled(false);
            webView = created;
            configure(created);
            webContainer.removeAllViews();
            webContainer.addView(created, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (activityResumed) created.onResume();
            startupReadiness.set(StartupReadiness.VIEW, true);
            beginCookieReset();
            completePrivateProfileReset();
        } catch (RuntimeException creationError) {
            webView = null;
            if (created != null) try { created.destroy(); } catch (RuntimeException ignored) {}
            Toast.makeText(this, "网页引擎初始化失败，隐私窗口已关闭", Toast.LENGTH_LONG).show();
            finishAndRemoveTask();
        }
    }

    private void beginCookieReset() {
        if (cookieResetStarted || webView == null || activityDestroyed) return;
        cookieResetStarted = true;
        try {
            CookieManager.getInstance().removeAllCookies(new ValueCallback<Boolean>() {
                @Override public void onReceiveValue(Boolean value) { markCookieResetComplete(); }
            });
        } catch (RuntimeException ignored) {
            markCookieResetComplete();
            return;
        }
        // Some vendor WebView builds lose the callback; the idempotent fallback prevents a blank page.
        handler.postDelayed(cookieResetFallback, 1500L);
    }

    private void markCookieResetComplete() {
        if (activityDestroyed || cookieResetComplete) return;
        cookieResetComplete = true;
        startupReadiness.set(StartupReadiness.COOKIES, true);
        handler.removeCallbacks(cookieResetFallback);
        completePrivateProfileReset();
    }

    private void completePrivateProfileReset() {
        if (privateProfileReady || activityDestroyed || isFinishing() || webView == null ||
                !cookieResetComplete || !filterRulesReady || !startupReadiness.claimPost()) return;
        Runnable complete = new Runnable() {
            @Override public void run() {
                if (!startupReadiness.begin() || activityDestroyed || isFinishing() || webView == null) return;
                finishPrivateProfileReset();
            }
        };
        postAfterAttachedFrame(complete);
    }

    private void postAfterAttachedFrame(final Runnable action) {
        final View target = webContainer;
        if (target == null) {
            handler.post(action);
        } else if (target.isAttachedToWindow()) {
            target.postOnAnimation(action);
        } else {
            target.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                    view.removeOnAttachStateChangeListener(this);
                    view.postOnAnimation(action);
                }
                @Override public void onViewDetachedFromWindow(View view) {}
            });
        }
    }

    private void finishPrivateProfileReset() {
        privateProfileReady = true;
        CookieManager.getInstance().flush();
        clearPrivateProfileMetadata();
        webView.clearCache(true);
        String queuedInput = pendingInput;
        String queuedEngine = pendingEngine;
        pendingInput = null;
        pendingEngine = "google";
        if (queuedInput == null || queuedInput.trim().length() == 0) showHome();
        else loadInput(queuedInput, queuedEngine);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 20, 23));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(7), dp(8), dp(7));
        TextView mask = button("◉", "隐私浏览");
        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("隐私搜索或输入网址");
        address.setHintTextColor(Color.rgb(150, 155, 162));
        address.setTextColor(Color.WHITE);
        address.setTextSize(15f);
        address.setBackgroundColor(Color.rgb(43, 46, 51));
        address.setPadding(dp(15), 0, dp(15), 0);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        TextView close = button("×", "关闭隐私浏览");
        top.addView(mask, new LinearLayout.LayoutParams(dp(42), dp(44)));
        top.addView(address, new LinearLayout.LayoutParams(0, dp(44), 1f));
        top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(138, 180, 248)));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(Color.rgb(18, 20, 23));
        LinearLayout startupSurface = new LinearLayout(this);
        startupSurface.setOrientation(LinearLayout.VERTICAL);
        startupSurface.setGravity(Gravity.CENTER);
        TextView startupName = new TextView(this);
        startupName.setText("median");
        startupName.setTextColor(Color.WHITE);
        startupName.setTextSize(23f);
        startupName.setGravity(Gravity.CENTER);
        startupName.setLetterSpacing(0.08f);
        startupSurface.addView(startupName, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView startupStatus = new TextView(this);
        startupStatus.setText("正在建立独立隐私会话…");
        startupStatus.setTextColor(Color.rgb(150, 155, 162));
        startupStatus.setTextSize(12f);
        startupStatus.setGravity(Gravity.CENTER);
        startupStatus.setPadding(0, dp(8), 0, 0);
        startupSurface.addView(startupStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        webContainer.addView(startupSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(webContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        TextView back = button("‹", "后退");
        TextView forward = button("›", "前进");
        TextView home = button("⌂", "隐私主页");
        TextView reload = button("↻", "刷新");
        TextView info = button("⋯", "隐私说明");
        TextView[] actions = new TextView[] { back, forward, home, reload, info };
        for (TextView action : actions) bottom.addView(action, new LinearLayout.LayoutParams(0, dp(52), 1f));
        root.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        setContentView(root);
        installSystemBarInsets(root);

        address.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    loadInput(address.getText().toString());
                    address.clearFocus();
                    return true;
                }
                return false;
            }
        });
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finishAndRemoveTask(); } });
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (webView != null && webView.canGoBack()) webView.goBack(); } });
        forward.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (webView != null && webView.canGoForward()) webView.goForward(); } });
        home.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showHome(); } });
        reload.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (webView != null && privateProfileReady) webView.reload(); } });
        info.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(PrivateActivity.this).setTitle("独立隐私会话")
                        .setMessage("此窗口运行在独立进程和独立 WebView 数据目录中，不记录 Median 历史、书签或标签会话；仍会读取普通窗口已下载的过滤订阅，但不运行持久用户脚本。关闭后会清除 Cookie、缓存及网站存储。下载已禁用，避免系统下载管理器留下记录。运营商、网站和网络管理者仍可能看到访问活动。")
                        .setPositiveButton("知道了", null).show();
            }
        });
    }

    private void installSystemBarInsets(final View target) {
        if (target == null || android.os.Build.VERSION.SDK_INT < 30) return;
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        android.view.WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) controller.setSystemBarsAppearance(0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        target.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public android.view.WindowInsets onApplyWindowInsets(View view, android.view.WindowInsets insets) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            }
        });
        target.requestApplyInsets();
    }

    private TextView button(String text, String description) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(220, 223, 228));
        view.setTextSize(25f);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(description);
        return view;
    }

    private void configure(final WebView target) {
        WebSettings settings = target.getSettings();
        WebViewPolicy.applySecureDefaults(settings, WebSettings.LOAD_NO_CACHE);
        settings.setSupportMultipleWindows(false);
        String mobileUa = WebViewPolicy.mobileUserAgent(settings.getUserAgentString());
        if (!mobileUa.equals(settings.getUserAgentString())) settings.setUserAgentString(mobileUa);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(target, false);

        EdgeNavigationController.attach(target, new EdgeNavigationController.Callback() {
            @Override public boolean canGoBack() { return webView == target && target.canGoBack(); }
            @Override public boolean canGoForward() { return webView == target && target.canGoForward(); }
            @Override public void goBack() { if (webView == target) target.goBack(); }
            @Override public void goForward() { if (webView == target) target.goForward(); }
        });

        target.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || !request.isForMainFrame()) return false;
                String url = request.getUrl().toString();
                watchInitialNetworkNavigation(view, url);
                return handle(url);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                watchInitialNetworkNavigation(view, url);
                return handle(url);
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if (activityDestroyed || view != webView) return;
                if (InternalNavigationPolicy.shouldClearHomeTrust(isHome(url), url)) trustedHome = false;
                pageHost = host(url);
                address.setText(isHome(url) ? "" : url);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String accept = request == null ? "" : request.getRequestHeaders().get("Accept");
                return request != null && adBlock.shouldBlock(request.getUrl(), pageHost, accept, request.isForMainFrame())
                        ? new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(EMPTY)) : null;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return adBlock.shouldBlock(url, pageHost)
                        ? new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(EMPTY)) : null;
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                if (!activityDestroyed && view == webView)
                    Toast.makeText(PrivateActivity.this, "证书验证失败，已停止加载", Toast.LENGTH_SHORT).show();
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (activityDestroyed || view != webView) return;
                initialNavigationGuard.acknowledge(url);
                if (isHome(url)) verifyTrustedHome(view);
                String css = adBlock.cosmeticCssForHost(pageHost);
                if (css.length() > 0) view.evaluateJavascript("(function f(){var p=document.head||document.documentElement;if(!p){setTimeout(f,30);return;}var s=document.getElementById('__median_adblock');if(!s){s=document.createElement('style');s.id='__median_adblock';s.textContent=" + JSONObject.quote(css) + ";p.appendChild(s);}})();", null);
            }
        });
        target.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsPrompt(final WebView view, String url, String message,
                                                final String defaultValue, JsPromptResult result) {
                if (HOME_TOKEN.equals(message)) {
                    boolean accepted = result != null && view == webView && trustedHome &&
                            isHome(view.getUrl()) && defaultValue != null &&
                            defaultValue.startsWith("median://");
                    if (result != null) result.confirm("");
                    if (accepted) view.post(new Runnable() {
                        @Override public void run() {
                            if (!activityDestroyed && view == webView && trustedHome && isHome(view.getUrl()))
                                handle(defaultValue);
                        }
                    });
                    return true;
                }
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }
            @Override public void onProgressChanged(WebView view, int value) {
                if (activityDestroyed || view != webView) return;
                if (value > 10) initialNavigationGuard.acknowledge(view.getUrl());
                progress.setVisibility(value < 100 ? View.VISIBLE : View.GONE);
                progress.setProgress(value);
            }
            @Override public void onPermissionRequest(android.webkit.PermissionRequest request) { if (request != null) request.deny(); }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                try { startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST); return true; }
                catch (Exception e) { fileChooserCallback = null; Toast.makeText(PrivateActivity.this, "文件选择器不可用", Toast.LENGTH_SHORT).show(); return false; }
            }
        });
        target.setDownloadListener(new android.webkit.DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent, String disposition, String mime, long size) {
                Toast.makeText(PrivateActivity.this, "隐私窗口不写入系统下载记录", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean handle(String url) {
        if (url == null) return false;
        Uri parsed;
        try { parsed = Uri.parse(url); } catch (RuntimeException ignored) { return true; }
        if ("median".equalsIgnoreCase(parsed.getScheme())) {
            if (!InternalNavigationPolicy.canHandleCommand(trustedHome, false)) return true;
            String action = parsed.getHost();
            if ("search".equals(action))
                loadInput(parsed.getQueryParameter("q"), parsed.getQueryParameter("engine"));
            else if ("bookmarks".equals(action))
                Toast.makeText(this, "隐私窗口不读取或保存书签", Toast.LENGTH_SHORT).show();
            return true;
        }
        String scheme = parsed.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            try { NetworkSecurity.parseHttpUrl(url); return false; }
            catch (Exception invalid) {
                Toast.makeText(this, "已阻止无效或含凭据的网址", Toast.LENGTH_SHORT).show();
                return true;
            }
        }
        if ("about:blank".equals(url)) return false;
        if (url.startsWith("data:") || url.startsWith("about:")) {
            Toast.makeText(this, "已阻止网页跳转到不透明内部地址", Toast.LENGTH_SHORT).show();
            return true;
        }
        confirmExternal(url);
        return true;
    }

    private void loadInput(String input) {
        loadInput(input, "google");
    }

    private void loadInput(String input, String engine) {
        String value = input == null ? "" : input.trim();
        if (value.length() == 0) return;
        if (webView == null || !privateProfileReady) {
            pendingInput = value;
            pendingEngine = engine == null ? "google" : engine;
            return;
        }
        trustedHome = false;
        if (OmniboxInput.isExplicitHttpUrl(value)) {
            try { loadNetworkUrl(UrlCleaner.cleanTracking(NetworkSecurity.parseHttpUrl(value).toString())); }
            catch (Exception invalid) { Toast.makeText(this, "网址无效或包含不安全的凭据", Toast.LENGTH_SHORT).show(); }
        } else if (OmniboxInput.looksLikeWebAddress(value)) {
            try { loadNetworkUrl(UrlCleaner.cleanTracking(NetworkSecurity.parseHttpsUrl(OmniboxInput.withDefaultHttpsScheme(value)).toString())); }
            catch (Exception invalid) { search(value, engine); }
        } else search(value, engine);
    }

    private void search(String query, String engine) {
        if (webView == null || !privateProfileReady) return;
        try {
            String encoded = URLEncoder.encode(query == null ? "" : query, "UTF-8");
            if ("baidu".equals(engine)) loadNetworkUrl("https://www.baidu.com/s?wd=" + encoded);
            else if ("bing".equals(engine)) loadNetworkUrl("https://www.bing.com/search?q=" + encoded);
            else loadNetworkUrl("https://www.google.com/search?q=" + encoded);
        } catch (Exception ignored) {}
    }

    private void loadNetworkUrl(String url) {
        if (webView == null || !privateProfileReady || !isNetworkUrl(url)) return;
        webView.getSettings().setBlockNetworkLoads(false);
        watchInitialNetworkNavigation(webView, url);
        webView.loadUrl(url);
    }

    private void watchInitialNetworkNavigation(final WebView target, String url) {
        if (target == null || target != webView || !privateProfileReady || !isNetworkUrl(url)) return;
        try { NetworkSecurity.parseHttpUrl(url); }
        catch (Exception invalid) { return; }
        long generation = initialNavigationGuard.arm(url);
        if (generation != 0L) scheduleInitialNavigationRetry(target, generation, INITIAL_NAVIGATION_ACK_MS);
    }

    private void scheduleInitialNavigationRetry(final WebView target, final long generation, long delayMs) {
        if (generation == 0L) return;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (activityDestroyed || target != webView || !activityResumed ||
                        initialNavigationGuard.pendingGeneration() != generation) return;
                if (!target.isAttachedToWindow()) {
                    scheduleInitialNavigationRetry(target, generation, 220L);
                    return;
                }
                String retryUrl = initialNavigationGuard.claimRetry(generation);
                if (retryUrl.length() == 0) return;
                target.getSettings().setBlockNetworkLoads(false);
                if (retryUrl.equals(target.getUrl())) target.reload();
                else target.loadUrl(retryUrl);
            }
        }, Math.max(0L, delayMs));
    }

    private static boolean isNetworkUrl(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    private void showHome() {
        if (webView == null || !privateProfileReady) return;
        pageHost = "";
        trustedHome = true;
        webView.loadDataWithBaseURL(HOME, HomePage.html("google", Collections.<BrowserDataStore.Bookmark>emptyList(), true, HOME_TOKEN), "text/html", "UTF-8", HOME);
    }

    private void verifyTrustedHome(final WebView view) {
        view.evaluateJavascript("(function(){var m=document.querySelector('meta[name=median-home-token]');return !!m&&m.content===" +
                JSONObject.quote(HOME_TOKEN) + ";})();", new ValueCallback<String>() {
            @Override public void onReceiveValue(String value) {
                try {
                    trustedHome = "true".equals(value) && webView == view && isHome(view.getUrl());
                } catch (RuntimeException ignored) {
                    trustedHome = false;
                }
            }
        });
    }

    private void confirmExternal(final String url) {
        new AlertDialog.Builder(this).setTitle("离开隐私窗口？")
                .setMessage("外部应用可能记录这次操作。仅在你信任当前网站时继续。")
                .setPositiveButton("继续", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dialog, int which) {
                        try {
                            Intent intent;
                            if (url.startsWith("intent:")) {
                                Intent parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                                if (parsed.getData() == null) throw new IllegalArgumentException("外部地址无效");
                                intent = new Intent(Intent.ACTION_VIEW, parsed.getData());
                            } else intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            intent.addCategory(Intent.CATEGORY_BROWSABLE);
                            intent.setComponent(null);
                            intent.setPackage(null);
                            intent.setSelector(null);
                            int unsafeGrants = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
                            intent.setFlags(intent.getFlags() & ~unsafeGrants);
                            intent.setClipData(null);
                            startActivity(intent);
                        } catch (Exception ignored) { Toast.makeText(PrivateActivity.this, "无法打开链接", Toast.LENGTH_SHORT).show(); }
                    }
                }).setNegativeButton("取消", null).show();
    }

    private static boolean isHome(String url) { return UrlCleaner.isInternalPage(url, "median-private.invalid"); }

    private static String host(String url) {
        try { String value = Uri.parse(url).getHost(); return value == null ? "" : value; }
        catch (Exception ignored) { return ""; }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileChooserCallback = null;
        }
    }

    private void handlePrivateBack() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else finishAndRemoveTask();
    }

    private void registerPredictiveBack() {
        if (android.os.Build.VERSION.SDK_INT < 33 || predictiveBackCallback != null) return;
        registerPredictiveBackApi33();
    }

    @android.annotation.TargetApi(33)
    private void registerPredictiveBackApi33() {
        final android.window.OnBackInvokedCallback callback = new android.window.OnBackInvokedCallback() {
            @Override public void onBackInvoked() { handlePrivateBack(); }
        };
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        predictiveBackCallback = callback;
    }

    private void unregisterPredictiveBack() {
        if (android.os.Build.VERSION.SDK_INT < 33 || predictiveBackCallback == null) return;
        try {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (android.window.OnBackInvokedCallback) predictiveBackCallback);
        } catch (RuntimeException ignored) {}
        predictiveBackCallback = null;
    }

    @Override public void onBackPressed() {
        handlePrivateBack();
    }

    @Override protected void onPause() {
        activityResumed = false;
        startupReadiness.set(StartupReadiness.RESUMED, false);
        if (webView != null) try { webView.onPause(); } catch (RuntimeException ignored) {}
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (webView != null) try { webView.onResume(); } catch (RuntimeException ignored) {}
        startupReadiness.set(StartupReadiness.RESUMED, true);
        completePrivateProfileReset();
        long pendingNavigation = initialNavigationGuard.pendingGeneration();
        if (pendingNavigation != 0L && webView != null)
            scheduleInitialNavigationRetry(webView, pendingNavigation, 250L);
    }

    @Override protected void onDestroy() {
        activityResumed = false;
        activityDestroyed = true;
        unregisterPredictiveBack();
        handler.removeCallbacksAndMessages(null);
        if (startupExecutor != null) startupExecutor.shutdownNow();
        startupExecutor = null;
        if (fileChooserCallback != null) { fileChooserCallback.onReceiveValue(null); fileChooserCallback = null; }
        if (webView != null) {
            WebView closing = webView;
            webView = null;
            try { closing.stopLoading(); closing.onPause(); closing.clearHistory(); closing.clearCache(true); } catch (RuntimeException ignored) {}
            closing.setWebChromeClient(null);
            closing.setWebViewClient(null);
            if (closing.getParent() instanceof ViewGroup) ((ViewGroup) closing.getParent()).removeView(closing);
            closing.destroy();
        }
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        clearPrivateProfileMetadata();
        if (filterStore != null) filterStore.close();
        super.onDestroy();
    }

    private void clearPrivateProfileMetadata() {
        WebStorage.getInstance().deleteAllData();
        GeolocationPermissions.getInstance().clearAll();
        try {
            WebViewDatabase database = WebViewDatabase.getInstance(this);
            database.clearHttpAuthUsernamePassword();
            database.clearFormData();
        } catch (RuntimeException ignored) {}
    }
}
