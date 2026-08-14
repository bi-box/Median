package com.xinyv.median;

import static com.xinyv.median.ByteFormat.humanBytes;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.PictureInPictureParams;
import android.print.PrintManager;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.util.Rational;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.JsPromptResult;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebResourceError;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.security.MessageDigest;
import java.util.Iterator;

public final class MainActivity extends Activity implements Runnable {
    private static final int FILE_CHOOSER_REQUEST = 401;
    private static final int BACKUP_EXPORT_REQUEST = 402;
    private static final int BACKUP_IMPORT_REQUEST = 403;
    private static final int WEB_PERMISSION_REQUEST = 404;
    private static final int GEOLOCATION_PERMISSION_REQUEST = 405;
    private static final int VAULT_UNLOCK_REQUEST = 406;
    private static final int FULL_BACKUP_EXPORT_REQUEST = 407;
    private static final int FULL_BACKUP_IMPORT_REQUEST = 408;
    private static final int HOME_WALLPAPER_REQUEST = 409;
    private static final int HOME_LOGO_REQUEST = 410;
    private static final String PREFS = "median_browser_v2";
    private static final String HOME_URL = "https://median.invalid/";
    private static final String STATE_WEBVIEW = "median.webview.state";
    private static final String PREF_STARTUP_DIAGNOSTIC = "startup_diagnostic_v1";
    private static final String HOME_TOKEN = UrlCleaner.randomToken();
    private static final int MAX_TABS = 64;
    private static final long INITIAL_NAVIGATION_ACK_MS = 450L;
    private static final int MAX_WEBVIEW_STATE_BYTES = 262144;
    private static final byte[] EMPTY_RESPONSE = new byte[0];
    private static final String MODE_PERFORMANCE = "performance";
    private static final String MODE_STANDARD = "standard";
    private static final String MODE_POWER_SAVE = "power_save";
    private static final int HOME_SECTION_MAIN = 0;
    private static final int HOME_SECTION_LAYOUT = 1;
    private static final int HOME_SECTION_LOGO = 2;
    private static final int HOME_SECTION_SEARCH = 3;
    private static final int HOME_SECTION_BACKGROUND = 4;
    private static final int HOME_SECTION_SHORTCUTS = 5;
    private static final int HOME_SECTION_CODE = 6;
    private static final int SHEET_ROW_NAVIGATE = 0;
    private static final int SHEET_ROW_ACTION = 1;
    private static final int SHEET_ROW_TOGGLE_OFF = 2;
    private static final int SHEET_ROW_TOGGLE_ON = 3;
    private static final String SHEET_TAG_SURFACE = "median:sheet:surface";
    private static final String SHEET_TAG_HANDLE = "median:sheet:handle";
    private static final String SHEET_TAG_PRIMARY = "median:sheet:primary";
    private static final String SHEET_TAG_MUTED = "median:sheet:muted";
    private static final String SHEET_TAG_ACCENT = "median:sheet:accent";
    private static final String PREF_COMMUNITY_NOTICE_SHOWN = "community_notice_shown_v1";
    private static final String GITHUB_URL = "https://github.com/bi-box/Median";
    private static final String TELEGRAM_URL = "https://telegram.me/MedianBeta";
    private static final String COMMUNITY_INFO =
            "项目更新、版本发布与问题反馈：\n" + GITHUB_URL +
            "\n\n官方测试频道与公告：\n" + TELEGRAM_URL;

    private static final int WHITE = Color.rgb(255, 255, 255);
    private static final int TEXT = Color.rgb(32, 33, 36);
    private static final int MUTED = Color.rgb(95, 99, 104);
    private static final int SURFACE = Color.rgb(241, 243, 244);
    private static final int BLUE = Color.rgb(26, 115, 232);

    private FrameLayout rootFrame;
    private LinearLayout browserChrome;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private FrameLayout webContainer;
    private WebView webView;
    private EditText addressBar;
    private LinearLayout addressPill;
    private ProgressBar progressBar;
    private BrowserIconView backButton;
    private BrowserIconView forwardButton;
    private BrowserIconView tabButton;
    private BrowserIconView shieldButton;
    private BrowserIconView refreshButton;
    private ValueCallback<Uri[]> fileChooserCallback;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private ExecutorService startupExecutor;
    private ThreadPoolExecutor navigationExecutor;
    private ExecutorService scriptExecutor;
    private ExecutorService scriptNetworkExecutor;
    private boolean activityResumed;
    private volatile boolean activityDestroyed;
    private boolean startupReady;
    private final long startupStartedAt = SystemClock.elapsedRealtime();
    private long startupReadyAt;
    private long startupNavigationAt;
    private volatile long scriptRegistrationGeneration;
    private final StartupReadiness startupReadiness = new StartupReadiness(
            StartupReadiness.VIEW | StartupReadiness.DATA | StartupReadiness.RESUMED);
    private Bundle startupSavedState;
    private String startupExternalUrl = "";
    private String startupScriptToken = "";
    private String startupScriptSource = "";
    private String pendingStartupInput;
    private long lastExecutorBusyToastAt;
    private boolean deferredStartupPending;
    private boolean deferredStartupComplete;
    private final Runnable deferredStartup = new Runnable() {
        @Override public void run() {
            deferredStartupPending = false;
            if (deferredStartupComplete || isFinishing() || !activityResumed) return;
            deferredStartupComplete = true;
            executeTask(scriptExecutor, new Runnable() {
                @Override public void run() {
                    try { Process.setThreadPriority(scriptThreadPriority()); } catch (RuntimeException ignored) {}
                    rebuildAdBlockRules();
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (!activityDestroyed) updateFilterSubscriptions(true);
                        }
                    });
                }
            });
            executeTask(startupExecutor, new Runnable() {
                @Override public void run() {
                    if (!activityDestroyed && services != null) services.warmLocalIndexes();
                }
            });
        }
    };
    private View customView;
    private View activeOverlay;
    private View activeOverlayPanel;
    private boolean activeOverlaySheet;
    private boolean overlayDismissInProgress;
    private Runnable activeOverlayBackAction;
    private Object predictiveBackCallback;
    private Runnable homeCustomizationBackAction;
    private Runnable bookmarkFolderRootBackAction;
    private int pendingHomeImageReturnSection = HOME_SECTION_MAIN;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private int previousOrientation;
    private int previousSystemUi;

    private final AdBlockEngine adBlock = new AdBlockEngine();
    private UserScriptStore scriptStore;
    private final MediaResourceSniffer mediaSniffer = new MediaResourceSniffer();
    private BrowserServices services;
    private BrowserDataStore dataStore;
    private BookmarkFolderStore bookmarkFolders;
    private HomeImageStore homeImages;
    private FaviconStore favicons;
    private SearchEngineStore searchEngines;
    private SiteSettingsStore siteSettingsStore;
    private DeviceProfile deviceProfile;
    private SharedPreferences prefs;
    private volatile boolean adBlockEnabled;
    private boolean desktopMode;
    private boolean nightMode;
    private boolean httpsOnly;
    private boolean restoreTabs;
    private boolean acceptThirdPartyCookies;
    private String searchEngine;
    private volatile String performanceMode;
    private volatile boolean performanceNetworkDirect;
    private volatile Set<String> siteExceptions;
    private int blockedAtPageStart;
    private volatile boolean scriptDownloadInProgress;
    private final ConcurrentHashMap<WebView, String> scriptBridgeTokens = new ConcurrentHashMap<WebView, String>();
    private final ConcurrentHashMap<WebView, ScriptHandler> scriptHandlers = new ConcurrentHashMap<WebView, ScriptHandler>();
    private final ConcurrentHashMap<WebView, String> credentialCaptureTokens = new ConcurrentHashMap<WebView, String>();
    private final Set<WebView> documentStartScriptViews = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, HttpURLConnection> scriptConnections = new ConcurrentHashMap<String, HttpURLConnection>();
    private volatile boolean filterUpdateInProgress;
    private boolean autoPictureInPicture;
    private boolean cleanTrackingParameters;
    private boolean passwordAutofillEnabled;
    private boolean passwordSavePromptsEnabled;
    private final AtomicBoolean cookieFlushPending = new AtomicBoolean(false);
    private boolean compatibilityDialogShowing;
    private String lastCompatibilityOfferHost = "";
    private long lastCompatibilityOfferAt;
    private boolean rendererRecoveryPending;
    // WebView request callbacks run off the UI thread. Never call WebView methods there.
    private volatile String currentPageUrl = HOME_URL;
    private volatile String currentPageHost = "";
    private final ConcurrentHashMap<WebView, String> pageHosts = new ConcurrentHashMap<WebView, String>();
    private final ConcurrentHashMap<WebView, Boolean> adBlockActiveByView = new ConcurrentHashMap<WebView, Boolean>();
    private final ConcurrentHashMap<WebView, String> mobileUserAgents = new ConcurrentHashMap<WebView, String>();
    private final ConcurrentHashMap<WebView, String> appliedSiteSettings = new ConcurrentHashMap<WebView, String>();
    private final ConcurrentHashMap<WebView, InitialNavigationGuard> initialNavigationGuards =
            new ConcurrentHashMap<WebView, InitialNavigationGuard>();
    private final ConcurrentHashMap<WebView, Boolean> cosmeticInjected = new ConcurrentHashMap<WebView, Boolean>();
    private final Set<WebView> unresponsiveWebViews = ConcurrentHashMap.newKeySet();
    private final Set<WebView> trustedHomeViews = ConcurrentHashMap.newKeySet();
    private final Set<WebView> customHomeViews = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<WebView, String> renderedHomeKeys = new ConcurrentHashMap<WebView, String>();
    private String cachedHomeKey = "";
    private String cachedHomeHtml = "";

    private volatile long navigationSequence;
    private boolean pageCommitted;
    private boolean pageFinished;
    private PreparedInjection preparedInjection;
    private long injectedStartSequence = -1;
    private long injectedEndSequence = -1;
    private boolean chromeUpdatePending;
    private boolean progressUpdatePending;
    private boolean hotTrimPending;
    private int pendingProgress = 100;
    private int renderedProgress = -1;
    private String renderedAddress;
    private Boolean renderedBackEnabled;
    private Boolean renderedForwardEnabled;
    private Integer renderedTabCount;
    private Boolean renderedShieldActive;
    private PermissionRequest pendingPermissionRequest;
    private String[] pendingWebPermissionResources;
    private WebView pendingPermissionView;
    private String pendingPermissionOrigin;
    private GeolocationPermissions.Callback pendingGeolocationCallback;
    private String pendingGeolocationOrigin;
    private WebView pendingGeolocationView;
    private Runnable pendingVaultAction;
    private long vaultUnlockedUntil;
    private String handledCredentialPageKey = "";
    private PasswordVault.Credential stagedCredential;
    private long stagedCredentialAt;
    private String lastCredentialOfferKey = "";
    private long lastCredentialOfferAt;

    private static final class PreparedInjection {
        final long sequence;
        final String url;
        final String startScript;
        final String endScript;

        PreparedInjection(long sequence, String url, String startScript, String endScript) {
            this.sequence = sequence;
            this.url = url;
            this.startScript = startScript;
            this.endScript = endScript;
        }
    }

    private static final class BrowserTab {
        String title = "新标签页";
        String url = HOME_URL;
        Bundle state;
        WebView liveView;
        long lastActiveAt;
        boolean pinned;
    }

    private static final class StartupLoad {
        final AtomicInteger remaining = new AtomicInteger(2);
        BrowserDataStore data;
        BookmarkFolderStore folders;
        SiteSettingsStore sites;
        UserScriptStore scripts;
        String scriptToken = "";
        String scriptSource = "";
    }

    private final ArrayList<BrowserTab> tabs = new ArrayList<BrowserTab>();
    private final ArrayList<BrowserTab> closedTabs = new ArrayList<BrowserTab>();
    private int currentTabIndex = 0;

    @Override
    protected void onCreate(Bundle state) {
        // Select the real Activity theme before WebView or window content is constructed. WebView
        // reads isLightTheme to expose prefers-color-scheme to websites.
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        nightMode = prefs.getBoolean("night_mode", false);
        setTheme(nightMode ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(state);
        Window window = getWindow();
        int startupChrome = nightMode ? Color.rgb(27, 29, 32) : WHITE;
        window.setStatusBarColor(startupChrome);
        window.setNavigationBarColor(startupChrome);
        window.getDecorView().setSystemUiVisibility(nightMode ? 0 :
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);

        deviceProfile = DeviceProfile.detect(this);
        migrateLegacyLogoExample();
        homeImages = new HomeImageStore(this);
        favicons = new FaviconStore(this);
        searchEngines = new SearchEngineStore(prefs);
        adBlockEnabled = prefs.getBoolean("adblock", true);
        desktopMode = prefs.getBoolean("desktop", false);
        httpsOnly = prefs.getBoolean("https_only", true);
        restoreTabs = HomeOpenPolicy.restoresLast(prefs.getString("home_open_mode", ""),
                prefs.getBoolean("restore_tabs", true));
        acceptThirdPartyCookies = prefs.getBoolean("accept_third_party_cookies", false);
        searchEngine = prefs.getString("search_engine", "google");
        if (!searchEngines.contains(searchEngine)) searchEngine = "google";
        performanceMode = prefs.getString("performance_mode", MODE_STANDARD);
        performanceNetworkDirect = prefs.getBoolean("performance_network_direct", false);
        autoPictureInPicture = prefs.getBoolean("auto_picture_in_picture", false);
        cleanTrackingParameters = prefs.getBoolean("clean_tracking_parameters", true);
        passwordAutofillEnabled = prefs.getBoolean("password_autofill", true);
        passwordSavePromptsEnabled = prefs.getBoolean("password_save_prompts", true);
        if (!MODE_PERFORMANCE.equals(performanceMode) && !MODE_POWER_SAVE.equals(performanceMode)) performanceMode = MODE_STANDARD;
        siteExceptions = new HashSet<String>(prefs.getStringSet("site_exceptions", new HashSet<String>()));
        services = new BrowserServices(this);
        startupExecutor = BackgroundExecutor.create(2, 8, "median-startup", false);
        navigationExecutor = BackgroundExecutor.create(1, 2, "median-navigation", true);
        scriptExecutor = BackgroundExecutor.create(1, 64, "median-work", false);
        scriptNetworkExecutor = BackgroundExecutor.create(3, 96, "median-network", false);

        BrowserTab first = new BrowserTab();
        tabs.add(first);
        buildUi();
        registerPredictiveBack();
        // Creating the first WebView directly is the most compatible startup path. Some vendor
        // providers advertise asynchronous startup but lose its completion callback, which used
        // to leave the browser apparently unable to open a page until a three-second fallback.
        initializeInitialWebView();
        beginStartupLoad(state, getIntent());
    }

    /**
     * Constructs the first WebView through the framework's normal synchronous path. This avoids
     * depending on optional OEM support-library startup callbacks before navigation can begin.
     */
    private void initializeInitialWebView() {
        if (activityDestroyed || isFinishing() || webView != null || webContainer == null) return;
        webContainer.removeAllViews();
        webView = createConfiguredWebView();
        webContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // configureWebView() runs before this field is assigned, so its generic renderer policy
        // cannot yet know that this is the visible tab. Promote it immediately after binding;
        // otherwise the first homepage/network navigation may run at background priority.
        applyPerformanceMode(webView);
        BrowserTab first = tabs.get(0);
        first.liveView = webView;
        first.lastActiveAt = SystemClock.uptimeMillis();
        if (activityResumed) webView.onResume();
        // Do not start a placeholder WebView navigation here. The stores usually finish while
        // that navigation is still committing; replacing it with a restored/network page can be
        // dropped by some WebView providers and leaves the user needing a manual refresh.
        // completeStartup() owns the one and only initial navigation.
        startupReadiness.set(StartupReadiness.VIEW, true);
        updateChrome();
        maybeCompleteStartup();
    }

    /**
     * Large user state is decoded away from the first-frame path. WebView navigation begins only
     * after per-site policy and document-start scripts are ready, so startup never races a
     * placeholder page and speed never weakens privacy semantics.
     */
    private void beginStartupLoad(final Bundle savedState, Intent intent) {
        final String externalUrl = isExternalHttpIntent(intent) ? intent.getData().toString() : "";
        startupSavedState = savedState;
        startupExternalUrl = externalUrl;
        final StartupLoad load = new StartupLoad();
        if (!executeTask(startupExecutor, new Runnable() {
            @Override public void run() {
                BrowserDataStore data = null;
                BookmarkFolderStore folders = null;
                try {
                    data = new BrowserDataStore(getApplicationContext());
                    folders = new BookmarkFolderStore(getApplicationContext());
                } catch (RuntimeException ignored) {}
                load.data = data;
                load.folders = folders;
                finishStartupLoadPart(load);
            }
        })) finishStartupLoadPart(load);
        if (!executeTask(startupExecutor, new Runnable() {
            @Override public void run() {
                SiteSettingsStore sites = null;
                UserScriptStore scripts = null;
                try {
                    sites = new SiteSettingsStore(getApplicationContext());
                    scripts = new UserScriptStore(getApplicationContext());
                } catch (RuntimeException ignored) {}
                String preparedToken = "";
                String preparedSource = "";
                if (scripts != null && scripts.hasEnabledScripts()) {
                    try {
                        preparedToken = UrlCleaner.randomToken();
                        preparedSource = scripts.buildDocumentStartScript(preparedToken);
                    } catch (RuntimeException ignored) {
                        preparedToken = "";
                        preparedSource = "";
                    }
                }
                load.sites = sites;
                load.scripts = scripts;
                load.scriptToken = preparedToken;
                load.scriptSource = preparedSource;
                finishStartupLoadPart(load);
            }
        })) finishStartupLoadPart(load);
    }

    private void finishStartupLoadPart(final StartupLoad load) {
        if (load.remaining.decrementAndGet() != 0) return;
        if (activityDestroyed) {
            closeStartupStores(load.data, load.sites, load.scripts);
            return;
        }
        uiHandler.post(new Runnable() {
            @Override public void run() {
                if (activityDestroyed || isFinishing()) {
                    closeStartupStores(load.data, load.sites, load.scripts);
                } else if (load.data == null || load.sites == null || load.folders == null || load.scripts == null) {
                    closeStartupStores(load.data, load.sites, load.scripts);
                    toast("本地浏览数据无法读取，请重新启动 Median");
                    finishAfterTransition();
                } else {
                    dataStore = load.data;
                    bookmarkFolders = load.folders;
                    siteSettingsStore = load.sites;
                    scriptStore = load.scripts;
                    startupScriptToken = load.scriptToken;
                    startupScriptSource = load.scriptSource;
                    startupReadiness.set(StartupReadiness.DATA, true);
                    maybeCompleteStartup();
                }
            }
        });
    }

    private static void closeStartupStores(BrowserDataStore data, SiteSettingsStore sites,
                                           UserScriptStore scripts) {
        if (data != null) data.close();
        if (sites != null) sites.close();
        if (scripts != null) scripts.close();
    }

    private void maybeCompleteStartup() {
        if (startupReady || webView == null || !startupReadiness.claimPost()) return;
        postAfterUiTransition(new Runnable() {
            @Override public void run() {
                if (!startupReadiness.begin() || activityDestroyed || isFinishing() || webView == null) return;
                Bundle savedState = startupSavedState;
                String externalUrl = startupExternalUrl;
                startupSavedState = null;
                startupExternalUrl = "";
                completeStartup(savedState, externalUrl);
            }
        });
    }

    private void completeStartup(Bundle savedState, String externalUrl) {
        startupReady = true;
        startupReadyAt = SystemClock.elapsedRealtime();
        installPreparedDocumentStartUserScripts(webView);
        BrowserTab active = tabs.get(0);
        Bundle webViewState = savedState == null ? null :
                (savedState.containsKey(STATE_WEBVIEW) ? savedState.getBundle(STATE_WEBVIEW) : savedState);
        String directInput = StartupNavigationPolicy.preferredInput(pendingStartupInput, externalUrl);
        pendingStartupInput = null;
        if (directInput.length() > 0) {
            // Typed input is newer than an external launch intent. Loading the homepage/session
            // first and this destination immediately afterwards recreates the cold-start race
            // fixed above, so execute only the selected direct navigation.
            loadInput(directInput);
        } else if (webViewState != null && webView.restoreState(webViewState) != null) {
            active.url = webView.getUrl() == null ? HOME_URL : webView.getUrl();
            if (isHomeUrl(active.url)) {
                // A restored WebView can contain the placeholder homepage rendered before the
                // bookmark stores finished loading. Always rebuild internal home pages from the
                // now-ready stores so long-idle/process-death restores cannot show stale/empty data.
                renderedHomeKeys.remove(webView);
                showHome();
            } else if (isNetworkPage(active.url)) {
                currentPageUrl = active.url;
                currentPageHost = hostOf(active.url);
                prepareNetworkDestination(webView, active.url);
                watchInitialNetworkNavigation(webView, active.url);
            } else if (isOfflineUrl(active.url)) {
                currentPageUrl = active.url;
                currentPageHost = "";
                prepareOfflineDestination(webView, active.url);
            } else {
                currentPageUrl = active.url;
                currentPageHost = "";
                applyPageAccessPolicy(webView, active.url);
                appliedSiteSettings.remove(webView);
            }
        } else {
            List<BrowserDataStore.SessionTab> saved = restoreTabs
                    ? dataStore.restoreSession() : Collections.<BrowserDataStore.SessionTab>emptyList();
            if (!saved.isEmpty()) {
                tabs.clear();
                for (BrowserDataStore.SessionTab item : saved) {
                    BrowserTab restored = new BrowserTab();
                    restored.title = item.title;
                    restored.url = item.url;
                    restored.pinned = item.pinned;
                    tabs.add(restored);
                }
                currentTabIndex = Math.min(dataStore.restoredSessionIndex(), tabs.size() - 1);
                active = tabs.get(currentTabIndex);
                active.liveView = webView;
                active.lastActiveAt = SystemClock.uptimeMillis();
            } else {
                active.url = configuredHomeUrl();
            }
            if (isHomeUrl(active.url)) {
                showHome();
            } else {
                currentPageUrl = active.url;
                currentPageHost = hostOf(active.url);
                pageHosts.put(webView, currentPageHost);
                loadNetworkUrl(webView, active.url);
            }
        }
        requestChromeUpdate();
        scheduleDeferredStartupWork();
        showCommunityNoticeOnFirstLaunch(savedState);
    }

    /** Heavy rule parsing and subscription I/O must not compete with the first frame. */
    private void scheduleDeferredStartupWork() {
        if (!startupReady) return;
        if (deferredStartupComplete || deferredStartupPending) return;
        // Large filter subscriptions and local indexes must not start while the first website is
        // still painting. Built-in network rules remain available during this idle delay.
        long delay = MODE_POWER_SAVE.equals(performanceMode) ? 8000L :
                (MODE_PERFORMANCE.equals(performanceMode) ? 4000L : 5500L);
        deferredStartupPending = true;
        uiHandler.postDelayed(deferredStartup, delay);
    }

    private boolean executeTask(ExecutorService executor, Runnable task) {
        if (executor == null || executor.isShutdown() || task == null) return false;
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException ignored) {
            long now = SystemClock.uptimeMillis();
            if (!activityDestroyed && now - lastExecutorBusyToastAt > 2500L) {
                lastExecutorBusyToastAt = now;
                uiHandler.post(new Runnable() {
                    @Override public void run() { if (!activityDestroyed) toast("后台任务繁忙，请稍后重试"); }
                });
            }
            return false;
        }
    }

    private boolean requireStartupReady() {
        if (startupReady) return true;
        long now = SystemClock.uptimeMillis();
        if (now - lastExecutorBusyToastAt > 1200L) {
            lastExecutorBusyToastAt = now;
            toast("正在恢复本地浏览数据…");
        }
        return false;
    }

    /**
     * 2.1.5 wrote its editor example into real preferences before the user
     * pressed Save. Repair only the exact inconsistent legacy state once.
     */
    private void migrateLegacyLogoExample() {
        final String migrationKey = "home_logo_example_migrated_v216";
        if (prefs.getBoolean(migrationKey, false)) return;
        String code = LogoMarkup.clean(prefs.getString("home_logo_code", ""));
        String title = HomePageConfig.cleanTitle(prefs.getString("home_title", HomePageConfig.DEFAULT_TITLE));
        SharedPreferences.Editor editor = prefs.edit().putBoolean(migrationKey, true);
        if (LogoMarkup.LEGACY_GRADIENT_EXAMPLE.equals(code) && HomePageConfig.DEFAULT_TITLE.equals(title)) {
            editor.remove("home_logo_code");
            if ("custom".equals(prefs.getString("home_logo_style", "median")))
                editor.putString("home_logo_style", "median");
        }
        editor.apply();
    }

    private void buildUi() {
        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(nightMode ? Color.rgb(17, 19, 21) : WHITE);

        browserChrome = new LinearLayout(this);
        browserChrome.setOrientation(LinearLayout.VERTICAL);
        browserChrome.setBackgroundColor(WHITE);
        rootFrame.addView(browserChrome, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(7), dp(10), dp(7));
        topBar.setBackgroundColor(WHITE);

        addressPill = new LinearLayout(this);
        addressPill.setOrientation(LinearLayout.HORIZONTAL);
        addressPill.setGravity(Gravity.CENTER_VERTICAL);
        addressPill.setPadding(dp(3), 0, dp(3), 0);
        addressPill.setBackground(roundRect(SURFACE, 22));

        shieldButton = iconButton(BrowserIconView.SHIELD, "保护与脚本");
        addressPill.addView(shieldButton, new LinearLayout.LayoutParams(dp(40), dp(42)));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextSize(15f);
        addressBar.setTextColor(TEXT);
        addressBar.setHintTextColor(Color.rgb(128, 134, 139));
        addressBar.setHint("搜索或输入网址");
        addressBar.setSelectAllOnFocus(true);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setBackgroundColor(Color.TRANSPARENT);
        addressBar.setPadding(dp(3), 0, dp(3), 0);
        addressPill.addView(addressBar, new LinearLayout.LayoutParams(0, dp(44), 1f));

        refreshButton = iconButton(BrowserIconView.RELOAD, "刷新");
        addressPill.addView(refreshButton, new LinearLayout.LayoutParams(dp(40), dp(42)));
        topBar.addView(addressPill, new LinearLayout.LayoutParams(0, dp(44), 1f));

        browserChrome.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));
        progressBar.setVisibility(View.GONE);
        browserChrome.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(Color.rgb(5, 5, 5));
        LinearLayout startupSurface = new LinearLayout(this);
        startupSurface.setOrientation(LinearLayout.VERTICAL);
        startupSurface.setGravity(Gravity.CENTER);
        startupSurface.setPadding(dp(24), dp(24), dp(24), dp(24));
        ImageView startupMark = new ImageView(this);
        startupMark.setImageResource(R.drawable.ic_launcher_foreground);
        startupMark.setContentDescription("Median");
        startupMark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        startupSurface.addView(startupMark, new LinearLayout.LayoutParams(dp(88), dp(88)));
        TextView startupName = new TextView(this);
        startupName.setText("median");
        startupName.setTextColor(Color.WHITE);
        startupName.setTextSize(23f);
        startupName.setGravity(Gravity.CENTER);
        startupName.setLetterSpacing(0.08f);
        startupSurface.addView(startupName, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView startupStatus = new TextView(this);
        startupStatus.setText("正在准备网页引擎…");
        startupStatus.setTextColor(Color.rgb(154, 160, 166));
        startupStatus.setTextSize(12f);
        startupStatus.setGravity(Gravity.CENTER);
        startupStatus.setPadding(0, dp(8), 0, 0);
        startupSurface.addView(startupStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        webContainer.addView(startupSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        browserChrome.addView(webContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(dp(8), dp(2), dp(8), dp(2));
        bottomBar.setBackgroundColor(WHITE);
        bottomBar.setElevation(dp(6));

        backButton = iconButton(BrowserIconView.BACK, "后退");
        forwardButton = iconButton(BrowserIconView.FORWARD, "前进");
        BrowserIconView home = iconButton(BrowserIconView.HOME, "主页");
        tabButton = iconButton(BrowserIconView.TABS, "标签页");
        BrowserIconView menu = iconButton(BrowserIconView.MENU, "菜单");
        BrowserIconView[] bottomButtons = new BrowserIconView[] { backButton, forwardButton, home, tabButton, menu };
        for (BrowserIconView button : bottomButtons) bottomBar.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1f));
        browserChrome.addView(bottomBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        setContentView(rootFrame);
        installSystemBarInsets(rootFrame);

        addressBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH ||
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    loadInput(addressBar.getText().toString());
                    hideKeyboard();
                    addressBar.clearFocus();
                    return true;
                }
                return false;
            }
        });
        addressBar.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                Motion.focusPill(addressPill, hasFocus, reduceMotion());
                if (hasFocus) {
                    String url = currentPageUrl;
                    if (isHomeUrl(url)) addressBar.setText("");
                    else if (url != null && !url.contentEquals(addressBar.getText())) addressBar.setText(url);
                    addressBar.selectAll();
                } else {
                    updateAddressBar();
                }
            }
        });
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (webView == null) return;
                if (isHomeUrl(currentPageUrl)) {
                    renderedHomeKeys.remove(webView);
                    showHome();
                } else webView.reload();
            }
        });
        refreshButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                if (webView == null) return true;
                webView.stopLoading();
                toast("已停止加载");
                return true;
            }
        });
        shieldButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showProtectionPanel(); }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (webView != null && webView.canGoBack()) webView.goBack();
            }
        });
        forwardButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (webView != null && webView.canGoForward()) webView.goForward();
            }
        });
        home.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openConfiguredHome(); }
        });
        home.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { newTab(); return true; }
        });
        tabButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTabs(); }
        });
        tabButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { newTab(); return true; }
        });
        menu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMainMenu(); }
        });
        applyChromeTheme();
    }


    private void installSystemBarInsets(final View target) {
        if (target == null || Build.VERSION.SDK_INT < 30) return;
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

    private BrowserIconView iconButton(int icon, String description) {
        BrowserIconView button = new BrowserIconView(this, icon);
        button.setContentDescription(description);
        button.setBackgroundResource(selectableBorderless());
        return button;
    }

    private WebView createConfiguredWebView() {
        WebView view = new WebView(this);
        styleWebView(view);
        configureWebView(view);
        pageHosts.put(view, "");
        return view;
    }

    private void styleWebView(WebView view) {
        view.setBackgroundColor(nightMode ? Color.rgb(17, 19, 21) : WHITE);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClickable(true);
        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
        view.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        view.setDrawingCacheEnabled(false);
        resetWebViewTransform(view);
    }

    /** WebView owns its input surface. Never animate or leave transforms on that surface. */
    private void resetWebViewTransform(WebView view) {
        if (view == null) return;
        view.animate().cancel();
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    private WebView acquireWebView() {
        return createConfiguredWebView();
    }

    private int liveWebViewCount() {
        int count = 0;
        for (BrowserTab tab : tabs) if (tab.liveView != null) count++;
        return count;
    }

    private BrowserTab tabForView(WebView view) {
        if (view == null) return null;
        for (BrowserTab tab : tabs) if (tab.liveView == view) return tab;
        return null;
    }

    private String pageHostFor(WebView view) {
        if (view == webView) return currentPageHost;
        String value = pageHosts.get(view);
        return value == null ? "" : value;
    }

    private void updateTabForView(WebView view, String url, String title) {
        BrowserTab tab = tabForView(view);
        if (tab == null) return;
        if (url != null) tab.url = url;
        if (title != null && title.length() > 0) tab.title = title;
    }


    private interface SheetHandler {
        void onItem(int index);
    }

    /** One object handles both dialog cancellation paths instead of allocating two listeners. */
    private final class HomeDialogReturn implements DialogInterface.OnClickListener,
            DialogInterface.OnCancelListener, Runnable {
        private final int section;
        HomeDialogReturn(int section) { this.section = section; }
        @Override public void onClick(DialogInterface dialog, int which) { postAfterUiTransition(this); }
        @Override public void onCancel(DialogInterface dialog) { postAfterUiTransition(this); }
        @Override public void run() { showHomeSection(section); }
    }

    private final class SettingsDialogReturn implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
        private final Runnable action;
        SettingsDialogReturn(Runnable action) { this.action = action; }
        @Override public void onClick(DialogInterface dialog, int which) { continueSettingsPanel(action); }
        @Override public void onCancel(DialogInterface dialog) { continueSettingsPanel(action); }
    }

    private final class HomeOpenDialogReturn implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
        private final boolean customization;
        HomeOpenDialogReturn(boolean customization) { this.customization = customization; }
        @Override public void onClick(DialogInterface dialog, int which) { continueHomeOpenSettings(customization); }
        @Override public void onCancel(DialogInterface dialog) { continueHomeOpenSettings(customization); }
    }

    /** Shared denial path for auth, Web permission and geolocation dialogs. */
    private static final class DenyDialog implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
        private final Object request;
        private final String origin;
        DenyDialog(Object request, String origin) { this.request = request; this.origin = origin; }
        @Override public void onClick(DialogInterface dialog, int which) { deny(); }
        @Override public void onCancel(DialogInterface dialog) { deny(); }
        private void deny() {
            if (request instanceof HttpAuthHandler) ((HttpAuthHandler) request).cancel();
            else if (request instanceof PermissionRequest) ((PermissionRequest) request).deny();
            else ((GeolocationPermissions.Callback) request).invoke(origin, false, false);
        }
    }

    private void showActionSheet(String title, String subtitle, String[] items, int[] icons, final SheetHandler handler) {
        showActionSheet(title, subtitle, items, icons, null, null, null, handler);
    }

    private void showActionSheet(String title, String subtitle, String[] items, int[] icons,
                                 int[] rowKinds, String[] sectionHeaders, final Runnable backAction,
                                 final SheetHandler handler) {
        dismissOverlayForNavigation();
        final int sheetSurface = nightMode ? Color.rgb(35, 38, 42) : WHITE;
        final int sheetText = nightMode ? Color.rgb(232, 234, 237) : TEXT;
        final int sheetMuted = nightMode ? Color.rgb(154, 160, 166) : MUTED;
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(104, 0, 0, 0));
        overlay.setClickable(true);
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismissOverlay(); }
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setTag(SHEET_TAG_SURFACE);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(roundRect(sheetSurface, 24));
        panel.setPadding(dp(10), dp(8), dp(10), dp(12));
        panel.setClickable(true);
        panel.setElevation(dp(12));

        View handle = new View(this);
        handle.setTag(SHEET_TAG_HANDLE);
        handle.setBackground(roundRect(Color.argb(nightMode ? 80 : 54, 95, 99, 104), 2));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(4));
        panel.addView(handle, handleParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(backAction == null ? dp(12) : dp(2), dp(2), dp(4), dp(4));
        if (backAction != null) {
            BrowserIconView back = iconButton(BrowserIconView.BACK, "返回上一级");
            back.setTintColor(sheetText);
            header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { navigateOverlayBack(); }
            });
        }
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this);
        heading.setTag(SHEET_TAG_PRIMARY);
        heading.setText(title);
        heading.setTextColor(sheetText);
        heading.setTextSize(19f);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titles.addView(heading);
        if (subtitle != null && subtitle.length() > 0) {
            TextView sub = new TextView(this);
            sub.setTag(SHEET_TAG_MUTED);
            sub.setText(subtitle);
            sub.setTextColor(sheetMuted);
            sub.setTextSize(12.5f);
            sub.setPadding(0, dp(3), 0, 0);
            titles.addView(sub);
        }
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        BrowserIconView close = iconButton(BrowserIconView.CLOSE, "关闭");
        close.setTintColor(sheetText);
        header.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(4));
        scroll.addView(list);
        int rowsHeight = 0;
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            String section = sectionHeaders != null && i < sectionHeaders.length ? sectionHeaders[i] : null;
            if (section != null && section.trim().length() > 0) {
                TextView sectionLabel = new TextView(this);
                sectionLabel.setTag(SHEET_TAG_ACCENT);
                sectionLabel.setText(section.trim());
                sectionLabel.setTextColor(BLUE);
                sectionLabel.setTextSize(12f);
                sectionLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                sectionLabel.setGravity(Gravity.BOTTOM | Gravity.START);
                sectionLabel.setPadding(dp(16), dp(7), dp(8), dp(5));
                sectionLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                list.addView(sectionLabel, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
                rowsHeight += 32;
            }
            String item = items[i] == null ? "" : items[i];
            int detailBreak = item.indexOf('\n');
            String primaryText = detailBreak < 0 ? item : item.substring(0, detailBreak);
            String detailText = detailBreak < 0 ? "" : item.substring(detailBreak + 1).trim();
            final int rowKind = rowKinds != null && i < rowKinds.length ? rowKinds[i] : SHEET_ROW_NAVIGATE;
            final boolean toggleRow = rowKind == SHEET_ROW_TOGGLE_OFF || rowKind == SHEET_ROW_TOGGLE_ON;
            int rowHeight = detailText.length() == 0 ? 56 : 70;
            rowsHeight += rowHeight;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), 0, dp(8), 0);
            row.setBackgroundResource(selectableBounded());
            row.setClickable(true);
            BrowserIconView icon = iconButton(icons != null && i < icons.length ? icons[i] : BrowserIconView.MENU, primaryText);
            icon.setClickable(false);
            icon.setTintColor(sheetText);
            row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(rowHeight - 4)));
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            labels.setPadding(dp(7), 0, dp(4), 0);
            TextView label = new TextView(this);
            label.setTag(SHEET_TAG_PRIMARY);
            label.setText(primaryText);
            label.setTextColor(sheetText);
            label.setTextSize(15f);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            if (detailText.length() > 0) {
                TextView detail = new TextView(this);
                detail.setTag(SHEET_TAG_MUTED);
                detail.setText(detailText);
                detail.setTextColor(sheetMuted);
                detail.setTextSize(12.5f);
                detail.setSingleLine(true);
                detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
                detail.setPadding(0, dp(2), 0, 0);
                labels.addView(detail, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            row.addView(labels, new LinearLayout.LayoutParams(0, dp(rowHeight), 1f));
            if (toggleRow) {
                final Switch toggle = new Switch(this);
                toggle.setChecked(rowKind == SHEET_ROW_TOGGLE_ON);
                toggle.setShowText(false);
                toggle.setClickable(false);
                toggle.setFocusable(false);
                toggle.setMinimumWidth(0);
                int[][] states = new int[][] {
                        new int[] { android.R.attr.state_checked }, new int[] {}
                };
                toggle.setThumbTintList(new ColorStateList(states, new int[] {
                        BLUE, nightMode ? Color.rgb(189, 193, 198) : Color.rgb(117, 117, 117)
                }));
                toggle.setTrackTintList(new ColorStateList(states, new int[] {
                        Color.argb(118, 26, 115, 232),
                        nightMode ? Color.rgb(80, 84, 89) : Color.rgb(189, 193, 198)
                }));
                row.addView(toggle, new LinearLayout.LayoutParams(dp(54), dp(rowHeight)));
                row.setContentDescription(primaryText + (toggle.isChecked() ? "，已开启" : "，已关闭"));
                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        boolean next = !toggle.isChecked();
                        toggle.setChecked(next);
                        v.setContentDescription(primaryText + (next ? "，已开启" : "，已关闭"));
                        handler.onItem(index);
                    }
                });
            } else {
                if (rowKind != SHEET_ROW_ACTION) {
                    TextView chevron = new TextView(this);
                    chevron.setTag(SHEET_TAG_MUTED);
                    chevron.setText("›");
                    chevron.setTextColor(sheetMuted);
                    chevron.setTextSize(27f);
                    chevron.setGravity(Gravity.CENTER);
                    chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    row.addView(chevron, new LinearLayout.LayoutParams(dp(28), dp(rowHeight)));
                } else {
                    row.setPadding(dp(8), 0, dp(18), 0);
                }
                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        dismissOverlayForNavigation();
                        handler.onItem(index);
                    }
                });
            }
            list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(rowHeight)));
        }
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * .86f);
        int desiredHeight = dp(94 + rowsHeight);
        int panelHeight = Math.min(maxHeight, desiredHeight);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, panelHeight, Gravity.BOTTOM);
        panelParams.setMargins(dp(8), 0, dp(8), dp(8));
        overlay.addView(panel, panelParams);
        rootFrame.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activeOverlay = overlay;
        activeOverlayPanel = panel;
        activeOverlaySheet = true;
        activeOverlayBackAction = backAction;
        overlayDismissInProgress = false;
        Motion.showSheet(overlay, panel, reduceMotion());
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismissOverlay(); }
        });
    }

    private void dismissOverlay() {
        if (activeOverlay == null) return;
        final View removing = activeOverlay;
        final View panel = activeOverlayPanel;
        final boolean sheet = activeOverlaySheet;
        activeOverlay = null;
        activeOverlayPanel = null;
        activeOverlayBackAction = null;
        activeOverlaySheet = false;
        overlayDismissInProgress = true;
        Motion.hideOverlay(removing, panel, sheet, reduceMotion(), new Runnable() {
            @Override public void run() {
                if (removing.getParent() instanceof ViewGroup) ((ViewGroup) removing.getParent()).removeView(removing);
                overlayDismissInProgress = false;
            }
        });
    }

    private void dismissOverlayForNavigation() {
        if (activeOverlay == null) return;
        View removing = activeOverlay;
        View panel = activeOverlayPanel;
        activeOverlay = null;
        activeOverlayPanel = null;
        activeOverlayBackAction = null;
        activeOverlaySheet = false;
        overlayDismissInProgress = false;
        removing.animate().cancel();
        if (panel != null) panel.animate().cancel();
        if (removing.getParent() instanceof ViewGroup) ((ViewGroup) removing.getParent()).removeView(removing);
    }

    private void navigateOverlayBack() {
        if (activeOverlay == null) return;
        Runnable action = activeOverlayBackAction;
        if (action == null) {
            dismissOverlay();
            return;
        }
        dismissOverlayForNavigation();
        action.run();
    }

    private void registerPredictiveBack() {
        if (Build.VERSION.SDK_INT < 33 || predictiveBackCallback != null) return;
        registerPredictiveBackApi33();
    }

    @android.annotation.TargetApi(33)
    private void registerPredictiveBackApi33() {
        final android.window.OnBackInvokedCallback callback = new android.window.OnBackInvokedCallback() {
            @Override public void onBackInvoked() { handleBrowserBack(); }
        };
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        predictiveBackCallback = callback;
    }

    private void unregisterPredictiveBack() {
        if (Build.VERSION.SDK_INT < 33 || predictiveBackCallback == null) return;
        unregisterPredictiveBackApi33();
    }

    @android.annotation.TargetApi(33)
    private void unregisterPredictiveBackApi33() {
        try {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (android.window.OnBackInvokedCallback) predictiveBackCallback);
        } catch (RuntimeException ignored) {
        }
        predictiveBackCallback = null;
    }

    /** Installs the cold-start payload already assembled on the background startup thread. */
    private void installPreparedDocumentStartUserScripts(WebView target) {
        String token = startupScriptToken;
        String source = startupScriptSource;
        startupScriptToken = "";
        startupScriptSource = "";
        installDocumentStartUserScripts(target, token, source);
    }

    private void installDocumentStartUserScripts(WebView target) {
        if (target == null || scriptStore == null || !scriptStore.hasEnabledScripts()) {
            removeDocumentStartUserScripts(target);
            return;
        }
        String token = UrlCleaner.randomToken();
        String source = scriptStore.buildDocumentStartScript(token);
        installDocumentStartUserScripts(target, token, source);
    }

    /** UI-only registration; expensive payload assembly is performed before this call. */
    private void installDocumentStartUserScripts(WebView target, String token, String source) {
        removeDocumentStartUserScripts(target);
        if (target == null || token == null || token.length() < 32 || source == null || source.length() == 0 ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        try {
            ScriptHandler handler = WebViewCompat.addDocumentStartJavaScript(target, source, "*");
            scriptBridgeTokens.put(target, token);
            scriptHandlers.put(target, handler);
            documentStartScriptViews.add(target);
        } catch (RuntimeException error) {
            scriptBridgeTokens.remove(target);
            scriptHandlers.remove(target);
            documentStartScriptViews.remove(target);
        }
    }

    private void removeDocumentStartUserScripts(WebView target) {
        if (target == null) return;
        ScriptHandler handler = scriptHandlers.remove(target);
        if (handler != null) try { handler.remove(); } catch (RuntimeException ignored) {}
        cancelScriptRequests(target);
        scriptBridgeTokens.remove(target);
        documentStartScriptViews.remove(target);
    }

    private void refreshUserScriptRegistrations(boolean reloadActive) {
        final ArrayList<WebView> live = new ArrayList<WebView>();
        for (BrowserTab tab : tabs) if (tab.liveView != null && !live.contains(tab.liveView)) live.add(tab.liveView);
        final boolean reload = reloadActive;
        final long generation = ++scriptRegistrationGeneration;
        final UserScriptStore store = scriptStore;
        Runnable prepare = new Runnable() {
            @Override public void run() {
                final String[] tokens = new String[live.size()];
                final String[] sources = new String[live.size()];
                if (store != null && store.hasEnabledScripts()) for (int i = 0; i < live.size(); i++) {
                    tokens[i] = UrlCleaner.randomToken();
                    sources[i] = store.buildDocumentStartScript(tokens[i]);
                }
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (activityDestroyed || generation != scriptRegistrationGeneration) return;
                        for (int i = 0; i < live.size(); i++)
                            installDocumentStartUserScripts(live.get(i), tokens[i], sources[i]);
                        if (reload && webView != null && isNetworkPage(webView.getUrl())) {
                            webView.reload();
                            toast("用户脚本权限已更新，当前页面正在重新加载");
                        }
                    }
                });
            }
        };
        if (!executeTask(scriptExecutor, prepare)) {
            for (WebView view : live) installDocumentStartUserScripts(view);
            if (reload && webView != null && isNetworkPage(webView.getUrl())) webView.reload();
        }
    }

    private void cancelScriptRequests(WebView target) {
        String token = scriptBridgeTokens.get(target);
        if (token == null || token.length() == 0) return;
        String prefix = token + "|";
        for (Map.Entry<String, HttpURLConnection> entry : new ArrayList<Map.Entry<String, HttpURLConnection>>(scriptConnections.entrySet())) {
            if (!entry.getKey().startsWith(prefix)) continue;
            HttpURLConnection connection = scriptConnections.remove(entry.getKey());
            if (connection != null) try { connection.disconnect(); } catch (RuntimeException ignored) {}
        }
    }

    private void configureWebView(final WebView target) {
        initialNavigationGuards.put(target, new InitialNavigationGuard());
        WebSettings settings = target.getSettings();
        WebViewPolicy.applySecureDefaults(settings, WebSettings.LOAD_DEFAULT);
        settings.setLoadsImagesAutomatically(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        // target=_blank and user-gesture popups are promoted to real browser tabs.
        settings.setSupportMultipleWindows(true);
        settings.setTextZoom(100);
        String mobileUserAgent = WebViewPolicy.mobileUserAgent(settings.getUserAgentString());
        mobileUserAgents.put(target, mobileUserAgent);
        if (!mobileUserAgent.equals(settings.getUserAgentString())) settings.setUserAgentString(mobileUserAgent);
        applyDesktopMode(target);
        applyPerformanceMode(target);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(target, acceptThirdPartyCookies);
        // Configure the native renderer before any URL can be loaded. No page-wide JavaScript or
        // DOM traversal is involved in dark mode.
        applyDarkMode(target);
        installDocumentStartUserScripts(target);

        EdgeNavigationController.attach(target, new EdgeNavigationController.Callback() {
            @Override public boolean canGoBack() { return target.canGoBack(); }
            @Override public boolean canGoForward() { return target.canGoForward(); }
            @Override public void goBack() { target.goBack(); }
            @Override public void goForward() { target.goForward(); }
        });
        target.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View ignored) { return handleWebViewLongPress(target); }
        });

        if (Build.VERSION.SDK_INT >= 29) {
            RendererWatchdog.attach(target, getMainExecutor(), new RendererWatchdog.Callback() {
                @Override public void onUnresponsive(final WebView view, final RendererWatchdog.Terminator terminateRenderer) {
                    if (view == null || !unresponsiveWebViews.add(view)) return;
                    uiHandler.postDelayed(new Runnable() {
                        @Override public void run() {
                            if (!unresponsiveWebViews.remove(view) || isFinishing()) return;
                            if (!terminateRenderer.terminate()) {
                                try { view.stopLoading(); } catch (RuntimeException ignored) {}
                            }
                            toast("页面无响应，正在重启渲染器");
                        }
                    }, 4500L);
                }

                @Override public void onResponsive(WebView view) {
                    if (view != null) unresponsiveWebViews.remove(view);
                }
            });
        }

        target.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (view != webView) return false;
                if (isNetworkPage(url)) {
                    prepareNetworkDestination(view, url);
                    watchInitialNetworkNavigation(view, url);
                }
                return handleNavigation(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (view != webView) return false;
                if (request == null) return false;
                // Subframe navigation must never change the active page host or its
                // WebSettings. Doing so breaks SPA controls while scrolling still works.
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString();
                if (isNetworkPage(url)) {
                    prepareNetworkDestination(view, url);
                    watchInitialNetworkNavigation(view, url);
                }
                return handleNavigation(view, url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return interceptRequest(view, url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request == null) return null;
                return interceptRequest(view, request);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                if (view == webView && MediaResourceSniffer.isObviousLoadResource(url))
                    mediaSniffer.observe(url, "", pageHostFor(view), "webview", 0, 0, 0d);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                unresponsiveWebViews.remove(view);
                cancelScriptRequests(view);
                credentialCaptureTokens.remove(view);
                if (InternalNavigationPolicy.shouldClearHomeTrust(isHomeUrl(url), url)) {
                    trustedHomeViews.remove(view);
                    customHomeViews.remove(view);
                }
                boolean offline = isOfflineUrl(url);
                if (offline) prepareOfflineDestination(view, url);
                else if (isHomeUrl(url)) prepareHomeDestination(view);
                else {
                    applyPageAccessPolicy(view, url);
                    applySiteSettings(view, hostOf(url));
                }
                updateTabForView(view, url, view.getTitle());
                cosmeticInjected.remove(view);
                if (url != null) {
                    String startedHost = hostOf(url);
                    pageHosts.put(view, startedHost);
                    adBlockActiveByView.put(view, Boolean.valueOf(isAdBlockActiveForHost(startedHost)));
                }
                if (view != webView) return;
                if (url != null) {
                    currentPageUrl = url;
                    currentPageHost = hostOf(url);
                    if (stagedCredential != null && !currentPageHost.equalsIgnoreCase(stagedCredential.host)) {
                        stagedCredential = null;
                        stagedCredentialAt = 0L;
                    }
                    mediaSniffer.beginPage(url);
                    if (isAdBlockActiveForHost(currentPageHost) && adBlock.requiresEarlyCosmetic(currentPageHost)) {
                        scheduleCosmeticInjection(view, currentPageHost);
                    }
                }
                blockedAtPageStart = adBlock.getBlockedCount();
                if (view == webView && refreshButton != null) {
                    refreshButton.animate().cancel();
                    refreshButton.animate().rotationBy(180f).setDuration(reduceMotion() ? 100L : 220L).start();
                }
                advanceNavigationSequence();
                pageCommitted = false;
                pageFinished = false;
                preparedInjection = null;
                schedulePageEnhancements(url, navigationSequence);
                requestChromeUpdate();
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                if (view != webView) return;
                pageCommitted = true;
                installLiveMediaCapture(view);
                injectPreparedStart(navigationSequence);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isHomeUrl(url)) verifyTrustedHome(view);
                updateTabForView(view, url, view.getTitle());
                if (url != null) {
                    String finishedHost = hostOf(url);
                    pageHosts.put(view, finishedHost);
                    adBlockActiveByView.put(view, Boolean.valueOf(isAdBlockActiveForHost(finishedHost)));
                }
                if (view != webView) return;
                if (url != null) {
                    currentPageUrl = url;
                    currentPageHost = hostOf(url);
                }
                pageFinished = true;
                pageCommitted = true;
                installLiveMediaCapture(view);
                if (refreshButton != null) refreshButton.animate().rotation(0f).setDuration(120L).start();
                injectPreparedStart(navigationSequence);
                injectPreparedEnd(navigationSequence);
                if (dataStore != null && !isHomeUrl(url)) dataStore.recordVisit(view.getTitle(), url);
                updateCurrentTab(url, view.getTitle());
                persistSession();
                installCredentialExperience(view, url, navigationSequence);
                requestChromeUpdate();
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                updateTabForView(view, url, view.getTitle());
                if (url != null) pageHosts.put(view, hostOf(url));
                if (view != webView) return;
                if (url != null) {
                    currentPageUrl = url;
                    currentPageHost = hostOf(url);
                }
                updateCurrentTab(url, view.getTitle());
                if (pageFinished) installCredentialExperience(view, url, navigationSequence);
                requestChromeUpdate();
            }

            public boolean onRenderProcessGone(final WebView view, android.webkit.RenderProcessGoneDetail detail) {
                if (!rendererRecoveryPending) {
                    rendererRecoveryPending = true;
                    uiHandler.post(new Runnable() {
                        @Override public void run() { recoverFromRendererLoss(); }
                    });
                }
                return true;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                if (view == webView) {
                    toast("证书验证失败，已停止加载");
                    String failed = error == null ? currentPageUrl : error.getUrl();
                    if (hostOf(failed).equals(currentPageHost)) {
                        maybeOfferCompatibilityMode(currentPageUrl, "主页面证书或 HTTPS 加载失败");
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (view == webView && request != null && request.isForMainFrame() &&
                        error != null && compatibilityRelevantError(error.getErrorCode())) {
                    String failed = request.getUrl() == null ? currentPageUrl : request.getUrl().toString();
                    maybeOfferCompatibilityMode(failed, "主页面加载失败（" + error.getErrorCode() + "）");
                }
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, final android.webkit.HttpAuthHandler handler, String host, String realm) {
                if (view != webView || handler == null) { if (handler != null) handler.cancel(); return; }
                final EditText username = new EditText(MainActivity.this);
                username.setHint("用户名");
                username.setSingleLine(true);
                final EditText password = new EditText(MainActivity.this);
                password.setHint("密码");
                password.setSingleLine(true);
                password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                LinearLayout fields = new LinearLayout(MainActivity.this);
                fields.setOrientation(LinearLayout.VERTICAL);
                fields.setPadding(dp(18), 0, dp(18), 0);
                fields.addView(username);
                fields.addView(password);
                DenyDialog deny = new DenyDialog(handler, null);
                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this).setTitle("网站身份验证")
                        .setMessage(host + (realm == null || realm.length() == 0 ? "" : " · " + realm)).setView(fields)
                        .setPositiveButton("登录", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) { handler.proceed(username.getText().toString(), password.getText().toString()); }
                        }).setNegativeButton("取消", deny).setOnCancelListener(deny).create();
                secureDialog(dialog);
                dialog.show();
            }
        });

        target.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (view == webView) {
                    // A queued or merely accepted navigation commonly reports exactly 10%.
                    // Crossing that boundary proves the main-frame load made forward progress.
                    InitialNavigationGuard guard = initialNavigationGuards.get(view);
                    String url = view.getUrl();
                    if (newProgress > 10 && guard != null && guard.acknowledge(url) && startupNavigationAt != 0L)
                        recordStartupNavigation("首航已响应", url);
                    scheduleProgressUpdate(newProgress);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                updateTabForView(view, view.getUrl(), title);
                if (view == webView) requestChromeUpdate();
            }

            @Override
            public void onReceivedIcon(WebView view, android.graphics.Bitmap icon) {
                if (icon == null || favicons == null || scriptExecutor == null || scriptExecutor.isShutdown()) return;
                final String host = hostOf(view == null ? null : view.getUrl());
                if (host.length() == 0) return;
                final android.graphics.Bitmap copy;
                try { copy = icon.copy(android.graphics.Bitmap.Config.ARGB_8888, false); }
                catch (RuntimeException error) { return; }
                executeTask(scriptExecutor, new Runnable() {
                    @Override public void run() {
                        try { favicons.put(host, copy); }
                        finally { copy.recycle(); }
                    }
                });
            }

            @Override
            public boolean onJsPrompt(final WebView view, String url, String message,
                                      final String defaultValue, JsPromptResult result) {
                if (handleCredentialAutofillPrompt(view, message, result)) return true;
                if (handleCredentialCapturePrompt(view, message, result)) return true;
                if (HOME_TOKEN.equals(message)) {
                    boolean accepted = result != null && view == webView && isHomeUrl(view.getUrl()) &&
                            trustedHomeViews.contains(view) && !customHomeViews.contains(view) &&
                            defaultValue != null && defaultValue.startsWith("median://");
                    if (result != null) result.confirm("");
                    if (accepted) view.post(new Runnable() {
                        @Override public void run() {
                            if (activityDestroyed || view != webView || !isHomeUrl(view.getUrl()) ||
                                    !trustedHomeViews.contains(view) || customHomeViews.contains(view)) return;
                            handleNavigation(view, defaultValue);
                        }
                    });
                    return true;
                }
                if (handleScriptBridgePrompt(view, url, message, result)) return true;
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (target != webView) { callback.onCustomViewHidden(); return; }
                enterFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                if (target == webView) exitFullscreen();
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                handleWebPermissionRequest(target, request);
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (request != null && request == pendingPermissionRequest) {
                    pendingPermissionRequest = null;
                    pendingWebPermissionResources = null;
                    pendingPermissionView = null;
                    pendingPermissionOrigin = null;
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                handleGeolocationRequest(target, origin, callback);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                SiteSettingsStore.SiteSettings site = siteSettingsStore.forHost(pageHostFor(view));
                int popups = site.get(SiteSettingsStore.POPUPS);
                if (popups == SiteSettingsStore.BLOCK || (!isUserGesture && popups != SiteSettingsStore.ALLOW) ||
                        resultMsg == null || !(resultMsg.obj instanceof WebView.WebViewTransport) || tabs.size() >= MAX_TABS) return false;
                BrowserTab tab = new BrowserTab();
                WebView popup = createConfiguredWebView();
                tab.liveView = popup;
                tab.lastActiveAt = SystemClock.uptimeMillis();
                tabs.add(tab);
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                activateTab(tabs.size() - 1);
                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                BrowserTab tab = tabForView(window);
                if (tab == null) return;
                int index = tabs.indexOf(tab);
                if (index == currentTabIndex) {
                    closeCurrentTab();
                } else if (index >= 0) {
                    tabs.remove(index);
                    destroyTabView(tab, false);
                    if (index < currentTabIndex) currentTabIndex--;
                    renderedTabCount = null;
                    persistSession();
                    requestChromeUpdate();
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (view != webView) { callback.onReceiveValue(null); return true; }
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                Intent intent;
                try {
                    intent = params.createIntent();
                } catch (Exception e) {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileChooserCallback = null;
                    toast("没有可用的文件选择器");
                    return false;
                }
            }
        });

        target.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                                  String mimetype, long contentLength) {
                if (looksLikeUserScript(url)) {
                    installScriptFromUrl(url);
                    return;
                }
                enqueueDownload(target, url, userAgent, contentDisposition, mimetype, contentLength);
            }
        });

    }

    private boolean handleWebViewLongPress(final WebView source) {
        WebView.HitTestResult hit = source == null ? null : source.getHitTestResult();
        if (hit == null) return false;
        int type = hit.getType();
        final boolean image = type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE;
        boolean link = type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE;
        final String url = hit.getExtra();
        if ((!image && !link) || url == null || (!url.startsWith("https://") && !url.startsWith("http://"))) return false;
        if (!image && looksLikeUserScript(url)) {
            showActionSheet("用户脚本链接", hostOf(url),
                    new String[] { "安装并检查脚本", "复制脚本地址", "在新标签页打开" },
                    new int[] { BrowserIconView.SCRIPT, BrowserIconView.PLUS, BrowserIconView.TABS },
                    new SheetHandler() {
                        @Override public void onItem(int which) {
                            if (which == 0) installScriptFromUrl(url);
                            else if (which == 1) {
                                ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("UserScript", url));
                                toast("脚本地址已复制");
                            } else openUrlInNewTab(url, true);
                        }
                    });
            return true;
        }
        String[] items = new String[] { "在新标签页打开", "在后台标签页打开", "复制地址", "分享地址", image ? "下载图片" : "下载链接" };
        int[] icons = new int[] { BrowserIconView.PLUS, BrowserIconView.TABS, BrowserIconView.PLUS, BrowserIconView.SHARE, BrowserIconView.STORAGE };
        showActionSheet(image ? "图片与链接" : "链接操作", hostOf(url), items, icons, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) openUrlInNewTab(url, true);
                else if (which == 1) openUrlInNewTab(url, false);
                else if (which == 2) {
                    android.content.ClipboardManager manager = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (manager != null) manager.setPrimaryClip(android.content.ClipData.newPlainText("链接", url));
                    toast("地址已复制");
                } else if (which == 3) {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT, url);
                    try { startActivity(Intent.createChooser(share, "分享链接")); } catch (Exception e) { toast("没有可用的分享应用"); }
                } else {
                    String ua = source.getSettings().getUserAgentString();
                    enqueueDownload(source, url, ua, null, image ? "image/*" : "application/octet-stream");
                }
            }
        });
        return true;
    }

    private void openUrlInNewTab(String url, boolean foreground) {
        if (url == null || (!url.startsWith("https://") && !url.startsWith("http://"))) return;
        if (tabs.size() >= MAX_TABS) { toast("最多允许 " + MAX_TABS + " 个标签页"); return; }
        BrowserTab tab = new BrowserTab();
        tab.url = url;
        String host = hostOf(url);
        tab.title = host.length() == 0 ? "新标签页" : host;
        tabs.add(tab);
        if (foreground) activateTab(tabs.size() - 1);
        else {
            renderedTabCount = null;
            persistSession();
            requestChromeUpdate();
            toast("已在后台标签页打开");
        }
    }

    private void enqueueDownload(WebView source, String url, String userAgent,
                                 String contentDisposition, String mimetype) {
        enqueueDownload(source, url, userAgent, contentDisposition, mimetype, 0L);
    }

    private void enqueueDownload(WebView source, String url, String userAgent,
                                 String contentDisposition, String mimetype, long expectedTotalBytes) {
        enqueueDownloadAdvanced(url, userAgent, contentDisposition, mimetype, "",
                downloadContextHeaders(source, url), false, expectedTotalBytes);
    }

    private boolean enqueueDownloadAdvanced(String url, String userAgent, String contentDisposition,
                                            String mimetype, String preferredName, Map<String, String> extraHeaders) {
        return enqueueDownloadAdvanced(url, userAgent, contentDisposition, mimetype, preferredName, extraHeaders, false);
    }

    private boolean enqueueDownloadAdvanced(String url, String userAgent, String contentDisposition,
                                            String mimetype, String preferredName, Map<String, String> extraHeaders,
                                            boolean publicOnly) {
        return enqueueDownloadAdvanced(url, userAgent, contentDisposition, mimetype, preferredName,
                extraHeaders, publicOnly, 0L);
    }

    private boolean enqueueDownloadAdvanced(String url, String userAgent, String contentDisposition,
                                            String mimetype, String preferredName, Map<String, String> extraHeaders,
                                            boolean publicOnly, long expectedTotalBytes) {
        try {
            url = NetworkSecurity.parseHttpUrl(url).toString();
        } catch (Exception invalidUrl) {
            toast("只允许下载有效的 HTTP(S) 资源");
            return false;
        }
        DownloadStore.Item duplicate = services.downloads().findBlockingDuplicate(url, 15000L);
        if (duplicate != null) {
            if (DownloadStore.STATUS_FAILED.equals(duplicate.status))
                toast("相同地址刚刚失败，请在下载中心重试");
            else if (DownloadStore.STATUS_PAUSED.equals(duplicate.status))
                toast("相同任务已暂停，请在下载中心继续");
            else toast("相同资源已经在下载中");
            return false;
        }
        String filename = uniqueDownloadName(DownloadFileTypes.resolveName(
                url, contentDisposition, mimetype, "", preferredName));
        String resolvedMime = DownloadFileTypes.resolveMime(filename, mimetype, "");
        try {
            long expected = Math.max(0L, expectedTotalBytes);
            enqueueAdaptiveFallback(url, userAgent, filename, resolvedMime, extraHeaders, publicOnly, expected);
            toast("开始下载：" + filename + (expected > 0L ? " · " + humanBytes(expected) : ""));
            return true;
        } catch (Exception failure) {
            toast("下载启动失败：" + safeMessage(failure));
        }
        return false;
    }

    private void enqueueAdaptiveFallback(String url, String userAgent, String filename, String mime,
                                         Map<String, String> extraHeaders, boolean publicOnly,
                                         long expectedTotalBytes) throws Exception {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 409);
        boolean wifiOnly = prefs.getBoolean("download_wifi_only", false);
        boolean allowRoaming = prefs.getBoolean("download_allow_roaming", false);
        boolean chargingOnly = prefs.getBoolean("download_charging_only", false);
        String persistedHeaders = downloadHeadersJson(extraHeaders);
        long id = services.downloads().addAdaptive(url, filename, mime,
                userAgent == null ? "" : userAgent, persistedHeaders,
                wifiOnly, allowRoaming, chargingOnly, publicOnly, expectedTotalBytes);
        try {
            Intent service = new Intent(this, AdaptiveDownloadService.class);
            service.setAction(AdaptiveDownloadService.ACTION_DOWNLOAD);
            service.putExtra(AdaptiveDownloadService.EXTRA_ID, id);
            service.putExtra(AdaptiveDownloadService.EXTRA_URL, url);
            service.putExtra(AdaptiveDownloadService.EXTRA_NAME, filename);
            service.putExtra(AdaptiveDownloadService.EXTRA_MIME, mime);
            service.putExtra(AdaptiveDownloadService.EXTRA_USER_AGENT, userAgent == null ? "" : userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            service.putExtra(AdaptiveDownloadService.EXTRA_COOKIE, cookie == null ? "" : cookie);
            service.putExtra(AdaptiveDownloadService.EXTRA_HEADERS, persistedHeaders);
            service.putExtra(AdaptiveDownloadService.EXTRA_WIFI_ONLY, wifiOnly);
            service.putExtra(AdaptiveDownloadService.EXTRA_ALLOW_ROAMING, allowRoaming);
            service.putExtra(AdaptiveDownloadService.EXTRA_CHARGING_ONLY, chargingOnly);
            service.putExtra(AdaptiveDownloadService.EXTRA_PUBLIC_ONLY, publicOnly);
            service.putExtra(AdaptiveDownloadService.EXTRA_TOTAL_BYTES, expectedTotalBytes);
            startForegroundService(service);
        } catch (Exception error) {
            services.downloads().remove(id);
            throw error;
        }
    }

    private Map<String, String> downloadContextHeaders(WebView source, String downloadUrl) {
        HashMap<String, String> headers = new HashMap<String, String>();
        String pageUrl = source == null || source.getUrl() == null ? "" : source.getUrl();
        try {
            URL page = NetworkSecurity.parseHttpUrl(pageUrl);
            URL download = NetworkSecurity.parseHttpUrl(downloadUrl);
            if (NetworkSecurity.sameOrigin(page, download)) headers.put("Referer", page.toString());
            else if (!"https".equalsIgnoreCase(page.getProtocol()) || "https".equalsIgnoreCase(download.getProtocol())) {
                String origin = page.getProtocol() + "://" + page.getHost();
                int port = page.getPort();
                if (port >= 0 && port != page.getDefaultPort()) origin += ":" + port;
                headers.put("Referer", origin + "/");
            }
        } catch (Exception ignored) {}
        return headers;
    }

    private String uniqueDownloadName(String requested) {
        String name = DownloadFileTypes.sanitize(requested);
        if (name.length() == 0) name = "download.bin";
        HashSet<String> used = new HashSet<String>();
        for (DownloadStore.Item item : services.downloads().getAll()) used.add(item.filename.toLowerCase(Locale.US));
        if (!used.contains(name.toLowerCase(Locale.US))) return name;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i <= 999; i++) {
            String candidate = base + " (" + i + ")" + extension;
            if (!used.contains(candidate.toLowerCase(Locale.US))) return candidate;
        }
        return base + '-' + System.currentTimeMillis() + extension;
    }

    private String downloadHeadersJson(Map<String, String> headers) {
        JSONObject object = new JSONObject();
        if (headers == null) return object.toString();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String name = header.getKey() == null ? "" : header.getKey().trim();
            String value = header.getValue() == null ? "" : header.getValue();
            if (!NetworkSecurity.validHeader(name, value) || NetworkSecurity.isForbiddenRequestHeader(name) ||
                    NetworkSecurity.isCredentialHeader(name) || "user-agent".equalsIgnoreCase(name)) continue;
            try { object.put(name, value); } catch (Exception ignored) {}
        }
        return object.toString();
    }

    private boolean handleScriptBridgePrompt(final WebView source, String pageUrl, String message, JsPromptResult result) {
        final String prefix = "__MEDIAN_BRIDGE__";
        if (message == null || !message.startsWith(prefix)) return false;
        String response;
        try {
            JSONObject request = new JSONObject(message.substring(prefix.length()));
            String token = request.optString("t", "");
            final String scriptId = request.optString("s", "");
            String action = request.optString("a", "");
            JSONObject args = request.optJSONObject("p");
            if (args == null) args = new JSONObject();
            String currentUrl = source == null ? "" : source.getUrl();
            String expectedToken = source == null ? null : scriptBridgeTokens.get(source);
            if (source == null || expectedToken == null || token.length() < 32 || !token.equals(expectedToken) ||
                    currentUrl == null || !scriptStore.isRunnable(scriptId) || !scriptStore.matchesUrl(scriptId, currentUrl)) {
                response = bridgeError("unauthorized");
            } else if (!scriptStore.allowsApi(scriptId, action)) {
                response = bridgeError("grant denied");
            } else if ("getValue".equals(action)) {
                String key = args.optString("k", "");
                boolean exists = services.scriptValues().contains(scriptId, key);
                String value = services.scriptValues().getJson(scriptId, key, args.optString("d", "null"));
                response = new JSONObject().put("ok", true).put("exists", exists).put("v", value).toString();
            } else if ("setValue".equals(action)) {
                response = bridgeOk(services.scriptValues().setJson(scriptId, args.optString("k", ""), args.optString("v", "null")));
            } else if ("deleteValue".equals(action)) {
                response = bridgeOk(services.scriptValues().delete(scriptId, args.optString("k", "")));
            } else if ("listValues".equals(action)) {
                response = new JSONObject().put("ok", true).put("v", new JSONArray(services.scriptValues().listJson(scriptId))).toString();
            } else if ("openTab".equals(action)) {
                String url = args.optString("u", "");
                boolean allowed = isHttpUrl(url);
                if (allowed) openUrlInNewTab(url, args.optBoolean("active", true));
                response = bridgeOk(allowed);
            } else if ("clipboard".equals(action)) {
                android.content.ClipboardManager manager = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                String value = args.optString("v", "");
                boolean allowed = source == webView && activityResumed && manager != null;
                if (allowed) manager.setPrimaryClip(android.content.ClipData.newPlainText("UserScript", value));
                response = bridgeOk(allowed);
            } else if ("notification".equals(action)) {
                String text = args.optString("text", "");
                String notice = args.optString("title", "Median") + (text.length() == 0 ? "" : "：" + text);
                toast(notice.substring(0, Math.min(240, notice.length())));
                response = bridgeOk(true);
            } else if ("cookie".equals(action)) {
                response = handleScriptCookie(currentUrl, args);
            } else if ("download".equals(action)) {
                String url = args.optString("u", "");
                boolean allowed = isHttpUrl(url) && scriptStore.canConnect(scriptId, url, currentUrl);
                Map<String, String> headers = jsonStringMap(args.optJSONObject("h"));
                boolean pageIsLocal = false;
                try { pageIsLocal = NetworkSecurity.isObviouslyLocalHost(NetworkSecurity.normalizedHost(NetworkSecurity.parseHttpUrl(currentUrl))); }
                catch (Exception ignored) {}
                response = bridgeOk(allowed && enqueueDownloadAdvanced(url, source.getSettings().getUserAgentString(), null,
                        "application/octet-stream", args.optString("n", ""), headers, !pageIsLocal));
            } else if ("xhr".equals(action)) {
                String url = args.optString("u", "");
                String callbackId = args.optString("i", "");
                boolean allowed = callbackId.matches("[A-Za-z0-9_-]{1,96}") && isHttpUrl(url) &&
                        scriptStore.canConnect(scriptId, url, currentUrl);
                if (allowed) startScriptRequest(source, token, scriptId, callbackId, args, currentUrl);
                response = allowed ? bridgeOk(true) : bridgeError("@connect denied");
            } else if ("xhrAbort".equals(action)) {
                HttpURLConnection connection = scriptConnections.remove(token + "|" + args.optString("i", ""));
                if (connection != null) connection.disconnect();
                response = bridgeOk(true);
            } else response = bridgeError("unknown action");
        } catch (Exception e) {
            response = bridgeError("bad request");
        }
        result.confirm(response);
        return true;
    }

    private String handleScriptCookie(String pageUrl, JSONObject args) {
        try {
            URL page = NetworkSecurity.parseHttpUrl(pageUrl);
            String host = NetworkSecurity.normalizedHost(page);
            String operation = args.optString("op", "list");
            JSONObject details = args.optJSONObject("d");
            if (details == null) details = new JSONObject();
            CookieManager manager = CookieManager.getInstance();
            if ("list".equals(operation)) {
                JSONArray cookies = new JSONArray();
                String filter = details.optString("name", "");
                String header = manager.getCookie(pageUrl);
                if (header != null) for (String part : header.split(";\\s*")) {
                    int equals = part.indexOf('=');
                    String name = equals < 0 ? part.trim() : part.substring(0, equals).trim();
                    if (name.length() == 0 || (filter.length() > 0 && !filter.equals(name))) continue;
                    String value = equals < 0 ? "" : part.substring(equals + 1);
                    cookies.put(new JSONObject().put("name", name).put("value", value).put("domain", host)
                            .put("path", "/").put("secure", "https".equalsIgnoreCase(page.getProtocol()))
                            .put("session", true));
                }
                return new JSONObject().put("ok", true).put("v", cookies).toString();
            }
            String name = details.optString("name", "").trim();
            String value = details.optString("value", "");
            String path = details.optString("path", "/");
            String domain = details.optString("domain", "").trim().toLowerCase(Locale.US);
            if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,256}") || !safeCookiePart(value, false) ||
                    !safeCookiePart(path, true)) return bridgeError("invalid cookie");
            if (domain.startsWith(".")) domain = domain.substring(1);
            if (domain.length() > 0 && !(host.equals(domain) || host.endsWith("." + domain)))
                return bridgeError("cookie domain denied");
            StringBuilder cookie = new StringBuilder(name).append('=');
            if (!"delete".equals(operation)) cookie.append(value);
            cookie.append("; Path=").append(path);
            if (domain.length() > 0) cookie.append("; Domain=").append(domain);
            if ("delete".equals(operation)) cookie.append("; Max-Age=0");
            else {
                if (details.optBoolean("secure", false)) cookie.append("; Secure");
                if (details.has("expirationDate")) {
                    long maxAge = Math.max(0L, (long) details.optDouble("expirationDate", 0) - System.currentTimeMillis() / 1000L);
                    cookie.append("; Max-Age=").append(maxAge);
                }
                String sameSite = details.optString("sameSite", "");
                if ("strict".equalsIgnoreCase(sameSite)) cookie.append("; SameSite=Strict");
                else if ("lax".equalsIgnoreCase(sameSite)) cookie.append("; SameSite=Lax");
                else if ("none".equalsIgnoreCase(sameSite) && details.optBoolean("secure", false)) cookie.append("; SameSite=None");
            }
            if (!"set".equals(operation) && !"delete".equals(operation)) return bridgeError("unknown cookie action");
            manager.setCookie(pageUrl, cookie.toString());
            scheduleCookieFlush();
            return bridgeOk(true);
        } catch (Exception error) {
            return bridgeError("cookie failed");
        }
    }

    private static boolean safeCookiePart(String value, boolean path) {
        if (value == null || value.length() > 4096 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf(';') >= 0) return false;
        return !path || value.startsWith("/");
    }

    private void startScriptRequest(final WebView source, final String token, final String scriptId,
                                    final String callbackId, final JSONObject args, final String pageUrl) {
        if (scriptNetworkExecutor == null || scriptNetworkExecutor.isShutdown()) return;
        executeTask(scriptNetworkExecutor, new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                String key = token + "|" + callbackId;
                try {
                    URL current = NetworkSecurity.parseHttpUrl(args.optString("u", ""));
                    URL initial = current;
                    URL page = NetworkSecurity.parseHttpUrl(pageUrl);
                    int requestedTimeout = args.optInt("to", 0);
                    int timeout = requestedTimeout <= 0 ? 20000 : requestedTimeout;
                    String method = args.optString("m", "GET").toUpperCase(Locale.US);
                    if (!method.matches("GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")) throw new IllegalArgumentException("unsupported method");
                    byte[] requestBody = args.optBoolean("b64", false) ?
                            android.util.Base64.decode(args.optString("d", ""), android.util.Base64.DEFAULT) :
                            args.optString("d", "").getBytes("UTF-8");
                    JSONObject headerObject = args.optJSONObject("h");
                    boolean anonymous = args.optBoolean("anon", false);
                    int status = 0;
                    for (int redirects = 0; redirects <= NetworkSecurity.MAX_REDIRECTS; redirects++) {
                        if (!scriptStore.canConnect(scriptId, current.toString(), pageUrl)) throw new IllegalArgumentException("@connect denied after redirect");
                        boolean pageIsLocal = NetworkSecurity.isLocalOrPrivateHost(NetworkSecurity.normalizedHost(page));
                        boolean targetIsLocal = NetworkSecurity.isLocalOrPrivateHost(NetworkSecurity.normalizedHost(current));
                        if (targetIsLocal && !pageIsLocal) throw new IllegalArgumentException("local network target denied");

                        connection = (HttpURLConnection) current.openConnection();
                        scriptConnections.put(key, connection);
                        connection.setInstanceFollowRedirects(false);
                        connection.setConnectTimeout(timeout);
                        connection.setReadTimeout(timeout);
                        connection.setUseCaches(false);
                        connection.setRequestProperty("Accept-Encoding", "identity");
                        connection.setRequestMethod(method);
                        boolean sameInitialOrigin = NetworkSecurity.sameOrigin(initial, current);
                        if (headerObject != null) {
                            Iterator<String> names = headerObject.keys();
                            while (names.hasNext()) {
                                String name = names.next();
                                String value = headerObject.optString(name, "");
                                if (!NetworkSecurity.validHeader(name, value) || NetworkSecurity.isForbiddenRequestHeader(name)) continue;
                                if (!sameInitialOrigin && NetworkSecurity.isCredentialHeader(name)) continue;
                                connection.setRequestProperty(name, value);
                            }
                        }
                        if (!anonymous) {
                            String cookie = CookieManager.getInstance().getCookie(current.toString());
                            if (cookie != null && cookie.length() > 0) connection.setRequestProperty("Cookie", cookie);
                        }
                        if (requestBody.length > 0 && !"GET".equals(method) && !"HEAD".equals(method)) {
                            connection.setDoOutput(true);
                            OutputStream requestOutput = connection.getOutputStream();
                            try { requestOutput.write(requestBody); } finally { requestOutput.close(); }
                        }

                        status = connection.getResponseCode();
                        if (!anonymous) storeResponseCookies(current, connection);
                        if (!NetworkSecurity.isRedirect(status)) break;
                        if (redirects == NetworkSecurity.MAX_REDIRECTS) throw new IllegalArgumentException("too many redirects");
                        URL next = NetworkSecurity.resolveRedirect(current, connection.getHeaderField("Location"), false);
                        connection.disconnect();
                        connection = null;
                        if ((status == 301 || status == 302 || status == 303) && !"GET".equals(method) && !"HEAD".equals(method)) {
                            method = "GET";
                            requestBody = new byte[0];
                        }
                        current = next;
                    }
                    if (connection == null) throw new IllegalStateException("request failed");
                    InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream(32768);
                    byte[] buffer = new byte[16384];
                    int read, total = 0;
                    long declared = connection.getContentLengthLong();
                    long lastProgress = 0;
                    if (input != null) try {
                        while ((read = input.read(buffer)) != -1) {
                            total += read;
                            output.write(buffer, 0, read);
                            if (total - lastProgress >= 262144) {
                                lastProgress = total;
                                JSONObject progress = new JSONObject().put("loaded", total).put("total", Math.max(0, declared)).put("lengthComputable", declared > 0);
                                dispatchScriptEvent(source, token, scriptId, callbackId, "progress", progress);
                            }
                        }
                    } finally { input.close(); }
                    byte[] bytes = output.toByteArray();
                    String responseType = args.optString("rt", "text");
                    String contentType = connection.getContentType() == null ? "" : connection.getContentType();
                    String text = decodeResponseText(bytes, contentType);
                    boolean binary = "arraybuffer".equalsIgnoreCase(responseType) || "blob".equalsIgnoreCase(responseType);
                    JSONObject payload = new JSONObject();
                    payload.put("status", status);
                    payload.put("statusText", connection.getResponseMessage() == null ? "" : connection.getResponseMessage());
                    payload.put("finalUrl", connection.getURL().toString());
                    payload.put("responseHeaders", responseHeaders(connection));
                    payload.put("contentType", contentType);
                    payload.put("responseText", binary ? "" : text);
                    payload.put("response", binary ? android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP) : text);
                    dispatchScriptEvent(source, token, scriptId, callbackId, "load", payload);
                } catch (java.net.SocketTimeoutException timeoutError) {
                    dispatchScriptEvent(source, token, scriptId, callbackId, "timeout", errorPayload(timeoutError));
                } catch (Exception error) {
                    String event = scriptConnections.containsKey(key) ? "error" : "abort";
                    dispatchScriptEvent(source, token, scriptId, callbackId, event, errorPayload(error));
                } finally {
                    scriptConnections.remove(key);
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private JSONObject errorPayload(Exception error) {
        JSONObject payload = new JSONObject();
        try { payload.put("error", safeMessage(error)); } catch (Exception ignored) {}
        return payload;
    }

    private void dispatchScriptEvent(final WebView source, final String token, final String scriptId,
                                     final String callbackId, final String event, final JSONObject payload) {
        uiHandler.post(new Runnable() {
            @Override public void run() {
                String expected = source == null ? null : scriptBridgeTokens.get(source);
                String current = source == null ? null : source.getUrl();
                if (source == null || expected == null || !token.equals(expected) || !scriptStore.matchesUrl(scriptId, current)) return;
                String objectName = UserScriptStore.dispatchObjectName(token, scriptId);
                String js = "(function(){var d=window[" + JSONObject.quote(objectName) + "];if(d&&typeof d.dispatch==='function')d.dispatch(" +
                        JSONObject.quote(token) + "," + JSONObject.quote(callbackId) + "," + JSONObject.quote(event) + "," +
                        (payload == null ? "{}" : payload.toString()) + ");})();";
                try { source.evaluateJavascript(js, null); } catch (RuntimeException ignored) {}
            }
        });
    }

    private void storeResponseCookies(URL url, HttpURLConnection connection) {
        Map<String, List<String>> fields = connection.getHeaderFields();
        if (fields == null) return;
        CookieManager manager = CookieManager.getInstance();
        boolean changed = false;
        for (Map.Entry<String, List<String>> field : fields.entrySet()) {
            String name = field.getKey();
            if (name == null || field.getValue() == null ||
                    !("set-cookie".equalsIgnoreCase(name) || "set-cookie2".equalsIgnoreCase(name))) continue;
            for (String value : field.getValue()) {
                if (value != null && value.length() > 0) {
                    manager.setCookie(url.toString(), value);
                    changed = true;
                }
            }
        }
        if (changed) scheduleCookieFlush();
    }

    private void scheduleCookieFlush() {
        if (!cookieFlushPending.compareAndSet(false, true)) return;
        uiHandler.postDelayed(new Runnable() {
            @Override public void run() {
                cookieFlushPending.set(false);
                try { CookieManager.getInstance().flush(); } catch (RuntimeException ignored) {}
            }
        }, 1000L);
    }

    private static String decodeResponseText(byte[] bytes, String contentType) {
        Charset charset = StandardCharsets.UTF_8;
        if (contentType != null) {
            String[] parts = contentType.split(";");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.toLowerCase(Locale.US).startsWith("charset=")) continue;
                String name = trimmed.substring(8).trim().replace("\"", "");
                try { charset = Charset.forName(name); } catch (Exception ignored) {}
                break;
            }
        }
        return new String(bytes, charset);
    }

    private static String responseHeaders(HttpURLConnection connection) {
        StringBuilder out = new StringBuilder();
        Map<String, List<String>> fields = connection.getHeaderFields();
        if (fields == null) return "";
        for (Map.Entry<String, List<String>> field : fields.entrySet()) {
            if (field.getKey() == null || field.getValue() == null) continue;
            String lower = field.getKey().toLowerCase(Locale.US);
            if (lower.equals("set-cookie") || lower.equals("set-cookie2") || lower.equals("proxy-authenticate")) continue;
            for (String value : field.getValue()) out.append(field.getKey()).append(": ").append(value).append("\r\n");
        }
        return out.toString();
    }

    private static Map<String, String> jsonStringMap(JSONObject object) {
        HashMap<String, String> result = new HashMap<String, String>();
        if (object == null) return result;
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            result.put(key, object.optString(key, ""));
        }
        return result;
    }

    private static String bridgeOk(boolean ok) {
        try { return new JSONObject().put("ok", ok).toString(); }
        catch (Exception ignored) { return ok ? "{\"ok\":true}" : "{\"ok\":false}"; }
    }

    private static String bridgeError(String error) {
        try { return new JSONObject().put("ok", false).put("error", error).toString(); }
        catch (Exception ignored) { return "{\"ok\":false}"; }
    }

    private static boolean isHttpUrl(String value) {
        try {
            NetworkSecurity.parseHttpUrl(value);
            return true;
        } catch (Exception ignored) { return false; }
    }

    private WebResourceResponse interceptRequest(WebView source, String requestUrl) {
        Uri uri = null;
        try { uri = Uri.parse(requestUrl); } catch (RuntimeException ignored) {}
        WebResourceResponse homeAsset = interceptHomeAsset(source, uri);
        if (homeAsset != null) return homeAsset;
        String pageHost = pageHostFor(source);
        observeMediaIfLikely(source, uri, "", pageHost);
        if (MODE_PERFORMANCE.equals(performanceMode) && performanceNetworkDirect) return null;
        if (cachedAdBlockActive(source, pageHost) &&
                (uri == null ? adBlock.shouldBlock(requestUrl, pageHost) : adBlock.shouldBlock(uri, pageHost))) {
            scheduleCosmeticInjection(source, pageHost);
            return blockedResponse();
        }
        return null;
    }

    private WebResourceResponse interceptRequest(WebView source, Uri requestUri) {
        WebResourceResponse homeAsset = interceptHomeAsset(source, requestUri);
        if (homeAsset != null) return homeAsset;
        String pageHost = pageHostFor(source);
        observeMediaIfLikely(source, requestUri, "", pageHost);
        if (MODE_PERFORMANCE.equals(performanceMode) && performanceNetworkDirect) return null;
        if (cachedAdBlockActive(source, pageHost) && adBlock.shouldBlock(requestUri, pageHost)) {
            scheduleCosmeticInjection(source, pageHost);
            return blockedResponse();
        }
        return null;
    }

    private WebResourceResponse interceptRequest(WebView source, WebResourceRequest request) {
        Uri requestUri = request.getUrl();
        WebResourceResponse homeAsset = interceptHomeAsset(source, requestUri);
        if (homeAsset != null) return homeAsset;
        if (customHomeViews.contains(source) && !request.isForMainFrame() && requestUri != null &&
                ("http".equalsIgnoreCase(requestUri.getScheme()) || "https".equalsIgnoreCase(requestUri.getScheme())) &&
                !"median.invalid".equalsIgnoreCase(requestUri.getHost())) return blockedResponse();
        String pageHost = pageHostFor(source);
        boolean adBlockActive = cachedAdBlockActive(source, pageHost);
        Map<String, String> requestHeaders = request.getRequestHeaders();
        String accept = shouldReadAcceptHeader(requestUri, request.isForMainFrame()) ? requestHeader(requestHeaders, "Accept") : "";
        String mediaHint = mediaRequestHint(requestHeaders, accept);
        observeMediaIfLikely(source, requestUri, mediaHint, pageHost);
        if (MODE_PERFORMANCE.equals(performanceMode) && performanceNetworkDirect) return null;
        if (adBlockActive && adBlock.shouldBlock(requestUri, pageHost, accept, request.isForMainFrame())) {
            scheduleCosmeticInjection(source, pageHost);
            return blockedResponse();
        }
        return null;
    }

    private boolean cachedAdBlockActive(WebView source, String pageHost) {
        if (source == null) return isAdBlockActiveForHost(pageHost);
        Boolean cached = adBlockActiveByView.get(source);
        if (cached != null) return cached.booleanValue();
        boolean active = isAdBlockActiveForHost(pageHost);
        adBlockActiveByView.put(source, Boolean.valueOf(active));
        return active;
    }

    private static String requestHeader(Map<String, String> headers, String wanted) {
        if (headers == null || headers.size() == 0) return "";
        String exact = headers.get(wanted);
        if (exact != null) return exact;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (wanted.equalsIgnoreCase(entry.getKey())) return entry.getValue() == null ? "" : entry.getValue();
        }
        return "";
    }

    private static String mediaRequestHint(Map<String, String> headers, String accept) {
        String destination = requestHeader(headers, "Sec-Fetch-Dest");
        if ("video".equalsIgnoreCase(destination)) return "video/*";
        if ("audio".equalsIgnoreCase(destination)) return "audio/*";
        if (requestHeader(headers, "Range").toLowerCase(Locale.US).startsWith("bytes=") &&
                (accept == null || accept.length() == 0 || "*/*".equals(accept.trim())))
            return "application/x-median-range";
        return accept == null ? "" : accept;
    }

    private static boolean shouldReadAcceptHeader(Uri uri, boolean mainFrame) {
        if (mainFrame) return true;
        if (uri == null) return false;
        String path = uri.getPath();
        if (path == null) return true;
        String lower = path.toLowerCase(Locale.US);
        return !(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") ||
                lower.endsWith(".webp") || lower.endsWith(".svg") || lower.endsWith(".css") || lower.endsWith(".js") ||
                lower.endsWith(".woff") || lower.endsWith(".woff2") || lower.endsWith(".ttf") || lower.endsWith(".ico") ||
                lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8") || lower.endsWith(".mp3") ||
                lower.endsWith(".m4a") || lower.endsWith(".wav"));
    }

    private void observeMediaIfLikely(WebView source, Uri uri, String accept, String pageHost) {
        if (source != webView || uri == null) return;
        if (!MediaResourceSniffer.shouldInspectRequest(uri.getEncodedPath(), uri.getEncodedQuery(), accept)) return;
        mediaSniffer.observe(uri.toString(), accept, pageHost, "request", 0, 0, 0d);
    }

    private void installLiveMediaCapture(WebView target) {
        if (target == null || target != webView || isHomeUrl(target.getUrl())) return;
        try { target.evaluateJavascript(MediaProbeScript.install(), null); }
        catch (RuntimeException ignored) {}
    }

    private WebResourceResponse interceptHomeAsset(WebView source, Uri uri) {
        if (source == null || uri == null || !trustedHomeViews.contains(source) ||
                !"https".equalsIgnoreCase(uri.getScheme()) || !"median.invalid".equalsIgnoreCase(uri.getHost())) return null;
        if ("/home-custom".equals(uri.getPath())) {
            if (!customHomeViews.contains(source)) return blockedResponse();
            String html = prefs == null ? "" : prefs.getString("home_custom_html", "");
            if (!CustomHomeHtml.valid(html)) return blockedResponse();
            byte[] data = CustomHomeHtml.document(html).getBytes(StandardCharsets.UTF_8);
            return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(data));
        }
        if ("/favicon".equals(uri.getPath())) {
            InputStream input = favicons == null ? null : favicons.open(uri.getQueryParameter("host"));
            if (input == null) return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                    Collections.<String, String>emptyMap(), new ByteArrayInputStream(EMPTY_RESPONSE));
            return new WebResourceResponse("image/png", null, input);
        }
        if (homeImages == null) return blockedResponse();
        HomeImageStore.Kind kind;
        if ("/home-wallpaper".equals(uri.getPath())) kind = HomeImageStore.Kind.WALLPAPER;
        else if ("/home-logo".equals(uri.getPath())) kind = HomeImageStore.Kind.LOGO;
        else return null;
        try {
            InputStream input = homeImages.open(kind);
            return input == null ? blockedResponse() : new WebResourceResponse(homeImages.mime(kind), null, input);
        } catch (Exception ignored) {
            return blockedResponse();
        }
    }

    private void scheduleCosmeticInjection(final WebView source, final String pageHost) {
        if (source == null || pageHost == null || pageHost.length() == 0) return;
        // A page can block hundreds of resources. Claim the one injection slot before
        // assembling selectors so only the first callback performs that work.
        if (cosmeticInjected.putIfAbsent(source, Boolean.TRUE) != null) return;
        final String css = adBlock.cosmeticCssForHost(pageHost);
        if (css.length() == 0) return;
        uiHandler.post(new Runnable() {
            @Override public void run() {
                if (!pageHost.equalsIgnoreCase(pageHostFor(source))) {
                    cosmeticInjected.remove(source);
                    return;
                }
                String script = "(function f(){var p=document.head||document.documentElement;if(!p){setTimeout(f,30);return;}var s=document.getElementById('__median_adblock');if(!s&&" + (css.length() > 0 ? "true" : "false") + "){s=document.createElement('style');s.id='__median_adblock';s.textContent=" +
                        JSONObject.quote(css) + ";p.appendChild(s);}})();";
                try { source.evaluateJavascript(script, null); } catch (RuntimeException ignored) {}
            }
        });
    }

    private WebResourceResponse blockedResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(EMPTY_RESPONSE));
    }

    private boolean handleNavigation(WebView source, String url) {
        if (url == null) return false;
        if (!startupReady && url.startsWith("median://")) {
            requireStartupReady();
            return true;
        }
        Uri parsed;
        try { parsed = Uri.parse(url); } catch (RuntimeException ignored) { return true; }
        if ("median".equalsIgnoreCase(parsed.getScheme())) {
            if (!InternalNavigationPolicy.canHandleCommand(
                    source != null && trustedHomeViews.contains(source),
                    source != null && customHomeViews.contains(source))) {
                // Internal schemes from ordinary webpages are blocked silently. A legitimate
                // homepage click must never punish the user because WebView exposed a pending URL.
                return true;
            }
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if ("engine".equals(host)) {
                setSearchEngine(uri.getQueryParameter("name"));
            } else if ("search".equals(host)) {
                String engine = uri.getQueryParameter("engine");
                String query = uri.getQueryParameter("q");
                setSearchEngine(engine);
                loadInput(query);
            } else if ("open".equals(host)) {
                loadInput(uri.getQueryParameter("url"));
            } else if ("bookmarks".equals(host)) {
                openBookmarkManager(null);
            } else if ("folders".equals(host)) {
                openBookmarkManager(null);
            } else if ("folder".equals(host)) {
                bookmarkFolderRootBackAction = null;
                showBookmarkFolder(uri.getQueryParameter("id"));
            }
            return true;
        }
        if (looksLikeUserScript(url)) {
            installScriptFromUrl(url);
            return true;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            String cleaned = shouldCleanTracking(hostOf(url)) ? UrlCleaner.cleanTracking(url) : url;
            if (!url.equals(cleaned)) {
                loadNetworkUrl(webView, cleaned);
                toast("已移除跟踪参数");
                return true;
            }
        }
        if (url.startsWith("http://") && shouldUpgradeHttp(url)) {
            loadNetworkUrl(webView, "https://" + url.substring(7));
            return true;
        }
        if (url.startsWith("http://") || url.startsWith("https://") || "about:blank".equals(url) || isOfflineUrl(url)) return false;
        if (url.startsWith("data:") || url.startsWith("about:")) { toast("已阻止网页跳转到不透明内部地址"); return true; }
        confirmExternalNavigation(url);
        return true;
    }

    private boolean isOfflineUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String name = uri.getLastPathSegment();
            return "content".equalsIgnoreCase(uri.getScheme()) && OfflineContentProvider.AUTHORITY.equals(uri.getAuthority()) &&
                    name != null && name.matches("[A-Za-z0-9._-]+") && name.endsWith(".mht");
        } catch (RuntimeException ignored) { return false; }
    }

    private String homeOpenMode() {
        return HomeOpenPolicy.normalize(prefs.getString("home_open_mode", ""),
                prefs.getBoolean("restore_tabs", true));
    }

    private String normalizeConfiguredHomeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() == 0 || value.length() > 2048) return "";
        if (!OmniboxInput.isExplicitHttpUrl(value)) {
            if (!OmniboxInput.looksLikeWebAddress(value)) return "";
            value = OmniboxInput.withDefaultHttpsScheme(value);
        }
        try { value = NetworkSecurity.parseHttpUrl(value).toString(); }
        catch (Exception invalid) { return ""; }
        if ("median.invalid".equalsIgnoreCase(Uri.parse(value).getHost())) return "";
        if (value.startsWith("http://") && shouldUpgradeHttp(value)) value = "https://" + value.substring(7);
        try { return NetworkSecurity.parseHttpUrl(value).toString(); }
        catch (Exception invalid) { return ""; }
    }

    private String configuredHomeUrl() {
        String custom = normalizeConfiguredHomeUrl(prefs.getString("home_custom_url", ""));
        return HomeOpenPolicy.usesCustomUrl(homeOpenMode(), false, custom) ? custom : HOME_URL;
    }

    private String homeOpenBehaviorLabel() {
        String mode = homeOpenMode();
        if (HomeOpenPolicy.KEEP_LAST.equals(mode)) return "保留上一次访问的内容";
        if (HomeOpenPolicy.OPEN_CUSTOM_URL.equals(mode)) {
            String custom = normalizeConfiguredHomeUrl(prefs.getString("home_custom_url", ""));
            return custom.length() == 0 ? "自定义页面（未设置）" : "自定义页面 · " + hostOf(custom);
        }
        return "打开主页";
    }

    private void openConfiguredHome() {
        String target = configuredHomeUrl();
        if (isHomeUrl(target)) showHome();
        else {
            updateCurrentTab(target, "主页");
            loadInput(target);
        }
    }

    /** Applies page-type access independently from performance tuning or the previously visible URL. */
    private void applyPageAccessPolicy(WebView target, String url) {
        if (target == null) return;
        boolean offline = isOfflineUrl(url);
        WebSettings settings = target.getSettings();
        settings.setAllowContentAccess(offline);
        settings.setBlockNetworkLoads(offline);
    }

    /** Restores the trusted local homepage baseline after restrictive site or MHTML settings. */
    private void prepareHomeDestination(WebView target) {
        if (target == null) return;
        applyPageAccessPolicy(target, HOME_URL);
        appliedSiteSettings.remove(target);
        WebSettings settings = target.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setTextZoom(100);
    }

    /** MHTML can read only its own provider content and cannot execute archived JavaScript. */
    private void prepareOfflineDestination(WebView target, String url) {
        if (target == null || !isOfflineUrl(url)) return;
        applyPageAccessPolicy(target, url);
        // A cached settings key must never suppress re-applying JavaScript/image policy when the
        // user returns to the same online host.
        appliedSiteSettings.remove(target);
        WebSettings settings = target.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
    }

    /** Clears any offline-page state before WebView accepts an HTTP(S) main-frame request. */
    private void prepareNetworkDestination(WebView target, String url) {
        if (target == null || !isNetworkPage(url)) return;
        trustedHomeViews.remove(target);
        customHomeViews.remove(target);
        applyPageAccessPolicy(target, url);
        applySiteSettings(target, hostOf(url));
    }

    /** Prepares the destination settings before issuing an HTTP(S) navigation. */
    private void loadNetworkUrl(WebView target, String url) {
        if (target == null || !isNetworkPage(url)) return;
        String host = hostOf(url);
        prepareNetworkDestination(target, url);
        watchInitialNetworkNavigation(target, url);
        if (target == webView) {
            currentPageUrl = url;
            currentPageHost = host;
        }
        target.loadUrl(url);
    }

    private void watchInitialNetworkNavigation(final WebView target, String url) {
        if (target == null || target != webView || !startupReady) return;
        try { NetworkSecurity.parseHttpUrl(url); }
        catch (Exception invalid) { return; }
        InitialNavigationGuard guard = initialNavigationGuards.get(target);
        if (guard == null) {
            InitialNavigationGuard created = new InitialNavigationGuard();
            InitialNavigationGuard existing = initialNavigationGuards.putIfAbsent(target, created);
            guard = existing == null ? created : existing;
        }
        long generation = guard.arm(url);
        if (generation != 0L) {
            if (startupNavigationAt == 0L) startupNavigationAt = SystemClock.elapsedRealtime();
            scheduleInitialNavigationRetry(target, generation, INITIAL_NAVIGATION_ACK_MS);
        }
    }

    private void scheduleInitialNavigationRetry(final WebView target, final long generation, long delayMs) {
        if (generation == 0L) return;
        uiHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (activityDestroyed || target != webView) return;
                InitialNavigationGuard guard = initialNavigationGuards.get(target);
                if (guard == null || guard.pendingGeneration() != generation || !activityResumed) return;
                if (!target.isAttachedToWindow()) {
                    scheduleInitialNavigationRetry(target, generation, 220L);
                    return;
                }
                String retryUrl = guard.claimRetry(generation);
                if (retryUrl.length() == 0) return;
                recordStartupNavigation("首航重载", retryUrl);
                prepareNetworkDestination(target, retryUrl);
                currentPageUrl = retryUrl;
                currentPageHost = hostOf(retryUrl);
                if (retryUrl.equals(target.getUrl())) target.reload();
                else target.loadUrl(retryUrl);
            }
        }, Math.max(0L, delayMs));
    }

    /** Persists one privacy-safe cold-start record for the built-in compatibility report. */
    private void recordStartupNavigation(String result, String url) {
        if (prefs == null || startupNavigationAt == 0L) return;
        long now = SystemClock.elapsedRealtime();
        PackageInfo provider = WebView.getCurrentWebViewPackage();
        String webViewVersion = provider == null ? "未知" : provider.packageName + " " + provider.versionName;
        String report = result + "\n就绪：" + Math.max(0L, startupReadyAt - startupStartedAt) +
                " ms · 首次导航：" + Math.max(0L, startupNavigationAt - startupStartedAt) +
                " ms · 当前：" + Math.max(0L, now - startupStartedAt) + " ms\n目标站点：" +
                hostOf(url) + "\nSystem WebView：" + webViewVersion;
        prefs.edit().putString(PREF_STARTUP_DIAGNOSTIC, report).apply();
    }

    private void loadInput(String input) {
        if (input == null) return;
        if (!startupReady) {
            pendingStartupInput = input;
            return;
        }
        String text = input.trim();
        if (text.length() == 0) return;
        boolean explicitHttp = OmniboxInput.isExplicitHttpUrl(text);
        if (explicitHttp) {
            try { text = NetworkSecurity.parseHttpUrl(text).toString(); }
            catch (Exception invalidUrl) { toast("网页地址无效"); return; }
        }
        if (text.startsWith("http://") && shouldUpgradeHttp(text)) text = "https://" + text.substring(7);
        if (explicitHttp || "about:blank".equals(text)) {
            if (explicitHttp) {
                if (shouldCleanTracking(hostOf(text))) text = UrlCleaner.cleanTracking(text);
                loadNetworkUrl(webView, text);
            } else {
                trustedHomeViews.remove(webView);
                customHomeViews.remove(webView);
                applyPageAccessPolicy(webView, text);
                currentPageUrl = text;
                currentPageHost = "";
                webView.loadUrl(text);
            }
        } else if (OmniboxInput.looksLikeWebAddress(text)) {
            String candidate;
            try { candidate = NetworkSecurity.parseHttpsUrl(OmniboxInput.withDefaultHttpsScheme(text)).toString(); }
            catch (Exception invalidUrl) { toast("网页地址无效"); return; }
            loadNetworkUrl(webView, candidate);
        } else {
            loadSearch(text, searchEngine);
        }
    }

    private void loadSearch(String query, String engine) {
        if (query == null || query.trim().length() == 0) return;
        try {
            String q = URLEncoder.encode(query.trim(), "UTF-8");
            String url = searchEngines.template(engine).replace("%s", q);
            loadNetworkUrl(webView, url);
        } catch (Exception e) {
            toast("无法创建搜索地址");
        }
    }

    private void showHome() {
        if (webView == null) return;
        prepareHomeDestination(webView);
        currentPageUrl = HOME_URL;
        trustedHomeViews.add(webView);
        HomePageConfig config = homePageConfig();
        if (config.customHtmlEnabled) customHomeViews.add(webView);
        else customHomeViews.remove(webView);
        List<BrowserDataStore.Bookmark> bookmarks = dataStore == null
                ? Collections.<BrowserDataStore.Bookmark>emptyList() : dataStore.bookmarks();
        List<HomePage.Shortcut> shortcuts = homeShortcuts(bookmarks);
        String renderKey = homeRenderKey(config, shortcuts);
        boolean alreadyRendered = renderKey.equals(renderedHomeKeys.get(webView)) &&
                isHomeUrl(webView.getUrl()) && trustedHomeViews.contains(webView);
        if (!alreadyRendered) {
            if (!renderKey.equals(cachedHomeKey)) {
                cachedHomeHtml = HomePage.html(searchEngine, bookmarks, nightMode, HOME_TOKEN, config,
                        searchEngines.customEngines(), shortcuts);
                cachedHomeKey = renderKey;
            }
            renderedHomeKeys.put(webView, renderKey);
            webView.loadDataWithBaseURL(HOME_URL, cachedHomeHtml, "text/html", "UTF-8", HOME_URL);
        }
        currentPageHost = "";
        if (webView != null) pageHosts.put(webView, "");
        updateCurrentTab(HOME_URL, "主页");
        requestChromeUpdate();
    }

    private List<HomePage.Shortcut> homeShortcuts(List<BrowserDataStore.Bookmark> bookmarks) {
        ArrayList<HomePage.Shortcut> result = new ArrayList<HomePage.Shortcut>();
        if (bookmarks != null) for (BrowserDataStore.Bookmark item : bookmarks) {
            String parent = bookmarkFolders == null ? BookmarkFolderStore.ROOT : bookmarkFolders.parentForUrl(item.url);
            if (parent.length() == 0)
                result.add(new HomePage.Shortcut(false, "", item.title, item.url, item.createdAt));
        }
        if (bookmarkFolders != null) for (BookmarkFolderStore.Folder folder : bookmarkFolders.homeFolders())
            result.add(new HomePage.Shortcut(true, folder.id, folder.name, "", folder.createdAt));
        Collections.sort(result, new java.util.Comparator<HomePage.Shortcut>() {
            @Override public int compare(HomePage.Shortcut a, HomePage.Shortcut b) {
                return a.createdAt == b.createdAt ? 0 : (a.createdAt > b.createdAt ? -1 : 1);
            }
        });
        return result;
    }

    private String homeRenderKey(HomePageConfig value, List<HomePage.Shortcut> shortcuts) {
        StringBuilder key = new StringBuilder(512);
        key.append(searchEngine).append('|').append(searchEngines.signature()).append('|').append(nightMode).append('|')
                .append(value.title).append('|').append(value.subtitle).append('|')
                .append(value.logoStyle).append('|').append(value.logoCode).append('|')
                .append(value.logoMode).append('|').append(value.logoLetterSpacing).append('|')
                .append(value.logoGradientAngle).append('|').append(value.logoFontSize).append('|')
                .append(value.logoFontWeight).append('|').append(value.logoImageWidth).append('|')
                .append(value.logoImageHeight).append('|').append(value.logoImageRadius).append('|')
                .append(value.accent).append('|').append(value.wallpaperDim).append('|')
                .append(value.wallpaperBlur).append('|').append(value.wallpaperFit).append('|')
                .append(value.searchStyle).append('|').append(value.layout).append('|')
                .append(value.tileShape).append('|').append(value.shortcutColumns).append('|')
                .append(value.showSearch).append('|').append(value.showEngines).append('|')
                .append(value.showShortcuts).append('|').append(value.showCornerBrand).append('|')
                .append(value.showClock).append('|').append(value.customHtmlEnabled).append('|')
                .append(value.hasWallpaper).append('|').append(value.hasLogo).append('|')
                .append(value.customHtmlVersion).append('|').append(value.wallpaperVersion).append('|')
                .append(value.logoVersion).append('|').append(value.customCss.length()).append(':')
                .append(value.customCss.hashCode());
        int count = Math.min(12, shortcuts == null ? 0 : shortcuts.size());
        for (int i = 0; i < count; i++) {
            HomePage.Shortcut item = shortcuts.get(i);
            key.append('|').append(item.folder).append(':').append(item.id).append('\u001f')
                    .append(item.title).append('\u001f').append(item.url);
        }
        return key.toString();
    }

    private HomePageConfig homePageConfig() {
        String customHtml = prefs.getString("home_custom_html", "");
        boolean customHtmlEnabled = prefs.getBoolean("home_custom_html_enabled", false) &&
                CustomHomeHtml.valid(customHtml);
        boolean hasLogo = homeImages != null && homeImages.has(HomeImageStore.Kind.LOGO);
        String logoMode = prefs.getString("home_logo_mode", "");
        if (!"text".equals(logoMode) && !"image".equals(logoMode) && !"none".equals(logoMode))
            logoMode = hasLogo ? "image" : "text";
        return HomePageConfig.createPersonalized(
                prefs.getString("home_title", HomePageConfig.DEFAULT_TITLE),
                prefs.getString("home_subtitle", ""),
                prefs.getString("home_logo_style", "median"),
                prefs.getString("home_logo_code", ""),
                prefs.getInt("home_logo_letter_spacing", 0),
                prefs.getInt("home_logo_gradient_angle", 90),
                prefs.getString("home_accent", "blue"),
                prefs.getInt("home_wallpaper_dim", 28),
                prefs.getInt("home_wallpaper_blur", 0),
                prefs.getString("home_wallpaper_fit", "cover"),
                prefs.getString("home_search_style", "solid"),
                prefs.getString("home_layout", "center"),
                prefs.getString("home_tile_shape", "rounded"),
                prefs.getInt("home_shortcut_columns", 4),
                prefs.getBoolean("home_show_search", true),
                prefs.getBoolean("home_show_engines", true),
                prefs.getBoolean("home_show_shortcuts", true),
                prefs.getBoolean("home_show_corner", true),
                prefs.getBoolean("home_show_clock", false),
                customHtmlEnabled,
                homeImages != null && homeImages.has(HomeImageStore.Kind.WALLPAPER),
                hasLogo,
                prefs.getLong("home_custom_html_version", 0L),
                homeImages == null ? 0L : homeImages.version(HomeImageStore.Kind.WALLPAPER),
                homeImages == null ? 0L : homeImages.version(HomeImageStore.Kind.LOGO),
                logoMode,
                prefs.getInt("home_logo_font_size", 47),
                prefs.getInt("home_logo_font_weight", 720),
                prefs.getInt("home_logo_image_width", 132),
                prefs.getInt("home_logo_image_height", 96),
                prefs.getInt("home_logo_image_radius", 0),
                prefs.getString("home_custom_css", ""));
    }

    private void verifyTrustedHome(final WebView view) {
        if (view == null) return;
        String probe = "(function(){var m=document.querySelector('meta[name=median-home-token]');return !!m&&m.content===" +
                JSONObject.quote(HOME_TOKEN) + ";})();";
        view.evaluateJavascript(probe, new ValueCallback<String>() {
            @Override public void onReceiveValue(String value) {
                try {
                    if ("true".equals(value) && isHomeUrl(view.getUrl())) {
                        trustedHomeViews.add(view);
                        if (homePageConfig().customHtmlEnabled) customHomeViews.add(view);
                        else customHomeViews.remove(view);
                    } else {
                        trustedHomeViews.remove(view);
                        customHomeViews.remove(view);
                    }
                } catch (RuntimeException ignored) {
                    trustedHomeViews.remove(view);
                    customHomeViews.remove(view);
                }
            }
        });
    }

    private void schedulePageEnhancements(final String url, final long sequence) {
        if (url == null || isHomeUrl(url) || url.startsWith("about:") || url.startsWith("data:")) return;
        boolean directNetwork = MODE_PERFORMANCE.equals(performanceMode) && performanceNetworkDirect;
        final WebView target = webView;
        final boolean ensureScripts = scriptStore != null && scriptStore.hasEnabledScripts();
        final String bridgeToken = target == null || scriptBridgeTokens.get(target) == null ? "" :
                scriptBridgeTokens.get(target);
        if ((!isAdBlockActive(url) || directNetwork) && !ensureScripts) return;
        navigationExecutor.execute(new Runnable() {
            @Override public void run() {
                if (sequence != navigationSequence) return;
                try { Process.setThreadPriority(scriptThreadPriority()); } catch (RuntimeException ignored) {}
                StringBuilder start = new StringBuilder(4096);
                // A number of OEM providers advertise document-start support and accept a
                // registration, but occasionally fail to execute it. Every registered payload
                // sets a per-script page marker; this post-commit payload only runs missing
                // scripts, so working providers stay fast while broken providers recover.
                String startScripts = ensureScripts ? scriptStore.buildInjection(url, true, bridgeToken) : "";
                if (startScripts.length() > 0) start.append(startScripts);
                if (sequence != navigationSequence) return;
                final PreparedInjection prepared = new PreparedInjection(sequence, url, start.toString(),
                        ensureScripts ? scriptStore.buildInjection(url, false, bridgeToken) : "");
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (sequence != navigationSequence || webView == null) return;
                        preparedInjection = prepared;
                        if (pageCommitted) injectPreparedStart(sequence);
                        if (pageFinished) injectPreparedEnd(sequence);
                    }
                });
            }
        });
    }

    private long advanceNavigationSequence() {
        long next = ++navigationSequence;
        if (navigationExecutor != null) navigationExecutor.getQueue().clear();
        return next;
    }

    private void injectPreparedStart(long sequence) {
        PreparedInjection prepared = preparedInjection;
        if (prepared == null || prepared.sequence != sequence || injectedStartSequence == sequence) return;
        injectedStartSequence = sequence;
        if (prepared.startScript.length() > 0) executeUserScriptPayload(prepared.startScript);
    }

    private void injectPreparedEnd(final long sequence) {
        final PreparedInjection prepared = preparedInjection;
        if (prepared == null || prepared.sequence != sequence || injectedEndSequence == sequence) return;
        injectedEndSequence = sequence;
        Runnable inject = new Runnable() {
            @Override public void run() {
                if (sequence == navigationSequence && prepared.endScript.length() > 0 && webView != null) {
                    executeUserScriptPayload(prepared.endScript);
                }
                if (sequence == navigationSequence) preparedInjection = null;
            }
        };
        if (MODE_POWER_SAVE.equals(performanceMode)) uiHandler.postDelayed(inject, 160L); else inject.run();
    }

    private void executeUserScriptPayload(String payload) {
        final WebView active = webView;
        if (active == null || payload == null || payload.length() == 0) return;
        try {
            active.evaluateJavascript(payload, null);
        } catch (RuntimeException ignored) {
        }
    }

    private boolean isAdBlockActive(String url) {
        return isAdBlockActiveForHost(hostOf(url));
    }

    private boolean isAdBlockActiveForHost(String host) {
        if (!adBlockEnabled) return false;
        SiteSettingsStore.SiteSettings site = siteSettingsStore == null ? null : siteSettingsStore.forHost(host);
        if (site != null && site.compatibilityMode()) return false;
        return matchingSiteException(host) == null;
    }

    private String matchingSiteException(String host) {
        Set<String> exceptions = siteExceptions;
        if (host == null || host.length() == 0) return null;
        String candidate = host.toLowerCase(Locale.US);
        while (candidate.length() > 0) {
            if (exceptions.contains(candidate)) return candidate;
            int dot = candidate.indexOf('.');
            if (dot < 0) break;
            candidate = candidate.substring(dot + 1);
        }
        return null;
    }

    private void showProtectionPanel() {
        if (!requireStartupReady()) return;
        final String host = currentHost();
        final String matchedException = matchingSiteException(host);
        final boolean sitePaused = host.length() > 0 && matchedException != null;
        final boolean directNetwork = MODE_PERFORMANCE.equals(performanceMode) && performanceNetworkDirect;
        int pageBlocked = Math.max(0, adBlock.getBlockedCount() - blockedAtPageStart);
        String subtitle = host.length() == 0 ? "当前为主页" : host + " · 本页拦截 " + pageBlocked + " 项";
        String[] items = new String[] {
                "全局广告拦截：" + (adBlockEnabled ? (directNetwork ? "已暂停 · 性能优先" : "已开启") : "已关闭"),
                host.length() == 0 ? "当前页面没有站点设置" : (sitePaused ? "恢复此网站拦截" : "暂停此网站拦截"),
                "过滤器中心 · 已启用 " + services.filters().enabledCount() + " 个订阅",
                "用户脚本 · 已启用 " + enabledScriptCount() + " 个",
                "浏览适用于当前网站的脚本",
                "自定义拦截规则"
        };
        int[] icons = new int[] { BrowserIconView.SHIELD, BrowserIconView.SHIELD, BrowserIconView.STORAGE, BrowserIconView.SCRIPT, BrowserIconView.SEARCH, BrowserIconView.STORAGE };
        showActionSheet("保护与脚本", subtitle, items, icons, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) {
                    adBlockEnabled = !adBlockEnabled;
                    prefs.edit().putBoolean("adblock", adBlockEnabled).apply();
                    adBlockActiveByView.clear();
                    webView.reload();
                } else if (which == 1 && host.length() > 0) {
                    HashSet<String> updated = new HashSet<String>(siteExceptions);
                    if (matchedException != null) updated.remove(matchedException); else updated.add(host);
                    siteExceptions = updated;
                    prefs.edit().putStringSet("site_exceptions", new HashSet<String>(updated)).apply();
                    adBlockActiveByView.clear();
                    webView.reload();
                } else if (which == 2) {
                    showFilterCenter();
                } else if (which == 3) {
                    showScriptCenter();
                } else if (which == 4) {
                    String target = host.length() == 0 ? "https://greasyfork.org/zh-CN/scripts" : "https://greasyfork.org/zh-CN/scripts/by-site/" + host;
                    loadNetworkUrl(webView, target);
                } else if (which == 5) {
                    showCustomFilterRules();
                }
            }
        });
    }

    private void rebuildAdBlockRules() {
        List<String> sources = services.filters().readEnabledRuleSources();
        adBlock.updateRules(prefs.getString("custom_filter_rules", ""), sources);
    }

    private void rebuildAdBlockRulesAsync(final boolean notifyUser) {
        if (scriptExecutor == null || scriptExecutor.isShutdown()) {
            rebuildAdBlockRules();
            return;
        }
        executeTask(scriptExecutor, new Runnable() {
            @Override public void run() {
                try { Process.setThreadPriority(scriptThreadPriority()); } catch (RuntimeException ignored) {}
                rebuildAdBlockRules();
                final AdBlockEngine.Stats stats = adBlock.getStats();
                if (notifyUser) uiHandler.post(new Runnable() {
                    @Override public void run() {
                        toast("过滤器已编译：" + stats.networkRules + " 条网络规则、" + stats.cosmeticRules + " 条外观规则");
                        if (webView != null) webView.reload();
                    }
                });
            }
        });
    }

    private void updateFilterSubscriptions(final boolean automatic) {
        if (filterUpdateInProgress) return;
        filterUpdateInProgress = true;
        if (!automatic) toast("正在更新过滤订阅…");
        services.filters().updateEnabled(automatic, new FilterSubscriptionStore.Callback() {
            @Override public void onComplete(int updated, int unchanged, int failed, String message) {
                filterUpdateInProgress = false;
                if (updated > 0) rebuildAdBlockRulesAsync(false);
                if (!automatic) {
                    String result = "订阅更新完成：" + updated + " 个更新，" + unchanged + " 个未变化";
                    if (failed > 0) result += "，" + failed + " 个失败" + (message.length() == 0 ? "" : "（" + message + "）");
                    if (updated > 0) result += "；新外观规则在下次刷新完整生效";
                    toast(result);
                }
            }
        });
    }

    private void showFilterCenter() {
        final List<FilterSubscriptionStore.Subscription> subscriptions = services.filters().getAll();
        final AdBlockEngine.Stats stats = adBlock.getStats();
        String[] items = new String[subscriptions.size() + 4];
        int[] icons = new int[items.length];
        items[0] = filterUpdateInProgress ? "过滤订阅正在更新" : "立即更新全部已启用订阅";
        items[1] = "添加 HTTPS 过滤订阅";
        items[2] = "编辑自定义规则";
        for (int i = 0; i < subscriptions.size(); i++) {
            FilterSubscriptionStore.Subscription item = subscriptions.get(i);
            String state = item.enabled ? "已启用" : "已停用";
            String count = item.ruleCount > 0 ? " · " + item.ruleCount + " 行" : " · 尚未下载";
            items[i + 3] = item.name + "：" + state + count + (item.error.length() == 0 ? "" : " · 上次失败");
        }
        items[items.length - 1] = "过滤诊断 · " + stats.networkRules + " 条网络 / " + stats.cosmeticRules + " 条外观规则";
        for (int i = 0; i < icons.length; i++) icons[i] = i == 1 ? BrowserIconView.PLUS : (i == items.length - 1 ? BrowserIconView.INFO : BrowserIconView.SHIELD);
        showActionSheet("过滤器中心", "EasyList 兼容子集 · HTTPS 更新 · ETag 缓存 · 原子替换", items, icons, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) updateFilterSubscriptions(false);
                else if (which == 1) showAddFilterSubscription();
                else if (which == 2) showCustomFilterRules();
                else if (which == subscriptions.size() + 3) showFilterDiagnostics();
                else {
                    int index = which - 3;
                    if (index >= 0 && index < subscriptions.size()) showFilterSubscriptionActions(subscriptions.get(index));
                }
            }
        });
    }

    private void showFilterSubscriptionActions(final FilterSubscriptionStore.Subscription item) {
        boolean custom = item.id.startsWith("custom-");
        String[] actions = custom
                ? new String[] { item.enabled ? "停用此订阅" : "启用此订阅", "立即更新所有已启用订阅", "删除此订阅" }
                : new String[] { item.enabled ? "停用此订阅" : "启用此订阅", "立即更新所有已启用订阅" };
        int[] icons = custom
                ? new int[] { BrowserIconView.SHIELD, BrowserIconView.RELOAD, BrowserIconView.CLOSE }
                : new int[] { BrowserIconView.SHIELD, BrowserIconView.RELOAD };
        String subtitle = item.url + (item.error.length() == 0 ? "" : "\n上次错误：" + item.error);
        showActionSheet(item.name, subtitle, actions, icons, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) {
                    services.filters().setEnabled(item.id, !item.enabled);
                    rebuildAdBlockRulesAsync(true);
                } else if (which == 1) {
                    updateFilterSubscriptions(false);
                } else {
                    services.filters().remove(item.id);
                    rebuildAdBlockRulesAsync(true);
                }
            }
        });
    }

    private void showAddFilterSubscription() {
        final EditText name = new EditText(this);
        name.setHint("名称（可选）");
        name.setSingleLine(true);
        final EditText url = new EditText(this);
        url.setHint("https://example.com/filter.txt");
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), 0, dp(18), 0);
        content.addView(name);
        content.addView(url);
        new AlertDialog.Builder(this).setTitle("添加过滤订阅")
                .setMessage("仅接受 HTTPS 文本规则列表。单个订阅上限 12 MB，更新会验证内容后原子替换。")
                .setView(content).setPositiveButton("添加并更新", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            services.filters().add(name.getText().toString(), url.getText().toString().trim());
                            updateFilterSubscriptions(false);
                        } catch (Exception e) { toast("添加失败：" + safeMessage(e)); }
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void showFilterDiagnostics() {
        AdBlockEngine.Stats stats = adBlock.getStats();
        new AlertDialog.Builder(this).setTitle("过滤诊断")
                .setMessage("已编译网络规则：" + stats.networkRules +
                        "\n已编译外观规则：" + stats.cosmeticRules +
                        "\n读取源文件行数：" + stats.sourceLines +
                        "\n本次运行检查请求：" + stats.inspectedRequests +
                        "\n已拦截请求：" + stats.blockedRequests +
                        "\n例外放行请求：" + stats.allowedRequests +
                        "\n\n支持 hosts、||域名^、@@、通配符、domain=、third-party、常见资源类型和 ## / #@#。出于安全与 WebView 兼容性，不执行过滤列表中的任意脚本片段。")
                .setPositiveButton("确定", null).show();
    }

    private void showCustomFilterRules() {
        final EditText input = new EditText(this);
        input.setText(prefs.getString("custom_filter_rules", ""));
        input.setHint("每行一条，例如：\n||ads.example.com^\n@@||trusted.example.com^");
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(8);
        input.setMaxLines(14);
        input.setHorizontallyScrolling(false);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        new AlertDialog.Builder(this).setTitle("自定义拦截规则")
                .setMessage("支持 hosts、||域名^、@@例外、通配符、domain=、third-party、资源类型以及 ## / #@# 外观规则。自定义文本上限 256 KB。")
                .setView(input).setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String raw = input.getText().toString();
                        if (raw.length() > 256 * 1024) { toast("规则不能超过 256 KB"); return; }
                        prefs.edit().putString("custom_filter_rules", raw).apply();
                        rebuildAdBlockRulesAsync(true);
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void showMainMenu() {
        if (!requireStartupReady()) return;
        String subtitle = currentHost().length() == 0 ? modeLabel() : currentHost() + " · " + modeLabel();
        boolean bookmarked = dataStore != null && dataStore.isBookmarked(currentPageUrl);
        String[] items = new String[] {
                "新建标签页",
                "标签页工具",
                "下载中心",
                "新建独立隐私窗口",
                bookmarked ? "管理当前收藏" : "收藏当前页面",
                "当前网站设置",
                "桌面网站\n为当前页面切换桌面布局",
                "网页深色模式\n为当前页面切换深色显示",
                "页面工具",
                "书签、历史与迁移",
                "隐私、脚本与密码",
                "浏览器设置"
        };
        int[] icons = new int[] {
                BrowserIconView.PLUS, BrowserIconView.TABS, BrowserIconView.DOWNLOAD, BrowserIconView.SHIELD,
                BrowserIconView.BOOKMARK, BrowserIconView.SHIELD, BrowserIconView.DESKTOP,
                BrowserIconView.APPEARANCE, BrowserIconView.MENU, BrowserIconView.HISTORY,
                BrowserIconView.SHIELD, BrowserIconView.SETTINGS
        };
        int[] kinds = new int[] {
                SHEET_ROW_ACTION, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE, SHEET_ROW_ACTION,
                SHEET_ROW_ACTION, SHEET_ROW_NAVIGATE,
                desktopMode ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                nightMode ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE
        };
        String[] sections = new String[items.length];
        sections[0] = "浏览";
        sections[4] = "当前页面";
        sections[9] = "资料与工具";
        sections[11] = "浏览器";
        showActionSheet("Median", subtitle, items, icons, kinds, sections, null, new SheetHandler() {
            @Override public void onItem(int which) {
                switch (which) {
                    case 0: newTab(); break;
                    case 1: showTabTools(); break;
                    case 2: showDownloadCenter(); break;
                    case 3: openPrivateWindow(); break;
                    case 4: toggleCurrentBookmark(); break;
                    case 5: showSiteSettings(); break;
                    case 6:
                        desktopMode = !desktopMode;
                        prefs.edit().putBoolean("desktop", desktopMode).apply();
                        applyDesktopMode();
                        webView.reload();
                        break;
                    case 7:
                        nightMode = !nightMode;
                        prefs.edit().putBoolean("night_mode", nightMode).apply();
                        applyDarkMode();
                        if (isHomeUrl(currentPageUrl)) showHome();
                        break;
                    case 8: showPageTools(); break;
                    case 9: showBrowserLibrary(); break;
                    case 10: showPrivacyTools(); break;
                    case 11: showBrowserSettings(); break;
                    default: break;
                }
            }
        });
    }

    private void showPrivacyTools() {
        String[] items = new String[] {
                "广告与隐私过滤器\n订阅、例外与规则诊断",
                "用户脚本\n安装、更新与风险隔离",
                "密码管理器\n本地加密保存与填充"
        };
        int[] icons = new int[] { BrowserIconView.SHIELD, BrowserIconView.SCRIPT, BrowserIconView.KEY };
        showActionSheet("隐私与扩展", "全部数据默认只保存在本机", items, icons,
                null, null, new Runnable() {
                    @Override public void run() { showMainMenu(); }
                }, new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showFilterCenter();
                        else if (which == 1) showScriptCenter();
                        else showPasswordMenu();
                    }
                });
    }

    private void openPrivateWindow() {
        if (Build.VERSION.SDK_INT < 28) {
            toast("Android 8 的系统 WebView 不支持可靠的数据目录隔离；为避免伪无痕，本机不开放隐私窗口");
            return;
        }
        try { startActivity(new Intent(this, PrivateActivity.class)); }
        catch (Exception e) { toast("无法启动隐私窗口"); }
    }

    private void toggleCurrentBookmark() {
        String url = currentPageUrl;
        if (url == null || isHomeUrl(url) || (!url.startsWith("https://") && !url.startsWith("http://"))) {
            toast("当前页面不能收藏");
            return;
        }
        BrowserDataStore.Bookmark existing = dataStore.bookmark(url);
        if (existing != null) {
            showBookmarkActions(existing, bookmarkFolders == null ? BookmarkFolderStore.ROOT : bookmarkFolders.parentForUrl(url));
            return;
        }
        showBookmarkEditor(null, webView.getTitle(), url, BookmarkFolderStore.ROOT);
    }

    private void showBrowserLibrary() {
        String[] items = new String[] {
                "收藏与文件夹（" + dataStore.bookmarks().size() + "）\n自定义网站、重命名与多级文件夹",
                "最近历史记录",
                "搜索历史记录",
                "离线页面（" + services.offlinePages().getAll().size() + "）",
                "导出书签备份",
                "导入书签备份",
                "导出加密完整备份",
                "导入加密完整备份"
        };
        int[] icons = new int[] { BrowserIconView.PLUS, BrowserIconView.TABS, BrowserIconView.SEARCH,
                BrowserIconView.STORAGE, BrowserIconView.SHARE, BrowserIconView.STORAGE,
                BrowserIconView.KEY, BrowserIconView.KEY };
        showActionSheet("书签与历史", "本地保存 · 不上传云端", items, icons, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) openBookmarkManager(new Runnable() {
                    @Override public void run() { showBrowserLibrary(); }
                });
                else if (which == 1) showHistoryList("");
                else if (which == 2) showHistorySearch();
                else if (which == 3) showOfflinePages();
                else if (which == 4) beginBackupExport();
                else if (which == 5) beginBackupImport();
                else if (which == 6) beginFullBackupExport();
                else beginFullBackupImport();
            }
        });
    }

    private void showBookmarkList(boolean ignored) { openBookmarkManager(null); }

    private void openBookmarkManager(Runnable rootBackAction) {
        bookmarkFolderRootBackAction = rootBackAction;
        showBookmarkFolder(BookmarkFolderStore.ROOT);
    }

    private void showBookmarkFolder(String requestedId) {
        if (dataStore == null || bookmarkFolders == null) return;
        final BookmarkFolderStore.Folder current = bookmarkFolders.folder(requestedId);
        final String folderId = current == null ? BookmarkFolderStore.ROOT : current.id;
        final List<BookmarkFolderStore.Folder> childFolders = bookmarkFolders.foldersIn(folderId);
        final ArrayList<BrowserDataStore.Bookmark> childBookmarks = new ArrayList<BrowserDataStore.Bookmark>();
        for (BrowserDataStore.Bookmark item : dataStore.bookmarks())
            if (folderId.equals(bookmarkFolders.parentForUrl(item.url))) childBookmarks.add(item);

        ArrayList<String> labels = new ArrayList<String>();
        ArrayList<Integer> iconList = new ArrayList<Integer>();
        ArrayList<Integer> kindList = new ArrayList<Integer>();
        labels.add("新建收藏网站\n无需先打开网页"); iconList.add(BrowserIconView.PLUS); kindList.add(SHEET_ROW_ACTION);
        labels.add("新建子文件夹"); iconList.add(BrowserIconView.STORAGE); kindList.add(SHEET_ROW_ACTION);
        if (current != null) {
            labels.add("当前文件夹设置\n重命名与主页显示"); iconList.add(BrowserIconView.SETTINGS); kindList.add(SHEET_ROW_ACTION);
            labels.add("删除当前文件夹\n子文件夹一并删除，网站移回全部收藏"); iconList.add(BrowserIconView.CLOSE); kindList.add(SHEET_ROW_ACTION);
        }
        final int actionCount = labels.size();
        for (BookmarkFolderStore.Folder folder : childFolders) {
            labels.add(folder.name + "\n文件夹" + (folder.showOnHome ? " · 已显示在主页" : ""));
            iconList.add(BrowserIconView.STORAGE); kindList.add(SHEET_ROW_NAVIGATE);
        }
        for (BrowserDataStore.Bookmark item : childBookmarks) {
            labels.add(item.title + "\n" + hostOf(item.url));
            iconList.add(BrowserIconView.BOOKMARK); kindList.add(SHEET_ROW_NAVIGATE);
        }
        String[] items = labels.toArray(new String[labels.size()]);
        int[] icons = new int[iconList.size()];
        int[] kinds = new int[kindList.size()];
        String[] sections = new String[items.length];
        for (int i = 0; i < icons.length; i++) { icons[i] = iconList.get(i).intValue(); kinds[i] = kindList.get(i).intValue(); }
        if (items.length > 0) sections[0] = "操作";
        if (!childFolders.isEmpty()) sections[actionCount] = "文件夹";
        if (!childBookmarks.isEmpty()) sections[actionCount + childFolders.size()] = "网站";
        Runnable back = current == null ? bookmarkFolderRootBackAction : new Runnable() {
            @Override public void run() { showBookmarkFolder(current.parentId); }
        };
        showActionSheet(current == null ? "收藏与文件夹" : current.name,
                bookmarkFolders.path(folderId), items, icons, kinds, sections, back, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) showBookmarkEditor(null, "", "", folderId);
                else if (which == 1) showBookmarkFolderEditor(null, folderId);
                else if (current != null && which == 2) showBookmarkFolderEditor(current, current.parentId);
                else if (current != null && which == 3) confirmDeleteBookmarkFolder(current, false);
                else {
                    int index = which - actionCount;
                    if (index < childFolders.size()) showBookmarkFolder(childFolders.get(index).id);
                    else showBookmarkActions(childBookmarks.get(index - childFolders.size()), folderId);
                }
            }
        });
    }

    private void showBookmarkActions(final BrowserDataStore.Bookmark item, final String returnFolder) {
        showActionSheet(item.title, item.url,
                new String[] { "打开网站", "编辑名称、网址与文件夹", "删除收藏" },
                new int[] { BrowserIconView.SEARCH, BrowserIconView.SETTINGS, BrowserIconView.CLOSE },
                new int[] { SHEET_ROW_ACTION, SHEET_ROW_NAVIGATE, SHEET_ROW_ACTION }, null,
                new Runnable() { @Override public void run() { showBookmarkFolder(returnFolder); } },
                new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) loadInput(item.url);
                        else if (which == 1) showBookmarkEditor(item, item.title, item.url,
                                bookmarkFolders.parentForUrl(item.url));
                        else confirmDeleteBookmark(item, returnFolder);
                    }
                });
    }

    private void confirmDeleteBookmark(final BrowserDataStore.Bookmark item, final String returnFolder) {
        new AlertDialog.Builder(this).setTitle("删除收藏？").setMessage(item.title)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dataStore.removeBookmark(item.url);
                        bookmarkFolders.removeUrl(item.url);
                        refreshHomeAfterLibraryChange();
                        showBookmarkFolder(returnFolder);
                    }
                }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { showBookmarkActions(item, returnFolder); }
                }).show();
    }

    private void showBookmarkEditor(final BrowserDataStore.Bookmark existing, String initialTitle,
                                    String initialUrl, final String initialFolder) {
        final EditText title = new EditText(this);
        title.setHint("名称"); title.setSingleLine(true); title.setText(initialTitle == null ? "" : initialTitle);
        final EditText url = new EditText(this);
        url.setHint("网址，例如 example.com"); url.setSingleLine(true); url.setText(initialUrl == null ? "" : initialUrl);
        final String[] selectedFolder = new String[] { bookmarkFolders.folder(initialFolder) == null ? BookmarkFolderStore.ROOT : initialFolder };
        final Button folder = new Button(this);
        folder.setAllCaps(false); folder.setText("保存位置：" + bookmarkFolders.path(selectedFolder[0]));
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(dp(18), 0, dp(18), 0);
        fields.addView(title); fields.addView(url); fields.addView(folder);
        folder.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showBookmarkFolderPicker(selectedFolder, folder); }
        });
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "新建收藏网站" : "编辑收藏")
                .setView(fields).setPositiveButton("保存", null)
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { showBookmarkFolder(initialFolder); }
                }).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String normalized = normalizeConfiguredHomeUrl(url.getText().toString());
                        if (normalized.length() == 0) { url.setError("请输入有效的 HTTP(S) 网址"); return; }
                        String name = title.getText().toString().trim();
                        if (name.length() == 0) name = hostOf(normalized);
                        if (existing == null) {
                            BrowserDataStore.Bookmark added = dataStore.addBookmark(name, normalized);
                            if (added == null) { url.setError("这个网址已经收藏"); return; }
                            bookmarkFolders.setParentForUrl(added.url, selectedFolder[0]);
                        } else {
                            if (!dataStore.updateBookmark(existing.url, name, normalized)) {
                                url.setError("网址无效或已经存在"); return;
                            }
                            bookmarkFolders.updateUrl(existing.url, normalized);
                            bookmarkFolders.setParentForUrl(normalized, selectedFolder[0]);
                        }
                        dialog.dismiss();
                        refreshHomeAfterLibraryChange();
                        showBookmarkFolder(selectedFolder[0]);
                    }
                });
            }
        });
        dialog.show();
    }

    private void showBookmarkFolderPicker(final String[] selected, final Button label) {
        final ArrayList<BookmarkFolderStore.Folder> folders = new ArrayList<BookmarkFolderStore.Folder>(bookmarkFolders.allFolders());
        Collections.sort(folders, new java.util.Comparator<BookmarkFolderStore.Folder>() {
            @Override public int compare(BookmarkFolderStore.Folder a, BookmarkFolderStore.Folder b) {
                return bookmarkFolders.path(a.id).compareToIgnoreCase(bookmarkFolders.path(b.id));
            }
        });
        String[] names = new String[folders.size() + 1];
        names[0] = "全部收藏（主页快捷栏）";
        int checked = 0;
        for (int i = 0; i < folders.size(); i++) {
            names[i + 1] = bookmarkFolders.path(folders.get(i).id);
            if (folders.get(i).id.equals(selected[0])) checked = i + 1;
        }
        new AlertDialog.Builder(this).setTitle("选择文件夹").setSingleChoiceItems(names, checked,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        selected[0] = which == 0 ? BookmarkFolderStore.ROOT : folders.get(which - 1).id;
                        label.setText("保存位置：" + bookmarkFolders.path(selected[0]));
                        dialog.dismiss();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void showBookmarkFolderEditor(final BookmarkFolderStore.Folder existing, final String parentId) {
        final EditText name = new EditText(this);
        name.setHint("文件夹名称"); name.setSingleLine(true); name.setText(existing == null ? "" : existing.name);
        final CheckBox home = new CheckBox(this);
        home.setText("在主页快捷网站栏显示"); home.setChecked(existing != null && existing.showOnHome);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(dp(18), 0, dp(18), 0);
        fields.addView(name); fields.addView(home);
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle(existing == null ? "新建子文件夹" : "编辑文件夹")
                .setView(fields).setPositiveButton("保存", null)
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { showBookmarkFolder(parentId); }
                }).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String title = name.getText().toString().trim();
                        if (title.length() == 0) { name.setError("请输入文件夹名称"); return; }
                        boolean saved = existing == null
                                ? bookmarkFolders.create(title, parentId, home.isChecked()) != null
                                : bookmarkFolders.update(existing.id, title, existing.parentId, home.isChecked());
                        if (!saved) { name.setError("无法保存文件夹"); return; }
                        dialog.dismiss(); refreshHomeAfterLibraryChange(); showBookmarkFolder(parentId);
                    }
                });
            }
        });
        dialog.show();
    }

    private void showBookmarkFolderActions(final BookmarkFolderStore.Folder folder) {
        showActionSheet(folder.name, bookmarkFolders.path(folder.id),
                new String[] { "重命名与主页显示", "删除文件夹\n子文件夹会删除，网站移回全部收藏" },
                new int[] { BrowserIconView.SETTINGS, BrowserIconView.CLOSE },
                new int[] { SHEET_ROW_NAVIGATE, SHEET_ROW_ACTION }, null,
                new Runnable() { @Override public void run() { showBookmarkFolder(folder.id); } },
                new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showBookmarkFolderEditor(folder, folder.parentId);
                        else confirmDeleteBookmarkFolder(folder, true);
                    }
                });
    }

    private void confirmDeleteBookmarkFolder(final BookmarkFolderStore.Folder folder, final boolean returnToActions) {
        if (folder == null || bookmarkFolders.folder(folder.id) == null) {
            toast("文件夹已经不存在");
            showBookmarkFolder(folder == null ? BookmarkFolderStore.ROOT : folder.parentId);
            return;
        }
        int nested = bookmarkFolders.descendantFolderCount(folder.id);
        int bookmarks = bookmarkFolders.bookmarkCountInTree(folder.id);
        StringBuilder message = new StringBuilder("“").append(folder.name).append("”会被删除");
        if (nested > 0) message.append("，同时删除 ").append(nested).append(" 个子文件夹");
        if (bookmarks > 0) message.append("。其中 ").append(bookmarks).append(" 个网站不会删除，会移回全部收藏");
        message.append("。");
        new AlertDialog.Builder(this).setTitle("删除文件夹？").setMessage(message.toString())
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (!bookmarkFolders.remove(folder.id)) {
                            toast("删除失败：文件夹已经不存在");
                        } else {
                            refreshHomeAfterLibraryChange();
                            toast("文件夹已删除");
                        }
                        showBookmarkFolder(folder.parentId);
                    }
                }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (returnToActions) showBookmarkFolderActions(folder); else showBookmarkFolder(folder.id);
                    }
                }).show();
    }

    private void refreshHomeAfterLibraryChange() {
        cachedHomeKey = "";
        renderedHomeKeys.clear();
        if (isHomeUrl(currentPageUrl)) showHome();
    }

    private void showHistoryList(final String query) {
        final List<BrowserDataStore.HistoryItem> all = dataStore.recentHistory(160, query);
        if (all.size() == 0) {
            new AlertDialog.Builder(this).setTitle("历史记录").setMessage(query.length() == 0 ? "还没有浏览历史。" : "没有匹配的历史记录。")
                    .setPositiveButton("知道了", null).show();
            return;
        }
        String[] names = new String[all.size()];
        long now = System.currentTimeMillis();
        for (int i = 0; i < all.size(); i++) {
            BrowserDataStore.HistoryItem item = all.get(i);
            CharSequence relative = android.text.format.DateUtils.getRelativeTimeSpanString(item.visitedAt, now, android.text.format.DateUtils.MINUTE_IN_MILLIS);
            names[i] = item.title + "\n" + hostOf(item.url) + " · " + relative;
        }
        new AlertDialog.Builder(this).setTitle(query.length() == 0 ? "最近历史记录" : "历史搜索：" + query)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { loadInput(all.get(which).url); }
                }).setNeutralButton("清空历史", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { confirmClearHistory(); }
                }).setNegativeButton("关闭", null).show();
    }

    private void showHistorySearch() {
        final EditText input = new EditText(this);
        input.setHint("标题或网址");
        input.setSingleLine(true);
        input.setPadding(dp(18), dp(8), dp(18), dp(8));
        new AlertDialog.Builder(this).setTitle("搜索历史记录").setView(input)
                .setPositiveButton("搜索", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { showHistoryList(input.getText().toString().trim()); }
                }).setNegativeButton("取消", null).show();
    }

    private void confirmClearHistory() {
        confirmClearHistory(null);
    }

    private void confirmClearHistory(final Runnable returnAction) {
        SettingsDialogReturn back = new SettingsDialogReturn(returnAction);
        new AlertDialog.Builder(this).setTitle("清空历史记录？").setMessage("书签、密码和网站登录状态不会删除。")
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dataStore.clearHistory();
                        webView.clearHistory();
                        toast("历史记录已清空");
                        continueSettingsPanel(returnAction);
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void beginBackupExport() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "Median-bookmarks.json");
            startActivityForResult(intent, BACKUP_EXPORT_REQUEST);
        } catch (Exception e) { toast("没有可用的文件保存器"); }
    }

    private void beginBackupImport() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, BACKUP_IMPORT_REQUEST);
        } catch (Exception e) { toast("没有可用的文件选择器"); }
    }

    private void beginFullBackupExport() {
        persistSession();
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "Median-encrypted-backup.json");
            startActivityForResult(intent, FULL_BACKUP_EXPORT_REQUEST);
        } catch (Exception e) { toast("没有可用的文件保存器"); }
    }

    private void beginFullBackupImport() {
        if (filterUpdateInProgress || scriptDownloadInProgress) { toast("请等待过滤器或脚本任务完成后再恢复"); return; }
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, FULL_BACKUP_IMPORT_REQUEST);
        } catch (Exception e) { toast("没有可用的文件选择器"); }
    }

    private void showSiteSettings() {
        final String host = currentHost();
        if (host.length() == 0) { toast("主页没有网站设置"); return; }
        final SiteSettingsStore.SiteSettings settings = siteSettingsStore.forHost(host);
        String[] items = new String[] {
                "兼容模式\n放宽兼容限制并暂停本站过滤",
                "内容与隐私\nJavaScript、图片、Cookie、弹窗与跟踪清理",
                "网站权限\n位置、摄像头与麦克风",
                "显示方式\n桌面模式、深色模式与文字缩放",
                "重置此网站设置",
                "清除此网站 Cookie 与存储"
        };
        int[] icons = new int[] { BrowserIconView.SPEED, BrowserIconView.SHIELD,
                BrowserIconView.KEY, BrowserIconView.APPEARANCE, BrowserIconView.RELOAD, BrowserIconView.CLOSE };
        int[] kinds = new int[] {
                settings.compatibilityMode() ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE,
                SHEET_ROW_ACTION, SHEET_ROW_ACTION
        };
        showActionSheet("网站设置", host + " · 仅影响当前网站", items, icons,
                kinds, null, null, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) toggleCompatibilityMode(host, settings);
                else if (which == 1) showSiteContentSettings(host);
                else if (which == 2) showSitePermissionSettings(host);
                else if (which == 3) showSiteDisplaySettings(host);
                else if (which == 4) {
                    siteSettingsStore.clear(host);
                    adBlockActiveByView.clear();
                    applySiteSettings(webView, host);
                    webView.reload();
                    toast("已重置此网站设置");
                } else confirmClearCurrentSiteData();
            }
        });
    }

    private void showSiteContentSettings(final String host) {
        final SiteSettingsStore.SiteSettings settings = siteSettingsStore.forHost(host);
        String[] items = new String[] {
                "JavaScript：" + triStateLabel(settings.get(SiteSettingsStore.JAVASCRIPT), "跟随全局"),
                "图片加载：" + triStateLabel(settings.get(SiteSettingsStore.IMAGES), "跟随全局"),
                "第三方 Cookie：" + triStateLabel(settings.get(SiteSettingsStore.THIRD_PARTY_COOKIES), "跟随全局"),
                "弹窗：" + triStateLabel(settings.get(SiteSettingsStore.POPUPS), "仅允许用户触发"),
                "媒体自动播放：" + triStateLabel(settings.get(SiteSettingsStore.AUTOPLAY), "需要用户操作"),
                "跟踪参数清理：" + triStateLabel(settings.get(SiteSettingsStore.TRACKING_PROTECTION),
                        cleanTrackingParameters ? "跟随全局开启" : "跟随全局关闭")
        };
        int[] icons = new int[] { BrowserIconView.SCRIPT, BrowserIconView.INFO, BrowserIconView.COOKIE,
                BrowserIconView.SHIELD, BrowserIconView.SPEED, BrowserIconView.CLEAN };
        int[] kinds = actionKinds(items.length);
        showActionSheet("内容与隐私", host + " · 轻触依次切换：跟随、允许、阻止", items, icons,
                kinds, null, new Runnable() {
                    @Override public void run() { showSiteSettings(); }
                }, new SheetHandler() {
                    @Override public void onItem(int which) {
                        int key = which == 0 ? SiteSettingsStore.JAVASCRIPT :
                                which == 1 ? SiteSettingsStore.IMAGES :
                                which == 2 ? SiteSettingsStore.THIRD_PARTY_COOKIES :
                                which == 3 ? SiteSettingsStore.POPUPS :
                                which == 4 ? SiteSettingsStore.AUTOPLAY : SiteSettingsStore.TRACKING_PROTECTION;
                        settings.set(key, nextTriState(settings.get(key)));
                        saveSiteSettingsAndReturn(host, settings, new Runnable() {
                            @Override public void run() { showSiteContentSettings(host); }
                        });
                    }
                });
    }

    private void showSitePermissionSettings(final String host) {
        final SiteSettingsStore.SiteSettings settings = siteSettingsStore.forHost(host);
        String[] items = new String[] {
                "位置：" + triStateLabel(settings.get(SiteSettingsStore.LOCATION), "每次询问"),
                "摄像头：" + triStateLabel(settings.get(SiteSettingsStore.CAMERA), "每次询问"),
                "麦克风：" + triStateLabel(settings.get(SiteSettingsStore.MICROPHONE), "每次询问")
        };
        int[] icons = new int[] { BrowserIconView.SHIELD, BrowserIconView.SHIELD, BrowserIconView.SHIELD };
        showActionSheet("网站权限", host + " · 跟随表示每次询问", items, icons,
                actionKinds(items.length), null, new Runnable() {
                    @Override public void run() { showSiteSettings(); }
                }, new SheetHandler() {
                    @Override public void onItem(int which) {
                        int key = which == 0 ? SiteSettingsStore.LOCATION :
                                which == 1 ? SiteSettingsStore.CAMERA : SiteSettingsStore.MICROPHONE;
                        settings.set(key, nextTriState(settings.get(key)));
                        saveSiteSettingsAndReturn(host, settings, new Runnable() {
                            @Override public void run() { showSitePermissionSettings(host); }
                        });
                    }
                });
    }

    private void showSiteDisplaySettings(final String host) {
        final SiteSettingsStore.SiteSettings settings = siteSettingsStore.forHost(host);
        String[] items = new String[] {
                "桌面模式：" + triStateLabel(settings.get(SiteSettingsStore.DESKTOP), "跟随全局"),
                "深色模式：" + triStateLabel(settings.get(SiteSettingsStore.DARK), "跟随全局"),
                "文字缩放：" + settings.textZoom() + "%"
        };
        int[] icons = new int[] { BrowserIconView.DESKTOP, BrowserIconView.APPEARANCE, BrowserIconView.SEARCH };
        showActionSheet("显示方式", host, items, icons, actionKinds(items.length), null,
                new Runnable() {
                    @Override public void run() { showSiteSettings(); }
                }, new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which < 2) {
                            int key = which == 0 ? SiteSettingsStore.DESKTOP : SiteSettingsStore.DARK;
                            settings.set(key, nextTriState(settings.get(key)));
                        }
                        else {
                            showTextZoomDialog(host, settings, new Runnable() {
                                @Override public void run() { showSiteDisplaySettings(host); }
                            });
                            return;
                        }
                        if (which == 1) {
                            siteSettingsStore.save(host, settings);
                            appliedSiteSettings.remove(webView);
                            applySiteSettings(webView, host);
                            postAfterUiTransition(new Runnable() {
                                @Override public void run() { showSiteDisplaySettings(host); }
                            });
                            return;
                        }
                        saveSiteSettingsAndReturn(host, settings, new Runnable() {
                            @Override public void run() { showSiteDisplaySettings(host); }
                        });
                    }
                });
    }

    private static int[] actionKinds(int count) {
        int[] result = new int[Math.max(0, count)];
        java.util.Arrays.fill(result, SHEET_ROW_ACTION);
        return result;
    }

    private void saveSiteSettingsAndReturn(String host, SiteSettingsStore.SiteSettings settings,
                                           Runnable returnAction) {
        siteSettingsStore.save(host, settings);
        adBlockActiveByView.clear();
        applySiteSettings(webView, host);
        webView.reload();
        postAfterUiTransition(returnAction);
    }


    private void toggleCompatibilityMode(String host, SiteSettingsStore.SiteSettings settings) {
        if (host == null || host.length() == 0 || settings == null) return;
        if (settings.compatibilityMode()) {
            settings.compatibilityMode(false);
            siteSettingsStore.save(host, settings);
            adBlockActiveByView.clear();
            applySiteSettings(webView, host);
            webView.reload();
            toast("兼容模式已关闭");
            return;
        }
        enableCompatibilityForHost(host, true);
    }

    private void enableCompatibilityForHost(String host, boolean reload) {
        if (host == null || host.length() == 0 || siteSettingsStore == null) return;
        SiteSettingsStore.SiteSettings settings = siteSettingsStore.forHost(host);
        settings.compatibilityMode(true);
        siteSettingsStore.save(host, settings);
        adBlockActiveByView.clear();
        if (webView != null) applySiteSettings(webView, host);
        if (reload && webView != null) webView.reload();
        toast("已为此网站启用兼容模式");
    }

    private static boolean compatibilityRelevantError(int errorCode) {
        return errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ||
                errorCode == WebViewClient.ERROR_REDIRECT_LOOP ||
                errorCode == WebViewClient.ERROR_UNSUPPORTED_SCHEME ||
                errorCode == WebViewClient.ERROR_UNKNOWN;
    }

    private void maybeOfferCompatibilityMode(final String failedUrl, String reason) {
        if (isFinishing() || !activityResumed || compatibilityDialogShowing ||
                failedUrl == null || failedUrl.length() == 0 || !isNetworkPage(failedUrl)) return;
        final String host = hostOf(failedUrl);
        if (host.length() == 0 || siteSettingsStore == null) return;
        SiteSettingsStore.SiteSettings existing = siteSettingsStore.forHost(host);
        if (existing.compatibilityMode()) return;
        long now = SystemClock.uptimeMillis();
        if (host.equals(lastCompatibilityOfferHost) && now - lastCompatibilityOfferAt < 15000L) return;
        lastCompatibilityOfferHost = host;
        lastCompatibilityOfferAt = now;
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("启用兼容模式重试？")
                .setMessage(reason + "：" + host + "\n\n将仅对这个网站放宽第三方 Cookie、用户触发弹窗和混合内容，并暂停本站广告/跟踪过滤。")
                .setPositiveButton("兼容重试", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        enableCompatibilityForHost(host, false);
                        if (webView != null) loadNetworkUrl(webView, failedUrl);
                    }
                })
                .setNegativeButton("取消", null);
        if (failedUrl.startsWith("https://")) {
            builder.setNeutralButton("HTTP 重试（不安全）", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    enableCompatibilityForHost(host, false);
                    if (webView != null) loadNetworkUrl(webView, "http://" + failedUrl.substring(8));
                }
            });
        }
        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface dialog) { compatibilityDialogShowing = false; }
        });
        compatibilityDialogShowing = true;
        dialog.show();
    }

    private void showTextZoomDialog(final String host, final SiteSettingsStore.SiteSettings settings) {
        showTextZoomDialog(host, settings, null);
    }

    private void showTextZoomDialog(final String host, final SiteSettingsStore.SiteSettings settings,
                                    final Runnable returnAction) {
        SettingsDialogReturn back = new SettingsDialogReturn(returnAction);
        final int[] values = new int[] { 80, 90, 100, 110, 125, 150, 175, 200 };
        String[] labels = new String[values.length];
        int checked = 2;
        for (int i = 0; i < values.length; i++) { labels[i] = values[i] + "%"; if (values[i] == settings.textZoom()) checked = i; }
        new AlertDialog.Builder(this).setTitle("文字缩放").setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                settings.textZoom(values[which]);
                siteSettingsStore.save(host, settings);
                applySiteSettings(webView, host);
                webView.reload();
                dialog.dismiss();
                continueSettingsPanel(returnAction);
            }
        }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void confirmClearCurrentSiteData() {
        final String url = currentPageUrl;
        final String host = currentHost();
        if (url == null || host.length() == 0) return;
        new AlertDialog.Builder(this).setTitle("清除此网站数据？").setMessage("将尝试删除 " + host + " 的 Cookie 和本地存储，网站可能会退出登录。")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        CookieManager cookies = CookieManager.getInstance();
                        String raw = cookies.getCookie(url);
                        if (raw != null) {
                            String[] pairs = raw.split(";");
                            for (String pair : pairs) {
                                int equals = pair.indexOf('=');
                                String name = (equals < 0 ? pair : pair.substring(0, equals)).trim();
                                if (name.length() > 0) {
                                    String expired = name + "=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/";
                                    cookies.setCookie(url, expired);
                                    cookies.setCookie(url, expired + "; Secure");
                                    String domain = host;
                                    while (domain.indexOf('.') > 0) {
                                        cookies.setCookie(url, expired + "; Domain=" + domain);
                                        cookies.setCookie(url, expired + "; Domain=." + domain);
                                        cookies.setCookie(url, expired + "; Secure; Domain=." + domain);
                                        domain = domain.substring(domain.indexOf('.') + 1);
                                    }
                                }
                            }
                            cookies.flush();
                        }
                        Uri uri = currentUri();
                        if (uri != null) {
                            String origin = uri.getScheme() + "://" + uri.getAuthority();
                            android.webkit.WebStorage.getInstance().deleteOrigin(origin);
                            android.webkit.GeolocationPermissions.getInstance().clear(origin);
                        }
                        webView.reload();
                        toast("此网站数据已清除");
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void showPageTools() {
        int offlineCount = services.offlinePages().getAll().size();
        PageAssistant assistant = services.assistant();
        String[] items = new String[] {
                "页面内查找", "切换沉浸阅读模式", assistant.isSpeaking() ? "停止朗读" : "朗读正文",
                "页面结构与统计", "TLS 证书与 SHA-256", "保存完整离线页面", "离线页面库 · " + offlineCount + " 页",
                "分享当前页面", "复制页面地址", "复制无跟踪参数地址", "翻译为简体中文",
                "打印或另存为 PDF", "媒体中心 · 已发现 " + mediaSniffer.size() + " 项", "当前页脚本命令"
        };
        int[] icons = new int[] { BrowserIconView.SEARCH, BrowserIconView.INFO, BrowserIconView.SPEED, BrowserIconView.INFO,
                BrowserIconView.SHIELD, BrowserIconView.STORAGE, BrowserIconView.STORAGE, BrowserIconView.SHARE, BrowserIconView.PLUS,
                BrowserIconView.SHIELD, BrowserIconView.INFO, BrowserIconView.STORAGE,
                BrowserIconView.SPEED, BrowserIconView.SCRIPT };
        showActionSheet("页面工具", currentHost(), items, icons, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) showFindDialog();
                else if (which == 1) toggleReaderMode();
                else if (which == 2) toggleReadAloud();
                else if (which == 3) showPageInfo();
                else if (which == 4) showTlsInfo();
                else if (which == 5) saveOfflinePage();
                else if (which == 6) showOfflinePages();
                else if (which == 7) sharePage();
                else if (which == 8) copyPageUrl();
                else if (which == 9) copyCleanPageUrl();
                else if (which == 10) translatePage();
                else if (which == 11) printPage();
                else if (which == 12) probeAndShowMediaCenter();
                else showPageScriptCommands();
            }
        });
    }

    private void toggleReaderMode() {
        if (webView == null || !isNetworkPage(currentPageUrl)) { toast("当前页面不支持阅读模式"); return; }
        final WebView source = webView;
        final long sequence = navigationSequence;
        services.assistant().toggleReader(source, nightMode, new PageAssistant.Callback<String>() {
            @Override public void onResult(String value, Exception error) {
                if (!isCurrentPageCallback(source, sequence)) return;
                if (error != null) toast("阅读模式失败：" + safeMessage(error));
                else if ("none".equals(value)) toast("没有识别到足够长的正文");
                else toast("on".equals(value) ? "阅读模式已开启" : "阅读模式已关闭");
            }
        });
    }

    private void toggleReadAloud() {
        final PageAssistant assistant = services.assistant();
        if (assistant.isSpeaking()) { assistant.stop(); toast("朗读已停止"); return; }
        final WebView source = webView;
        final long sequence = navigationSequence;
        assistant.speak(source, new PageAssistant.Callback<Boolean>() {
            @Override public void onResult(Boolean value, Exception error) {
                if (!isCurrentPageCallback(source, sequence)) {
                    assistant.stop();
                    return;
                }
                if (error == null && Boolean.TRUE.equals(value)) toast("开始朗读正文");
                else toast(error == null ? "无法开始朗读" : "朗读失败：" + safeMessage(error));
            }
        });
    }

    private void showPageInfo() {
        if (webView == null) return;
        final WebView source = webView;
        final long sequence = navigationSequence;
        services.assistant().pageInfo(source, new PageAssistant.Callback<JSONObject>() {
            @Override public void onResult(JSONObject info, Exception error) {
                if (!isCurrentPageCallback(source, sequence)) return;
                if (error != null || info == null) { toast("无法分析页面"); return; }
                new AlertDialog.Builder(MainActivity.this).setTitle("页面信息")
                        .setMessage("标题：" + info.optString("title", "") +
                                "\n语言：" + info.optString("lang", "未声明") +
                                "\n编码：" + info.optString("charset", "") +
                                "\n字符：" + info.optLong("characters", 0L) +
                                "\n词语估算：" + info.optLong("words", 0L) +
                                "\n链接：" + info.optInt("links", 0) +
                                "\n图片：" + info.optInt("images", 0) +
                                "\n表单：" + info.optInt("forms", 0) +
                                "\n脚本：" + info.optInt("scripts", 0) + "\n\n" + info.optString("url", ""))
                        .setPositiveButton("确定", null).show();
            }
        });
    }

    private void showTlsInfo() {
        final String url = currentPageUrl;
        final WebView source = webView;
        final long sequence = navigationSequence;
        if (url == null || !url.startsWith("https://") || scriptExecutor == null || scriptExecutor.isShutdown()) {
            toast("当前页面不是可检查的 HTTPS 页面");
            return;
        }
        toast("正在独立验证服务器证书…");
        executeTask(scriptExecutor, new Runnable() {
            @Override public void run() {
                TlsInspector.Result value = null;
                Exception failure = null;
                try { value = TlsInspector.inspect(url); } catch (Exception e) { failure = e; }
                final TlsInspector.Result result = value;
                final Exception error = failure;
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (!isCurrentPageCallback(source, sequence) || !url.equals(currentPageUrl)) return;
                        if (error != null) { toast("证书检查失败：" + safeMessage(error)); return; }
                        new AlertDialog.Builder(MainActivity.this).setTitle("TLS 证书")
                                .setMessage(result.summary()).setPositiveButton("确定", null).show();
                    }
                });
            }
        });
    }

    private void saveOfflinePage() {
        if (webView == null || !isNetworkPage(currentPageUrl)) { toast("仅支持保存 HTTP(S) 页面"); return; }
        toast("正在保存完整离线页面…");
        services.offlinePages().save(webView, webView.getTitle(), currentPageUrl, new OfflinePageStore.Callback() {
            @Override public void onComplete(OfflinePageStore.Entry entry, Exception error) {
                toast(error == null ? "离线页面已保存 · " + humanBytes(entry.size) : "保存失败：" + safeMessage(error));
            }
        });
    }

    private void showOfflinePages() {
        final List<OfflinePageStore.Entry> entries = services.offlinePages().getAll();
        if (entries.size() == 0) { toast("还没有离线页面"); return; }
        String[] labels = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            OfflinePageStore.Entry entry = entries.get(i);
            labels[i] = safeTitle(entry.title, entry.url) + " · " + humanBytes(entry.size) + "\n" + hostOf(entry.url);
        }
        new AlertDialog.Builder(this).setTitle("离线页面").setItems(labels, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { showOfflineActions(entries.get(which)); }
        }).setNegativeButton("关闭", null).show();
    }

    private void showOfflineActions(final OfflinePageStore.Entry entry) {
        String[] items = new String[] { "离线打开", "打开原网页", "分享归档文件", "删除离线页面" };
        showActionSheet(entry.title, entry.url, items, new int[] { BrowserIconView.PLUS, BrowserIconView.RELOAD,
                BrowserIconView.SHARE, BrowserIconView.CLOSE }, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) {
                    String offlineUrl = services.offlinePages().uriFor(entry).toString();
                    prepareOfflineDestination(webView, offlineUrl);
                    webView.loadUrl(offlineUrl);
                }
                else if (which == 1) loadInput(entry.url);
                else if (which == 2) {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/x-mimearchive");
                    share.putExtra(Intent.EXTRA_STREAM, services.offlinePages().uriFor(entry));
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivity(Intent.createChooser(share, "分享离线页面")); }
                    catch (Exception e) { toast("没有可用的分享应用"); }
                } else {
                    services.offlinePages().remove(entry.file);
                    toast("离线页面已删除");
                }
            }
        });
    }

    private void copyCleanPageUrl() {
        String url = currentPageUrl;
        if (url == null || !isNetworkPage(url)) return;
        String cleaned = UrlCleaner.cleanTracking(url);
        android.content.ClipboardManager manager = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(android.content.ClipData.newPlainText("无跟踪参数地址", cleaned));
        toast(url.equals(cleaned) ? "地址中没有已知跟踪参数" : "已复制清理后的地址");
    }

    @Override public void run() { showBrowserSettings(); }

    private void showBrowserSettings() {
        String[] items = new String[] {
                "HTTPS 优先\n自动把可升级的地址优先使用安全连接",
                "跟踪参数清理\n打开网页前移除常见的跨站跟踪参数",
                "第三方 Cookie\n默认阻止；仍可在网站设置中单独放行",
                "每次打开\n当前：" + homeOpenBehaviorLabel(),
                "搜索\n当前：" + searchEngineLabel() + " · 默认与自定义规则",
                "主页与外观\n布局、Logo、搜索框、背景与快捷网站",
                "收藏与文件夹\n自定义网站、重命名与多级整理",
                "性能调度\n响应、内存与耗电策略",
                "存储与数据\n缓存、Cookie、历史与网站数据",
                "关于 Median\n版本、隐私说明与 WebView 兼容诊断"
        };
        int[] icons = new int[] {
                BrowserIconView.SHIELD, BrowserIconView.CLEAN, BrowserIconView.COOKIE,
                BrowserIconView.STARTUP, BrowserIconView.SEARCH, BrowserIconView.APPEARANCE,
                BrowserIconView.BOOKMARK, BrowserIconView.SPEED, BrowserIconView.STORAGE, BrowserIconView.INFO
        };
        int[] kinds = new int[] {
                httpsOnly ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                cleanTrackingParameters ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                acceptThirdPartyCookies ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE,
                SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE
        };
        String[] sections = new String[items.length];
        sections[0] = "隐私与安全";
        sections[3] = "启动与搜索";
        sections[5] = "界面";
        sections[7] = "资源与数据";
        sections[9] = "信息";
        showActionSheet("浏览器设置", "轻触开关立即保存 · 返回键只关闭设置", items, icons,
                kinds, sections, null, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) {
                    httpsOnly = !httpsOnly;
                    prefs.edit().putBoolean("https_only", httpsOnly).apply();
                } else if (which == 1) {
                    cleanTrackingParameters = !cleanTrackingParameters;
                    prefs.edit().putBoolean("clean_tracking_parameters", cleanTrackingParameters).apply();
                } else if (which == 2) {
                    acceptThirdPartyCookies = !acceptThirdPartyCookies;
                    prefs.edit().putBoolean("accept_third_party_cookies", acceptThirdPartyCookies).apply();
                    applyCookiePolicyToAll();
                    toast(acceptThirdPartyCookies ? "第三方 Cookie 已全局允许" :
                            "第三方 Cookie 已默认阻止，可按网站放行");
                } else {
                    final Runnable returnAction = MainActivity.this;
                    if (which == 3) showHomeOpenBehaviorChoice(false);
                    else if (which == 4) showSearchSettings();
                    else if (which == 5) showHomeCustomization(returnAction);
                    else if (which == 6) openBookmarkManager(returnAction);
                    else if (which == 7) showPerformancePanel(returnAction);
                    else if (which == 8) showStoragePanel(returnAction);
                    else showAbout(returnAction);
                }
            }
        });
    }

    private void showSearchSettings() {
        int customCount = searchEngines.customEngines().size();
        String[] items = new String[] {
                "默认搜索引擎\n当前：" + searchEngineLabel(),
                "管理自定义搜索引擎\n已保存 " + customCount + " 个"
        };
        int[] icons = new int[] { BrowserIconView.SEARCH, BrowserIconView.SETTINGS };
        final Runnable returnHere = new Runnable() {
            @Override public void run() { showSearchSettings(); }
        };
        showActionSheet("搜索设置", "地址栏与主页共用同一搜索策略", items, icons,
                null, null, this, new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showSearchEngineDialog(returnHere);
                        else showCustomSearchList(returnHere);
                    }
                });
    }

    private void showHomeCustomization() {
        showHomeCustomization(new Runnable() {
            @Override public void run() { showMainMenu(); }
        });
    }

    private void showHomeCustomization(Runnable backAction) {
        homeCustomizationBackAction = backAction;
        showHomeCustomizationPanel();
    }

    private void showHomeCustomizationPanel() {
        final HomePageConfig value = homePageConfig();
        boolean hasCustomHtml = CustomHomeHtml.valid(prefs.getString("home_custom_html", ""));
        boolean hasCustomCss = CustomHomeCss.clean(prefs.getString("home_custom_css", "")).length() > 0;
        String[] items = new String[] {
                "页面布局\n调整整体排版、副标题、时钟与强调色",
                "每次打开\n当前：" + homeOpenBehaviorLabel(),
                "Logo\n当前：" + logoModeLabel(value),
                "搜索框\n当前：" + (!value.showSearch ? "隐藏" : ("glass".equals(value.searchStyle) ? "磨砂" : "纯色")),
                "背景\n当前：" + (value.hasWallpaper ? "自定义壁纸" : "默认纯色"),
                "快捷网站\n当前：" + (value.showShortcuts ? value.shortcutColumns + " 列" : "隐藏"),
                "自定义主页\n当前：" + (value.customHtmlEnabled ? "完整 HTML" :
                        (hasCustomCss ? "自定义 CSS" : (hasCustomHtml ? "HTML 已保存、未启用" : "未设置")))
        };
        int[] icons = new int[] {
                BrowserIconView.TABS, BrowserIconView.HOME, BrowserIconView.INFO,
                BrowserIconView.SEARCH, BrowserIconView.DESKTOP, BrowserIconView.TABS,
                BrowserIconView.SCRIPT
        };
        showActionSheet("主页与外观", "修改立即保存 · 返回或关闭都不会离开当前网页", items, icons,
                null, null, homeCustomizationBackAction, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) showHomeLayoutCustomization();
                else if (which == 1) showHomeOpenBehaviorChoice(true);
                else if (which == 2) showLogoCustomization();
                else if (which == 3) showSearchCustomization();
                else if (which == 4) showBackgroundCustomization();
                else if (which == 5) showShortcutCustomization();
                else showCustomCodeCustomization();
            }
        });
    }

    private void showHomeLayoutCustomization() {
        final HomePageConfig value = homePageConfig();
        String[] items = new String[] {
                "副标题：" + settingPreview(value.subtitle, 16, "未设置"),
                "页面排列方式\n当前：" + ("compact".equals(value.layout) ? "紧凑" : "居中"),
                "主页时钟：" + (value.showClock ? "显示" : "隐藏"),
                "强调色：" + accentLabel(value.accent),
                "左上角品牌：" + (value.showCornerBrand ? "显示" : "隐藏"),
                "恢复全部默认外观"
        };
        showActionSheet("页面布局", "点击项目修改；选中后立即应用并返回此页", items,
                new int[] { BrowserIconView.INFO, BrowserIconView.TABS, BrowserIconView.INFO,
                        BrowserIconView.SPEED, BrowserIconView.TABS, BrowserIconView.CLOSE },
                null, null, new HomeDialogReturn(HOME_SECTION_MAIN), new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showHomeTextSetting(false, HOME_SECTION_LAYOUT);
                        else if (which == 1) showHomeStringChoiceInSection("页面布局", "home_layout",
                                new String[] { "center", "compact" }, new String[] { "居中", "紧凑" }, value.layout, HOME_SECTION_LAYOUT);
                        else if (which == 2) toggleHomeBooleanInSection("home_show_clock", value.showClock, HOME_SECTION_LAYOUT);
                        else if (which == 3) showHomeStringChoiceInSection("主页强调色", "home_accent",
                                new String[] { "blue", "violet", "green", "orange", "rose", "teal" },
                                new String[] { "蓝色", "紫色", "绿色", "橙色", "玫红", "青色" }, value.accent, HOME_SECTION_LAYOUT);
                        else if (which == 4) toggleHomeBooleanInSection("home_show_corner", value.showCornerBrand, HOME_SECTION_LAYOUT);
                        else confirmResetHomeCustomization(HOME_SECTION_LAYOUT);
                    }
                });
    }

    private void showLogoCustomization() {
        final HomePageConfig value = homePageConfig();
        ArrayList<String> labels = new ArrayList<String>();
        ArrayList<Integer> actions = new ArrayList<Integer>();
        labels.add("Logo 类型：" + logoModeLabel(value)); actions.add(Integer.valueOf(0));
        if ("text".equals(value.logoMode)) {
            labels.add("Logo 文字：" + settingPreview(value.title, 18, "Median")); actions.add(Integer.valueOf(1));
            labels.add("文字配色：" + logoStyleLabel(value.logoStyle)); actions.add(Integer.valueOf(2));
            labels.add("文字排版：" + value.logoFontSize + " px · " + value.logoFontWeight + " · " +
                    logoLetterSpacingLabel(value.logoLetterSpacing)); actions.add(Integer.valueOf(3));
            labels.add("高级文字代码"); actions.add(Integer.valueOf(7));
        } else if ("image".equals(value.logoMode)) {
            labels.add(value.hasLogo ? "更换 Logo 图片" : "选择 Logo 图片"); actions.add(Integer.valueOf(8));
            labels.add("图片宽度：" + value.logoImageWidth + " px"); actions.add(Integer.valueOf(9));
            labels.add("图片高度：" + value.logoImageHeight + " px"); actions.add(Integer.valueOf(10));
            labels.add("图片圆角：" + value.logoImageRadius + "%"); actions.add(Integer.valueOf(11));
        }
        final int[] mapped = new int[actions.size()];
        for (int i = 0; i < mapped.length; i++) mapped[i] = actions.get(i).intValue();
        String[] items = labels.toArray(new String[labels.size()]);
        int[] icons = new int[items.length];
        for (int i = 0; i < icons.length; i++) icons[i] = i == 0 ? BrowserIconView.INFO : BrowserIconView.SPEED;
        showActionSheet("Logo", "点击项目修改；高级文字代码只有保存后才会生效", items, icons,
                null, null, new HomeDialogReturn(HOME_SECTION_MAIN), new SheetHandler() {
            @Override public void onItem(int which) {
                int action = mapped[which];
                if (action == 0) showLogoModeChoice(value);
                else if (action == 1) showHomeTextSetting(true, HOME_SECTION_LOGO);
                else if (action == 2) showLogoStyleChoice(value, HOME_SECTION_LOGO);
                else if (action == 3) showLogoTypographyCustomization();
                else if (action == 7) showLogoCodeEditor(HOME_SECTION_LOGO);
                else if (action == 8) chooseHomeImage(HomeImageStore.Kind.LOGO, HOME_SECTION_LOGO);
                else if (action == 9) showHomeIntegerChoiceInSection("图片宽度", "home_logo_image_width",
                        new int[] { 64, 88, 112, 132, 160, 200, 240 },
                        new String[] { "64 px", "88 px", "112 px", "132 px", "160 px", "200 px", "240 px" }, value.logoImageWidth, HOME_SECTION_LOGO);
                else if (action == 10) showHomeIntegerChoiceInSection("图片高度", "home_logo_image_height",
                        new int[] { 48, 64, 80, 96, 112, 144, 176 },
                        new String[] { "48 px", "64 px", "80 px", "96 px", "112 px", "144 px", "176 px" }, value.logoImageHeight, HOME_SECTION_LOGO);
                else showHomeIntegerChoiceInSection("图片圆角", "home_logo_image_radius",
                        new int[] { 0, 8, 16, 24, 32, 50 },
                        new String[] { "0%", "8%", "16%", "24%", "32%", "50% 圆形" }, value.logoImageRadius, HOME_SECTION_LOGO);
            }
        });
    }

    private void showLogoTypographyCustomization() {
        final HomePageConfig value = homePageConfig();
        String[] items = new String[] {
                "字号：" + value.logoFontSize + " px",
                "字重：" + value.logoFontWeight,
                "字间距：" + logoLetterSpacingLabel(value.logoLetterSpacing),
                "渐变方向：" + logoGradientAngleLabel(value.logoGradientAngle)
        };
        showActionSheet("文字排版", "统一调整 Logo 的尺寸与间隔", items,
                new int[] { BrowserIconView.SPEED, BrowserIconView.SPEED, BrowserIconView.SPEED, BrowserIconView.SPEED },
                null, null, new HomeDialogReturn(HOME_SECTION_LOGO), new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showHomeIntegerChoiceInSection("Logo 字号", "home_logo_font_size",
                                new int[] { 28, 36, 44, 47, 52, 64, 72, 88 },
                                new String[] { "28 px", "36 px", "44 px", "47 px", "52 px", "64 px", "72 px", "88 px" }, value.logoFontSize, HOME_SECTION_LOGO);
                        else if (which == 1) showHomeIntegerChoiceInSection("Logo 字重", "home_logo_font_weight",
                                new int[] { 300, 400, 500, 600, 700, 800, 900 },
                                new String[] { "300 细", "400 常规", "500 中等", "600 半粗", "700 粗体", "800 特粗", "900 黑体" }, value.logoFontWeight, HOME_SECTION_LOGO);
                        else if (which == 2) showHomeIntegerChoiceInSection("Logo 字间距", "home_logo_letter_spacing",
                                new int[] { -3, -2, -1, 0, 1, 2, 3, 4, 6, 8, 10 },
                                new String[] { "-3 px", "-2 px", "-1 px", "标准", "+1 px", "+2 px", "+3 px", "+4 px", "+6 px", "+8 px", "+10 px" }, value.logoLetterSpacing, HOME_SECTION_LOGO);
                        else showHomeIntegerChoiceInSection("渐变方向", "home_logo_gradient_angle",
                                new int[] { 90, 135, 180, 45, 0 },
                                new String[] { "左 → 右", "左上 → 右下", "上 → 下", "左下 → 右上", "下 → 上" }, value.logoGradientAngle, HOME_SECTION_LOGO);
                    }
                });
    }

    private void showLogoModeChoice(final HomePageConfig value) {
        HomeDialogReturn back = new HomeDialogReturn(HOME_SECTION_LOGO);
        int checked;
        boolean defaults = "text".equals(value.logoMode) && "Median".equals(value.title) &&
                "median".equals(value.logoStyle) && value.logoFontSize == 47 &&
                value.logoFontWeight == 720 && value.logoLetterSpacing == 0;
        if ("none".equals(value.logoMode)) checked = 3;
        else if ("image".equals(value.logoMode)) checked = 2;
        else checked = defaults ? 0 : 1;
        new AlertDialog.Builder(this).setTitle("Logo 类型").setSingleChoiceItems(
                new String[] { "默认 Median", "自定义文字", "图片", "无" }, checked,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        SharedPreferences.Editor editor = prefs.edit();
                        if (which == 0) {
                            editor.putString("home_logo_mode", "text").putString("home_title", "Median")
                                    .putString("home_logo_style", "median").remove("home_logo_code")
                                    .putInt("home_logo_font_size", 47).putInt("home_logo_font_weight", 720)
                                    .putInt("home_logo_letter_spacing", 0).apply();
                            homeCustomizationChanged("已恢复默认 Median Logo", HOME_SECTION_LOGO);
                        } else if (which == 1) {
                            editor.putString("home_logo_mode", "text").apply();
                            homeCustomizationChanged(null, HOME_SECTION_LOGO);
                        } else if (which == 2) {
                            editor.putString("home_logo_mode", "image").apply();
                            if (value.hasLogo) homeCustomizationChanged(null, HOME_SECTION_LOGO);
                            else {
                                if (isHomeUrl(currentPageUrl)) showHome();
                                chooseHomeImage(HomeImageStore.Kind.LOGO, HOME_SECTION_LOGO);
                            }
                        } else {
                            editor.putString("home_logo_mode", "none").apply();
                            homeCustomizationChanged("主页 Logo 已隐藏", HOME_SECTION_LOGO);
                        }
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showSearchCustomization() {
        final HomePageConfig value = homePageConfig();
        String[] items = new String[] {
                "搜索框：" + (!value.showSearch ? "隐藏" : ("glass".equals(value.searchStyle) ? "磨砂" : "纯色")),
                "搜索引擎按钮：" + (value.showEngines ? "显示" : "隐藏"),
                "默认搜索引擎：" + searchEngineLabel()
        };
        showActionSheet("搜索框", "功能保持内置，CSS 只修改外观", items,
                new int[] { BrowserIconView.SEARCH, BrowserIconView.SEARCH, BrowserIconView.SEARCH },
                null, null, new HomeDialogReturn(HOME_SECTION_MAIN), new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showHomeSearchChoice(value, HOME_SECTION_SEARCH);
                        else if (which == 1) toggleHomeBooleanInSection("home_show_engines", value.showEngines, HOME_SECTION_SEARCH);
                        else showSearchEngineDialog(new HomeDialogReturn(HOME_SECTION_SEARCH));
                    }
                });
    }

    private void showBackgroundCustomization() {
        final HomePageConfig value = homePageConfig();
        String[] items = new String[] {
                "主页壁纸：" + (value.hasWallpaper ? "已设置" : "默认纯色"),
                "壁纸遮罩：" + value.wallpaperDim + "%",
                "壁纸模糊：" + (value.wallpaperBlur == 0 ? "关闭" : value.wallpaperBlur + " px"),
                "壁纸显示：" + ("contain".equals(value.wallpaperFit) ? "完整显示" : "填充裁剪")
        };
        showActionSheet("背景", "壁纸继续独立于 CSS 与 HTML", items,
                new int[] { BrowserIconView.DESKTOP, BrowserIconView.SHIELD, BrowserIconView.SPEED, BrowserIconView.DESKTOP },
                null, null, new HomeDialogReturn(HOME_SECTION_MAIN), new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showHomeAssetActions(HomeImageStore.Kind.WALLPAPER);
                        else if (which == 1) showHomeIntegerChoiceInSection("壁纸遮罩", "home_wallpaper_dim",
                                new int[] { 0, 15, 28, 40, 55, 70 }, new String[] { "0%", "15%", "28%", "40%", "55%", "70%" }, value.wallpaperDim, HOME_SECTION_BACKGROUND);
                        else if (which == 2) showHomeIntegerChoiceInSection("壁纸模糊", "home_wallpaper_blur",
                                new int[] { 0, 3, 6, 9, 12 }, new String[] { "关闭", "3 px", "6 px", "9 px", "12 px" }, value.wallpaperBlur, HOME_SECTION_BACKGROUND);
                        else showHomeStringChoiceInSection("壁纸显示方式", "home_wallpaper_fit",
                                new String[] { "cover", "contain" }, new String[] { "填充裁剪", "完整显示" }, value.wallpaperFit, HOME_SECTION_BACKGROUND);
                    }
                });
    }

    private void showShortcutCustomization() {
        final HomePageConfig value = homePageConfig();
        String[] items = new String[] {
                "快捷网站：" + (value.showShortcuts ? "显示" : "隐藏"),
                "入口列数：" + value.shortcutColumns + " 列",
                "入口形状：" + tileShapeLabel(value.tileShape),
                "管理快捷网站"
        };
        showActionSheet("快捷网站", "显示内容来自本地书签", items,
                new int[] { BrowserIconView.TABS, BrowserIconView.TABS, BrowserIconView.TABS, BrowserIconView.INFO },
                null, null, new HomeDialogReturn(HOME_SECTION_MAIN), new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) toggleHomeBooleanInSection("home_show_shortcuts", value.showShortcuts, HOME_SECTION_SHORTCUTS);
                        else if (which == 1) showHomeIntegerChoiceInSection("快捷入口列数", "home_shortcut_columns",
                                new int[] { 3, 4, 5 }, new String[] { "3 列", "4 列", "5 列" }, value.shortcutColumns, HOME_SECTION_SHORTCUTS);
                        else if (which == 2) showHomeStringChoiceInSection("快捷入口形状", "home_tile_shape",
                                new String[] { "rounded", "circle", "square" }, new String[] { "圆角方形", "圆形", "小圆角方形" }, value.tileShape, HOME_SECTION_SHORTCUTS);
                        else openBookmarkManager(new HomeDialogReturn(HOME_SECTION_SHORTCUTS));
                    }
                });
    }

    private void showCustomCodeCustomization() {
        HomePageConfig value = homePageConfig();
        boolean hasCss = value.customCss.length() > 0;
        boolean hasHtml = CustomHomeHtml.valid(prefs.getString("home_custom_html", ""));
        String[] items = new String[] {
                "自定义 CSS\n" + (hasCss ? (value.customHtmlEnabled ? "已保存，HTML 模式下暂不显示" : "已启用") : "未设置") + " · 保留内置功能（推荐）",
                "完整 HTML\n" + (value.customHtmlEnabled ? "已启用" : (hasHtml ? "已保存，未启用" : "未设置")) + " · 完全替换主页，JS 在沙箱内运行"
        };
        showActionSheet("自定义主页", "选择一种方式进入编辑或启停管理", items,
                new int[] { BrowserIconView.SPEED, BrowserIconView.SCRIPT },
                null, null, new HomeDialogReturn(HOME_SECTION_MAIN), new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showCustomCssActions();
                        else showCustomHomeActions(HOME_SECTION_CODE);
                    }
                });
    }

    private void showHomeOpenBehaviorChoice() { showHomeOpenBehaviorChoice(false); }

    private void showHomeOpenBehaviorChoice(final boolean returnToCustomization) {
        HomeOpenDialogReturn back = new HomeOpenDialogReturn(returnToCustomization);
        final String[] modes = new String[] { HomeOpenPolicy.OPEN_HOME, HomeOpenPolicy.OPEN_CUSTOM_URL,
                HomeOpenPolicy.KEEP_LAST };
        String[] labels = new String[] {
                "打开主页\n使用内置主页或自定义 HTML 主页",
                "自定义页面\n将一个网页链接设为主页",
                "保留上一次访问的内容\n冷启动时恢复上次标签页"
        };
        int checked = 0;
        String current = homeOpenMode();
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(current)) checked = i;
        new AlertDialog.Builder(this).setTitle("每次打开").setSingleChoiceItems(labels, checked,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (HomeOpenPolicy.OPEN_CUSTOM_URL.equals(modes[which])) {
                            showCustomHomeUrlEditor(returnToCustomization);
                            return;
                        }
                        restoreTabs = HomeOpenPolicy.KEEP_LAST.equals(modes[which]);
                        prefs.edit().putString("home_open_mode", modes[which])
                                .putBoolean("restore_tabs", restoreTabs).apply();
                        toast(restoreTabs ? "下次启动将恢复上次内容" : "已设为打开主页");
                        continueHomeOpenSettings(returnToCustomization);
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showCustomHomeUrlEditor(final boolean returnToCustomization) {
        final EditText input = new EditText(this);
        final HomeOpenDialogReturn back = new HomeOpenDialogReturn(returnToCustomization);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("例如：www.example.com");
        input.setText(prefs.getString("home_custom_url", ""));
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(16), dp(8), dp(16), dp(8));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("自定义页面")
                .setMessage("输入网页地址后，主页键、冷启动和每个新建标签页都会打开它。没有协议时自动使用 HTTPS。")
                .setView(input).setPositiveButton("保存并使用", null)
                .setNegativeButton("取消", back).create();
        dialog.setOnCancelListener(back);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        String normalized = normalizeConfiguredHomeUrl(input.getText().toString());
                        if (normalized.length() == 0) { toast("请输入有效的 HTTP 或 HTTPS 网页地址"); return; }
                        boolean saved = prefs.edit().putString("home_open_mode", HomeOpenPolicy.OPEN_CUSTOM_URL)
                                .putString("home_custom_url", normalized).putBoolean("restore_tabs", false).commit();
                        if (!saved) { toast("主页设置保存失败"); return; }
                        restoreTabs = false;
                        dialog.dismiss();
                        toast("自定义页面已设为主页");
                        continueHomeOpenSettings(returnToCustomization);
                    }
                });
            }
        });
        dialog.show();
    }

    private void continueHomeOpenSettings(boolean returnToCustomization) {
        if (returnToCustomization) continueHomeCustomization();
        else continueSettingsPanel(this);
    }

    private void showCustomCssActions() {
        final String saved = CustomHomeCss.clean(prefs.getString("home_custom_css", ""));
        if (saved.length() == 0) { showCustomCssEditor(); return; }
        HomeDialogReturn back = new HomeDialogReturn(HOME_SECTION_CODE);
        new AlertDialog.Builder(this).setTitle("自定义 CSS")
                .setItems(new String[] { "编辑 CSS", "清除 CSS" }, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) showCustomCssEditor();
                        else {
                            prefs.edit().remove("home_custom_css").apply();
                            homeCustomizationChanged("自定义 CSS 已清除", HOME_SECTION_CODE);
                        }
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showCustomCssEditor() {
        final EditText input = compactCodeEditor();
        final HomeDialogReturn back = new HomeDialogReturn(HOME_SECTION_CODE);
        String saved = CustomHomeCss.clean(prefs.getString("home_custom_css", ""));
        input.setText(saved.length() == 0 ? CustomHomeCss.EXAMPLE : saved);
        input.setHint(CustomHomeCss.EXAMPLE);
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("自定义 CSS")
                .setMessage("推荐方案：只改变外观，搜索、时钟和快捷网站继续由 Median 负责。常用选择器：.brand、.search、.engines、.shortcuts、.tile、.corner、.wrap。最大 32 KB；禁止外部 URL 与 @import。")
                .setView(input).setPositiveButton("保存并使用", null).setNeutralButton("示例", null)
                .setNegativeButton("取消", back).create();
        dialog.setOnCancelListener(back);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) { input.setText(CustomHomeCss.EXAMPLE); input.setSelection(input.length()); }
                });
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        String raw = input.getText().toString().trim();
                        if (raw.length() == 0) { toast("CSS 不能为空，可使用“清除 CSS”移除"); return; }
                        String error = CustomHomeCss.error(raw);
                        if (error.length() > 0) { toast(error); return; }
                        prefs.edit().putString("home_custom_css", raw)
                                .putBoolean("home_custom_html_enabled", false).apply();
                        dialog.dismiss();
                        homeCustomizationChanged("自定义 CSS 已启用", HOME_SECTION_CODE);
                    }
                });
            }
        });
        dialog.show();
    }

    private EditText compactCodeEditor() {
        EditText input = new EditText(this);
        input.setMinLines(4);
        input.setMaxLines(8);
        input.setMinHeight(dp(132));
        input.setMaxHeight(dp(238));
        input.setVerticalScrollBarEnabled(true);
        input.setHorizontallyScrolling(false);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setTypeface(android.graphics.Typeface.MONOSPACE);
        input.setTextSize(13.5f);
        input.setTextColor(nightMode ? Color.rgb(232, 234, 237) : TEXT);
        input.setHintTextColor(nightMode ? Color.rgb(154, 160, 166) : MUTED);
        input.setBackground(roundRect(nightMode ? Color.rgb(43, 46, 51) : SURFACE, 12));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        return input;
    }

    private void showCustomHomeActions() { showCustomHomeActions(HOME_SECTION_MAIN); }

    private void showCustomHomeActions(final int returnSection) {
        final String saved = prefs.getString("home_custom_html", "");
        if (!CustomHomeHtml.valid(saved)) { showCustomHomeEditor(returnSection); return; }
        final boolean enabled = prefs.getBoolean("home_custom_html_enabled", false);
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        new AlertDialog.Builder(this).setTitle("完整 HTML 页面")
                .setItems(new String[] { "编辑 HTML", enabled ? "停用并显示默认主页" : "启用自定义主页", "删除自定义主页" },
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) showCustomHomeEditor(returnSection);
                                else if (which == 1) {
                                    prefs.edit().putBoolean("home_custom_html_enabled", !enabled).apply();
                                    homeCustomizationChanged(enabled ? "已停用完整 HTML" : "已启用完整 HTML", returnSection);
                                } else confirmDeleteCustomHome(returnSection);
                            }
                        }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showCustomHomeEditor(final int returnSection) {
        final EditText input = compactCodeEditor();
        final HomeDialogReturn back = new HomeDialogReturn(returnSection);
        String saved = prefs.getString("home_custom_html", "");
        input.setText(CustomHomeHtml.valid(saved) ? saved : CustomHomeHtml.EXAMPLE);
        input.setHint(CustomHomeHtml.EXAMPLE);
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("自定义主页 HTML")
                .setMessage("完整替换主页，支持 HTML、CSS 和本地 JavaScript，最大 64 KB。脚本在无同源、无联网、无内部权限的沙箱中运行；壁纸仍独立控制。")
                .setView(input).setPositiveButton("保存并启用", null).setNeutralButton("示例", null)
                .setNegativeButton("取消", back).create();
        dialog.setOnCancelListener(back);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        input.setText(CustomHomeHtml.EXAMPLE);
                        input.setSelection(input.length());
                    }
                });
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        String raw = input.getText().toString().trim();
                        if (raw.length() == 0) { toast("HTML 不能为空"); return; }
                        if (raw.getBytes(StandardCharsets.UTF_8).length > CustomHomeHtml.MAX_LENGTH) {
                            toast("HTML 不能超过 64 KB"); return;
                        }
                        long previous = Math.max(0L, prefs.getLong("home_custom_html_version", 0L));
                        long next = previous == Long.MAX_VALUE ? 1L : previous + 1L;
                        boolean saved = prefs.edit().putString("home_custom_html", raw)
                                .putBoolean("home_custom_html_enabled", true)
                                .putLong("home_custom_html_version", next).commit();
                        if (!saved) { toast("自定义主页保存失败"); return; }
                        dialog.dismiss();
                        homeCustomizationChanged("完整 HTML 已保存并启用", returnSection);
                    }
                });
            }
        });
        dialog.show();
    }

    private void confirmDeleteCustomHome(final int returnSection) {
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        new AlertDialog.Builder(this).setTitle("删除自定义主页？")
                .setMessage("HTML 代码将从本机永久删除，壁纸不会受影响。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        prefs.edit().remove("home_custom_html").remove("home_custom_html_enabled")
                                .remove("home_custom_html_version").apply();
                        homeCustomizationChanged("完整 HTML 已删除", returnSection);
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showHomeAssetActions(final HomeImageStore.Kind kind) {
        final int returnSection = kind == HomeImageStore.Kind.WALLPAPER ? HOME_SECTION_BACKGROUND : HOME_SECTION_LOGO;
        if (homeImages == null || !homeImages.has(kind)) { chooseHomeImage(kind, returnSection); return; }
        String label = kind == HomeImageStore.Kind.WALLPAPER ? "壁纸" : "Logo";
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        new AlertDialog.Builder(this).setTitle("主页" + label)
                .setItems(new String[] { "更换图片", "移除自定义" + label }, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) chooseHomeImage(kind, returnSection);
                        else {
                            homeImages.remove(kind);
                            if (kind == HomeImageStore.Kind.LOGO) prefs.edit().putString("home_logo_mode", "text").apply();
                            homeCustomizationChanged(kind == HomeImageStore.Kind.WALLPAPER ?
                                    "已恢复默认背景" : "已恢复文字 Logo", returnSection);
                        }
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void chooseHomeImage(HomeImageStore.Kind kind, int returnSection) {
        pendingHomeImageReturnSection = returnSection;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, kind == HomeImageStore.Kind.WALLPAPER ? HOME_WALLPAPER_REQUEST : HOME_LOGO_REQUEST);
        } catch (Exception error) {
            toast("没有可用的图片选择器");
            continueHomeSection(pendingHomeImageReturnSection);
        }
    }

    private void importHomeImage(final Uri uri, final HomeImageStore.Kind kind) {
        if (uri == null || homeImages == null || scriptExecutor == null || scriptExecutor.isShutdown()) {
            toast("无法读取所选图片");
            continueHomeSection(pendingHomeImageReturnSection);
            return;
        }
        toast(kind == HomeImageStore.Kind.WALLPAPER ? "正在优化壁纸…" : "正在优化 Logo…");
        try {
            executeTask(scriptExecutor, new Runnable() {
                @Override public void run() {
                    Exception failure = null;
                    try { homeImages.save(uri, kind); }
                    catch (Exception error) { failure = error; }
                    final Exception result = failure;
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (result != null) {
                                toast("图片处理失败：" + safeMessage(result));
                                continueHomeSection(pendingHomeImageReturnSection);
                                return;
                            }
                            if (kind == HomeImageStore.Kind.LOGO)
                                prefs.edit().putString("home_logo_mode", "image").apply();
                            homeCustomizationChanged(kind == HomeImageStore.Kind.WALLPAPER ?
                                    "主页壁纸已更新" : "主页 Logo 已更新", pendingHomeImageReturnSection);
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            toast("图片处理队列繁忙，请稍后重试");
            continueHomeSection(pendingHomeImageReturnSection);
        }
    }

    private void showHomeTextSetting(final boolean title) { showHomeTextSetting(title, HOME_SECTION_MAIN); }

    private void showHomeTextSetting(final boolean title, final int returnSection) {
        final EditText input = new EditText(this);
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        input.setSingleLine(true);
        input.setText(title ? homePageConfig().title : homePageConfig().subtitle);
        input.setHint(title ? "Median" : "例如：今天也要保持好奇");
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(16), dp(8), dp(16), dp(8));
        new AlertDialog.Builder(this).setTitle(title ? "Logo 文字" : "主页副标题")
                .setMessage(title ? "最多 28 个字符；留空恢复 Median。" : "最多 64 个字符；留空则隐藏。")
                .setView(input).setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String value = title ? HomePageConfig.cleanTitle(input.getText().toString()) :
                                HomePageConfig.cleanSubtitle(input.getText().toString());
                        SharedPreferences.Editor editor = prefs.edit().putString(title ? "home_title" : "home_subtitle", value);
                        if (title) {
                            editor.putString("home_logo_mode", "text");
                            if ("custom".equals(homePageConfig().logoStyle))
                                editor.putString("home_logo_style", "median").remove("home_logo_code");
                        }
                        editor.apply();
                        homeCustomizationChanged(title ? "Logo 文字已更新" : "主页副标题已更新", returnSection);
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showLogoStyleChoice(final HomePageConfig value) { showLogoStyleChoice(value, HOME_SECTION_MAIN); }

    private void showLogoStyleChoice(final HomePageConfig value, final int returnSection) {
        final String[] styles = new String[] { "median", "google", "aurora", "sunset", "ocean", "rose_gold", "custom" };
        String[] labels = new String[] { "Median 经典", "Google 官方配色", "极光渐变", "日落渐变", "海洋渐变", "玫瑰金渐变", "自定义代码" };
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        int checked = 0;
        for (int i = 0; i < styles.length; i++) if (styles[i].equals(value.logoStyle)) checked = i;
        new AlertDialog.Builder(this).setTitle("文字 Logo 样式").setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String style = styles[which];
                dialog.dismiss();
                if ("custom".equals(style)) {
                    showLogoCodeEditor(returnSection);
                    return;
                }
                prefs.edit().putString("home_logo_style", style)
                        .putString("home_logo_mode", "text").apply();
                homeCustomizationChanged(null, returnSection);
            }
        }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showLogoCodeEditor() { showLogoCodeEditor(HOME_SECTION_MAIN); }

    private void showLogoCodeEditor(final int returnSection) {
        final EditText input = new EditText(this);
        final HomeDialogReturn back = new HomeDialogReturn(returnSection);
        String saved = homePageConfig().logoCode;
        String example = LogoMarkup.gradientExample(homePageConfig().title);
        input.setText(saved.length() == 0 ? example : saved);
        input.setHint(example);
        input.setMinLines(5);
        input.setMaxLines(9);
        input.setHorizontallyScrolling(false);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setTypeface(android.graphics.Typeface.MONOSPACE);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("自定义文字 Logo")
                .setMessage("安全标记语法（不会执行 HTML/JavaScript）：\n\n" +
                        "[color=#4285F4]文字[/color]\n" +
                        "[gradient=#8B5CF6,#6366F1,#22D3EE]文字[/gradient]\n" +
                        "Med[space=4]ian\n\n" +
                        "普通空格会保留；[space=0–24] 可精确控制局部间隔。渐变支持 2–4 个颜色。")
                .setView(input).setPositiveButton("保存", null)
                .setNeutralButton("Google 示例", null)
                .setNegativeButton("取消", back).create();
        dialog.setOnCancelListener(back);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) { input.setText(LogoMarkup.GOOGLE_CODE); input.setSelection(input.length()); }
                });
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        LogoMarkup.Result parsed = LogoMarkup.parse(input.getText().toString(), homePageConfig().logoGradientAngle);
                        if (!parsed.valid()) { toast(parsed.error); return; }
                        prefs.edit().putString("home_logo_mode", "text")
                                .putString("home_logo_style", "custom")
                                .putString("home_logo_code", LogoMarkup.clean(input.getText().toString()))
                                .putString("home_title", HomePageConfig.cleanTitle(parsed.plainText)).apply();
                        dialog.dismiss();
                        homeCustomizationChanged("自定义文字 Logo 已保存", returnSection);
                    }
                });
            }
        });
        dialog.show();
    }

    private void showHomeStringChoice(String title, final String key, final String[] values,
                                      String[] labels, String current) {
        showHomeStringChoiceInSection(title, key, values, labels, current, HOME_SECTION_MAIN);
    }

    private void showHomeStringChoiceInSection(String title, final String key, final String[] values,
                                      String[] labels, String current, final int returnSection) {
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) checked = i;
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                prefs.edit().putString(key, values[which]).apply();
                dialog.dismiss();
                homeCustomizationChanged(null, returnSection);
            }
        }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showHomeIntegerChoice(String title, final String key, final int[] values,
                                       String[] labels, int current) {
        showHomeIntegerChoiceInSection(title, key, values, labels, current, HOME_SECTION_MAIN);
    }

    private void showHomeIntegerChoiceInSection(String title, final String key, final int[] values,
                                       String[] labels, int current, final int returnSection) {
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == current) checked = i;
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                prefs.edit().putInt(key, values[which]).apply();
                dialog.dismiss();
                homeCustomizationChanged(null, returnSection);
            }
        }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showHomeSearchChoice(HomePageConfig value) { showHomeSearchChoice(value, HOME_SECTION_MAIN); }

    private void showHomeSearchChoice(HomePageConfig value, final int returnSection) {
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        int checked = !value.showSearch ? 2 : ("glass".equals(value.searchStyle) ? 1 : 0);
        new AlertDialog.Builder(this).setTitle("搜索框").setSingleChoiceItems(
                new String[] { "显示 · 纯色", "显示 · 磨砂", "隐藏" }, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences.Editor editor = prefs.edit().putBoolean("home_show_search", which != 2);
                        if (which != 2) editor.putString("home_search_style", which == 1 ? "glass" : "solid");
                        editor.apply();
                        dialog.dismiss();
                        homeCustomizationChanged(null, returnSection);
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void toggleHomeBoolean(String key, boolean current) {
        toggleHomeBooleanInSection(key, current, HOME_SECTION_MAIN);
    }

    private void toggleHomeBooleanInSection(String key, boolean current, int returnSection) {
        prefs.edit().putBoolean(key, !current).apply();
        homeCustomizationChanged(null, returnSection);
    }

    private void confirmResetHomeCustomization(final int returnSection) {
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        new AlertDialog.Builder(this).setTitle("恢复默认主页？")
                .setMessage("将移除自定义壁纸、Logo、CSS 和 HTML，并恢复标题、颜色、布局及模块显示。书签和“每次打开”设置不会删除。")
                .setPositiveButton("恢复", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences.Editor editor = prefs.edit();
                        String[] keys = new String[] { "home_title", "home_subtitle", "home_accent", "home_wallpaper_dim",
                                "home_logo_style", "home_logo_code", "home_logo_letter_spacing", "home_logo_gradient_angle",
                                "home_logo_mode", "home_logo_font_size", "home_logo_font_weight", "home_logo_image_width",
                                "home_logo_image_height", "home_logo_image_radius", "home_custom_css",
                                "home_wallpaper_blur", "home_wallpaper_fit", "home_search_style", "home_layout",
                                "home_tile_shape", "home_shortcut_columns", "home_show_search", "home_show_engines",
                                "home_show_shortcuts", "home_show_corner", "home_show_clock", "home_custom_html",
                                "home_custom_html_enabled", "home_custom_html_version" };
                        for (String key : keys) editor.remove(key);
                        editor.apply();
                        if (homeImages != null) homeImages.removeAll();
                        homeCustomizationChanged("已恢复默认主页", returnSection);
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void homeCustomizationChanged(String message) {
        homeCustomizationChanged(message, HOME_SECTION_MAIN);
    }

    private void homeCustomizationChanged(String message, int returnSection) {
        if (isHomeUrl(currentPageUrl)) showHome();
        if (message != null && message.length() > 0) toast(message);
        continueHomeSection(returnSection);
    }

    private void continueHomeCustomization() {
        continueHomeSection(HOME_SECTION_MAIN);
    }

    private void continueSettingsPanel(final Runnable action) {
        if (action == null) return;
        postAfterUiTransition(new Runnable() {
            @Override public void run() {
                if (isFinishing() || isDestroyed()) return;
                action.run();
            }
        });
    }

    private void continueHomeSection(final int section) {
        postAfterUiTransition(new HomeDialogReturn(section));
    }

    private void showHomeSection(int section) {
        if (isFinishing() || isDestroyed()) return;
        if (section == HOME_SECTION_LAYOUT) showHomeLayoutCustomization();
        else if (section == HOME_SECTION_LOGO) showLogoCustomization();
        else if (section == HOME_SECTION_SEARCH) showSearchCustomization();
        else if (section == HOME_SECTION_BACKGROUND) showBackgroundCustomization();
        else if (section == HOME_SECTION_SHORTCUTS) showShortcutCustomization();
        else if (section == HOME_SECTION_CODE) showCustomCodeCustomization();
        else showHomeCustomizationPanel();
    }

    private void postAfterUiTransition(Runnable action) {
        if (action == null) return;
        final View target = rootFrame;
        if (target == null) {
            uiHandler.post(action);
        } else if (target.isAttachedToWindow()) {
            target.postOnAnimation(action);
        } else {
            // onResume is invoked before WindowManager necessarily attaches the decor view. A
            // plain Handler.post here can still run before the first traversal and lets vendor
            // WebView providers drop the initial load. Cross the attach boundary and one frame.
            final Runnable pending = action;
            target.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                    view.removeOnAttachStateChangeListener(this);
                    view.postOnAnimation(pending);
                }
                @Override public void onViewDetachedFromWindow(View view) {}
            });
        }
    }

    private static String settingPreview(String value, int max, String empty) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() == 0) return empty;
        int count = clean.codePointCount(0, clean.length());
        return count <= max ? clean : clean.substring(0, clean.offsetByCodePoints(0, max)) + "…";
    }

    private static String accentLabel(String value) {
        if ("violet".equals(value)) return "紫色";
        if ("green".equals(value)) return "绿色";
        if ("orange".equals(value)) return "橙色";
        if ("rose".equals(value)) return "玫红";
        if ("teal".equals(value)) return "青色";
        return "蓝色";
    }

    private static String tileShapeLabel(String value) {
        if ("circle".equals(value)) return "圆形";
        if ("square".equals(value)) return "小圆角方形";
        return "圆角方形";
    }

    private static String logoModeLabel(HomePageConfig value) {
        if (value == null || "none".equals(value.logoMode)) return "无";
        if ("image".equals(value.logoMode)) return value.hasLogo ? "图片" : "图片（未选择）";
        return "文字 · " + settingPreview(value.title, 12, "Median");
    }

    private static String logoStyleLabel(String value) {
        if ("google".equals(value)) return "Google 官方配色";
        if ("aurora".equals(value)) return "极光渐变";
        if ("sunset".equals(value)) return "日落渐变";
        if ("ocean".equals(value)) return "海洋渐变";
        if ("rose_gold".equals(value)) return "玫瑰金渐变";
        if ("custom".equals(value)) return "自定义代码";
        return "Median 经典";
    }

    private static String logoLetterSpacingLabel(int value) {
        if (value == 0) return "标准";
        return (value > 0 ? "+" : "") + value + " px";
    }

    private static String logoGradientAngleLabel(int value) {
        if (value == 135) return "左上 → 右下";
        if (value == 180) return "上 → 下";
        if (value == 45) return "左下 → 右上";
        if (value == 0) return "下 → 上";
        if (value == 90) return "左 → 右";
        return value + "°";
    }

    private void showSearchEngineDialog() {
        showSearchEngineDialog(this);
    }

    private void showSearchEngineDialog(final Runnable returnAction) {
        SettingsDialogReturn back = new SettingsDialogReturn(returnAction);
        final List<SearchEngineStore.Engine> custom = searchEngines.customEngines();
        final String[] values = new String[custom.size() + 4];
        final String[] labels = new String[values.length];
        values[0] = "google"; labels[0] = "Google";
        values[1] = "baidu"; labels[1] = "百度";
        values[2] = "bing"; labels[2] = "Bing";
        for (int i = 0; i < custom.size(); i++) {
            values[i + 3] = custom.get(i).id;
            labels[i + 3] = custom.get(i).name;
        }
        values[values.length - 1] = "";
        labels[labels.length - 1] = "＋ 添加自定义搜索引擎";
        int checked = -1;
        for (int i = 0; i < values.length - 1; i++) if (values[i].equals(searchEngine)) checked = i;
        new AlertDialog.Builder(this).setTitle("默认搜索引擎").setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == values.length - 1) {
                    dialog.dismiss();
                    showCustomSearchEditor(null, returnAction);
                    return;
                }
                setSearchEngine(values[which]);
                dialog.dismiss();
                if (isHomeUrl(currentPageUrl)) showHome();
                continueSettingsPanel(returnAction);
            }
        }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showCustomSearchList(final Runnable returnAction) {
        final List<SearchEngineStore.Engine> engines = searchEngines.customEngines();
        String[] labels = new String[engines.size() + 1];
        for (int i = 0; i < engines.size(); i++) {
            SearchEngineStore.Engine item = engines.get(i);
            labels[i] = item.name + (item.id.equals(searchEngine) ? " · 默认" : "") + "\n" + item.template;
        }
        labels[labels.length - 1] = "＋ 添加搜索引擎";
        new AlertDialog.Builder(this).setTitle("自定义搜索引擎").setItems(labels, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == engines.size()) showCustomSearchEditor(null, returnAction);
                else showCustomSearchActions(engines.get(which), returnAction);
            }
        }).setNegativeButton("返回", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { continueSettingsPanel(returnAction); }
        }).show();
    }

    private void showCustomSearchActions(final SearchEngineStore.Engine engine, final Runnable returnAction) {
        String[] actions = new String[] { "设为默认", "重命名或修改地址", "删除" };
        new AlertDialog.Builder(this).setTitle(engine.name).setItems(actions, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    setSearchEngine(engine.id);
                    if (isHomeUrl(currentPageUrl)) showHome();
                    showCustomSearchList(returnAction);
                } else if (which == 1) {
                    showCustomSearchEditor(engine, returnAction);
                } else {
                    searchEngines.delete(engine.id);
                    if (engine.id.equals(searchEngine)) setSearchEngine("google");
                    cachedHomeKey = "";
                    if (isHomeUrl(currentPageUrl)) showHome();
                    showCustomSearchList(returnAction);
                }
            }
        }).setNegativeButton("返回", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { showCustomSearchList(returnAction); }
        }).show();
    }

    private void showCustomSearchEditor(final SearchEngineStore.Engine existing, final Runnable returnAction) {
        final EditText name = new EditText(this);
        name.setHint("名称，例如 DuckDuckGo");
        name.setSingleLine(true);
        name.setText(existing == null ? "" : existing.name);
        final EditText address = new EditText(this);
        address.setHint("https://example.com/search?q=%s");
        address.setText(existing == null ? "" : existing.template);
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(18), 0, dp(18), 0);
        fields.addView(name);
        fields.addView(address);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "添加搜索引擎" : "编辑搜索引擎")
                .setMessage("使用 %s 代表经过编码的搜索词，只允许 HTTPS 地址。")
                .setView(fields).setPositiveButton(existing == null ? "保存并使用" : "保存", null)
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) { showCustomSearchList(returnAction); }
                }).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        String label = name.getText().toString().trim();
                        String template = address.getText().toString().trim();
                        if (label.length() == 0) { toast("请输入搜索引擎名称"); return; }
                        if (!SearchEngineStore.validTemplate(template)) { toast("地址必须以 https:// 开头并包含 %s"); return; }
                        SearchEngineStore.Engine saved = existing == null
                                ? searchEngines.add(label, template)
                                : searchEngines.update(existing.id, label, template);
                        if (saved == null) { toast("无法保存搜索引擎"); return; }
                        if (existing == null) setSearchEngine(saved.id);
                        cachedHomeKey = "";
                        if (isHomeUrl(currentPageUrl)) showHome();
                        dialog.dismiss();
                        showCustomSearchList(returnAction);
                    }
                });
            }
        });
        dialog.show();
    }

    private void copyText(String label, String text, String confirmation) {
        android.content.ClipboardManager manager = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager == null) { toast("剪贴板不可用"); return; }
        manager.setPrimaryClip(android.content.ClipData.newPlainText(label == null ? "Median" : label, text == null ? "" : text));
        toast(confirmation == null || confirmation.length() == 0 ? "已复制" : confirmation);
    }

    private void copyPageUrl() {
        String url = webView == null ? null : webView.getUrl();
        if (url == null || isHomeUrl(url)) return;
        android.content.ClipboardManager manager = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(android.content.ClipData.newPlainText("页面地址", url));
        toast("页面地址已复制");
    }

    private void translatePage() {
        String url = webView == null ? null : webView.getUrl();
        if (url == null || !url.startsWith("http")) return;
        try { loadInput("https://translate.google.com/translate?sl=auto&tl=zh-CN&u=" + URLEncoder.encode(url, "UTF-8")); }
        catch (Exception e) { toast("无法创建翻译地址"); }
    }

    private void printPage() {
        if (webView == null || isHomeUrl(webView.getUrl())) return;
        try {
            PrintManager manager = (PrintManager) getSystemService(PRINT_SERVICE);
            if (manager != null) manager.print(safeTitle(webView.getTitle(), webView.getUrl()),
                    webView.createPrintDocumentAdapter("Median page"), new android.print.PrintAttributes.Builder().build());
        } catch (Exception e) { toast("系统打印服务不可用"); }
    }

    private void showDownloadCenter() {
        try { startActivity(new Intent(this, DownloadCenterActivity.class)); }
        catch (RuntimeException error) { toast("无法打开下载中心：" + safeMessage(error)); }
    }

    private String downloadPolicySummary() {
        ArrayList<String> parts = new ArrayList<String>();
        if (prefs.getBoolean("download_wifi_only", false)) parts.add("仅非计费网络"); else parts.add("允许移动网络");
        if (prefs.getBoolean("download_allow_roaming", false)) parts.add("允许漫游");
        if (prefs.getBoolean("download_charging_only", false)) parts.add("仅充电时");
        parts.add("Median 单连接下载");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) { if (i > 0) out.append(" · "); out.append(parts.get(i)); }
        return out.toString();
    }

    private void showDownloadPolicy() {
        final String[] labels = new String[] { "仅在非计费网络下载", "允许数据漫游下载", "仅在充电时下载" };
        final boolean[] checked = new boolean[] {
                prefs.getBoolean("download_wifi_only", false),
                prefs.getBoolean("download_allow_roaming", false),
                prefs.getBoolean("download_charging_only", false)
        };
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("下载策略")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which, boolean isChecked) { checked[which] = isChecked; }
                })
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        prefs.edit().putBoolean("download_wifi_only", checked[0])
                                .putBoolean("download_allow_roaming", checked[1])
                                .putBoolean("download_charging_only", checked[2]).apply();
                        toast("下载策略已更新");
                    }
                }).setNegativeButton("取消", null).create();
        secureDialog(dialog);
        dialog.show();
    }

    private String downloadStatusSummary(long id) {
        DownloadStore.Item item = services.downloads().get(id);
        if (item != null && item.isAdaptive()) return adaptiveDownloadStatus(item);
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (manager == null) return "状态未知";
        Cursor cursor = null;
        try {
            cursor = manager.query(new DownloadManager.Query().setFilterById(id));
            if (cursor == null || !cursor.moveToFirst()) return "记录已失效";
            return downloadStatusFromCursor(cursor);
        } catch (Exception e) {
            return "状态未知";
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private String adaptiveDownloadStatus(DownloadStore.Item item) {
        if (DownloadStore.STATUS_COMPLETED.equals(item.status)) {
            return "已完成" + (item.totalBytes > 0L ? " · " + humanBytes(item.totalBytes) : "");
        }
        if (DownloadStore.STATUS_FAILED.equals(item.status)) return "失败 · " + (item.reason.length() == 0 ? "未知原因" : item.reason);
        if (DownloadStore.STATUS_CANCELLED.equals(item.status)) return "已取消";
        if (DownloadStore.STATUS_PAUSED.equals(item.status)) return "已暂停" + (item.reason.length() == 0 ? "" : " · " + item.reason);
        if (DownloadStore.STATUS_WAITING.equals(item.status)) return item.reason.length() == 0 ? "等待中" : item.reason;
        if (DownloadStore.STATUS_PENDING.equals(item.status)) return "准备中";
        long percent = DownloadCenterPolicy.progressPermille(item.downloadedBytes, item.totalBytes) / 10L;
        StringBuilder out = new StringBuilder("下载中");
        if (item.totalBytes > 0L) out.append(" · ").append(percent).append('%');
        if (item.bytesPerSecond > 0L) out.append(" · ").append(humanBytes(item.bytesPerSecond)).append("/s");
        return out.toString();
    }

    private Map<Long, String> downloadStatusSummaries(List<DownloadStore.Item> downloads, int limit) {
        HashMap<Long, String> result = new HashMap<Long, String>();
        int count = Math.min(limit, downloads == null ? 0 : downloads.size());
        if (count == 0) return result;
        ArrayList<Long> systemIds = new ArrayList<Long>();
        for (int i = 0; i < count; i++) {
            DownloadStore.Item item = downloads.get(i);
            if (item.isAdaptive()) result.put(Long.valueOf(item.id), adaptiveDownloadStatus(item));
            else systemIds.add(Long.valueOf(item.id));
        }
        if (systemIds.size() == 0) return result;
        long[] ids = new long[systemIds.size()];
        for (int i = 0; i < systemIds.size(); i++) ids[i] = systemIds.get(i).longValue();
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (manager == null) return result;
        Cursor cursor = null;
        try {
            cursor = manager.query(new DownloadManager.Query().setFilterById(ids));
            if (cursor == null) return result;
            int idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID);
            while (cursor.moveToNext()) result.put(Long.valueOf(cursor.getLong(idColumn)), downloadStatusFromCursor(cursor));
        } catch (Exception ignored) {
        } finally { if (cursor != null) cursor.close(); }
        return result;
    }

    private String downloadStatusFromCursor(Cursor cursor) {
        int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        long current = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
        long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
        if (status == DownloadManager.STATUS_SUCCESSFUL) return "已完成" + (total > 0 ? " · " + humanBytes(total) : "");
        if (status == DownloadManager.STATUS_FAILED) return "失败 · " + downloadReason(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
        if (status == DownloadManager.STATUS_PAUSED) return "已暂停 · " + downloadReason(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
        if (status == DownloadManager.STATUS_PENDING) return "等待中";
        return "下载中" + (total > 0 ? " · " + (DownloadCenterPolicy.progressPermille(current, total) / 10) + "%" : "");
    }

    private void showDownloadActions(final DownloadStore.Item original) {
        final DownloadStore.Item latest = services.downloads().get(original.id);
        final DownloadStore.Item item = latest == null ? original : latest;
        String cancelLabel = item.isAdaptive() ? "取消下载任务" : "移除旧版系统任务";
        String[] actions = new String[] { "打开文件", "分享文件", "重新下载", "复制下载地址", "计算 SHA-256", "查看详细信息", cancelLabel, "仅忘记 Median 记录" };
        int[] icons = new int[] { BrowserIconView.PLUS, BrowserIconView.SHARE, BrowserIconView.RELOAD, BrowserIconView.PLUS,
                BrowserIconView.SHIELD, BrowserIconView.STORAGE, BrowserIconView.CLOSE, BrowserIconView.CLOSE };
        showActionSheet(item.filename, downloadStatusSummary(item.id), actions, icons, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) openDownloadedFile(item, false);
                else if (which == 1) openDownloadedFile(item, true);
                else if (which == 2) enqueueDownloadAdvanced(item.url,
                        webView == null ? null : webView.getSettings().getUserAgentString(), null,
                        item.mime, item.filename, downloadContextHeaders(webView, item.url));
                else if (which == 3) copyText("下载地址", item.url, "下载地址已复制");
                else if (which == 4) calculateDownloadSha256(item);
                else if (which == 5) showDownloadDetails(item);
                else if (which == 6) {
                    if (item.isAdaptive()) {
                        Intent cancel = new Intent(MainActivity.this, AdaptiveDownloadService.class);
                        cancel.setAction(AdaptiveDownloadService.ACTION_CANCEL);
                        cancel.putExtra(AdaptiveDownloadService.EXTRA_ID, item.id);
                        try { startService(cancel); } catch (RuntimeException ignored) {}
                        toast("已请求取消下载");
                    } else {
                        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        if (manager != null) manager.remove(item.id);
                        services.downloads().remove(item.id);
                        toast("下载任务与系统记录已移除");
                    }
                } else {
                    services.downloads().remove(item.id);
                    toast("Median 下载记录已移除");
                }
            }
        });
    }

    private void showDownloadDetails(DownloadStore.Item source) {
        final DownloadStore.Item refreshed = services.downloads().get(source.id);
        final DownloadStore.Item item = refreshed == null ? source : refreshed;
        String time = item.createdAt <= 0 ? "未知" : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new java.util.Date(item.createdAt));
        String engine = item.isAdaptive() ? "Median 内部下载" : "旧版系统下载记录";
        String progress = item.isAdaptive() ? (item.totalBytes > 0L ?
                "\n进度：" + humanBytes(item.downloadedBytes) + " / " + humanBytes(item.totalBytes) :
                "\n进度：" + humanBytes(item.downloadedBytes) + " / 总大小未知") : "";
        String message = "状态：" + downloadStatusSummary(item.id) +
                "\n引擎：" + engine + progress +
                "\n文件：" + item.filename +
                "\n类型：" + (item.mime.length() == 0 ? "未知" : item.mime) +
                "\n创建时间：" + time +
                "\n任务 ID：" + item.id +
                "\n地址：" + item.url;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("下载详细信息").setMessage(message)
                .setPositiveButton("复制地址", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { copyText("下载地址", item.url, "下载地址已复制"); }
                }).setNegativeButton("关闭", null).create();
        secureDialog(dialog);
        dialog.show();
    }

    private void calculateDownloadSha256(final DownloadStore.Item source) {
        final DownloadStore.Item refreshed = services.downloads().get(source.id);
        final DownloadStore.Item item = refreshed == null ? source : refreshed;
        final Uri uri = downloadedUri(item);
        if (uri == null) { toast("文件尚未下载完成"); return; }
        if (scriptNetworkExecutor == null || scriptNetworkExecutor.isShutdown()) return;
        toast("正在计算 SHA-256…");
        executeTask(scriptNetworkExecutor, new Runnable() {
            @Override public void run() {
                InputStream input = null;
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    input = getContentResolver().openInputStream(uri);
                    if (input == null) throw new IllegalStateException("无法读取文件");
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
                    byte[] bytes = digest.digest();
                    final String hex = HexCodec.encode(bytes, bytes.length, false, false);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            copyText("SHA-256", hex, "SHA-256 已计算并复制");
                            AlertDialog dialog = new AlertDialog.Builder(MainActivity.this).setTitle("SHA-256")
                                    .setMessage(item.filename + "\n\n" + hex).setPositiveButton("确定", null).create();
                            secureDialog(dialog); dialog.show();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() { @Override public void run() { toast("校验失败：" + safeMessage(e)); } });
                } finally { NetworkSecurity.closeQuietly(input); }
            }
        });
    }

    private Uri downloadedUri(DownloadStore.Item item) {
        if (item == null) return null;
        if (item.isAdaptive()) {
            if (!DownloadStore.STATUS_COMPLETED.equals(item.status) || item.localUri.length() == 0) return null;
            try { return Uri.parse(item.localUri); } catch (RuntimeException ignored) { return null; }
        }
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        return manager == null ? null : manager.getUriForDownloadedFile(item.id);
    }

    private String downloadReason(int reason) {
        if (reason == DownloadManager.PAUSED_WAITING_TO_RETRY) return "等待重试";
        if (reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK) return "等待网络";
        if (reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI) return "等待非计费网络";
        if (reason == DownloadManager.PAUSED_UNKNOWN) return "未知原因";
        if (reason == DownloadManager.ERROR_CANNOT_RESUME) return "无法续传";
        if (reason == DownloadManager.ERROR_DEVICE_NOT_FOUND) return "存储不可用";
        if (reason == DownloadManager.ERROR_FILE_ALREADY_EXISTS) return "文件已存在";
        if (reason == DownloadManager.ERROR_FILE_ERROR) return "文件错误";
        if (reason == DownloadManager.ERROR_HTTP_DATA_ERROR) return "HTTP 数据错误";
        if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) return "空间不足";
        if (reason == DownloadManager.ERROR_TOO_MANY_REDIRECTS) return "重定向过多";
        if (reason >= 400 && reason <= 599) return "HTTP " + reason;
        return "代码 " + reason;
    }

    private void openDownloadedFile(DownloadStore.Item source, boolean share) {
        DownloadStore.Item refreshed = services.downloads().get(source.id);
        DownloadStore.Item item = refreshed == null ? source : refreshed;
        try {
            Uri uri = downloadedUri(item);
            if (uri == null) { toast("文件尚未下载完成或已被移除"); return; }
            String mime = DownloadFileTypes.mimeForOpen(getContentResolver(), uri, item.filename, item.mime);
            if (!share && DownloadFileTypes.isApk(item.filename, mime) && Build.VERSION.SDK_INT >= 26 &&
                    !getPackageManager().canRequestPackageInstalls()) {
                toast("请允许 Median 安装下载的 APK，授权后再次点“打开”");
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
                return;
            }
            Intent intent;
            if (share) {
                intent = new Intent(Intent.ACTION_SEND);
                intent.setType(mime);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
            } else {
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, mime);
            }
            intent.setClipData(ClipData.newRawUri(item.filename, uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            toast(share ? "正在打开分享面板" : "正在打开：" + item.filename);
            startActivity(share ? Intent.createChooser(intent, "分享下载文件") : intent);
        } catch (Exception e) { toast("打开失败：没有可用应用，或文件已被移动"); }
    }

    private void probeAndShowMediaCenter() {
        if (webView == null || isHomeUrl(webView.getUrl())) { showMediaCenter(); return; }
        final WebView source = webView;
        final long sequence = navigationSequence;
        final String sourceHost = currentHost();
        String probe = MediaProbeScript.build();
        try {
            source.evaluateJavascript(probe, new ValueCallback<String>() {
                @Override public void onReceiveValue(String value) {
                    if (!isCurrentPageCallback(source, sequence)) return;
                    try {
                        Object decoded = new JSONTokener(value == null ? "[]" : value).nextValue();
                        String json = decoded instanceof String ? (String) decoded : String.valueOf(decoded);
                        JSONArray array = new JSONArray(json);
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject object = array.optJSONObject(i);
                            if (object == null) continue;
                            int opaque = object.optInt("opaque", 0);
                            if (opaque > 0) {
                                mediaSniffer.noteOpaqueCount(opaque);
                                continue;
                            }
                            mediaSniffer.observe(object.optString("url", ""), object.optString("mime", ""),
                                    sourceHost, object.optString("source", "probe"),
                                    object.optInt("width", 0), object.optInt("height", 0),
                                    object.optDouble("duration", 0d));
                        }
                    } catch (Exception ignored) {}
                    showMediaCenter();
                }
            });
        } catch (RuntimeException e) { showMediaCenter(); }
    }

    private void showMediaCenter() {
        final List<MediaResourceSniffer.Resource> resources = mediaSniffer.getAll();
        final int opaqueMedia = mediaSniffer.opaqueCount();
        String[] items = new String[resources.size() + 2];
        int[] icons = new int[items.length];
        items[0] = "立即进入画中画";
        items[1] = "离开应用自动画中画：" + (autoPictureInPicture ? "已开启" : "已关闭");
        icons[0] = BrowserIconView.SPEED;
        icons[1] = BrowserIconView.SPEED;
        for (int i = 0; i < resources.size(); i++) {
            MediaResourceSniffer.Resource item = resources.get(i);
            String host = hostOf(item.url);
            String detail = item.width > 0 && item.height > 0 ? " · " + item.width + "×" + item.height : "";
            if (item.duration > 0) detail += " · " + Math.max(1, Math.round(item.duration)) + " 秒";
            items[i + 2] = item.kind + detail + " · " + (host.length() == 0 ? item.url : host);
            icons[i + 2] = BrowserIconView.STORAGE;
        }
        String subtitle = resources.size() == 0 ?
                (opaqueMedia > 0 ? "页面正在使用 blob/MediaSource；已继续追踪其清单、分片和网络源" :
                        "暂未发现直链；DRM 与加密流不会被伪装成可下载文件") :
                "已发现 " + resources.size() + " 个候选资源" +
                        (opaqueMedia > 0 ? "，另有 " + opaqueMedia + " 个 blob/MediaSource" : "") +
                        " · 已按清单、直链和分片排序";
        showActionSheet("媒体中心", subtitle, items, icons, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) enterPagePictureInPicture();
                else if (which == 1) {
                    autoPictureInPicture = !autoPictureInPicture;
                    prefs.edit().putBoolean("auto_picture_in_picture", autoPictureInPicture).apply();
                    toast(autoPictureInPicture ? "离开应用时将尝试进入画中画" : "自动画中画已关闭");
                } else {
                    int index = which - 2;
                    if (index >= 0 && index < resources.size()) showMediaResourceActions(resources.get(index));
                }
            }
        });
    }

    private void showMediaResourceActions(final MediaResourceSniffer.Resource resource) {
        showMediaResourceActions(resource, true);
    }

    private void showMediaResourceActions(final MediaResourceSniffer.Resource resource, boolean allowManifestInspection) {
        String lowerUrl = resource.url.toLowerCase(Locale.US);
        final boolean manifest = allowManifestInspection && ("HLS 流".equals(resource.kind) ||
                "DASH 流".equals(resource.kind) || "Smooth 流".equals(resource.kind) ||
                "媒体清单".equals(resource.kind) || resource.mime.contains("mpegurl") ||
                resource.mime.contains("dash+xml") || resource.mime.contains("vnd.ms-sstr") ||
                lowerUrl.contains(".m3u") || lowerUrl.contains(".mpd") ||
                lowerUrl.contains(".ism/manifest") || lowerUrl.contains(".isml/manifest"));
        String[] actions = manifest ? new String[] { "解析清单、轨道与加密信息", "使用外部播放器打开", "下载资源", "在新标签页打开", "复制媒体地址", "分享媒体地址" }
                : new String[] { "使用外部播放器打开", "下载资源", "在新标签页打开", "复制媒体地址", "分享媒体地址" };
        int[] icons = manifest ? new int[] { BrowserIconView.SPEED, BrowserIconView.SPEED, BrowserIconView.STORAGE, BrowserIconView.PLUS, BrowserIconView.PLUS, BrowserIconView.SHARE }
                : new int[] { BrowserIconView.SPEED, BrowserIconView.STORAGE, BrowserIconView.PLUS, BrowserIconView.PLUS, BrowserIconView.SHARE };
        showActionSheet(resource.kind, hostOf(resource.url) + " · " + resource.mime, actions, icons, new SheetHandler() {
            @Override public void onItem(int which) {
                if (manifest && which == 0) { inspectMediaManifest(resource); return; }
                if (manifest) which--;
                if (which == 0) openMediaExternally(resource);
                else if (which == 1) enqueueDownload(webView, resource.url,
                        webView == null ? null : webView.getSettings().getUserAgentString(), null, resource.mime);
                else if (which == 2) openUrlInNewTab(resource.url, true);
                else if (which == 3) {
                    android.content.ClipboardManager manager = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (manager != null) manager.setPrimaryClip(android.content.ClipData.newPlainText("媒体地址", resource.url));
                    toast("媒体地址已复制");
                } else {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT, resource.url);
                    try { startActivity(Intent.createChooser(share, "分享媒体地址")); } catch (Exception e) { toast("没有可用的分享应用"); }
                }
            }
        });
    }

    private void inspectMediaManifest(final MediaResourceSniffer.Resource resource) {
        final HashMap<String, String> headers = new HashMap<String, String>(downloadContextHeaders(webView, resource.url));
        headers.put("User-Agent", webView == null ? "MedianBrowser" : webView.getSettings().getUserAgentString());
        headers.put("Accept", "application/vnd.apple.mpegurl, application/x-mpegurl, application/dash+xml, application/vnd.ms-sstr+xml, application/xml, text/xml, text/plain, */*");
        String cookie = CookieManager.getInstance().getCookie(resource.url);
        if (cookie != null && cookie.length() > 0) headers.put("Cookie", cookie);
        toast("正在解析媒体清单…");
        if (!executeTask(scriptNetworkExecutor, new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    connection = NetworkSecurity.openPublicGetFollowingRedirects(
                            NetworkSecurity.parseHttpUrl(resource.url), 5000, 7000, headers);
                    int status = connection.getResponseCode();
                    if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
                    if (connection.getContentLength() > 2097152) throw new IOException("清单超过 2 MB");
                    byte[] data = NetworkSecurity.readBounded(connection.getInputStream(), 2097152, "清单超过 2 MB");
                    final String base = connection.getURL().toString();
                    String responseMime = connection.getContentType();
                    if (responseMime == null || responseMime.length() == 0) responseMime = resource.mime;
                    final MediaManifestParser.Playlist parsed = MediaManifestParser.parse(base, responseMime,
                            new String(data, StandardCharsets.UTF_8));
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (!activityDestroyed) showManifestVariants(resource, parsed);
                        }
                    });
                } catch (final Exception error) {
                    uiHandler.post(new Runnable() {
                        @Override public void run() { if (!activityDestroyed) toast("清单解析失败：" + safeMessage(error)); }
                    });
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        })) toast("媒体解析服务繁忙，请稍后重试");
    }

    private void showManifestVariants(MediaResourceSniffer.Resource source, final MediaManifestParser.Playlist playlist) {
        String state = (playlist.live ? "直播" : "点播") + " · " + playlist.segments + " 个已列出分片" +
                (playlist.encrypted ? " · 检测到加密" : " · 未发现清单加密标签");
        if (playlist.variants.size() == 0) {
            new AlertDialog.Builder(this).setTitle(playlist.format + " 清单信息").setMessage(state +
                    (playlist.encrypted ? "\n\n加密流只交给兼容播放器处理，不会伪装成可直接合并的视频。" : ""))
                    .setPositiveButton("确定", null).show();
            return;
        }
        final ArrayList<MediaResourceSniffer.Resource> variants = new ArrayList<MediaResourceSniffer.Resource>();
        String[] labels = new String[playlist.variants.size()];
        int[] icons = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            MediaManifestParser.Variant parsed = playlist.variants.get(i);
            MediaResourceSniffer.Resource item = new MediaResourceSniffer.Resource();
            item.url = parsed.url;
            item.mime = "HLS".equals(playlist.format) ? "application/vnd.apple.mpegurl" :
                    ("DASH".equals(playlist.format) ? "application/dash+xml" : "application/vnd.ms-sstr+xml");
            item.kind = playlist.format + " 子流";
            item.pageHost = source.pageHost;
            labels[i] = parsed.label;
            icons[i] = BrowserIconView.SPEED;
            variants.add(item);
            mediaSniffer.observe(item.url, item.mime, item.pageHost);
        }
        showActionSheet(playlist.format + " 清晰度与轨道", state, labels, icons, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which >= 0 && which < variants.size()) showMediaResourceActions(variants.get(which), false);
            }
        });
    }

    private void openMediaExternally(MediaResourceSniffer.Resource resource) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(resource.url), resource.mime.length() == 0 ? "video/*" : resource.mime);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) { toast("没有兼容此外部媒体的播放器"); }
    }

    private void enterPagePictureInPicture() {
        if (webView == null || isHomeUrl(currentPageUrl)) { toast("当前页面不能进入画中画"); return; }
        try {
            PictureInPictureParams params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build();
            if (!enterPictureInPictureMode(params)) toast("系统未允许进入画中画");
        } catch (Exception e) { toast("画中画不可用：" + safeMessage(e)); }
    }

    private void showPageScriptCommands() {
        if (webView == null || isHomeUrl(currentPageUrl)) { toast("当前页面没有脚本命令"); return; }
        final WebView source = webView;
        final long sequence = navigationSequence;
        final String sourceUrl = currentPageUrl;
        final String token = scriptBridgeTokens.get(source);
        if (token == null || token.length() < 32) { toast("当前 WebView 不支持安全脚本命令"); return; }
        StringBuilder query = new StringBuilder("(function(){var a=[];");
        for (UserScriptStore.Script script : scriptStore.getAll()) {
            if (!script.enabled || script.quarantined || !scriptStore.matchesUrl(script.id, sourceUrl)) continue;
            String objectName = UserScriptStore.dispatchObjectName(token, script.id);
            query.append("try{var d=window[").append(JSONObject.quote(objectName)).append("];if(d&&typeof d.menus==='function'){var m=d.menus(")
                    .append(JSONObject.quote(token)).append(");if(Array.isArray(m))m.forEach(function(x){x.scriptId=")
                    .append(JSONObject.quote(script.id)).append(";a.push(x);});}}catch(_){}");
        }
        query.append("return JSON.stringify(a);})();");
        try {
            source.evaluateJavascript(query.toString(), new ValueCallback<String>() {
                @Override public void onReceiveValue(String value) {
                    if (!isCurrentPageCallback(source, sequence) || !token.equals(scriptBridgeTokens.get(source))) return;
                    try {
                        Object decoded = new JSONTokener(value == null ? "[]" : value).nextValue();
                        String raw = decoded instanceof String ? (String) decoded : String.valueOf(decoded);
                        final JSONArray commands = new JSONArray(raw);
                        if (commands.length() == 0) { toast("当前页面的脚本没有注册命令"); return; }
                        String[] items = new String[commands.length()];
                        int[] icons = new int[commands.length()];
                        for (int i = 0; i < commands.length(); i++) {
                            JSONObject command = commands.optJSONObject(i);
                            items[i] = (command == null ? "脚本命令" : command.optString("caption", "脚本命令")) +
                                    (command == null || command.optString("script", "").length() == 0 ? "" : " · " + command.optString("script", ""));
                            icons[i] = BrowserIconView.SCRIPT;
                        }
                        showActionSheet("脚本命令", hostOf(sourceUrl), items, icons, new SheetHandler() {
                            @Override public void onItem(int which) {
                                JSONObject command = commands.optJSONObject(which);
                                if (command == null || !isCurrentPageCallback(source, sequence) ||
                                        !token.equals(scriptBridgeTokens.get(source))) return;
                                String scriptId = command.optString("scriptId", "");
                                String id = command.optString("id", "");
                                if (!scriptStore.matchesUrl(scriptId, source.getUrl())) return;
                                String objectName = UserScriptStore.dispatchObjectName(token, scriptId);
                                String run = "(function(){var d=window[" + JSONObject.quote(objectName) + "];if(d&&typeof d.runMenu==='function')d.runMenu(" +
                                        JSONObject.quote(token) + "," + JSONObject.quote(id) + ");})();";
                                try { source.evaluateJavascript(run, null); } catch (RuntimeException ignored) {}
                            }
                        });
                    } catch (Exception e) { toast("无法读取当前页脚本命令"); }
                }
            });
        } catch (RuntimeException e) { toast("当前页面不支持脚本命令"); }
    }

    private String triStateLabel(int value, String inherit) {
        return value == SiteSettingsStore.ALLOW ? "允许" : (value == SiteSettingsStore.BLOCK ? "阻止" : inherit);
    }

    private int nextTriState(int value) {
        if (value == SiteSettingsStore.INHERIT) return SiteSettingsStore.ALLOW;
        if (value == SiteSettingsStore.ALLOW) return SiteSettingsStore.BLOCK;
        return SiteSettingsStore.INHERIT;
    }

    private String searchEngineLabel() {
        return searchEngines == null ? "Google" : searchEngines.label(searchEngine);
    }

    private void showPerformancePanel() {
        showPerformancePanel(this);
    }

    private void showPerformancePanel(final Runnable backAction) {
        String[] modes = new String[] {
                "流畅模式 · 减少后台干扰",
                "标准模式 · 日常稳定优先",
                "低功耗模式 · 减少网页预热和后台资源",
                "性能模式下保持广告拦截：" + (!performanceNetworkDirect ? "已开启" : "已关闭"),
                "立即释放后台网页内存"
        };
        int[] icons = new int[] { BrowserIconView.SPEED, BrowserIconView.HOME, BrowserIconView.SHIELD, BrowserIconView.SHIELD, BrowserIconView.STORAGE };
        int[] kinds = new int[] { SHEET_ROW_ACTION, SHEET_ROW_ACTION, SHEET_ROW_ACTION,
                !performanceNetworkDirect ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF, SHEET_ROW_ACTION };
        showActionSheet("性能调度", modeLabel() + " · 快来自减少无效工作，不保持设备常亮", modes, icons,
                kinds, null, backAction, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 3) {
                    performanceNetworkDirect = !performanceNetworkDirect;
                    prefs.edit().putBoolean("performance_network_direct", performanceNetworkDirect).apply();
                    renderedShieldActive = null;
                    requestChromeUpdate();
                    toast(performanceNetworkDirect ? "性能模式下将暂停广告拦截" : "性能模式下仍保持广告拦截");
                    return;
                }
                if (which == 4) {
                    releaseInactiveTabStates();
                    if (services != null) services.trimMemory();
                    toast("已释放后台网页内存，标签页仍会保留");
                    return;
                }
                setPerformanceMode(which == 0 ? MODE_PERFORMANCE : (which == 2 ? MODE_POWER_SAVE : MODE_STANDARD));
                postAfterUiTransition(new Runnable() {
                    @Override public void run() { showPerformancePanel(backAction); }
                });
            }
        });
    }

    private void setPerformanceMode(String mode) {
        if (!MODE_PERFORMANCE.equals(mode) && !MODE_POWER_SAVE.equals(mode)) mode = MODE_STANDARD;
        if (mode.equals(performanceMode)) return;
        performanceMode = mode;
        prefs.edit().putString("performance_mode", performanceMode).apply();
        applyPerformanceMode();
        renderedProgress = -1;
        renderedAddress = null;
        renderedBackEnabled = null;
        renderedForwardEnabled = null;
        renderedTabCount = null;
        renderedShieldActive = null;
        requestChromeUpdate();
        toast("已切换为" + modeLabel());
    }

    private String modeLabel() {
        if (MODE_PERFORMANCE.equals(performanceMode)) return "流畅模式";
        if (MODE_POWER_SAVE.equals(performanceMode)) return "低功耗模式";
        return "标准模式";
    }

    private boolean reduceMotion() {
        return MODE_POWER_SAVE.equals(performanceMode);
    }

    private int scriptThreadPriority() {
        // Parsing subscriptions and preparing userscripts must never compete with WebView's
        // renderer or Android's UI thread, including in the performance preset.
        return Process.THREAD_PRIORITY_BACKGROUND;
    }

    private int progressStep() {
        return MODE_POWER_SAVE.equals(performanceMode) ? 12 : 8;
    }

    private void showScriptCenter() {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setTitle("用户脚本");

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(16));
        scroll.addView(content);

        TextView note = new TextView(this);
        note.setText("脚本来自第三方，能读取和修改匹配网页。只安装你信任的脚本。Median 会优先在 document-start 运行，并在厂商 WebView 漏执行时自动补偿；安装后刷新页面生效。");
        note.setTextColor(MUTED);
        note.setTextSize(13f);
        note.setPadding(0, 0, 0, dp(12));
        content.addView(note);

        Button browse = actionButton("浏览 Greasy Fork");
        Button current = actionButton("查找适用于当前网站的脚本");
        Button link = actionButton("粘贴链接或脚本代码安装");
        Button updateAll = actionButton("检查并更新全部脚本");
        content.addView(browse);
        content.addView(current);
        content.addView(link);
        content.addView(updateAll);

        TextView installedTitle = new TextView(this);
        installedTitle.setText("已安装脚本");
        installedTitle.setTextSize(15f);
        installedTitle.setTextColor(TEXT);
        installedTitle.setPadding(0, dp(18), 0, dp(6));
        content.addView(installedTitle);

        final List<UserScriptStore.Script> scripts = scriptStore.getAll();
        if (scripts.size() == 0) {
            TextView empty = new TextView(this);
            empty.setText("还没有安装脚本。进入 Greasy Fork 后点击“安装此脚本”，Median 会接管安装。 ");
            empty.setTextColor(MUTED);
            empty.setPadding(0, dp(8), 0, dp(8));
            content.addView(empty);
        } else {
            for (final UserScriptStore.Script script : scripts) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(3), 0, dp(3));

                CheckBox enabled = new CheckBox(this);
                String subtitle = script.version.length() > 0 ? "  v" + script.version : "";
                String state = script.quarantined ? "\n已隔离 · " + script.disabledReason : "\n" + script.riskSummary;
                enabled.setText(script.name + subtitle + state);
                enabled.setTextColor(script.quarantined ? Color.rgb(176, 0, 32) : TEXT);
                enabled.setTextSize(13.5f);
                enabled.setChecked(script.enabled && !script.quarantined);
                enabled.setEnabled(!script.quarantined);
                row.addView(enabled, new LinearLayout.LayoutParams(0, dp(62), 1f));

                Button details = new Button(this);
                details.setText("详情");
                details.setAllCaps(false);
                details.setTextSize(13f);
                details.setMinWidth(0);
                details.setMinHeight(0);
                row.addView(details, new LinearLayout.LayoutParams(dp(66), dp(42)));
                content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

                enabled.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        scriptStore.setEnabled(script.id, ((CheckBox) v).isChecked());
                        refreshUserScriptRegistrations(true);
                    }
                });
                details.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        dialog.dismiss();
                        showScriptDetails(script);
                    }
                });
            }
        }

        browse.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); loadNetworkUrl(webView, "https://greasyfork.org/zh-CN/scripts"); }
        });
        current.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                String host = currentHost();
                loadNetworkUrl(webView, host.length() == 0 || isHomeUrl(webView.getUrl()) ?
                        "https://greasyfork.org/zh-CN/scripts" : "https://greasyfork.org/zh-CN/scripts/by-site/" + host);
            }
        });
        link.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); showScriptImportDialog(); }
        });
        updateAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); updateAllUserScripts(); }
        });

        dialog.setView(scroll);
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "关闭", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) { d.dismiss(); }
        });
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface d) {
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(BLUE);
            }
        });
        dialog.show();
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setTextSize(14f);
        button.setTextColor(TEXT);
        button.setBackground(roundRect(SURFACE, 12));
        button.setPadding(dp(14), 0, dp(12), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(4), 0, dp(4));
        button.setLayoutParams(params);
        return button;
    }

    private void showScriptImportDialog() {
        final EditText input = new EditText(this);
        input.setHint("粘贴 HTTPS 脚本链接，或完整的 ==UserScript== 代码");
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(16), dp(8), dp(16), dp(8));
        String clipboard = clipboardText();
        if (UserScriptInstallPolicy.looksLikeInstallUrl(clipboard) ||
                UserScriptInstallPolicy.isSourceText(clipboard)) input.setText(clipboard);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("安装用户脚本")
                .setMessage("复制脚本链接后打开这里会自动填入；也可以直接粘贴完整代码。网页中的 .user.js 安装按钮会由 Median 直接接管。")
                .setView(input)
                .setPositiveButton("读取并检查", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String value = input.getText().toString().trim();
                        if (UserScriptInstallPolicy.isSourceText(value)) {
                            dialog.dismiss();
                            installScriptFromSource(value);
                            return;
                        }
                        try {
                            NetworkSecurity.parseHttpsUrl(value);
                            dialog.dismiss();
                            installScriptFromUrl(value);
                        } catch (Exception invalid) {
                            input.setError("请粘贴有效的 HTTPS 链接或完整 UserScript 代码");
                        }
                    }
                });
            }
        });
        dialog.show();
    }

    private String clipboardText() {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager == null || !manager.hasPrimaryClip() || manager.getPrimaryClip() == null ||
                manager.getPrimaryClip().getItemCount() == 0) return "";
        CharSequence text = manager.getPrimaryClip().getItemAt(0).coerceToText(this);
        return text == null ? "" : text.toString().trim();
    }

    private void installScriptFromUrl(final String sourceUrl) {
        final String normalizedSourceUrl;
        try { normalizedSourceUrl = NetworkSecurity.parseHttpsUrl(sourceUrl).toString(); }
        catch (Exception invalidUrl) {
            toast("只允许从有效的 HTTPS 地址安装脚本");
            return;
        }
        if (scriptDownloadInProgress) {
            toast("已有脚本正在读取，请稍候");
            return;
        }
        scriptDownloadInProgress = true;
        toast("正在读取脚本；网络抖动会自动重试…");
        final String userAgent = webView.getSettings().getUserAgentString();
        if (scriptNetworkExecutor == null || scriptNetworkExecutor.isShutdown()) {
            scriptDownloadInProgress = false;
            toast("脚本后台服务不可用");
            return;
        }
        if (!executeTask(scriptNetworkExecutor, new Runnable() {
            @Override public void run() {
                try { Process.setThreadPriority(scriptThreadPriority()); } catch (RuntimeException ignored) {}
                try {
                    final UserScriptStore.Script script = downloadAndPrepareUserScript(normalizedSourceUrl, userAgent);
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showScriptInstallConfirmation(script); }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { toast("脚本安装失败：" + safeMessage(e)); }
                    });
                } finally {
                    scriptDownloadInProgress = false;
                }
            }
        })) scriptDownloadInProgress = false;
    }

    private void installScriptFromSource(final String source) {
        if (!UserScriptInstallPolicy.isSourceText(source)) {
            toast("不是有效的 UserScript 代码");
            return;
        }
        if (scriptDownloadInProgress) {
            toast("已有脚本正在读取，请稍候");
            return;
        }
        scriptDownloadInProgress = true;
        toast("正在检查脚本与依赖…");
        final String userAgent = webView == null ? "MedianBrowser" : webView.getSettings().getUserAgentString();
        if (scriptNetworkExecutor == null || scriptNetworkExecutor.isShutdown()) {
            scriptDownloadInProgress = false;
            toast("脚本后台服务不可用");
            return;
        }
        if (!executeTask(scriptNetworkExecutor, new Runnable() {
            @Override public void run() {
                try { Process.setThreadPriority(scriptThreadPriority()); } catch (RuntimeException ignored) {}
                try {
                    final UserScriptStore.Script script = prepareUserScriptSource(source, "", userAgent);
                    runOnUiThread(new Runnable() {
                        @Override public void run() { showScriptInstallConfirmation(script); }
                    });
                } catch (final Exception error) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { toast("脚本安装失败：" + safeMessage(error)); }
                    });
                } finally {
                    scriptDownloadInProgress = false;
                }
            }
        })) scriptDownloadInProgress = false;
    }

    private UserScriptStore.Script downloadAndPrepareUserScript(String sourceUrl, String userAgent) throws Exception {
        FetchedScriptBytes fetched = fetchUserScriptBytes(sourceUrl, userAgent, 16 * 1024 * 1024, "脚本");
        return prepareUserScriptSource(new String(fetched.data, StandardCharsets.UTF_8), fetched.finalUrl, userAgent);
    }

    private UserScriptStore.Script prepareUserScriptSource(String source, String baseUrl, String userAgent) throws Exception {
        final UserScriptStore.Script parsed = scriptStore.parseUserScript(source, baseUrl);
        if (parsed.updateUrl.length() > 0 && !"none".equalsIgnoreCase(parsed.updateUrl))
            parsed.updateUrl = resolveHttpsUrl(baseUrl, parsed.updateUrl, "@updateURL");
        if (parsed.downloadUrl.length() > 0 && !"none".equalsIgnoreCase(parsed.downloadUrl))
            parsed.downloadUrl = resolveHttpsUrl(baseUrl, parsed.downloadUrl, "@downloadURL");

        final ArrayList<String> requireUrls = new ArrayList<String>();
        final ArrayList<Future<FetchedScriptBytes>> downloads = new ArrayList<Future<FetchedScriptBytes>>();
        try {
            for (String requireUrl : parsed.requires) {
                final String resolved = resolveHttpsUrl(baseUrl, requireUrl, "@require");
                requireUrls.add(resolved);
                downloads.add(submitScriptAsset(resolved, userAgent, "@require"));
            }
            final int resourceOffset = downloads.size();
            for (UserScriptStore.Script.Resource resource : parsed.resources) {
                final String resolved = resolveHttpsUrl(baseUrl, resource.url, "@resource");
                resource.url = resolved;
                downloads.add(submitScriptAsset(resolved, userAgent, "@resource"));
            }

            StringBuilder dependencies = new StringBuilder();
            for (int i = 0; i < requireUrls.size(); i++) {
                FetchedScriptBytes dependency = awaitScriptAsset(downloads.get(i));
                downloads.set(i, null);
                String resolved = requireUrls.get(i);
                dependencies.append("\n/* @require ").append(resolved.replace("*/", "* /")).append(" */\n")
                        .append(new String(dependency.data, StandardCharsets.UTF_8)).append('\n');
            }
            parsed.requireCode = dependencies.toString();
            for (int i = 0; i < parsed.resources.size(); i++) {
                UserScriptStore.Script.Resource resource = parsed.resources.get(i);
                int index = resourceOffset + i;
                FetchedScriptBytes data = awaitScriptAsset(downloads.get(index));
                downloads.set(index, null);
                resource.mime = guessResourceMime(resource.url);
                resource.base64 = android.util.Base64.encodeToString(data.data, android.util.Base64.NO_WRAP);
            }
        } catch (Exception failure) {
            for (Future<FetchedScriptBytes> download : downloads) if (download != null) download.cancel(true);
            throw failure;
        }
        scriptStore.refreshAnalysis(parsed);
        return parsed;
    }

    private Future<FetchedScriptBytes> submitScriptAsset(final String url, final String userAgent,
                                                          final String label) {
        if (scriptNetworkExecutor == null || scriptNetworkExecutor.isShutdown())
            throw new RejectedExecutionException("脚本后台服务不可用");
        return scriptNetworkExecutor.submit(new Callable<FetchedScriptBytes>() {
            @Override public FetchedScriptBytes call() throws Exception {
                try { Process.setThreadPriority(scriptThreadPriority()); } catch (RuntimeException ignored) {}
                return fetchUserScriptBytes(url, userAgent, 32 * 1024 * 1024, label);
            }
        });
    }

    private FetchedScriptBytes awaitScriptAsset(Future<FetchedScriptBytes> future) throws Exception {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("脚本读取已取消", interrupted);
        } catch (ExecutionException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new IOException("脚本依赖读取失败", cause);
        }
    }

    private FetchedScriptBytes fetchUserScriptBytes(String sourceUrl, String userAgent, int maxBytes,
                                                     String label) throws Exception {
        URL parsed = NetworkSecurity.parseHttpsUrl(sourceUrl);
        Exception lastFailure = null;
        for (int attempt = 0; attempt < UserScriptInstallPolicy.MAX_FETCH_ATTEMPTS; attempt++) {
            HttpURLConnection connection = null;
            try {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("User-Agent", userAgent == null ? "MedianBrowser/2.0" : userAgent);
                headers.put("Accept", "text/javascript, application/javascript, text/plain, */*");
                headers.put("Cache-Control", "no-cache");
                connection = NetworkSecurity.openPublicHttpsGetFollowingRedirects(parsed,
                        UserScriptInstallPolicy.connectTimeoutMs(attempt),
                        UserScriptInstallPolicy.readTimeoutMs(attempt), headers);
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    if (!UserScriptInstallPolicy.retryableHttpStatus(status) ||
                            attempt + 1 >= UserScriptInstallPolicy.MAX_FETCH_ATTEMPTS)
                        throw new IllegalArgumentException(label + "下载失败：HTTP " + status);
                    lastFailure = new IOException("HTTP " + status);
                } else {
                    int declared = connection.getContentLength();
                    if (declared > maxBytes) throw new IllegalArgumentException(label + "内容异常过大");
                    byte[] data = NetworkSecurity.readBounded(connection.getInputStream(), maxBytes, label + "内容异常过大");
                    return new FetchedScriptBytes(data, connection.getURL().toString());
                }
            } catch (Exception failure) {
                lastFailure = failure;
                if (!UserScriptInstallPolicy.retryableFailure(failure) ||
                        attempt + 1 >= UserScriptInstallPolicy.MAX_FETCH_ATTEMPTS) {
                    if (UserScriptInstallPolicy.retryableFailure(failure))
                        throw new IOException(label + "下载超时或网络中断（已自动重试）", failure);
                    throw failure;
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
            try { Thread.sleep(UserScriptInstallPolicy.retryDelayMs(attempt)); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("脚本读取已取消", interrupted);
            }
        }
        throw new IOException(label + "下载失败", lastFailure);
    }

    private String resolveHttpsUrl(String baseUrl, String candidate, String label) throws Exception {
        String resolved = baseUrl == null || baseUrl.length() == 0
                ? NetworkSecurity.parseHttpsUrl(candidate).toString()
                : new URL(new URL(baseUrl), candidate).toString();
        if (!resolved.startsWith("https://")) throw new IllegalArgumentException(label + " 只允许 HTTPS：" + resolved);
        return resolved;
    }

    private static final class FetchedScriptBytes {
        final byte[] data;
        final String finalUrl;
        FetchedScriptBytes(byte[] data, String finalUrl) {
            this.data = data;
            this.finalUrl = finalUrl;
        }
    }

    private String guessResourceMime(String url) {
        String lower = url.toLowerCase(Locale.US);
        if (lower.matches(".*\\.(css)(?:[?#].*)?$")) return "text/css";
        if (lower.matches(".*\\.(js|mjs)(?:[?#].*)?$")) return "text/javascript";
        if (lower.matches(".*\\.(json)(?:[?#].*)?$")) return "application/json";
        if (lower.matches(".*\\.(png)(?:[?#].*)?$")) return "image/png";
        if (lower.matches(".*\\.(jpe?g)(?:[?#].*)?$")) return "image/jpeg";
        if (lower.matches(".*\\.(svg)(?:[?#].*)?$")) return "image/svg+xml";
        if (lower.matches(".*\\.(webp)(?:[?#].*)?$")) return "image/webp";
        if (lower.matches(".*\\.(txt|md|html?)(?:[?#].*)?$")) return "text/plain;charset=utf-8";
        return "application/octet-stream";
    }

    private void updateAllUserScripts() {
        updateUserScripts(scriptStore.getAll());
    }

    private void updateOneUserScript(UserScriptStore.Script script) {
        ArrayList<UserScriptStore.Script> scripts = new ArrayList<UserScriptStore.Script>();
        scripts.add(script);
        updateUserScripts(scripts);
    }

    private void updateUserScripts(final List<UserScriptStore.Script> scripts) {
        if (scriptDownloadInProgress) { toast("已有脚本任务正在运行"); return; }
        if (scripts == null || scripts.size() == 0) { toast("没有可检查的脚本"); return; }
        scriptDownloadInProgress = true;
        toast("正在检查脚本更新…");
        final String userAgent = webView == null ? "MedianBrowser" : webView.getSettings().getUserAgentString();
        if (scriptNetworkExecutor == null || scriptNetworkExecutor.isShutdown()) {
            scriptDownloadInProgress = false;
            toast("脚本后台服务不可用");
            return;
        }
        if (!executeTask(scriptNetworkExecutor, new Runnable() {
            @Override public void run() {
                try { Process.setThreadPriority(scriptThreadPriority()); } catch (RuntimeException ignored) {}
                int checked = 0;
                int updated = 0;
                int failed = 0;
                int skipped = 0;
                ArrayList<UserScriptStore.Script> pendingSaves = new ArrayList<UserScriptStore.Script>();
                for (UserScriptStore.Script existing : scripts) {
                    String source = preferredUpdateUrl(existing);
                    if (!source.startsWith("https://")) { skipped++; continue; }
                    try {
                        UserScriptStore.Script candidate = downloadAndPrepareUserScript(source, userAgent);
                        checked++;
                        long now = System.currentTimeMillis();
                        int versionOrder = compareVersions(candidate.version, existing.version);
                        boolean changed = !candidate.code.equals(existing.code) || !candidate.requireCode.equals(existing.requireCode) ||
                                !resourceSignature(candidate).equals(resourceSignature(existing));
                        boolean shouldUpdate = versionOrder > 0 || (versionOrder == 0 && changed);
                        if (shouldUpdate) {
                            candidate.id = existing.id;
                            candidate.installedAt = existing.installedAt == 0L ? now : existing.installedAt;
                            candidate.updatedAt = now;
                            candidate.lastUpdateCheck = now;
                            candidate.enabled = existing.enabled;
                            candidate.quarantined = false;
                            candidate.disabledReason = "";
                            if (candidate.riskScore >= 8 || candidate.riskScore > existing.riskScore + 3) {
                                candidate.enabled = false;
                                candidate.quarantined = true;
                                candidate.disabledReason = "更新后权限或风险明显增加，请检查后重新启用";
                            }
                            pendingSaves.add(candidate);
                            updated++;
                        } else {
                            existing.lastUpdateCheck = now;
                            pendingSaves.add(existing);
                        }
                    } catch (Exception ignored) {
                        failed++;
                    }
                }
                scriptStore.saveBatch(pendingSaves);
                final int finalChecked = checked;
                final int finalUpdated = updated;
                final int finalFailed = failed;
                final int finalSkipped = skipped;
                scriptDownloadInProgress = false;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        String result = "脚本更新完成：检查 " + finalChecked + " 个，更新 " + finalUpdated + " 个";
                        if (finalSkipped > 0) result += "，跳过 " + finalSkipped + " 个无更新地址脚本";
                        if (finalFailed > 0) result += "，失败 " + finalFailed + " 个";
                        toast(result);
                        if (finalUpdated > 0) refreshUserScriptRegistrations(true);
                    }
                });
            }
        })) scriptDownloadInProgress = false;
    }

    private String preferredUpdateUrl(UserScriptStore.Script script) {
        if (script == null) return "";
        if ("none".equalsIgnoreCase(script.updateUrl) || "none".equalsIgnoreCase(script.downloadUrl)) return "";
        if (script.downloadUrl.length() > 0) return script.downloadUrl;
        if (script.sourceUrl.length() > 0) return script.sourceUrl;
        if (script.updateUrl.length() > 0) return script.updateUrl;
        return script.sourceUrl;
    }

    private int compareVersions(String left, String right) {
        String l = left == null ? "" : left.trim();
        String r = right == null ? "" : right.trim();
        int plus = l.indexOf('+'); if (plus >= 0) l = l.substring(0, plus);
        plus = r.indexOf('+'); if (plus >= 0) r = r.substring(0, plus);
        String[] lp = l.split("-", 2);
        String[] rp = r.split("-", 2);
        String[] a = lp[0].split("[._]");
        String[] b = rp[0].split("[._]");
        int count = Math.max(a.length, b.length);
        for (int i = 0; i < count; i++) {
            String av = i < a.length ? a[i] : "0";
            String bv = i < b.length ? b[i] : "0";
            int comparison = compareVersionPart(av, bv, false);
            if (comparison != 0) return comparison;
        }
        boolean aPre = lp.length > 1 && lp[1].length() > 0;
        boolean bPre = rp.length > 1 && rp[1].length() > 0;
        if (aPre != bPre) return aPre ? -1 : 1;
        if (!aPre) return 0;
        a = lp[1].split("[._-]");
        b = rp[1].split("[._-]");
        count = Math.max(a.length, b.length);
        for (int i = 0; i < count; i++) {
            if (i >= a.length) return -1;
            if (i >= b.length) return 1;
            int comparison = compareVersionPart(a[i], b[i], true);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private int compareVersionPart(String left, String right, boolean numericBeforeText) {
        boolean leftNumber = left.matches("[0-9]+");
        boolean rightNumber = right.matches("[0-9]+");
        if (leftNumber && rightNumber) {
            try { return Long.compare(Long.parseLong(left), Long.parseLong(right)); }
            catch (NumberFormatException ignored) {
                if (left.length() != right.length()) return left.length() < right.length() ? -1 : 1;
                return left.compareTo(right);
            }
        }
        if (numericBeforeText && leftNumber != rightNumber) return leftNumber ? -1 : 1;
        return left.compareToIgnoreCase(right);
    }

    private String resourceSignature(UserScriptStore.Script script) {
        StringBuilder value = new StringBuilder();
        for (UserScriptStore.Script.Resource resource : script.resources) {
            value.append(resource.name).append(':').append(resource.url).append(':').append(UrlCleaner.stableId(resource.base64)).append(';');
        }
        return value.toString();
    }

    private void showScriptInstallConfirmation(final UserScriptStore.Script script) {
        StringBuilder scope = new StringBuilder();
        int count = Math.min(6, script.matches.size());
        for (int i = 0; i < count; i++) scope.append("\n• ").append(script.matches.get(i));
        if (script.matches.size() > count) scope.append("\n• 以及其他 ").append(script.matches.size() - count).append(" 项");
        StringBuilder grants = new StringBuilder();
        int grantCount = Math.min(6, script.grants.size());
        for (int i = 0; i < grantCount; i++) grants.append(i == 0 ? "" : "、").append(script.grants.get(i));
        if (script.grants.size() > grantCount) grants.append(" 等 ").append(script.grants.size()).append(" 项");
        String message = "版本：" + (script.version.length() == 0 ? "未知" : script.version) +
                "\n来源：" + (script.sourceUrl.length() == 0 ? "本地粘贴" : script.sourceUrl) +
                "\n运行时机：" + script.runAt + (script.noFrames ? " · 仅顶层页面" : "") +
                "\n授权声明：" + (grants.length() == 0 ? "未声明" : grants.toString()) +
                "\nHTTPS 依赖：" + script.requires.size() + " 项" +
                "\nHTTPS 资源：" + script.resources.size() + " 项" +
                "\n网络范围：" + (script.connects.size() == 0 ? "未声明" : script.connects.toString()) +
                "\n风险评估：" + script.riskSummary + "（" + script.riskScore + " 分）" +
                "\n\n可运行的网站：" + scope.toString() +
                "\n\nMedian 支持任意数量的 @require、@resource、常用 GM4 API 与批量更新，不再按脚本大小、数量或运行耗时自动禁用。旧版 WebView 会自动使用同源降级实现；原生网络请求仍按 @connect 校验，并阻止远程网页访问本机与私网地址。";
        new AlertDialog.Builder(this)
                .setTitle("安装“" + script.name + "”？")
                .setMessage(message)
                .setPositiveButton("安装并启用", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        script.enabled = true;
                        scriptStore.save(script);
                        refreshUserScriptRegistrations(true);
                        toast("脚本已安装并启用");
                    }
                })
                .setNeutralButton("安装但禁用", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        script.enabled = false;
                        scriptStore.save(script);
                        refreshUserScriptRegistrations(false);
                        toast("脚本已安装，当前未启用");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showScriptDetails(final UserScriptStore.Script script) {
        StringBuilder matches = new StringBuilder();
        for (String item : script.matches) matches.append("\n• ").append(item);
        String message = "状态：" + (script.quarantined ? "已隔离" : (script.enabled ? "已启用" : "已禁用")) +
                (script.disabledReason.length() == 0 ? "" : "\n原因：" + script.disabledReason) +
                "\n版本：" + (script.version.length() == 0 ? "未知" : script.version) +
                "\n风险：" + script.riskSummary + "（" + script.riskScore + " 分）" +
                "\n运行时机：" + script.runAt + (script.noFrames ? " · 仅顶层页面" : "") +
                "\n声明授权：" + (script.grants.size() == 0 ? "未声明" : script.grants.toString()) +
                "\nHTTPS 依赖：" + script.requires.size() + " 项" +
                "\nHTTPS 资源：" + script.resources.size() + " 项" +
                "\n网络范围：" + (script.connects.size() == 0 ? "未声明" : script.connects.toString()) +
                "\n来源：" + (script.sourceUrl.length() == 0 ? "本地" : script.sourceUrl) +
                "\n匹配范围：" + matches.toString();
        String positive = script.quarantined ? "重新启用" : (preferredUpdateUrl(script).length() > 0 ? "检查更新" : "确定");
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(script.name)
                .setMessage(message)
                .setPositiveButton(positive, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (script.quarantined) {
                            scriptStore.setEnabled(script.id, true);
                            refreshUserScriptRegistrations(true);
                            toast("脚本已重新启用");
                        } else if (preferredUpdateUrl(script).length() > 0) updateOneUserScript(script);
                    }
                })
                .setNegativeButton("返回", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { showScriptCenter(); }
                });
        builder.setNeutralButton("删除", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                scriptStore.delete(script.id);
                refreshUserScriptRegistrations(true);
                toast("脚本已删除");
            }
        });
        builder.show();
    }

    private boolean looksLikeUserScript(String url) {
        return UserScriptInstallPolicy.looksLikeInstallUrl(url);
    }

    private int enabledScriptCount() {
        int count = 0;
        for (UserScriptStore.Script script : scriptStore.getAll()) if (script.enabled) count++;
        return count;
    }

    private void showTabs() {
        if (!requireStartupReady()) return;
        saveCurrentTab();
        dismissOverlay();
        final int pageBackground = nightMode ? Color.rgb(27, 29, 32) : Color.rgb(248, 249, 250);
        final int cardBackground = nightMode ? Color.rgb(38, 41, 45) : WHITE;
        final int activeCard = nightMode ? Color.rgb(32, 54, 82) : Color.rgb(232, 240, 254);
        final int pageText = nightMode ? Color.rgb(232, 234, 237) : TEXT;
        final int pageMuted = nightMode ? Color.rgb(154, 160, 166) : MUTED;
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(pageBackground);
        overlay.setClickable(true);
        overlay.setFocusableInTouchMode(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClickable(true);
        page.setPadding(dp(14), dp(8), dp(14), dp(12));
        overlay.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), 0, 0, dp(8));
        TextView title = new TextView(this);
        title.setText("标签页  " + tabs.size());
        title.setTextSize(22f);
        title.setTextColor(pageText);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1f));
        BrowserIconView add = iconButton(BrowserIconView.PLUS, "新建标签页");
        BrowserIconView closePage = iconButton(BrowserIconView.CLOSE, "返回网页");
        add.setTintColor(pageText);
        closePage.setTintColor(pageText);
        header.addView(add, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(closePage, new LinearLayout.LayoutParams(dp(48), dp(48)));
        page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        final BaseAdapter[] tabAdapterHolder = new BaseAdapter[1];
        final class TabRow {
            final LinearLayout root;
            final TextView title;
            final TextView url;
            final BrowserIconView close;

            TabRow() {
                root = new LinearLayout(MainActivity.this);
                root.setOrientation(LinearLayout.HORIZONTAL);
                root.setGravity(Gravity.CENTER_VERTICAL);
                root.setPadding(dp(16), dp(8), dp(7), dp(8));
                root.setElevation(dp(1));
                // Bind touch directly to the card instead of relying on ListView row dispatch.
                // Some OEM ListView implementations stop delivering item clicks when a row
                // contains an independently clickable child such as the close button.
                root.setClickable(true);
                root.setLongClickable(true);
                root.setFocusable(false);
                LinearLayout text = new LinearLayout(MainActivity.this);
                text.setOrientation(LinearLayout.VERTICAL);
                text.setGravity(Gravity.CENTER_VERTICAL);
                title = new TextView(MainActivity.this);
                title.setTextColor(pageText);
                title.setTextSize(15.5f);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                url = new TextView(MainActivity.this);
                url.setTextColor(pageMuted);
                url.setTextSize(12.5f);
                url.setSingleLine(true);
                url.setEllipsize(android.text.TextUtils.TruncateAt.END);
                text.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(27)));
                text.addView(url, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(23)));
                root.addView(text, new LinearLayout.LayoutParams(0, dp(58), 1f));
                close = iconButton(BrowserIconView.CLOSE, "关闭标签页");
                close.setTintColor(pageText);
                // Keep the close affordance touchable without allowing it to steal row focus.
                close.setFocusable(false);
                close.setFocusableInTouchMode(false);
                root.addView(close, new LinearLayout.LayoutParams(dp(46), dp(46)));
            }

            void bind(final int position) {
                final BrowserTab boundTab = tabs.get(position);
                title.setText((boundTab.pinned ? "固定 · " : "") + safeTitle(boundTab.title, boundTab.url));
                String host = hostOf(boundTab.url);
                url.setText(host.length() == 0 ? "主页" : host);
                root.setBackground(roundRect(position == currentTabIndex ? activeCard : cardBackground, 18));
                root.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        int currentPosition = tabs.indexOf(boundTab);
                        if (currentPosition < 0) return;
                        dismissOverlay();
                        switchTab(currentPosition);
                    }
                });
                root.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View view) {
                        int currentPosition = tabs.indexOf(boundTab);
                        if (currentPosition < 0) return false;
                        BrowserTab tab = tabs.get(currentPosition);
                        tab.pinned = !tab.pinned;
                        persistSession();
                        if (tabAdapterHolder[0] != null) tabAdapterHolder[0].notifyDataSetChanged();
                        toast(tab.pinned ? "标签已固定" : "已取消固定");
                        return true;
                    }
                });
                close.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        int currentPosition = tabs.indexOf(boundTab);
                        if (currentPosition >= 0) closeTabAt(currentPosition);
                    }
                });
            }
        }

        final BaseAdapter tabAdapter = new BaseAdapter() {
            @Override public int getCount() { return tabs.size(); }
            @Override public BrowserTab getItem(int position) { return tabs.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TabRow row;
                if (convertView == null) {
                    row = new TabRow();
                    row.root.setTag(row);
                    convertView = row.root;
                } else row = (TabRow) convertView.getTag();
                row.bind(position);
                return convertView;
            }
        };
        tabAdapterHolder[0] = tabAdapter;
        ListView list = new ListView(this);
        list.setAdapter(tabAdapter);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(10));
        list.setPadding(0, dp(4), 0, dp(20));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setItemsCanFocus(false);
        page.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rootFrame.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activeOverlay = overlay;
        activeOverlayPanel = page;
        activeOverlaySheet = false;
        Motion.showPage(overlay, page, reduceMotion());
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismissOverlay(); newTab(); }
        });
        closePage.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismissOverlay(); }
        });
    }

    private void closeTabAt(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (tabs.size() <= 1) {
            dismissOverlay();
            resetOnlyTabToHome(true);
            return;
        }
        if (index == currentTabIndex) {
            dismissOverlay();
            closeCurrentTab();
            return;
        }
        BrowserTab removed = tabs.remove(index);
        rememberClosedTab(removed);
        destroyTabView(removed, false);
        if (index < currentTabIndex) currentTabIndex--;
        renderedTabCount = null;
        persistSession();
        dismissOverlay();
        showTabs();
    }

    private void newTab() {
        if (!requireStartupReady()) return;
        if (tabs.size() >= MAX_TABS) { toast("最多允许 " + MAX_TABS + " 个标签页"); return; }
        BrowserTab tab = new BrowserTab();
        tab.url = configuredHomeUrl();
        tabs.add(tab);
        activateTab(tabs.size() - 1);
        if (webView != null) webView.clearHistory();
    }

    private void switchTab(int index) {
        if (index < 0 || index >= tabs.size() || index == currentTabIndex) return;
        activateTab(index);
    }

    private void activateTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        BrowserTab previous = currentTabIndex >= 0 && currentTabIndex < tabs.size() ? tabs.get(currentTabIndex) : null;
        WebView previousView = webView;
        if (previous != null && previousView != null) {
            updateTabForView(previousView, previousView.getUrl(), previousView.getTitle());
            previous.lastActiveAt = SystemClock.uptimeMillis();
            previousView.onPause();
            if (previousView.getParent() == webContainer) webContainer.removeView(previousView);
        }

        currentTabIndex = index;
        BrowserTab targetTab = tabs.get(index);
        boolean created = targetTab.liveView == null;
        if (created) targetTab.liveView = acquireWebView();
        webView = targetTab.liveView;
        if (previousView != null && previousView != webView) applyPerformanceMode(previousView);
        applyPerformanceMode(webView);
        targetTab.lastActiveAt = SystemClock.uptimeMillis();
        if (webView.getParent() instanceof ViewGroup) ((ViewGroup) webView.getParent()).removeView(webView);
        webContainer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.onResume();
        resetWebViewTransform(webView);
        webView.requestFocus(View.FOCUS_DOWN);

        advanceNavigationSequence();
        preparedInjection = null;
        pageCommitted = true;
        pageFinished = webView.getProgress() >= 100;
        injectedStartSequence = -1;
        injectedEndSequence = -1;

        if (created) {
            boolean restored = targetTab.state != null && webView.restoreState(targetTab.state) != null;
            targetTab.state = null;
            if (!restored) {
                if (isHomeUrl(targetTab.url)) showHome();
                else loadNetworkUrl(webView, targetTab.url);
            } else {
                currentPageUrl = webView.getUrl() == null ? targetTab.url : webView.getUrl();
                currentPageHost = hostOf(currentPageUrl);
                if (isHomeUrl(currentPageUrl)) {
                    renderedHomeKeys.remove(webView);
                    showHome();
                } else if (isNetworkPage(currentPageUrl)) {
                    prepareNetworkDestination(webView, currentPageUrl);
                    watchInitialNetworkNavigation(webView, currentPageUrl);
                } else if (isOfflineUrl(currentPageUrl)) {
                    prepareOfflineDestination(webView, currentPageUrl);
                } else {
                    applyPageAccessPolicy(webView, currentPageUrl);
                    appliedSiteSettings.remove(webView);
                }
            }
        } else {
            currentPageUrl = webView.getUrl() == null ? targetTab.url : webView.getUrl();
            currentPageHost = hostOf(currentPageUrl);
        }

        pageHosts.put(webView, currentPageHost == null ? "" : currentPageHost);
        scheduleHotWebViewTrim();
        requestChromeUpdate();
        persistSession();
    }

    private void closeCurrentTab() {
        if (tabs.size() <= 1) {
            resetOnlyTabToHome(true);
            return;
        }
        int closingIndex = currentTabIndex;
        BrowserTab closing = tabs.get(closingIndex);
        rememberClosedTab(closing);
        if (webView != null && webView.getParent() == webContainer) webContainer.removeView(webView);
        destroyTabView(closing, false);
        tabs.remove(closingIndex);
        int target = Math.min(closingIndex, tabs.size() - 1);
        currentTabIndex = -1;
        webView = null;
        activateTab(target);
    }

    private void resetOnlyTabToHome(boolean remember) {
        if (tabs.size() == 0) tabs.add(new BrowserTab());
        BrowserTab tab = tabs.get(0);
        if (remember) rememberClosedTab(tab);
        tab.title = "新标签页";
        final String target = configuredHomeUrl();
        tab.url = target;
        tab.state = null;
        tab.pinned = false;
        if (webView != null) {
            try { webView.stopLoading(); webView.clearHistory(); } catch (RuntimeException ignored) {}
        }
        currentTabIndex = 0;
        openConfiguredHome();
        uiHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (webView != null && (isHomeUrl(target) ? isHomeUrl(webView.getUrl()) : target.equals(webView.getUrl())))
                    webView.clearHistory();
            }
        }, 250L);
        persistSession();
    }

    private void rememberClosedTab(BrowserTab tab) {
        if (tab == null || tab.url == null || isHomeUrl(tab.url)) return;
        BrowserTab copy = new BrowserTab();
        copy.title = tab.title;
        copy.url = tab.url;
        copy.pinned = tab.pinned;
        closedTabs.add(0, copy);
        while (closedTabs.size() > 12) closedTabs.remove(closedTabs.size() - 1);
    }

    private void showTabTools() {
        String[] items = new String[] {
                "搜索标签页",
                "重新打开最近关闭的标签" + (closedTabs.size() == 0 ? " · 无记录" : " · " + closedTabs.size()),
                "复制当前标签",
                "关闭其他标签",
                "冻结后台标签释放内存",
                "关闭全部并返回主页"
        };
        int[] icons = new int[] { BrowserIconView.SEARCH, BrowserIconView.RELOAD, BrowserIconView.PLUS,
                BrowserIconView.CLOSE, BrowserIconView.SPEED, BrowserIconView.HOME };
        showActionSheet("标签页工具", tabs.size() + " 个标签", items, icons, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) showTabSearch();
                else if (which == 1) reopenClosedTab();
                else if (which == 2) duplicateCurrentTab();
                else if (which == 3) closeOtherTabs();
                else if (which == 4) {
                    for (int i = 0; i < tabs.size(); i++) if (i != currentTabIndex && !tabs.get(i).pinned) freezeTab(tabs.get(i));
                    toast("后台标签已冻结");
                } else closeAllTabs();
            }
        });
    }

    private void reopenClosedTab() {
        if (closedTabs.size() == 0) { toast("没有最近关闭的标签"); return; }
        if (tabs.size() >= MAX_TABS) { toast("标签页已达到上限"); return; }
        BrowserTab closed = closedTabs.remove(0);
        BrowserTab tab = new BrowserTab();
        tab.title = closed.title;
        tab.url = closed.url;
        tab.pinned = closed.pinned;
        tabs.add(tab);
        activateTab(tabs.size() - 1);
    }

    private void duplicateCurrentTab() {
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) return;
        if (tabs.size() >= MAX_TABS) { toast("标签页已达到上限"); return; }
        saveCurrentTab();
        BrowserTab current = tabs.get(currentTabIndex);
        BrowserTab duplicate = new BrowserTab();
        duplicate.title = current.title;
        duplicate.url = current.url;
        tabs.add(duplicate);
        activateTab(tabs.size() - 1);
    }

    private void closeOtherTabs() {
        if (tabs.size() <= 1) { toast("没有其他标签"); return; }
        BrowserTab current = tabs.get(currentTabIndex);
        for (int i = tabs.size() - 1; i >= 0; i--) {
            BrowserTab tab = tabs.get(i);
            if (tab == current || tab.pinned) continue;
            rememberClosedTab(tab);
            destroyTabView(tab, false);
            tabs.remove(i);
        }
        currentTabIndex = tabs.indexOf(current);
        renderedTabCount = null;
        persistSession();
        requestChromeUpdate();
    }

    private void closeAllTabs() {
        BrowserTab active = currentTabIndex >= 0 && currentTabIndex < tabs.size() ? tabs.get(currentTabIndex) : null;
        for (BrowserTab tab : new ArrayList<BrowserTab>(tabs)) {
            rememberClosedTab(tab);
            if (tab != active) destroyTabView(tab, false);
        }
        tabs.clear();
        if (active == null) active = new BrowserTab();
        tabs.add(active);
        currentTabIndex = 0;
        resetOnlyTabToHome(false);
    }

    private void showTabSearch() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("标题或网址");
        new AlertDialog.Builder(this).setTitle("搜索标签页").setView(input)
                .setPositiveButton("搜索", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String query = input.getText().toString().trim().toLowerCase(Locale.US);
                        final ArrayList<Integer> matches = new ArrayList<Integer>();
                        for (int i = 0; i < tabs.size(); i++) {
                            BrowserTab tab = tabs.get(i);
                            if (query.length() == 0 || tab.title.toLowerCase(Locale.US).contains(query) || tab.url.toLowerCase(Locale.US).contains(query)) matches.add(Integer.valueOf(i));
                        }
                        if (matches.size() == 0) { toast("没有匹配的标签"); return; }
                        String[] labels = new String[matches.size()];
                        for (int i = 0; i < labels.length; i++) {
                            BrowserTab tab = tabs.get(matches.get(i).intValue());
                            labels[i] = safeTitle(tab.title, tab.url) + "\n" + tab.url;
                        }
                        new AlertDialog.Builder(MainActivity.this).setTitle("匹配标签").setItems(labels, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) { activateTab(matches.get(which).intValue()); }
                        }).show();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void saveCurrentTab() {
        if (tabs.size() == 0 || currentTabIndex < 0 || currentTabIndex >= tabs.size() || webView == null) return;
        BrowserTab tab = tabs.get(currentTabIndex);
        updateTabForView(webView, webView.getUrl(), webView.getTitle());
        tab.lastActiveAt = SystemClock.uptimeMillis();
    }

    private void updateCurrentTab(String url, String title) {
        if (tabs.size() == 0 || currentTabIndex < 0 || currentTabIndex >= tabs.size()) return;
        BrowserTab tab = tabs.get(currentTabIndex);
        if (url != null) tab.url = url;
        if (title != null && title.length() > 0) tab.title = title;
    }

    private void scheduleHotWebViewTrim() {
        if (hotTrimPending || webContainer == null) return;
        hotTrimPending = true;
        webContainer.postOnAnimation(new Runnable() {
            @Override public void run() {
                hotTrimPending = false;
                enforceHotWebViewLimit();
            }
        });
    }

    private void enforceHotWebViewLimit() {
        if (deviceProfile == null) return;
        int limit = deviceProfile.hotWebViewLimit(performanceMode);
        while (liveWebViewCount() > limit) {
            BrowserTab oldest = null;
            for (int i = 0; i < tabs.size(); i++) {
                BrowserTab candidate = tabs.get(i);
                if (i == currentTabIndex || candidate.liveView == null) continue;
                if (oldest == null || candidate.lastActiveAt < oldest.lastActiveAt) oldest = candidate;
            }
            if (oldest == null) break;
            freezeTab(oldest);
        }
    }

    private void freezeTab(BrowserTab tab) {
        if (tab == null || tab.liveView == null || tab.liveView == webView) return;
        WebView view = tab.liveView;
        updateTabForView(view, view.getUrl(), view.getTitle());
        tab.state = captureWebViewState(view);
        destroyTabView(tab, true);
        trimColdTabStates();
    }

    /** Prevents oversized WebView history bundles from exhausting RAM or Binder saved state. */
    private static Bundle captureWebViewState(WebView view) {
        if (view == null) return null;
        Bundle state = new Bundle();
        if (view.saveState(state) == null) return null;
        Parcel parcel = Parcel.obtain();
        try {
            state.writeToParcel(parcel, 0);
            return parcel.dataSize() <= MAX_WEBVIEW_STATE_BYTES ? state : null;
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            parcel.recycle();
        }
    }

    private void trimColdTabStates() {
        int limit = deviceProfile == null ? 2 : deviceProfile.coldTabStateLimit(performanceMode);
        int count = 0;
        for (int i = 0; i < tabs.size(); i++) if (i != currentTabIndex && tabs.get(i).state != null) count++;
        while (count > limit) {
            BrowserTab oldest = null;
            for (int i = 0; i < tabs.size(); i++) {
                BrowserTab candidate = tabs.get(i);
                if (i == currentTabIndex || candidate.state == null) continue;
                if (oldest == null || candidate.lastActiveAt < oldest.lastActiveAt) oldest = candidate;
            }
            if (oldest == null) return;
            oldest.state = null;
            count--;
        }
    }

    private void destroyTabView(BrowserTab tab, boolean keepState) {
        if (tab == null || tab.liveView == null) return;
        WebView view = tab.liveView;
        tab.liveView = null;
        if (!keepState) tab.state = null;
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        destroyWebView(view);
    }

    private void recoverFromRendererLoss() {
        String reloadUrl = currentPageUrl;
        if (reloadUrl == null || reloadUrl.length() == 0) reloadUrl = HOME_URL;
        if (customView != null) exitFullscreen();
        ArrayList<WebView> affected = new ArrayList<WebView>();
        for (BrowserTab tab : tabs) {
            if (tab.liveView != null && !affected.contains(tab.liveView)) {
                updateTabForView(tab.liveView, tab.liveView.getUrl(), tab.liveView.getTitle());
                affected.add(tab.liveView);
            }
            tab.liveView = null;
            tab.state = null;
        }
        for (WebView view : affected) destroyWebView(view);

        BrowserTab active = currentTabIndex >= 0 && currentTabIndex < tabs.size() ? tabs.get(currentTabIndex) : null;
        webView = createConfiguredWebView();
        if (active != null) {
            active.liveView = webView;
            active.lastActiveAt = SystemClock.uptimeMillis();
        }
        webContainer.removeAllViews();
        webContainer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        currentPageUrl = reloadUrl;
        currentPageHost = hostOf(reloadUrl);
        pageHosts.put(webView, currentPageHost);
        adBlockActiveByView.put(webView, Boolean.valueOf(isAdBlockActiveForHost(currentPageHost)));
        rendererRecoveryPending = false;
        if (isHomeUrl(reloadUrl)) showHome(); else loadNetworkUrl(webView, reloadUrl);
        requestChromeUpdate();
        toast("网页渲染进程已恢复");
    }

    private void destroyWebView(WebView view) {
        if (view == null) return;
        initialNavigationGuards.remove(view);
        unresponsiveWebViews.remove(view);
        removeDocumentStartUserScripts(view);
        pageHosts.remove(view);
        adBlockActiveByView.remove(view);
        mobileUserAgents.remove(view);
        appliedSiteSettings.remove(view);
        cosmeticInjected.remove(view);
        trustedHomeViews.remove(view);
        customHomeViews.remove(view);
        credentialCaptureTokens.remove(view);
        renderedHomeKeys.remove(view);
        try { view.stopLoading(); } catch (RuntimeException ignored) {}
        try { view.onPause(); } catch (RuntimeException ignored) {}
        view.setOnTouchListener(null);
        view.setOnLongClickListener(null);
        view.setDownloadListener(null);
        if (Build.VERSION.SDK_INT >= 29) {
            RendererWatchdog.detach(view);
        }
        view.setWebChromeClient(null);
        view.setWebViewClient(null);
        view.destroy();
    }

    private String safeTitle(String title, String url) {
        String value = title == null || title.trim().length() == 0 ? url : title.trim();
        if (isHomeUrl(url)) value = "主页";
        return value.length() > 34 ? value.substring(0, 34) + "…" : value;
    }

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        previousOrientation = getRequestedOrientation();
        previousSystemUi = getWindow().getDecorView().getSystemUiVisibility();
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        browserChrome.setVisibility(View.GONE);
        rootFrame.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        view.requestFocus(View.FOCUS_DOWN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void exitFullscreen() {
        if (customView == null) return;
        rootFrame.removeView(customView);
        customView = null;
        browserChrome.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(previousSystemUi);
        setRequestedOrientation(previousOrientation);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
        resetWebViewTransform(webView);
        if (webView != null) webView.requestFocus(View.FOCUS_DOWN);
    }

    private void installCredentialExperience(final WebView view, final String url, final long sequence) {
        if (view == null || view != webView || !isSecureCredentialUrl(url) || activityDestroyed) {
            if (view != null) credentialCaptureTokens.remove(view);
            return;
        }
        final String host = hostOf(url);
        if (passwordSavePromptsEnabled || passwordAutofillEnabled) {
            String token = UrlCleaner.randomToken();
            credentialCaptureTokens.put(view, token);
            try { view.evaluateJavascript(CredentialAutofill.captureScript(token), null); }
            catch (RuntimeException ignored) { credentialCaptureTokens.remove(view); }
        } else credentialCaptureTokens.remove(view);

        if (!passwordAutofillEnabled || services == null ||
                !services.passwords().mightHaveForHost(host)) return;
        long[] delays = new long[] { 0L, 700L, 2200L, 5200L };
        for (final long delay : delays) uiHandler.postDelayed(new Runnable() {
            @Override public void run() { probeCredentialAutofill(view, url, host, sequence); }
        }, delay);
    }

    private void probeCredentialAutofill(final WebView view, final String expectedUrl,
                                         final String host, final long sequence) {
        if (!passwordAutofillEnabled || activityDestroyed || !activityResumed ||
                view != webView || sequence != navigationSequence ||
                !isSecureCredentialUrl(view.getUrl()) || !host.equalsIgnoreCase(hostOf(view.getUrl()))) return;
        final String pageKey = sequence + "|" + expectedUrl;
        try {
            view.evaluateJavascript(CredentialAutofill.detectScript(), new ValueCallback<String>() {
                @Override public void onReceiveValue(String value) {
                    if (activityDestroyed || view != webView || sequence != navigationSequence ||
                            !host.equalsIgnoreCase(currentHost())) return;
                    try {
                        Object decoded = new JSONTokener(value == null ? "{}" : value).nextValue();
                        JSONObject state = new JSONObject(decoded instanceof String ? (String) decoded : String.valueOf(decoded));
                        boolean login = state.optBoolean("login", false);
                        boolean usernameOnly = state.optBoolean("usernameOnly", false);
                        if (!login && !usernameOnly) return;
                        String stateKey = pageKey + (login ? "|password" : "|username");
                        if (stateKey.equals(handledCredentialPageKey)) return;
                        handledCredentialPageKey = stateKey;
                        withVaultUnlock(new Runnable() {
                            @Override public void run() {
                                fillSavedCredentialForPage(view, expectedUrl, host, sequence);
                            }
                        });
                    } catch (Exception ignored) {}
                }
            });
        } catch (RuntimeException ignored) {}
    }

    private void fillSavedCredentialForPage(final WebView view, final String expectedUrl,
                                            final String host, final long sequence) {
        if (services == null) return;
        if (stagedCredential != null && host.equalsIgnoreCase(stagedCredential.host) &&
                SystemClock.elapsedRealtime() - stagedCredentialAt < 120_000L) {
            fillCredentialOnPage(view, expectedUrl, stagedCredential, true);
            return;
        }
        services.passwords().forHost(host, new PasswordVault.Callback<List<PasswordVault.Credential>>() {
            @Override public void onComplete(final List<PasswordVault.Credential> credentials, Exception error) {
                if (error != null || activityDestroyed || view != webView || sequence != navigationSequence ||
                        !host.equalsIgnoreCase(currentHost()) || !isSecureCredentialUrl(currentPageUrl)) return;
                if (credentials == null || credentials.size() == 0) return;
                if (credentials.size() == 1) {
                    stageAutomaticCredential(credentials.get(0));
                    fillCredentialOnPage(view, expectedUrl, stagedCredential, true);
                    return;
                }
                String[] names = new String[credentials.size()];
                for (int i = 0; i < credentials.size(); i++) names[i] = credentials.get(i).username;
                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("选择要自动填充的账号")
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                if (which >= 0 && which < credentials.size()) {
                                    stageAutomaticCredential(credentials.get(which));
                                    fillCredentialOnPage(view, expectedUrl, stagedCredential, true);
                                }
                            }
                        }).setNegativeButton("取消", null).create();
                secureDialog(dialog);
                dialog.show();
            }
        });
    }

    private void stageAutomaticCredential(PasswordVault.Credential credential) {
        stagedCredential = credential == null ? null : credential.copy();
        stagedCredentialAt = stagedCredential == null ? 0L : SystemClock.elapsedRealtime();
    }

    private boolean handleCredentialAutofillPrompt(final WebView source, String message, JsPromptResult result) {
        final String prefix = "__MEDIAN_AUTOFILL__";
        if (message == null || !message.startsWith(prefix)) return false;
        if (result != null) result.confirm("");
        if (result == null || !passwordAutofillEnabled || activityDestroyed || source == null || source != webView ||
                services == null || message.length() > 2048 || !isSecureCredentialUrl(source.getUrl())) return true;
        try {
            JSONObject request = new JSONObject(message.substring(prefix.length()));
            String token = request.optString("t", "");
            final String host = request.optString("h", "").trim().toLowerCase(Locale.US);
            String expected = credentialCaptureTokens.get(source);
            if (expected == null || !expected.equals(token) || !host.equalsIgnoreCase(hostOf(source.getUrl())) ||
                    !services.passwords().mightHaveForHost(host)) return true;
            final String url = source.getUrl();
            final long sequence = navigationSequence;
            source.post(new Runnable() {
                @Override public void run() { probeCredentialAutofill(source, url, host, sequence); }
            });
        } catch (Exception ignored) {}
        return true;
    }

    private boolean handleCredentialCapturePrompt(final WebView source, String message, JsPromptResult result) {
        final String prefix = "__MEDIAN_CREDENTIAL__";
        if (message == null || !message.startsWith(prefix)) return false;
        if (result != null) result.confirm("");
        if (!passwordSavePromptsEnabled || result == null || source == null || source != webView ||
                message.length() > 10000 || !isSecureCredentialUrl(source.getUrl())) return true;
        try {
            JSONObject request = new JSONObject(message.substring(prefix.length()));
            String token = request.optString("t", "");
            final String host = request.optString("h", "").trim().toLowerCase(Locale.US);
            final String username = request.optString("u", "").trim();
            final String password = request.optString("p", "");
            String expected = credentialCaptureTokens.get(source);
            if (expected == null || !expected.equals(token) || !host.equalsIgnoreCase(hostOf(source.getUrl())) ||
                    username.length() == 0 || username.length() > 512 || password.length() == 0 || password.length() > 8192)
                return true;
            String offerKey = UrlCleaner.stableId(host + "\n" + username + "\n" + password);
            long now = SystemClock.elapsedRealtime();
            if (offerKey.equals(lastCredentialOfferKey) && now - lastCredentialOfferAt < 15000L) return true;
            lastCredentialOfferKey = offerKey;
            lastCredentialOfferAt = now;
            uiHandler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!activityDestroyed && passwordSavePromptsEnabled)
                        offerToSaveCapturedCredential(host, username, password);
                }
            }, 350L);
        } catch (Exception ignored) {}
        return true;
    }

    private void offerToSaveCapturedCredential(final String host, final String username, final String password) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("保存或更新密码？")
                .setMessage(host + "\n" + username + "\n\n密码只会加密保存在本机，Median 不会自动提交表单。")
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        withVaultUnlock(new Runnable() {
                            @Override public void run() {
                                services.passwords().saveCredential(host, username, password,
                                        new PasswordVault.Callback<Void>() {
                                    @Override public void onComplete(Void value, Exception error) {
                                        if (error == null) toast("账号已加密保存");
                                        else toast("保存失败：" + safeMessage(error));
                                    }
                                });
                            }
                        });
                    }
                }).setNegativeButton("不保存", null).create();
        secureDialog(dialog);
        dialog.show();
    }

    private boolean isSecureCredentialUrl(String url) {
        try {
            URL parsed = NetworkSecurity.parseHttpUrl(url);
            return "https".equalsIgnoreCase(parsed.getProtocol()) &&
                    parsed.getUserInfo() == null && !"median.invalid".equalsIgnoreCase(parsed.getHost());
        } catch (Exception ignored) { return false; }
    }

    private void showPasswordMenu() {
        String[] items = new String[] {
                "自动识别并填充：" + (passwordAutofillEnabled ? "已开启" : "已关闭"),
                "登录后询问保存：" + (passwordSavePromptsEnabled ? "已开启" : "已关闭"),
                "手动保存当前站点账号", "手动填充当前页面", "管理已保存密码", "密码安全说明"
        };
        new AlertDialog.Builder(this)
                .setTitle("密码管理器")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            passwordAutofillEnabled = !passwordAutofillEnabled;
                            prefs.edit().putBoolean("password_autofill", passwordAutofillEnabled).apply();
                            handledCredentialPageKey = "";
                            if (passwordAutofillEnabled && webView != null)
                                installCredentialExperience(webView, currentPageUrl, navigationSequence);
                            toast(passwordAutofillEnabled ? "密码自动填充已开启" : "密码自动填充已关闭");
                            return;
                        }
                        if (which == 1) {
                            passwordSavePromptsEnabled = !passwordSavePromptsEnabled;
                            prefs.edit().putBoolean("password_save_prompts", passwordSavePromptsEnabled).apply();
                            if (webView != null) installCredentialExperience(webView, currentPageUrl, navigationSequence);
                            toast(passwordSavePromptsEnabled ? "登录后将询问保存密码" : "登录保存提示已关闭");
                            return;
                        }
                        if (which == 5) { showVaultInfo(); return; }
                        final int action = which;
                        withVaultUnlock(new Runnable() {
                            @Override public void run() {
                                if (action == 2) saveCredentialDialog();
                                else if (action == 3) chooseCredentialToFill();
                                else manageCredentials();
                            }
                        });
                    }
                })
                .show();
    }

    private void withVaultUnlock(Runnable action) {
        if (action == null) return;
        if (SystemClock.elapsedRealtime() < vaultUnlockedUntil) { action.run(); return; }
        android.app.KeyguardManager manager = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (manager == null || !manager.isDeviceSecure()) {
            new AlertDialog.Builder(this).setTitle("需要安全锁屏")
                    .setMessage("Median 现在把 Keystore 密钥直接绑定到系统身份验证。请先设置 PIN、图案、密码或生物识别，密码库不会提供绕过入口。")
                    .setPositiveButton("打开安全设置", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            try { startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); }
                            catch (Exception e) { toast("无法打开系统安全设置"); }
                        }
                    }).setNegativeButton("取消", null).show();
            return;
        }
        Intent unlock = manager.createConfirmDeviceCredentialIntent("解锁 Median 密码库", "确认设备身份后继续");
        if (unlock == null) { toast("系统身份验证不可用，密码库保持锁定"); return; }
        pendingVaultAction = action;
        try { startActivityForResult(unlock, VAULT_UNLOCK_REQUEST); }
        catch (Exception e) { pendingVaultAction = null; toast("无法调用系统身份验证"); }
    }

    private boolean requireSecurePage() {
        Uri uri = currentUri();
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || isHomeUrl(uri.toString())) {
            toast("仅允许在 HTTPS 网站保存或填充密码");
            return false;
        }
        return currentHost().length() > 0;
    }

    private void saveCredentialDialog() {
        if (!requireSecurePage()) return;
        final String expectedUrl = currentPageUrl;
        final String expectedHost = currentHost();
        final EditText username = new EditText(this);
        username.setHint("用户名或邮箱");
        username.setSingleLine(true);
        final EditText password = new EditText(this);
        password.setHint("密码");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), 0, dp(18), 0);
        TextView host = new TextView(this);
        host.setText("站点：" + expectedHost);
        host.setPadding(0, dp(8), 0, dp(8));
        content.addView(host);
        content.addView(username);
        content.addView(password);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("保存账号")
                .setView(content)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        final String user = username.getText().toString().trim();
                        final String pass = password.getText().toString();
                        if (!UrlCleaner.sameOrigin(expectedUrl, currentPageUrl) || !expectedHost.equalsIgnoreCase(currentHost()) ||
                                !requireSecurePage()) {
                            toast("页面来源已经变化，已拒绝保存");
                            return;
                        }
                        if (user.length() == 0 || pass.length() == 0) {
                            toast("用户名和密码不能为空");
                            return;
                        }
                        if (user.length() > 512 || pass.length() > 8192) { toast("账号或密码长度超过安全限制"); return; }
                        services.passwords().saveCredential(expectedHost, user, pass, new PasswordVault.Callback<Void>() {
                            @Override public void onComplete(Void value, Exception error) {
                                if (error == null) toast("已加密保存");
                                else toast("保存失败：" + safeMessage(error));
                            }
                        });
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        secureDialog(dialog);
        dialog.show();
        String probe = "(function(){function v(e){return e&&!e.disabled&&e.offsetParent!==null;}var ps=Array.from(document.querySelectorAll('input[type=password]')).filter(v),p=ps.find(function(e){return e.autocomplete!=='new-password';})||ps[0],scope=p&&p.form?p.form:document;var us=Array.from(scope.querySelectorAll('input[autocomplete=username],input[type=email],input[type=text]')).filter(v),u=us[0];return JSON.stringify([u&&u.value||'',p&&p.value||'']);})();";
        webView.evaluateJavascript(probe, new ValueCallback<String>() {
            @Override public void onReceiveValue(String value) {
                if (!UrlCleaner.sameOrigin(expectedUrl, currentPageUrl)) return;
                try {
                    Object decoded = new JSONTokener(value).nextValue();
                    JSONArray fields = new JSONArray(decoded instanceof String ? (String) decoded : String.valueOf(decoded));
                    if (username.getText().length() == 0) username.setText(fields.optString(0, ""));
                    if (password.getText().length() == 0) password.setText(fields.optString(1, ""));
                } catch (Exception ignored) {}
            }
        });
    }

    private void chooseCredentialToFill() {
        if (!requireSecurePage()) return;
        final String requestedHost = currentHost();
        services.passwords().forHost(requestedHost, new PasswordVault.Callback<List<PasswordVault.Credential>>() {
            @Override public void onComplete(final List<PasswordVault.Credential> credentials, Exception error) {
                if (error != null) {
                    toast("读取密码库失败：" + safeMessage(error));
                    return;
                }
                if (!requestedHost.equalsIgnoreCase(currentHost())) {
                    toast("页面已经变化，请重新选择账号");
                    return;
                }
                if (credentials == null || credentials.size() == 0) {
                    toast("当前站点没有已保存账号");
                    return;
                }
                String[] names = new String[credentials.size()];
                for (int i = 0; i < credentials.size(); i++) names[i] = credentials.get(i).username;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("选择账号")
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) { fillCredential(credentials.get(which)); }
                        })
                        .show();
            }
        });
    }

    private void fillCredential(PasswordVault.Credential credential) {
        if (!requireSecurePage()) return;
        fillCredentialOnPage(webView, currentPageUrl, credential, false);
    }

    private void fillCredentialOnPage(final WebView target, final String expectedUrl,
                                      PasswordVault.Credential credential, final boolean automatic) {
        if (target == null || target != webView || credential == null ||
                !isSecureCredentialUrl(target.getUrl()) || !currentHost().equalsIgnoreCase(credential.host) ||
                !UrlCleaner.sameOrigin(expectedUrl, target.getUrl())) {
            if (!automatic) toast("站点或页面已经变化，已拒绝填充");
            return;
        }
        try {
            target.evaluateJavascript(CredentialAutofill.fillScript(credential.username, credential.password),
                    new ValueCallback<String>() {
                @Override public void onReceiveValue(String value) {
                    if (target != webView || !UrlCleaner.sameOrigin(expectedUrl, target.getUrl())) return;
                    boolean filled = false;
                    try {
                        Object decoded = new JSONTokener(value == null ? "{}" : value).nextValue();
                        JSONObject state = new JSONObject(decoded instanceof String ? (String) decoded : String.valueOf(decoded));
                        filled = state.optBoolean("filled", false);
                    } catch (Exception ignored) {}
                    if (filled) toast(automatic ? "已自动填充，请确认后登录" : "已填充，请确认后登录");
                    else if (!automatic) toast("当前页面没有可安全填充的登录密码框");
                }
            });
        } catch (RuntimeException ignored) {
            if (!automatic) toast("当前页面暂时无法填充");
        }
    }

    private void manageCredentials() {
        services.passwords().getAll(new PasswordVault.Callback<List<PasswordVault.Credential>>() {
            @Override public void onComplete(final List<PasswordVault.Credential> all, Exception error) {
                if (error != null) {
                    toast("读取密码库失败：" + safeMessage(error));
                    return;
                }
                if (all == null || all.size() == 0) {
                    toast("密码库为空");
                    return;
                }
                String[] names = new String[all.size()];
                for (int i = 0; i < all.size(); i++) names[i] = all.get(i).host + "\n" + all.get(i).username;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("已保存密码（点按删除）")
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) { confirmDeleteCredential(all.get(which)); }
                        })
                        .setNegativeButton("关闭", null)
                        .show();
            }
        });
    }

    private void confirmDeleteCredential(final PasswordVault.Credential credential) {
        new AlertDialog.Builder(this)
                .setTitle("删除账号？")
                .setMessage(credential.host + "\n" + credential.username)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        services.passwords().delete(credential.id, new PasswordVault.Callback<Void>() {
                            @Override public void onComplete(Void value, Exception error) {
                                if (error == null) toast("已删除");
                                else toast("删除失败：" + safeMessage(error));
                            }
                        });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showVaultInfo() {
        new AlertDialog.Builder(this)
                .setTitle("密码安全说明")
                .setMessage("密码数据使用 AES-GCM 加密，密钥存放在 Android Keystore，并要求最近 120 秒内完成系统身份验证；旧版未绑定身份的密钥会在首次成功解锁时迁移。应用禁止系统备份。\n\n自动填充严格匹配 HTTPS 主机，跳过新密码、确认密码和验证码字段，不会自动提交。登录保存监听只在真实用户提交时弹出本机确认，不向网页开放长期原生桥。\n\n仍未经过独立安全审计，不建议保存金融账户或主要邮箱密码。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void secureDialog(final AlertDialog dialog) {
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface d) {
                if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        });
    }

    private void showFindDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setPadding(dp(18), dp(8), dp(18), dp(8));
        new AlertDialog.Builder(this)
                .setTitle("页面内查找")
                .setView(input)
                .setPositiveButton("查找", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        webView.findAllAsync(input.getText().toString());
                        webView.showFindDialog(input.getText().toString(), false);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void sharePage() {
        String url = webView.getUrl();
        if (url == null || isHomeUrl(url)) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, (webView.getTitle() == null ? "" : webView.getTitle() + "\n") + url);
        startActivity(Intent.createChooser(share, "分享页面"));
    }

    private void showStoragePanel() {
        showStoragePanel(this);
    }

    private void showStoragePanel(final Runnable backAction) {
        toast("正在统计浏览数据");
        executeTask(scriptExecutor, new Runnable() {
            @Override public void run() {
                final StoragePolicy.Snapshot snapshot = StoragePolicy.snapshot(MainActivity.this);
                uiHandler.post(new Runnable() {
                    @Override public void run() { showStorageSnapshot(snapshot, backAction); }
                });
            }
        });
    }

    private void showStorageSnapshot(final StoragePolicy.Snapshot snapshot, final Runnable backAction) {
        String subtitle = "总计 " + StoragePolicy.format(snapshot.totalBytes) +
                " · 临时缓存 " + StoragePolicy.format(snapshot.transientBytes) +
                " · 站点及设置 " + StoragePolicy.format(snapshot.siteBytes);
        String[] items = new String[] {
                "清理临时缓存（保留登录）",
                "清除 Cookie 与站点数据（会退出登录）",
                "清除全部浏览数据",
                "重新统计"
        };
        int[] icons = new int[] { BrowserIconView.RELOAD, BrowserIconView.SHIELD, BrowserIconView.CLOSE, BrowserIconView.SEARCH };
        showActionSheet("存储与数据", subtitle, items, icons, null, null, backAction, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) {
                    webView.clearCache(true);
                    toast("临时缓存已清理");
                } else if (which == 1) {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    android.webkit.WebStorage.getInstance().deleteAllData();
                    toast("Cookie 与站点数据已清除");
                } else if (which == 2) {
                    confirmClearData();
                } else {
                    showStoragePanel(backAction);
                }
            }
        });
    }

    private void maybeTrimTransientCache() {
        if (scriptExecutor == null || scriptExecutor.isShutdown()) return;
        if (isMediaCompatibilityHost(currentPageHost)) return;
        long now = System.currentTimeMillis();
        long last = prefs.getLong("last_cache_check", 0L);
        if (now - last < 24L * 60L * 60L * 1000L) return;
        prefs.edit().putLong("last_cache_check", now).apply();
        final String mode = performanceMode;
        executeTask(scriptExecutor, new Runnable() {
            @Override public void run() {
                StoragePolicy.Snapshot snapshot = StoragePolicy.snapshot(MainActivity.this);
                long budget = StoragePolicy.budgetBytes(MainActivity.this, mode);
                final boolean substantiallyOverBudget = snapshot.transientBytes > budget + budget / 4L;
                if (!substantiallyOverBudget) return;
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (webView != null) webView.clearCache(false);
                    }
                });
            }
        });
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this)
                .setTitle("清除浏览数据")
                .setMessage("将清除 Cookie、缓存、表单、HTTP 身份验证、历史、下载索引、网站权限与本地存储。下载文件、书签、离线页面、密码和用户脚本不会删除。")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        CookieManager.getInstance().removeAllCookies(new ValueCallback<Boolean>() {
                            @Override public void onReceiveValue(Boolean value) { CookieManager.getInstance().flush(); }
                        });
                        for (BrowserTab tab : tabs) if (tab.liveView != null) {
                            tab.liveView.clearCache(true);
                            tab.liveView.clearFormData();
                            tab.liveView.clearHistory();
                        }
                        if (dataStore != null) dataStore.clearHistory();
                        services.downloads().clear();
                        if (siteSettingsStore != null) siteSettingsStore.clearAll();
                        appliedSiteSettings.clear();
                        for (BrowserTab tab : tabs) if (tab.liveView != null) applySiteSettings(tab.liveView, pageHostFor(tab.liveView));
                        android.webkit.WebStorage.getInstance().deleteAllData();
                        android.webkit.GeolocationPermissions.getInstance().clearAll();
                        android.webkit.WebViewDatabase database = android.webkit.WebViewDatabase.getInstance(MainActivity.this);
                        database.clearHttpAuthUsernamePassword();
                        database.clearFormData();
                        toast("浏览数据已清除");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCommunityNoticeOnFirstLaunch(Bundle state) {
        if (state != null || prefs.getBoolean(PREF_COMMUNITY_NOTICE_SHOWN, false)) return;
        prefs.edit().putBoolean(PREF_COMMUNITY_NOTICE_SHOWN, true).apply();
        uiHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                showCommunityNotice();
            }
        }, 350L);
    }

    private void showCommunityNotice() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("欢迎使用 Median Browser")
                .setMessage("感谢你使用 Median Browser。我们会持续改进轻量体验、隐私保护与网页兼容性。\n\n"
                        + COMMUNITY_INFO
                        + "\n\n本提示仅在首次启动时显示。之后可在“设置 → 关于 Median”中再次查看。")
                .setPositiveButton("开始使用", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) { enableDialogLinks(dialog); }
        });
        dialog.show();
    }

    private void enableDialogLinks(AlertDialog dialog) {
        TextView message = dialog.findViewById(android.R.id.message);
        if (message == null) return;
        android.text.util.Linkify.addLinks(message, android.text.util.Linkify.WEB_URLS);
        message.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        message.setLinksClickable(true);
    }

    private void showAbout() {
        showAbout(null);
    }

    private void showAbout(final Runnable returnAction) {
        String profile = deviceProfile == null ? "设备策略：尚未初始化" : deviceProfile.summary();
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Median Browser " + appVersionName())
                .setMessage("本版重点强化用户脚本兼容、HTTPS 密码自动填充和动态媒体发现，并整合过滤订阅、Median 单连接下载、独立隐私进程、离线 MHTML、阅读模式、系统朗读、标签批量工具、跟踪参数清理、站点权限和三档性能调度。所有新下载均由 Median 自己处理，不调用系统下载器。\n\n密码库使用系统身份验证与 Android Keystore；自动填充不会覆盖已输入内容或自动提交。应用不集成广告、分析或遥测 SDK。\n\n" + profile + "\n\nMedian 使用设备的 Android System WebView；网页兼容性、协议与媒体能力取决于系统 WebView 版本。\n\n" + COMMUNITY_INFO)
                .setPositiveButton("确定", null)
                .setNeutralButton("隐私说明", null)
                .setNegativeButton("兼容诊断", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                enableDialogLinks(dialog);
                Button privacy = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                if (privacy != null) privacy.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { showPrivacyNotice(); }
                });
                Button diagnostics = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (diagnostics != null) diagnostics.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { showCompatibilityDiagnostics(); }
                });
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) {
                continueSettingsPanel(returnAction);
            }
        });
        dialog.show();
    }

    private void showCompatibilityDiagnostics() {
        final String report = WebViewCompat.getDocumentStartDiagnosticReport()
                + "\n\n兼容恢复：厂商 WebView 若接受 document-start 注册却漏执行，Median 会在页面提交后只补运行缺失脚本。"
                + "完全不支持 document-start 时仍会运行脚本，并为存储、同源 XHR 和页面 API 提供安全降级；"
                + "跨域请求、下载、原生剪贴板等能力在没有可信桥接时保持关闭。\n\n最近一次首导航：\n"
                + prefs.getString(PREF_STARTUP_DIAGNOSTIC, "尚无网络首导航记录");
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("WebView 兼容诊断")
                .setMessage(report)
                .setPositiveButton("运行注入自测", null)
                .setNeutralButton("复制", null)
                .setNegativeButton("关闭", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                Button copy = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                if (copy != null) copy.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Median WebView diagnostics", report));
                            toast("诊断信息已复制");
                        }
                    }
                });
                Button test = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (test != null) test.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { runDocumentStartSelfTest(); }
                });
            }
        });
        dialog.show();
    }

    private void runDocumentStartSelfTest() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            toast("当前 System WebView 不支持安全的 document-start 注入");
            return;
        }
        final WebView probe = new WebView(this);
        final String token = UrlCleaner.randomToken();
        final ScriptHandler[] registration = new ScriptHandler[1];
        final boolean[] finished = new boolean[] { false };
        WebSettings settings = probe.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        probe.setAlpha(0f);
        rootFrame.addView(probe, new FrameLayout.LayoutParams(1, 1));

        final Runnable cleanup = new Runnable() {
            @Override public void run() {
                if (finished[0]) return;
                finished[0] = true;
                if (registration[0] != null) try { registration[0].remove(); } catch (RuntimeException ignored) {}
                if (probe.getParent() instanceof ViewGroup) ((ViewGroup) probe.getParent()).removeView(probe);
                try { probe.stopLoading(); } catch (RuntimeException ignored) {}
                probe.destroy();
            }
        };

        probe.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript("String(window.__medianCompatOrder||'missing')", new ValueCallback<String>() {
                    @Override public void onReceiveValue(String value) {
                        boolean passed = value != null && value.contains("before");
                        cleanup.run();
                        toast(passed ? "document-start 自测通过" : "document-start 自测失败，已保持安全降级");
                    }
                });
            }
        });
        try {
            registration[0] = WebViewCompat.addDocumentStartJavaScript(
                    probe, "window.__medianCompatToken=" + JSONObject.quote(token) + ";",
                    "https://median-compat.invalid");
            String html = "<!doctype html><meta charset=utf-8><script>"
                    + "window.__medianCompatOrder=(window.__medianCompatToken===" + JSONObject.quote(token)
                    + ")?'before':'after';</script>compat";
            probe.loadDataWithBaseURL("https://median-compat.invalid/", html, "text/html", "UTF-8", null);
            uiHandler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (finished[0]) return;
                    cleanup.run();
                    toast("document-start 自测超时，已保持安全降级");
                }
            }, 6000L);
        } catch (RuntimeException error) {
            cleanup.run();
            toast("自测无法启动：" + safeMessage(error));
        }
    }

    private String appVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Exception ignored) { return ""; }
    }

    private void showPrivacyNotice() {
        new AlertDialog.Builder(this)
                .setTitle("隐私与数据处理")
                .setMessage("Median 默认在设备本地保存书签、历史记录、标签会话、网站设置、下载记录、用户脚本和过滤规则。应用本身不包含广告、分析或遥测 SDK，也不运营同步服务器。\n\n访问网页时，目标网站、其内容提供方、所选搜索引擎、翻译服务或过滤订阅源会接收正常网络请求。网站可按其自身政策使用 Cookie 和其他存储。\n\n摄像头、麦克风和位置仅在 HTTPS 网站主动请求、当前页面来源匹配且你授予 Android 权限后提供。密码库使用 Android Keystore 与 AES-GCM；完整备份由你设置的密码加密。\n\n用户脚本属于第三方代码。Median 会显示其匹配范围和权限，并限制原生网络范围，但你仍应只安装可信脚本。清除浏览数据或卸载应用会删除相应本地数据；导出的文件由你自行保管。完整政策见 Google Play 商店页面或项目仓库中的 PRIVACY_POLICY.md。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void setSearchEngine(String engine) {
        if (searchEngines == null || !searchEngines.contains(engine)) engine = "google";
        searchEngine = engine;
        prefs.edit().putString("search_engine", searchEngine).apply();
        cachedHomeKey = "";
    }

    private void applyPerformanceMode() {
        for (BrowserTab tab : tabs) if (tab.liveView != null) applyPerformanceMode(tab.liveView);
        enforceHotWebViewLimit();
    }

    private void applyPerformanceMode(WebView target) {
        if (target == null) return;
        WebSettings settings = target.getSettings();
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setOffscreenPreRaster(false);
        try {
            // Only the visible renderer stays IMPORTANT. Hot background tabs remain
            // restorable but cannot compete at the same OOM/CPU scheduling class.
            boolean active = target == webView;
            target.setRendererPriorityPolicy(active ? WebView.RENDERER_PRIORITY_IMPORTANT : WebView.RENDERER_PRIORITY_BOUND, !active);
        } catch (RuntimeException ignored) {
        }
    }

    private void applyDesktopMode() {
        for (BrowserTab tab : tabs) if (tab.liveView != null) applyDesktopMode(tab.liveView);
    }

    private void applyDesktopMode(WebView target) {
        if (target == null) return;
        WebSettings settings = target.getSettings();
        SiteSettingsStore.SiteSettings site = siteSettingsStore == null ? new SiteSettingsStore.SiteSettings() : siteSettingsStore.forHost(pageHostFor(target));
        int desktop = site.get(SiteSettingsStore.DESKTOP);
        boolean enabled = desktop == SiteSettingsStore.ALLOW || (desktop == SiteSettingsStore.INHERIT && desktopMode);
        String mobile = mobileUserAgents.get(target);
        if (mobile == null || mobile.length() == 0) {
            mobile = WebViewPolicy.mobileUserAgent(settings.getUserAgentString());
            mobileUserAgents.put(target, mobile);
        }
        String desiredUserAgent = mobile;
        if (enabled) {
            desiredUserAgent = desktopUserAgent(mobile);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
        } else {
            settings.setUseWideViewPort(false);
            settings.setLoadWithOverviewMode(false);
        }
        if (!desiredUserAgent.equals(settings.getUserAgentString())) settings.setUserAgentString(desiredUserAgent);
    }

    private String desktopUserAgent(String mobile) {
        String chrome = "Chrome/120.0.0.0";
        int start = mobile == null ? -1 : mobile.indexOf("Chrome/");
        if (start >= 0) {
            int end = mobile.indexOf(' ', start);
            chrome = mobile.substring(start, end < 0 ? mobile.length() : end);
        }
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " + chrome + " Safari/537.36";
    }

    private void applySiteSettings(WebView target, String host) {
        if (target == null) return;
        String normalized = host == null ? "" : host;
        pageHosts.put(target, normalized);
        if (target == webView) currentPageHost = normalized;
        adBlockActiveByView.put(target, Boolean.valueOf(isAdBlockActiveForHost(normalized)));
        SiteSettingsStore.SiteSettings site = siteSettingsStore == null ? new SiteSettingsStore.SiteSettings() : siteSettingsStore.forHost(normalized);
        String configKey = normalized + '|' + site.packedStates() +
                '|' + desktopMode + '|' + nightMode + '|' + acceptThirdPartyCookies;
        if (configKey.equals(appliedSiteSettings.get(target))) return;
        WebSettings settings = target.getSettings();
        boolean compatibility = site.compatibilityMode();
        settings.setMixedContentMode(compatibility ? WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE : WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        int javascript = site.get(SiteSettingsStore.JAVASCRIPT);
        int images = site.get(SiteSettingsStore.IMAGES);
        int popups = site.get(SiteSettingsStore.POPUPS);
        int autoplay = site.get(SiteSettingsStore.AUTOPLAY);
        int thirdPartyCookies = site.get(SiteSettingsStore.THIRD_PARTY_COOKIES);
        settings.setJavaScriptEnabled(javascript != SiteSettingsStore.BLOCK);
        settings.setLoadsImagesAutomatically(images != SiteSettingsStore.BLOCK);
        settings.setBlockNetworkImage(images == SiteSettingsStore.BLOCK);
        settings.setTextZoom(site.textZoom());
        settings.setSupportMultipleWindows(compatibility || popups != SiteSettingsStore.BLOCK);
        settings.setJavaScriptCanOpenWindowsAutomatically(popups == SiteSettingsStore.ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(autoplay != SiteSettingsStore.ALLOW);
        boolean allowThirdParty = compatibility || thirdPartyCookies == SiteSettingsStore.ALLOW ||
                (thirdPartyCookies == SiteSettingsStore.INHERIT && acceptThirdPartyCookies);
        CookieManager.getInstance().setAcceptThirdPartyCookies(target, allowThirdParty);
        applyDesktopMode(target);
        applyDarkMode(target);
        appliedSiteSettings.put(target, configKey);
    }

    private boolean shouldCleanTracking(String host) {
        SiteSettingsStore.SiteSettings site = siteSettingsStore == null ? null : siteSettingsStore.forHost(host);
        if (site != null && site.compatibilityMode()) return false;
        int tracking = site == null ? SiteSettingsStore.INHERIT : site.get(SiteSettingsStore.TRACKING_PROTECTION);
        return tracking == SiteSettingsStore.ALLOW ||
                (tracking == SiteSettingsStore.INHERIT && cleanTrackingParameters);
    }

    private void applyCookiePolicyToAll() {
        for (BrowserTab tab : tabs) {
            if (tab.liveView == null) continue;
            appliedSiteSettings.remove(tab.liveView);
            applySiteSettings(tab.liveView, pageHostFor(tab.liveView));
        }
        scheduleCookieFlush();
    }

    private void applyDarkMode() {
        applyThemeSignal();
        for (BrowserTab tab : tabs) if (tab.liveView != null) applyDarkMode(tab.liveView);
        applyChromeTheme();
        refreshActiveSheetTheme();
    }

    private void applyThemeSignal() {
        try { getTheme().applyStyle(nightMode ? R.style.AppThemeSignalDark : R.style.AppThemeSignalLight, true); }
        catch (RuntimeException ignored) {}
    }

    private void applyChromeTheme() {
        if (browserChrome == null || topBar == null || bottomBar == null || addressPill == null || addressBar == null) return;
        int background = nightMode ? Color.rgb(27, 29, 32) : WHITE;
        int surface = nightMode ? Color.rgb(43, 46, 51) : SURFACE;
        int foreground = nightMode ? Color.rgb(232, 234, 237) : TEXT;
        int hint = nightMode ? Color.rgb(154, 160, 166) : Color.rgb(128, 134, 139);
        if (rootFrame != null) rootFrame.setBackgroundColor(background);
        browserChrome.setBackgroundColor(background);
        topBar.setBackgroundColor(background);
        bottomBar.setBackgroundColor(background);
        addressPill.setBackground(roundRect(surface, 22));
        addressBar.setTextColor(foreground);
        addressBar.setHintTextColor(hint);
        tintIconTree(topBar, foreground);
        tintIconTree(bottomBar, foreground);
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int appearance = nightMode ? 0 : (android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                controller.setSystemBarsAppearance(appearance,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            window.setStatusBarColor(background);
            window.setNavigationBarColor(background);
        }
        if (Build.VERSION.SDK_INT < 30) {
            int flags = 0;
            if (!nightMode) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void refreshActiveSheetTheme() {
        if (activeOverlayPanel != null && activeOverlaySheet) themeSheetTree(activeOverlayPanel);
    }

    private void themeSheetTree(View view) {
        if (view == null) return;
        int surface = nightMode ? Color.rgb(35, 38, 42) : WHITE;
        int primary = nightMode ? Color.rgb(232, 234, 237) : TEXT;
        int muted = nightMode ? Color.rgb(154, 160, 166) : MUTED;
        int accent = nightMode ? Color.rgb(110, 157, 216) : BLUE;
        Object tag = view.getTag();
        if (SHEET_TAG_SURFACE.equals(tag)) view.setBackground(roundRect(surface, 24));
        else if (SHEET_TAG_HANDLE.equals(tag))
            view.setBackground(roundRect(Color.argb(nightMode ? 80 : 54, 95, 99, 104), 2));
        if (view instanceof BrowserIconView) ((BrowserIconView) view).setTintColor(primary);
        if (view instanceof TextView) {
            if (SHEET_TAG_PRIMARY.equals(tag)) ((TextView) view).setTextColor(primary);
            else if (SHEET_TAG_MUTED.equals(tag)) ((TextView) view).setTextColor(muted);
            else if (SHEET_TAG_ACCENT.equals(tag)) ((TextView) view).setTextColor(accent);
        }
        if (view instanceof Switch) {
            Switch toggle = (Switch) view;
            int[][] states = new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} };
            toggle.setThumbTintList(new ColorStateList(states, new int[] {
                    accent, nightMode ? Color.rgb(189, 193, 198) : Color.rgb(117, 117, 117)
            }));
            toggle.setTrackTintList(new ColorStateList(states, new int[] {
                    Color.argb(118, 26, 115, 232),
                    nightMode ? Color.rgb(80, 84, 89) : Color.rgb(189, 193, 198)
            }));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) themeSheetTree(group.getChildAt(i));
        }
    }

    private void tintIconTree(View view, int color) {
        if (view instanceof BrowserIconView) ((BrowserIconView) view).setTintColor(color);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) tintIconTree(group.getChildAt(i), color);
        }
    }

    private void applyDarkMode(WebView target) {
        if (target == null) return;
        boolean enabled = darkModeEnabled(target);
        target.setBackgroundColor(enabled ? Color.rgb(17, 19, 21) : WHITE);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            try { WebSettingsCompat.setAlgorithmicDarkeningAllowed(target.getSettings(), enabled); }
            catch (RuntimeException ignored) {}
        }
        target.postInvalidateOnAnimation();
    }

    private boolean darkModeEnabled(WebView target) {
        String host = pageHostFor(target);
        SiteSettingsStore.SiteSettings site = siteSettingsStore == null
                ? new SiteSettingsStore.SiteSettings() : siteSettingsStore.forHost(host);
        int dark = site.get(SiteSettingsStore.DARK);
        boolean enabled = dark == SiteSettingsStore.ALLOW || (dark == SiteSettingsStore.INHERIT && nightMode);
        return enabled && !isHomeUrl(target == null ? null : target.getUrl());
    }

    private boolean isMediaCompatibilityHost(String host) {
        if (host == null) return false;
        String value = host.toLowerCase(Locale.US);
        return value.equals("youtube.com") || value.endsWith(".youtube.com") || value.equals("youtu.be") ||
                value.equals("youtube-nocookie.com") || value.endsWith(".youtube-nocookie.com");
    }

    private boolean isNetworkPage(String url) {
        return url != null && !isHomeUrl(url) &&
                (url.startsWith("https://") || url.startsWith("http://"));
    }

    private boolean validSearchTemplate(String value) {
        return SearchEngineStore.validTemplate(value);
    }

    private static boolean sameSecureOrigin(String origin, String pageUrl) {
        try {
            URL expected = NetworkSecurity.parseHttpsUrl(origin);
            URL current = NetworkSecurity.parseHttpsUrl(pageUrl);
            return NetworkSecurity.sameOrigin(expected, current);
        } catch (Exception ignored) { return false; }
    }

    private boolean shouldUpgradeHttp(String url) {
        if (!httpsOnly || url == null || !url.startsWith("http://")) return false;
        String host = hostOf(url);
        SiteSettingsStore.SiteSettings site = siteSettingsStore == null ? null : siteSettingsStore.forHost(host);
        if (site != null && site.compatibilityMode()) return false;
        if (host.length() == 0 || "localhost".equals(host) || host.endsWith(".local") || host.endsWith(".onion")) return false;
        return !host.matches("^(10\\.|127\\.|169\\.254\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.).*");
    }

    private void confirmExternalNavigation(final String url) {
        String scheme;
        try { scheme = Uri.parse(url).getScheme(); } catch (Exception e) { scheme = "外部应用"; }
        new AlertDialog.Builder(this).setTitle("打开外部应用？")
                .setMessage("网页请求打开 “" + (scheme == null ? "未知" : scheme) + "” 链接。仅在你信任当前网站时继续。")
                .setPositiveButton("继续", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { startExternalNavigation(url); }
                }).setNegativeButton("取消", null).show();
    }

    private void startExternalNavigation(String url) {
        try {
            Intent external;
            if (url.startsWith("intent:")) {
                Intent parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                if (parsed.getData() == null) throw new IllegalArgumentException("外部地址无效");
                external = new Intent(Intent.ACTION_VIEW, parsed.getData());
            } else external = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            external.addCategory(Intent.CATEGORY_BROWSABLE);
            external.setComponent(null);
            external.setPackage(null);
            external.setSelector(null);
            int unsafeGrants = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
            external.setFlags(external.getFlags() & ~unsafeGrants);
            external.setClipData(null);
            startActivity(external);
        } catch (Exception e) { toast("无法打开此链接"); }
    }

    private void handleWebPermissionRequest(final WebView source, final PermissionRequest request) {
        if (request == null || source == null || source != webView || request.getOrigin() == null ||
                !sameSecureOrigin(request.getOrigin().toString(), source.getUrl())) {
            if (request != null) request.deny();
            return;
        }
        if (pendingPermissionRequest != null) pendingPermissionRequest.deny();
        final ArrayList<String> webResources = new ArrayList<String>();
        final ArrayList<String> androidPermissions = new ArrayList<String>();
        StringBuilder names = new StringBuilder();
        boolean autoGrant = true;
        String originHost = request.getOrigin().getHost();
        if (originHost == null || originHost.length() == 0) { request.deny(); return; }
        final SiteSettingsStore.SiteSettings site = siteSettingsStore.forHost(originHost);
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                int camera = site.get(SiteSettingsStore.CAMERA);
                if (camera == SiteSettingsStore.BLOCK) continue;
                if (camera != SiteSettingsStore.ALLOW) autoGrant = false;
                webResources.add(resource);
                if (!androidPermissions.contains(Manifest.permission.CAMERA)) androidPermissions.add(Manifest.permission.CAMERA);
                if (names.length() > 0) names.append("、");
                names.append("摄像头");
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                int microphone = site.get(SiteSettingsStore.MICROPHONE);
                if (microphone == SiteSettingsStore.BLOCK) continue;
                if (microphone != SiteSettingsStore.ALLOW) autoGrant = false;
                webResources.add(resource);
                if (!androidPermissions.contains(Manifest.permission.RECORD_AUDIO)) androidPermissions.add(Manifest.permission.RECORD_AUDIO);
                if (names.length() > 0) names.append("、");
                names.append("麦克风");
            }
        }
        if (webResources.size() == 0) { request.deny(); return; }
        final String expectedOrigin = request.getOrigin().toString();
        if (autoGrant) {
            grantWebPermission(source, expectedOrigin, request, webResources, androidPermissions);
            return;
        }
        DenyDialog deny = new DenyDialog(request, null);
        new AlertDialog.Builder(this).setTitle("允许网站使用" + names + "？")
                .setMessage((originHost == null ? "当前网站" : originHost) + "\n\n只允许你正在使用并信任的网站。此次授权会在页面关闭后失效。")
                .setPositiveButton("仅此次允许", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        grantWebPermission(source, expectedOrigin, request, webResources, androidPermissions);
                    }
                }).setNegativeButton("阻止", deny).setOnCancelListener(deny).show();
    }

    private void grantWebPermission(WebView source, String origin, PermissionRequest request,
                                    List<String> webResources, List<String> androidPermissions) {
        if (origin == null || source != webView || !sameSecureOrigin(origin, source.getUrl())) { request.deny(); return; }
        ArrayList<String> missing = new ArrayList<String>();
        for (String permission : androidPermissions) if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
        if (missing.size() == 0) request.grant(webResources.toArray(new String[webResources.size()]));
        else {
            pendingPermissionRequest = request;
            pendingPermissionView = source;
            pendingPermissionOrigin = origin;
            pendingWebPermissionResources = webResources.toArray(new String[webResources.size()]);
            requestPermissions(missing.toArray(new String[missing.size()]), WEB_PERMISSION_REQUEST);
        }
    }

    private void handleGeolocationRequest(final WebView source, final String origin, final GeolocationPermissions.Callback callback) {
        if (callback == null) return;
        final String host = hostOf(origin);
        if (source == null || source != webView || !sameSecureOrigin(origin, source.getUrl())) {
            callback.invoke(origin, false, false);
            return;
        }
        SiteSettingsStore.SiteSettings site = siteSettingsStore.forHost(host);
        int location = site.get(SiteSettingsStore.LOCATION);
        if (location == SiteSettingsStore.BLOCK) { callback.invoke(origin, false, false); return; }
        if (location == SiteSettingsStore.ALLOW) {
            grantGeolocation(source, host, origin, callback);
            return;
        }
        DenyDialog deny = new DenyDialog(callback, origin);
        new AlertDialog.Builder(this).setTitle("允许网站获取位置？")
                .setMessage((host.length() == 0 ? "当前网站" : host) + "\n\nMedian 不会保留此授权；网站仍可能保存你提交的位置。")
                .setPositiveButton("仅此次允许", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        grantGeolocation(source, host, origin, callback);
                    }
                }).setNegativeButton("阻止", deny).setOnCancelListener(deny).show();
    }

    private void grantGeolocation(WebView source, String host, String origin, GeolocationPermissions.Callback callback) {
        if (source != webView || !sameSecureOrigin(origin, source.getUrl())) { callback.invoke(origin, false, false); return; }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            callback.invoke(origin, true, false);
        } else {
            pendingGeolocationCallback = callback;
            pendingGeolocationOrigin = origin;
            pendingGeolocationView = source;
            requestPermissions(new String[] { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION },
                    GEOLOCATION_PERMISSION_REQUEST);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0;
        for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) granted = false;
        if (requestCode == WEB_PERMISSION_REQUEST && pendingPermissionRequest != null) {
            if (granted && pendingWebPermissionResources != null && pendingPermissionView == webView &&
                    pendingPermissionOrigin != null && sameSecureOrigin(pendingPermissionOrigin, webView.getUrl()))
                pendingPermissionRequest.grant(pendingWebPermissionResources);
            else pendingPermissionRequest.deny();
            pendingPermissionRequest = null;
            pendingWebPermissionResources = null;
            pendingPermissionView = null;
            pendingPermissionOrigin = null;
        } else if (requestCode == GEOLOCATION_PERMISSION_REQUEST && pendingGeolocationCallback != null) {
            boolean locationGranted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            pendingGeolocationCallback.invoke(pendingGeolocationOrigin,
                    locationGranted && pendingGeolocationView == webView &&
                            sameSecureOrigin(pendingGeolocationOrigin, webView.getUrl()), false);
            pendingGeolocationCallback = null;
            pendingGeolocationOrigin = null;
            pendingGeolocationView = null;
        }
    }

    private void updateChrome() {
        requestChromeUpdate();
    }

    private void requestChromeUpdate() {
        if (chromeUpdatePending || webView == null) return;
        chromeUpdatePending = true;
        Runnable update = new Runnable() {
            @Override public void run() {
                chromeUpdatePending = false;
                performChromeUpdate();
            }
        };
        rootFrame.postOnAnimation(update);
    }

    private void performChromeUpdate() {
        if (webView == null) return;
        updateAddressBar();
        boolean canBack = webView.canGoBack();
        boolean canForward = webView.canGoForward();
        if (renderedBackEnabled == null || renderedBackEnabled.booleanValue() != canBack) {
            renderedBackEnabled = Boolean.valueOf(canBack);
            backButton.setEnabled(canBack);
            backButton.setAlpha(canBack ? 1f : .32f);
        }
        if (renderedForwardEnabled == null || renderedForwardEnabled.booleanValue() != canForward) {
            renderedForwardEnabled = Boolean.valueOf(canForward);
            forwardButton.setEnabled(canForward);
            forwardButton.setAlpha(canForward ? 1f : .32f);
        }
        int tabCount = tabs.size();
        if (renderedTabCount == null || renderedTabCount.intValue() != tabCount) {
            renderedTabCount = Integer.valueOf(tabCount);
            tabButton.setCount(tabCount);
        }
        boolean shieldActive = isAdBlockActiveForHost(currentPageHost) &&
                !(MODE_PERFORMANCE.equals(performanceMode) && performanceNetworkDirect);
        if (renderedShieldActive == null || renderedShieldActive.booleanValue() != shieldActive) {
            renderedShieldActive = Boolean.valueOf(shieldActive);
            shieldButton.setActive(shieldActive);
        }
    }

    private void updateAddressBar() {
        if (addressBar == null || addressBar.hasFocus()) return;
        String url = currentPageUrl;
        String display = "";
        if (url != null && !isHomeUrl(url) && !"about:blank".equals(url)) {
            String host = currentPageHost;
            if (host != null && host.length() > 0) {
                Uri uri = currentUri();
                String scheme = uri == null ? "" : uri.getScheme();
                display = (scheme == null || scheme.length() == 0 ? "" : scheme.toLowerCase(Locale.US) + "://") + host;
            } else {
                display = url;
            }
        }
        if (!display.equals(renderedAddress) || !display.contentEquals(addressBar.getText())) {
            renderedAddress = display;
            addressBar.setText(display);
        }
    }

    private void scheduleProgressUpdate(int progress) {
        pendingProgress = progress;
        if (progressUpdatePending || webView == null) return;
        progressUpdatePending = true;
        Runnable update = new Runnable() {
            @Override public void run() {
                progressUpdatePending = false;
                int value = pendingProgress;
                if (value >= 100) {
                    if (renderedProgress != 100) Motion.animateProgress(progressBar, renderedProgress, 100, reduceMotion());
                    renderedProgress = 100;
                    if (progressBar.getVisibility() != View.GONE) {
                        progressBar.animate().cancel();
                        progressBar.animate().alpha(0f).setDuration(reduceMotion() ? 70L : 130L).withEndAction(new Runnable() {
                            @Override public void run() { progressBar.setVisibility(View.GONE); progressBar.setAlpha(1f); }
                        }).start();
                    }
                    return;
                }
                if (renderedProgress >= 0 && renderedProgress < 100 && Math.abs(value - renderedProgress) < progressStep()) return;
                int previous = renderedProgress;
                renderedProgress = value;
                if (progressBar.getVisibility() != View.VISIBLE) {
                    progressBar.animate().cancel();
                    progressBar.setAlpha(0f);
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.animate().alpha(1f).setDuration(100L).start();
                }
                Motion.animateProgress(progressBar, previous, value, reduceMotion());
            }
        };
        rootFrame.postOnAnimation(update);
    }

    private Uri currentUri() {
        try {
            String url = currentPageUrl;
            return url == null ? null : Uri.parse(url);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String currentHost() {
        return currentPageHost == null ? "" : currentPageHost;
    }

    private boolean isCurrentPageCallback(WebView source, long sequence) {
        return !activityDestroyed && source != null && source == webView && sequence == navigationSequence;
    }

    private String hostOf(String url) {
        try {
            if (url == null) return "";
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if ("content".equalsIgnoreCase(uri.getScheme()) && OfflineContentProvider.AUTHORITY.equals(uri.getAuthority())) return "";
            return host == null || "median.invalid".equalsIgnoreCase(host) ? "" : host.toLowerCase(Locale.US);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private boolean isHomeUrl(String url) {
        return UrlCleaner.isInternalPage(url, "median.invalid");
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int selectableBorderless() {
        TypedValue out = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, out, true);
        return out.resourceId;
    }

    private int selectableBounded() {
        TypedValue out = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
        return out.resourceId;
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String safeMessage(Exception e) {
        if (e == null) return "未知错误";
        String message = e.getMessage();
        return message == null || message.length() == 0 ? e.getClass().getSimpleName() : message;
    }

    private void writeBackup(final Uri uri) {
        if (uri == null || scriptExecutor == null || scriptExecutor.isShutdown()) return;
        String prepared;
        try {
            JSONObject root = new JSONObject(dataStore.exportJson());
            root.put("bookmarkFolders", new JSONObject(bookmarkFolders.exportJson()));
            prepared = root.toString();
        } catch (Exception error) { toast("无法整理书签备份"); return; }
        final String json = prepared;
        executeTask(scriptExecutor, new Runnable() {
            @Override public void run() {
                OutputStream output = null;
                Exception failure = null;
                try {
                    output = getContentResolver().openOutputStream(uri, "w");
                    if (output == null) throw new IllegalStateException("无法打开目标文件");
                    output.write(json.getBytes("UTF-8"));
                    output.flush();
                } catch (Exception e) { failure = e; }
                finally { NetworkSecurity.closeQuietly(output); }
                final Exception error = failure;
                uiHandler.post(new Runnable() { @Override public void run() { toast(error == null ? "书签备份已导出" : "导出失败：" + safeMessage(error)); } });
            }
        });
    }

    private void readBackup(final Uri uri) {
        if (uri == null || scriptExecutor == null || scriptExecutor.isShutdown()) return;
        toast("正在检查备份…");
        executeTask(scriptExecutor, new Runnable() {
            @Override public void run() {
                InputStream input = null;
                int imported = 0;
                Exception failure = null;
                try {
                    input = getContentResolver().openInputStream(uri);
                    if (input == null) throw new IllegalStateException("无法打开备份文件");
                    ByteArrayOutputStream output = new ByteArrayOutputStream(32 * 1024);
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (output.size() + read > 4 * 1024 * 1024) throw new IllegalArgumentException("备份超过 4 MB");
                        output.write(buffer, 0, read);
                    }
                    String raw = output.toString("UTF-8");
                    imported = dataStore.importJson(raw);
                    JSONObject root = new JSONObject(raw);
                    JSONObject folders = root.optJSONObject("bookmarkFolders");
                    if (folders != null) bookmarkFolders.mergeJson(folders.toString());
                } catch (Exception e) { failure = e; }
                finally { NetworkSecurity.closeQuietly(input); }
                final int count = imported;
                final Exception error = failure;
                uiHandler.post(new Runnable() { @Override public void run() {
                    if (error == null) refreshHomeAfterLibraryChange();
                    toast(error == null ? "已导入 " + count + " 个新书签" : "导入失败：" + safeMessage(error));
                } });
            }
        });
    }

    private void showFullBackupPassword(final Uri uri, final boolean exporting) {
        if (uri == null) return;
        final EditText password = new EditText(this);
        password.setHint(exporting ? "设置备份口令（至少 10 个字符）" : "输入备份口令");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText confirmation = new EditText(this);
        confirmation.setHint("再次输入备份口令");
        confirmation.setSingleLine(true);
        confirmation.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(18), 0, dp(18), 0);
        fields.addView(password);
        if (exporting) fields.addView(confirmation);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(exporting ? "加密完整备份" : "解密完整备份")
                .setMessage(exporting ? "包含书签、历史、标签、设置、脚本、过滤订阅和密码。离线页面及下载文件不包含在内。忘记口令将无法恢复。" :
                        "恢复会替换当前浏览数据、脚本、设置和密码。AES-GCM 校验失败时不会应用备份。")
                .setView(fields)
                .setPositiveButton(exporting ? "加密导出" : "解密恢复", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String first = password.getText().toString();
                        if (first.length() < 10 || first.length() > 256) { toast("备份口令需为 10–256 个字符"); return; }
                        if (exporting && !first.equals(confirmation.getText().toString())) { toast("两次输入的口令不一致"); return; }
                        if (exporting) createFullBackup(uri, first.toCharArray());
                        else restoreFullBackup(uri, first.toCharArray());
                    }
                }).setNegativeButton("取消", null).create();
        secureDialog(dialog);
        dialog.show();
    }

    private void createFullBackup(final Uri uri, final char[] password) {
        toast("正在加密完整备份…");
        services.passwords().exportJson(new PasswordVault.Callback<String>() {
            @Override public void onComplete(final String vaultJson, Exception error) {
                if (error != null) {
                    java.util.Arrays.fill(password, '\0');
                    toast("读取密码库失败：" + safeMessage(error));
                    return;
                }
                executeTask(scriptExecutor, new Runnable() {
                    @Override public void run() {
                        OutputStream output = null;
                        Exception failure = null;
                        try {
                            JSONObject root = new JSONObject();
                            root.put("format", "median-portable-state");
                            root.put("version", 1);
                            root.put("createdAt", System.currentTimeMillis());
                            root.put("browser", dataStore.exportPortable());
                            root.put("bookmarkFolders", new JSONObject(bookmarkFolders.exportJson()));
                            root.put("sites", new JSONObject(siteSettingsStore.exportJson()));
                            root.put("scripts", new JSONArray(scriptStore.exportJson()));
                            root.put("filters", new JSONArray(services.filters().exportJson()));
                            root.put("customFilters", prefs.getString("custom_filter_rules", ""));
                            root.put("settings", settingsSnapshot());
                            root.put("passwords", new JSONArray(vaultJson == null ? "[]" : vaultJson));
                            byte[] encoded = PortableBackupCodec.encrypt(root, password);
                            output = getContentResolver().openOutputStream(uri, "w");
                            if (output == null) throw new IllegalStateException("无法打开目标文件");
                            output.write(encoded);
                            output.flush();
                        } catch (Exception e) { failure = e; }
                        finally {
                            java.util.Arrays.fill(password, '\0');
                            if (output != null) try { output.close(); } catch (Exception ignored) {}
                        }
                        final Exception result = failure;
                        uiHandler.post(new Runnable() {
                            @Override public void run() { toast(result == null ? "加密完整备份已导出" : "完整备份失败：" + safeMessage(result)); }
                        });
                    }
                });
            }
        });
    }

    private JSONObject settingsSnapshot() throws Exception {
        JSONObject value = new JSONObject();
        value.put("adBlock", adBlockEnabled);
        value.put("desktop", desktopMode);
        value.put("night", nightMode);
        value.put("httpsOnly", httpsOnly);
        value.put("restoreTabs", restoreTabs);
        value.put("homeOpenMode", homeOpenMode());
        value.put("homeCustomUrl", normalizeConfiguredHomeUrl(prefs.getString("home_custom_url", "")));
        value.put("thirdPartyCookies", acceptThirdPartyCookies);
        value.put("searchEngine", searchEngine);
        List<SearchEngineStore.Engine> customEngines = searchEngines.customEngines();
        value.put("customSearch", customEngines.size() == 0 ? "" : customEngines.get(0).template);
        value.put("customSearchEngines", searchEngines.exportJson());
        value.put("performanceMode", performanceMode);
        value.put("networkDirect", performanceNetworkDirect);
        value.put("autoPip", autoPictureInPicture);
        value.put("cleanTracking", cleanTrackingParameters);
        value.put("passwordAutofill", passwordAutofillEnabled);
        value.put("passwordSavePrompts", passwordSavePromptsEnabled);
        value.put("siteExceptions", new JSONArray(siteExceptions));
        HomePageConfig home = homePageConfig();
        value.put("homeTitle", home.title);
        value.put("homeSubtitle", home.subtitle);
        value.put("homeLogoStyle", home.logoStyle);
        value.put("homeLogoCode", home.logoCode);
        value.put("homeLogoMode", home.logoMode);
        value.put("homeLogoLetterSpacing", home.logoLetterSpacing);
        value.put("homeLogoGradientAngle", home.logoGradientAngle);
        value.put("homeLogoFontSize", home.logoFontSize);
        value.put("homeLogoFontWeight", home.logoFontWeight);
        value.put("homeLogoImageWidth", home.logoImageWidth);
        value.put("homeLogoImageHeight", home.logoImageHeight);
        value.put("homeLogoImageRadius", home.logoImageRadius);
        value.put("homeAccent", home.accent);
        value.put("homeWallpaperDim", home.wallpaperDim);
        value.put("homeWallpaperBlur", home.wallpaperBlur);
        value.put("homeWallpaperFit", home.wallpaperFit);
        value.put("homeSearchStyle", home.searchStyle);
        value.put("homeLayout", home.layout);
        value.put("homeTileShape", home.tileShape);
        value.put("homeShortcutColumns", home.shortcutColumns);
        value.put("homeShowSearch", home.showSearch);
        value.put("homeShowEngines", home.showEngines);
        value.put("homeShowShortcuts", home.showShortcuts);
        value.put("homeShowCorner", home.showCornerBrand);
        value.put("homeShowClock", home.showClock);
        value.put("homeCustomCss", home.customCss);
        value.put("homeCustomHtmlEnabled", home.customHtmlEnabled);
        value.put("homeCustomHtml", CustomHomeHtml.clean(prefs.getString("home_custom_html", "")));
        value.put("homeCustomHtmlVersion", home.customHtmlVersion);
        return value;
    }

    private void restoreFullBackup(final Uri uri, final char[] password) {
        if (scriptExecutor == null || scriptExecutor.isShutdown()) return;
        toast("正在验证并恢复完整备份…");
        executeTask(scriptExecutor, new Runnable() {
            @Override public void run() {
                InputStream input = null;
                Exception failure = null;
                String passwords = null;
                int bookmarks = 0;
                try {
                    input = getContentResolver().openInputStream(uri);
                    if (input == null) throw new IllegalStateException("无法打开备份文件");
                    byte[] encoded = NetworkSecurity.readBounded(input, 20 * 1024 * 1024, "备份超过 20 MB");
                    input = null;
                    JSONObject root = PortableBackupCodec.decrypt(encoded, password);
                    if (!"median-portable-state".equals(root.optString("format")) || root.optInt("version", 0) != 1)
                        throw new IllegalArgumentException("备份内容版本不受支持");
                    JSONObject browser = root.getJSONObject("browser");
                    JSONObject sites = root.getJSONObject("sites");
                    JSONArray scripts = root.getJSONArray("scripts");
                    JSONArray filters = root.getJSONArray("filters");
                    JSONArray vault = root.getJSONArray("passwords");
                    JSONObject settings = root.getJSONObject("settings");
                    if (scripts.length() > 128 || filters.length() > 32 || vault.length() > 500)
                        throw new IllegalArgumentException("备份条目超过安全限制");
                    bookmarks = dataStore.importPortable(browser);
                    JSONObject folderState = root.optJSONObject("bookmarkFolders");
                    if (folderState != null) bookmarkFolders.importJson(folderState.toString());
                    siteSettingsStore.importJson(sites.toString());
                    scriptStore.importJson(scripts.toString());
                    services.filters().importJson(filters.toString());
                    String custom = root.optString("customFilters", "");
                    if (custom.length() > 256 * 1024) throw new IllegalArgumentException("自定义规则超过限制");
                    prefs.edit().putString("custom_filter_rules", custom).commit();
                    applySettingsSnapshot(settings);
                    passwords = vault.toString();
                } catch (Exception e) { failure = e; }
                finally {
                    java.util.Arrays.fill(password, '\0');
                    if (input != null) try { input.close(); } catch (Exception ignored) {}
                }
                final Exception error = failure;
                final String vaultJson = passwords;
                final int bookmarkCount = bookmarks;
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (error != null) { toast("恢复失败：" + safeMessage(error)); return; }
                        services.passwords().importJson(vaultJson, new PasswordVault.Callback<Integer>() {
                            @Override public void onComplete(Integer count, Exception vaultError) {
                                if (vaultError != null) { toast("其他数据已恢复，但密码恢复失败：" + safeMessage(vaultError)); return; }
                                finishFullRestore(bookmarkCount, count == null ? 0 : count.intValue());
                            }
                        });
                    }
                });
            }
        });
    }

    private void applySettingsSnapshot(JSONObject value) throws Exception {
        String restoredMode = value.optString("performanceMode", MODE_STANDARD);
        if (!MODE_PERFORMANCE.equals(restoredMode) && !MODE_POWER_SAVE.equals(restoredMode)) restoredMode = MODE_STANDARD;
        String restoredSearch = value.optString("searchEngine", "google");
        String restoredCustomSearch = value.optString("customSearch", "").trim();
        if (!validSearchTemplate(restoredCustomSearch)) restoredCustomSearch = "";
        JSONArray restoredCustomEngines = value.optJSONArray("customSearchEngines");
        boolean legacyRestoreTabs = value.optBoolean("restoreTabs", true);
        String restoredOpenMode = HomeOpenPolicy.normalize(value.optString("homeOpenMode", ""), legacyRestoreTabs);
        String restoredHomeUrl = normalizeConfiguredHomeUrl(value.optString("homeCustomUrl", ""));
        if (HomeOpenPolicy.OPEN_CUSTOM_URL.equals(restoredOpenMode) && restoredHomeUrl.length() == 0)
            restoredOpenMode = HomeOpenPolicy.OPEN_HOME;
        String restoredCustomHtml = CustomHomeHtml.clean(value.optString("homeCustomHtml", ""));
        boolean restoredCustomHtmlEnabled = value.optBoolean("homeCustomHtmlEnabled", false) &&
                CustomHomeHtml.valid(restoredCustomHtml);
        long restoredCustomHtmlVersion = Math.max(0L, value.optLong("homeCustomHtmlVersion", 0L));
        String restoredCustomCss = CustomHomeCss.clean(value.optString("homeCustomCss", ""));
        if (!CustomHomeCss.valid(restoredCustomCss)) restoredCustomCss = "";
        String restoredLogoMode = value.optString("homeLogoMode", "text");
        if (!"text".equals(restoredLogoMode) && !"image".equals(restoredLogoMode) && !"none".equals(restoredLogoMode))
            restoredLogoMode = "text";
        boolean restoredKeepLast = HomeOpenPolicy.KEEP_LAST.equals(restoredOpenMode);
        HashSet<String> exceptions = new HashSet<String>();
        JSONArray array = value.optJSONArray("siteExceptions");
        if (array != null) for (int i = 0; i < array.length() && exceptions.size() < 500; i++) {
            String host = array.optString(i, "").trim().toLowerCase(Locale.US);
            if (host.matches("[a-z0-9._-]{1,253}")) exceptions.add(host);
        }
        HomePageConfig restoredHome = HomePageConfig.createPersonalized(
                value.optString("homeTitle", HomePageConfig.DEFAULT_TITLE),
                value.optString("homeSubtitle", ""), value.optString("homeLogoStyle", "median"),
                value.optString("homeLogoCode", ""), value.optInt("homeLogoLetterSpacing", 0),
                value.optInt("homeLogoGradientAngle", 90), value.optString("homeAccent", "blue"),
                value.optInt("homeWallpaperDim", 28), value.optInt("homeWallpaperBlur", 0),
                value.optString("homeWallpaperFit", "cover"), value.optString("homeSearchStyle", "solid"),
                value.optString("homeLayout", "center"), value.optString("homeTileShape", "rounded"),
                value.optInt("homeShortcutColumns", 4), value.optBoolean("homeShowSearch", true),
                value.optBoolean("homeShowEngines", true), value.optBoolean("homeShowShortcuts", true),
                value.optBoolean("homeShowCorner", true), value.optBoolean("homeShowClock", false),
                restoredCustomHtmlEnabled, false, false, restoredCustomHtmlVersion, 0L, 0L,
                restoredLogoMode, value.optInt("homeLogoFontSize", 47),
                value.optInt("homeLogoFontWeight", 720), value.optInt("homeLogoImageWidth", 132),
                value.optInt("homeLogoImageHeight", 96), value.optInt("homeLogoImageRadius", 0),
                restoredCustomCss);
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean("adblock", value.optBoolean("adBlock", true))
                .putBoolean("desktop", value.optBoolean("desktop", false))
                .putBoolean("night_mode", value.optBoolean("night", false))
                .putBoolean("https_only", value.optBoolean("httpsOnly", true))
                .putBoolean("restore_tabs", restoredKeepLast)
                .putString("home_open_mode", restoredOpenMode)
                .putString("home_custom_url", restoredHomeUrl)
                .putBoolean("accept_third_party_cookies", value.optBoolean("thirdPartyCookies", false))
                .putString("search_engine", restoredSearch)
                .putString("custom_search_template", restoredCustomSearch)
                .putString("performance_mode", restoredMode)
                .putBoolean("performance_network_direct", value.optBoolean("networkDirect", false))
                .putBoolean("auto_picture_in_picture", value.optBoolean("autoPip", false))
                .putBoolean("clean_tracking_parameters", value.optBoolean("cleanTracking", true))
                .putBoolean("password_autofill", value.optBoolean("passwordAutofill", true))
                .putBoolean("password_save_prompts", value.optBoolean("passwordSavePrompts", true))
                .putStringSet("site_exceptions", exceptions)
                .putString("home_title", restoredHome.title)
                .putString("home_subtitle", restoredHome.subtitle)
                .putString("home_logo_style", restoredHome.logoStyle)
                .putString("home_logo_code", restoredHome.logoCode)
                .putString("home_logo_mode", restoredHome.logoMode)
                .putInt("home_logo_letter_spacing", restoredHome.logoLetterSpacing)
                .putInt("home_logo_gradient_angle", restoredHome.logoGradientAngle)
                .putInt("home_logo_font_size", restoredHome.logoFontSize)
                .putInt("home_logo_font_weight", restoredHome.logoFontWeight)
                .putInt("home_logo_image_width", restoredHome.logoImageWidth)
                .putInt("home_logo_image_height", restoredHome.logoImageHeight)
                .putInt("home_logo_image_radius", restoredHome.logoImageRadius)
                .putString("home_accent", restoredHome.accent)
                .putInt("home_wallpaper_dim", restoredHome.wallpaperDim)
                .putInt("home_wallpaper_blur", restoredHome.wallpaperBlur)
                .putString("home_wallpaper_fit", restoredHome.wallpaperFit)
                .putString("home_search_style", restoredHome.searchStyle)
                .putString("home_layout", restoredHome.layout)
                .putString("home_tile_shape", restoredHome.tileShape)
                .putInt("home_shortcut_columns", restoredHome.shortcutColumns)
                .putBoolean("home_show_search", restoredHome.showSearch)
                .putBoolean("home_show_engines", restoredHome.showEngines)
                .putBoolean("home_show_shortcuts", restoredHome.showShortcuts)
                .putBoolean("home_show_corner", restoredHome.showCornerBrand)
                .putBoolean("home_show_clock", restoredHome.showClock)
                .putString("home_custom_css", restoredHome.customCss)
                .putString("home_custom_html", restoredCustomHtml)
                .putBoolean("home_custom_html_enabled", restoredCustomHtmlEnabled)
                .putLong("home_custom_html_version", restoredCustomHtmlVersion);
        if (!editor.commit()) throw new IllegalStateException("无法保存恢复设置");
        searchEngines.replaceFromJson(restoredCustomEngines, restoredCustomSearch);
        if ("custom".equals(restoredSearch)) {
            List<SearchEngineStore.Engine> migrated = searchEngines.customEngines();
            restoredSearch = migrated.size() == 0 ? "google" : migrated.get(0).id;
        }
        if (!searchEngines.contains(restoredSearch)) restoredSearch = "google";
        prefs.edit().putString("search_engine", restoredSearch).apply();
        adBlockEnabled = value.optBoolean("adBlock", true);
        desktopMode = value.optBoolean("desktop", false);
        nightMode = value.optBoolean("night", false);
        httpsOnly = value.optBoolean("httpsOnly", true);
        restoreTabs = restoredKeepLast;
        acceptThirdPartyCookies = value.optBoolean("thirdPartyCookies", false);
        searchEngine = restoredSearch;
        performanceMode = restoredMode;
        performanceNetworkDirect = value.optBoolean("networkDirect", false);
        autoPictureInPicture = value.optBoolean("autoPip", false);
        cleanTrackingParameters = value.optBoolean("cleanTracking", true);
        passwordAutofillEnabled = value.optBoolean("passwordAutofill", true);
        passwordSavePromptsEnabled = value.optBoolean("passwordSavePrompts", true);
        siteExceptions = exceptions;
    }

    private void finishFullRestore(int bookmarks, int passwords) {
        appliedSiteSettings.clear();
        refreshUserScriptRegistrations(false);
        rebuildAdBlockRulesAsync(false);
        applyDarkMode();
        applyPerformanceMode();
        applyRestoredTabs();
        updateFilterSubscriptions(false);
        toast("完整备份已恢复：" + bookmarks + " 个书签、" + passwords + " 个密码条目");
    }

    private void applyRestoredTabs() {
        webContainer.removeAllViews();
        ArrayList<WebView> oldViews = new ArrayList<WebView>();
        for (BrowserTab tab : tabs) if (tab.liveView != null && !oldViews.contains(tab.liveView)) oldViews.add(tab.liveView);
        for (WebView old : oldViews) destroyWebView(old);
        tabs.clear();
        closedTabs.clear();
        if (restoreTabs) {
            List<BrowserDataStore.SessionTab> restored = dataStore.restoreSession();
            for (BrowserDataStore.SessionTab item : restored) {
                BrowserTab tab = new BrowserTab();
                tab.title = item.title;
                tab.url = item.url;
                tab.pinned = item.pinned;
                tabs.add(tab);
            }
        }
        if (tabs.size() == 0) {
            BrowserTab home = new BrowserTab();
            home.url = configuredHomeUrl();
            tabs.add(home);
        }
        currentTabIndex = restoreTabs ? Math.min(dataStore.restoredSessionIndex(), tabs.size() - 1) : 0;
        BrowserTab active = tabs.get(currentTabIndex);
        webView = createConfiguredWebView();
        active.liveView = webView;
        active.lastActiveAt = SystemClock.uptimeMillis();
        webContainer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        currentPageUrl = active.url;
        currentPageHost = hostOf(active.url);
        pageHosts.put(webView, currentPageHost);
        if (isHomeUrl(active.url)) showHome(); else loadNetworkUrl(webView, active.url);
        renderedTabCount = null;
        requestChromeUpdate();
    }

    private void persistSession() {
        if (dataStore == null) return;
        saveCurrentTab();
        ArrayList<BrowserDataStore.SessionTab> snapshot = new ArrayList<BrowserDataStore.SessionTab>();
        for (BrowserTab tab : tabs) snapshot.add(new BrowserDataStore.SessionTab(tab.title, tab.url, tab.pinned));
        dataStore.saveSession(snapshot, currentTabIndex);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (isExternalHttpIntent(intent)) loadInput(intent.getData().toString());
        else if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) toast("已拒绝非 HTTP(S) 外部地址");
    }

    private static boolean isExternalHttpIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return false;
        try {
            NetworkSecurity.parseHttpUrl(intent.getData().toString());
            return true;
        } catch (Exception ignored) { return false; }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle webViewState = captureWebViewState(webView);
        if (webViewState != null) outState.putBundle(STATE_WEBVIEW, webViewState);
        persistSession();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileChooserCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileChooserCallback.onReceiveValue(result);
            fileChooserCallback = null;
        } else if (requestCode == BACKUP_EXPORT_REQUEST && resultCode == RESULT_OK && data != null) {
            writeBackup(data.getData());
        } else if (requestCode == BACKUP_IMPORT_REQUEST && resultCode == RESULT_OK && data != null) {
            readBackup(data.getData());
        } else if (requestCode == FULL_BACKUP_EXPORT_REQUEST && resultCode == RESULT_OK && data != null) {
            final Uri uri = data.getData();
            withVaultUnlock(new Runnable() { @Override public void run() { showFullBackupPassword(uri, true); } });
        } else if (requestCode == FULL_BACKUP_IMPORT_REQUEST && resultCode == RESULT_OK && data != null) {
            final Uri uri = data.getData();
            withVaultUnlock(new Runnable() { @Override public void run() { showFullBackupPassword(uri, false); } });
        } else if (requestCode == HOME_WALLPAPER_REQUEST) {
            if (resultCode == RESULT_OK && data != null) importHomeImage(data.getData(), HomeImageStore.Kind.WALLPAPER);
            else continueHomeSection(pendingHomeImageReturnSection);
        } else if (requestCode == HOME_LOGO_REQUEST) {
            if (resultCode == RESULT_OK && data != null) importHomeImage(data.getData(), HomeImageStore.Kind.LOGO);
            else continueHomeSection(pendingHomeImageReturnSection);
        } else if (requestCode == VAULT_UNLOCK_REQUEST) {
            Runnable action = pendingVaultAction;
            pendingVaultAction = null;
            if (resultCode == RESULT_OK) {
                vaultUnlockedUntil = SystemClock.elapsedRealtime() + 120_000L;
                if (action != null) action.run();
            } else toast("密码库保持锁定");
        }
    }

    @Override
    public void onBackPressed() {
        handleBrowserBack();
    }

    private void handleBrowserBack() {
        if (overlayDismissInProgress) {
            return;
        } else if (activeOverlay != null) {
            navigateOverlayBack();
        } else if (customView != null) {
            exitFullscreen();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else if (tabs.size() > 1) {
            closeCurrentTab();
        } else {
            finishAfterTransition();
        }
    }

    @Override
    protected void onStop() {
        persistSession();
        if (dataStore != null) dataStore.flush();
        if (siteSettingsStore != null) siteSettingsStore.flush();
        if (scriptStore != null) scriptStore.flush();
        if (deferredStartupPending) {
            uiHandler.removeCallbacks(deferredStartup);
            deferredStartupPending = false;
        }
        vaultUnlockedUntil = 0L;
        stagedCredential = null;
        stagedCredentialAt = 0L;
        if (services != null) services.trimMemory();
        maybeTrimTransientCache();
        super.onStop();
    }

    @Override
    protected void onPause() {
        boolean pictureInPicture = isInPictureInPictureMode();
        activityResumed = pictureInPicture;
        startupReadiness.set(StartupReadiness.RESUMED, pictureInPicture);
        if (webView != null && !pictureInPicture) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (webView != null) {
            webView.onResume();
        }
        startupReadiness.set(StartupReadiness.RESUMED, true);
        maybeCompleteStartup();
        InitialNavigationGuard navigationGuard = webView == null ? null : initialNavigationGuards.get(webView);
        long pendingInitialNavigation = navigationGuard == null ? 0L : navigationGuard.pendingGeneration();
        if (pendingInitialNavigation != 0L)
            scheduleInitialNavigationRetry(webView, pendingInitialNavigation, 250L);
        scheduleDeferredStartupWork();
    }

    @Override
    protected void onUserLeaveHint() {
        if (autoPictureInPicture && webView != null && isNetworkPage(currentPageUrl) &&
                !isInPictureInPictureMode()) enterPagePictureInPicture();
        super.onUserLeaveHint();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPictureInPictureMode, android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig);
        if (topBar != null) topBar.setVisibility(inPictureInPictureMode ? View.GONE : View.VISIBLE);
        if (bottomBar != null) bottomBar.setVisibility(inPictureInPictureMode ? View.GONE : View.VISIBLE);
        if (progressBar != null && inPictureInPictureMode) progressBar.setVisibility(View.GONE);
        activityResumed = inPictureInPictureMode || hasWindowFocus();
        if (webView != null) {
            if (inPictureInPictureMode) webView.onResume();
            else requestChromeUpdate();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        boolean runningPressure = level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW &&
                level <= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL;
        if (runningPressure || level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            preparedInjection = null;
            if (services != null) services.trimMemory();
        }

        boolean releaseTabs;
        if (MODE_POWER_SAVE.equals(performanceMode)) {
            releaseTabs = runningPressure || level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN;
        } else if (MODE_PERFORMANCE.equals(performanceMode)) {
            releaseTabs = level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                    level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE;
        } else {
            releaseTabs = level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                    level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE;
        }
        if (releaseTabs) {
            releaseInactiveTabStates();
            if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                    level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) clearColdTabBundles();
        }
    }

    @Override
    public void onLowMemory() {
        preparedInjection = null;
        releaseInactiveTabStates();
        clearColdTabBundles();
        super.onLowMemory();
    }

    private void releaseInactiveTabStates() {
        ArrayList<BrowserTab> inactive = new ArrayList<BrowserTab>();
        for (int i = 0; i < tabs.size(); i++) if (i != currentTabIndex && tabs.get(i).liveView != null) inactive.add(tabs.get(i));
        for (BrowserTab tab : inactive) freezeTab(tab);
    }

    private void clearColdTabBundles() {
        for (int i = 0; i < tabs.size(); i++) if (i != currentTabIndex) tabs.get(i).state = null;
    }

    @Override
    protected void onDestroy() {
        activityResumed = false;
        activityDestroyed = true;
        unregisterPredictiveBack();
        persistSession();
        if (pendingPermissionRequest != null) pendingPermissionRequest.deny();
        if (pendingGeolocationCallback != null) pendingGeolocationCallback.invoke(pendingGeolocationOrigin, false, false);
        dismissOverlay();
        uiHandler.removeCallbacksAndMessages(null);
        if (startupExecutor != null) startupExecutor.shutdownNow();
        if (navigationExecutor != null) navigationExecutor.shutdownNow();
        if (scriptExecutor != null) scriptExecutor.shutdownNow();
        if (scriptNetworkExecutor != null) scriptNetworkExecutor.shutdownNow();
        for (HttpURLConnection connection : scriptConnections.values()) try { connection.disconnect(); } catch (RuntimeException ignored) {}
        scriptConnections.clear();
        if (services != null) services.close();
        if (dataStore != null) dataStore.close();
        if (siteSettingsStore != null) siteSettingsStore.close();
        if (scriptStore != null) scriptStore.close();
        if (customView != null) exitFullscreen();
        ArrayList<WebView> views = new ArrayList<WebView>();
        for (BrowserTab tab : tabs) if (tab.liveView != null && !views.contains(tab.liveView)) views.add(tab.liveView);
        for (WebView view : views) destroyWebView(view);
        for (BrowserTab tab : tabs) tab.liveView = null;
        webView = null;
        super.onDestroy();
    }
}
