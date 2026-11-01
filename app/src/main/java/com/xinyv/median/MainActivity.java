// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Median Browser contributors

package com.xinyv.median;

import static com.xinyv.median.ByteFormat.humanBytes;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
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
import android.util.Rational;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.ViewPropertyAnimator;
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
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
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
import androidx.webkit.WebViewNavigationWarmupCompat;
import androidx.webkit.WebViewStartupCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.security.MessageDigest;
import java.util.Iterator;

public final class MainActivity extends Activity implements Runnable {
    private static final int FILE_CHOOSER_REQUEST = 401;
    private static final int BACKUP_EXPORT_REQUEST = 402;
    private static final int BACKUP_IMPORT_REQUEST = 403;
    private static final int WEB_PERMISSION_REQUEST = 404;
    private static final int GEOLOCATION_PERMISSION_REQUEST = 405;
    private static final int VAULT_UNLOCK_REQUEST = 406;
    private static final long VAULT_UNLOCK_WINDOW_MS = 105_000L;
    private static final int FULL_BACKUP_EXPORT_REQUEST = 407;
    private static final int FULL_BACKUP_IMPORT_REQUEST = 408;
    private static final int HOME_WALLPAPER_REQUEST = HomeCustomizationController.REQUEST_WALLPAPER;
    private static final int HOME_LOGO_REQUEST = HomeCustomizationController.REQUEST_LOGO;
    private static final int QR_SCAN_REQUEST = 411;
    private static final int DOWNLOAD_DIRECTORY_REQUEST = 412;
    private static final String PREFS = "median_browser_v2";
    private static final String HOME_URL = "https://median.invalid/";
    private static final String QR_URL = "https://median.invalid/scan";
    private static final String STATE_WEBVIEW = "median.webview.state";
    private static final String STATE_TABS = "median.tabs.state";
    private static final String PREF_STARTUP_DIAGNOSTIC = "startup_diagnostic_v1";
    private static final int MAX_TABS = 64;
    // Automatic subscription refresh is useful after startup, but its network traffic should not
    // compete with the first page's remaining resources.
    private static final long FILTER_SUBSCRIPTION_IDLE_DELAY_MS = 3200L;
    // A cold Chromium provider can take longer than one frame to dispatch its first callback.
    // Retrying at 450 ms often opens a second socket before the original request is accepted;
    // wait long enough for the normal acknowledgement path and keep the retry one-shot.
    private static final long INITIAL_NAVIGATION_ACK_MS = 1400L;
    private static final int MAX_WEBVIEW_STATE_BYTES = 262144;
    private static final int MAX_TAB_METADATA_BYTES = 262144;
    private static final int MAX_SAVED_TAB_URL_CHARS = 16384;
    private static final int MAX_SAVED_TAB_TITLE_CHARS = 256;
    // The bridge is transported through window.prompt. Reject oversized messages before
    // JSONObject allocates a tree on the UI thread.
    private static final int SCRIPT_BRIDGE_MAX_MESSAGE_CHARS = 256 * 1024;
    private static final int SCRIPT_ASSET_MAX_BYTES = 8 * 1024 * 1024;
    private static final long SCRIPT_ASSET_TOTAL_MAX_BYTES = 32L * 1024L * 1024L;
    private static final byte[] EMPTY_RESPONSE = new byte[0];
    private static final String MODE_PERFORMANCE = "performance";
    private static final String MODE_STANDARD = "standard";
    private static final String MODE_POWER_SAVE = "power_save";

    // Track user navigation to prevent unwanted home page redirects
    private boolean userHasNavigated = false;
    private static final int SHEET_ROW_NAVIGATE = 0;
    private static final int SHEET_ROW_ACTION = 1;
    private static final int SHEET_ROW_TOGGLE_OFF = 2;
    private static final int SHEET_ROW_TOGGLE_ON = 3;
    private static final int SHEET_TAG_SURFACE = 1;
    private static final int SHEET_TAG_HANDLE = 2;
    private static final int SHEET_TAG_PRIMARY = 3;
    private static final int SHEET_TAG_MUTED = 4;
    private static final int SHEET_TAG_ACCENT = 5;
    private static final int SHEET_TAG_ROW = 6;
    private static final int SHEET_TAG_PLAIN_SURFACE = 7;
    private static final String PREF_COMMUNITY_NOTICE_SHOWN = "community_notice_shown_v1";
    private static final String GITHUB_URL = "https://github.com/bi-box/Median";
    private static final String TELEGRAM_URL = "https://telegram.me/MedianBeta";
    private static final String COMMUNITY_INFO =
            "项目更新、版本发布与问题反馈：\n" + GITHUB_URL +
            "\n\n官方测试频道与公告：\n" + TELEGRAM_URL;

    // Modern color palette with refined tonal progression
    private static final int WHITE = 0xFFFCFCFD;
    private static final int TEXT = 0xFF0A0D12;
    private static final int MUTED = 0xFF6B7280;
    private static final int SURFACE = 0xFFF4F6F8;
    private static final int BLUE = 0xFF3B82F6;
    private static final int DARK_WEB = 0xFF05070C;
    private static final int DARK_SHEET = 0xFF0F131C;
    private static final int DARK_OVERLAY = 0xCC000000;
    private static final int LIGHT_OVERLAY = 0x4D000000;
    private static final int DARK_BACKGROUND = 0xFF0A0D12;
    private static final int DARK_CARD = 0xFF161D2B;
    private static final int DARK_SURFACE = 0xFF1E2636;
    private static final int DARK_TEXT = 0xFFF0F1F3;
    private static final int DARK_MUTED = 0xFF9CA3AF;
    private static final int DARK_ACCENT = 0xFF60A5FA;

    private FrameLayout rootFrame;
    private FrameLayout browserChrome;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private View statusBarBackground;
    private View navigationBarBackground;
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
    private View startupSurface;
    private TextView startupStatus;
    private ValueCallback<Uri[]> fileChooserCallback;
    private WebView fileChooserView;
    private String fileChooserDocumentUrl = "";
    private String homeToken;
    private String adPickToken;
    private String qrToken;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private ExecutorService startupExecutor;
    private StartupDataLoader startupDataLoader;
    private ExecutorService webViewStartupExecutor;
    private String webViewStartupDiagnostic = "兼容初始化";
    private ThreadPoolExecutor navigationExecutor;
    private ExecutorService scriptExecutor;
    private ExecutorService scriptNetworkExecutor;
    private UserScriptNetwork userScriptNetwork;
    private boolean activityResumed;
    private volatile boolean activityDestroyed;
    private boolean startupReady;
    private final long startupStartedAt = SystemClock.elapsedRealtime();
    private long startupReadyAt;
    private long startupNavigationAt;
    private volatile long scriptRegistrationGeneration;
    private final StartupReadiness startupReadiness = new StartupReadiness(
            StartupReadiness.VIEW | StartupReadiness.DATA | StartupReadiness.FILTERS | StartupReadiness.RESUMED);
    private Bundle startupSavedState;
    private String startupExternalUrl = "";
    private String startupScriptToken = "";
    private List<UserScriptStore.DocumentStartPayload> startupScriptPayloads = Collections.emptyList();
    private boolean startupScriptPayloadsApplied;
    private String pendingStartupInput;
    // The first navigation must not wait for the large local stores. Their decoded snapshots
    // are attached later on the UI thread and only refresh data that is actually visible.
    private boolean startupFastPosted;
    private boolean startupFastStarted;
    private boolean startupDataReady;
    private boolean startupCustomRulesReady;
    private boolean startupSessionPending;
    private long lastExecutorBusyToastAt;
    private boolean deferredStartupPending;
    private boolean deferredStartupComplete;
    private final Runnable deferredStartup = new Runnable() {
        @Override public void run() {
            deferredStartupPending = false;
            if (deferredStartupComplete || isFinishing() || !activityResumed) return;
            // Local rules start alongside provider initialization. Only subscription network
            // refresh waits for the completed page's idle window.
            if (!pageFinished || !isNetworkPage(currentPageUrl)) return;
            deferredStartupComplete = true;
            updateFilterSubscriptions(true);
        }
    };
    private View customView;
    private View activeOverlay;
    private View activeOverlayPanel;
    private boolean activeOverlaySheet;
    private boolean overlayDismissInProgress;
    private Runnable activeOverlayBackAction;
    private Runnable activeOverlayDismissAction;
    private Object predictiveBackCallback;
    private HomeCustomizationController homeCustomizationController;
    private Runnable bookmarkFolderRootBackAction;
    private Runnable filterCenterBackAction;
    private Runnable scriptCenterBackAction;
    private Runnable passwordMenuBackAction;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private int previousOrientation;
    private int previousSystemUi;

    private final AdBlockEngine adBlock = new AdBlockEngine();
    private AdBlockRuleLoader adBlockRuleLoader;
    private NavigationConnectionHints connectionHints;
    private UserScriptStore scriptStore;
    private final MediaResourceSniffer mediaSniffer = new MediaResourceSniffer();
    private long liveMediaCaptureSequence = -1L;
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
    private boolean edgeNavigationEnabled;
    private boolean httpsOnly;
    private boolean restoreTabs;
    private boolean acceptThirdPartyCookies;
    private String searchEngine;
    private volatile String performanceMode;
    private volatile Set<String> siteExceptions;
    private int blockedAtPageStart;
    private volatile boolean scriptDownloadInProgress;
    private final ConcurrentHashMap<WebView, String> scriptBridgeTokens = new ConcurrentHashMap<WebView, String>();
    private final ConcurrentHashMap<WebView, Long> scriptDocumentGenerations = new ConcurrentHashMap<WebView, Long>();
    private final ConcurrentHashMap<WebView, List<ScriptHandler>> scriptHandlers =
            new ConcurrentHashMap<WebView, List<ScriptHandler>>();
    private final ConcurrentHashMap<WebView, String> scriptTabIds = new ConcurrentHashMap<WebView, String>();
    private final ScriptTabStateStore scriptTabValues = new ScriptTabStateStore();
    private long scriptTabSequence;
    private final Set<WebView> documentStartScriptViews = ConcurrentHashMap.newKeySet();
    private volatile boolean filterUpdateInProgress;
    private boolean autoPictureInPicture;
    private boolean immersiveScrollChrome;
    private boolean clearDataOnExit;
    private boolean exitDataCleared;
    private boolean chromeHiddenByScroll;
    private boolean immersivePageLayout;
    private boolean immersiveGestureActive;
    private boolean immersiveGestureDirectionLocked;
    private boolean immersiveGestureVertical;
    private float immersiveGestureDownX;
    private float immersiveGestureDownY;
    private float immersiveGestureY;
    private float immersiveGestureOffset;
    private boolean immersiveGestureStartHidden;
    private boolean keyboardVisible;
    private int legacyKeyboardOverlap;
    private int systemKeyboardBottom;
    private boolean systemKeyboardVisible;
    private int systemInsetLeft;
    private int systemInsetTop;
    private int systemInsetRight;
    private int systemInsetBottom;
    private boolean cleanTrackingParameters;
    private boolean passwordAutofillEnabled;
    private boolean passwordSavePromptsEnabled;
    private final CookieFlushScheduler cookieFlushScheduler = new CookieFlushScheduler();
    private boolean compatibilityDialogShowing;
    private String lastCompatibilityOfferHost = "";
    private long lastCompatibilityOfferAt;
    private boolean rendererRecoveryPending;
    // WebView request callbacks run off the UI thread. Never call WebView methods there.
    private volatile String currentPageUrl = HOME_URL;
    private volatile String currentPageHost = "";
    private boolean homeThemeRepairPending;
    private final ConcurrentHashMap<WebView, String> pageHosts = new ConcurrentHashMap<WebView, String>();
    private final ConcurrentHashMap<WebView, String> startedPageUrls = new ConcurrentHashMap<WebView, String>();
    private final ConcurrentHashMap<WebView, Boolean> adBlockActiveByView = new ConcurrentHashMap<WebView, Boolean>();
    private final ConcurrentHashMap<WebView, String> mobileUserAgents = new ConcurrentHashMap<WebView, String>();
    private final ConcurrentHashMap<WebView, String> appliedSiteSettings = new ConcurrentHashMap<WebView, String>();
    private final ConcurrentHashMap<WebView, InitialNavigationGuard> initialNavigationGuards =
            new ConcurrentHashMap<WebView, InitialNavigationGuard>();
    private final ConcurrentHashMap<WebView, NavigationGestureGuard> navigationGestureGuards =
            new ConcurrentHashMap<WebView, NavigationGestureGuard>();
    private final ConcurrentHashMap<WebView, String> navigationSourceDocuments = new ConcurrentHashMap<WebView, String>();
    private final ConcurrentHashMap<WebView, MainFrameNavigationContext> mainFrameNavigationContexts =
            new ConcurrentHashMap<WebView, MainFrameNavigationContext>();
    private final ConcurrentHashMap<WebView, String> popupSourceDocuments = new ConcurrentHashMap<WebView, String>();
    private final ConcurrentHashMap<WebView, Object> cosmeticInjected = new ConcurrentHashMap<WebView, Object>();
    private final Set<WebView> unresponsiveWebViews = ConcurrentHashMap.newKeySet();
    private final Set<WebView> trustedHomeViews = ConcurrentHashMap.newKeySet();
    private final Set<WebView> customHomeViews = ConcurrentHashMap.newKeySet();
    private final Set<WebView> adPickerViews = ConcurrentHashMap.newKeySet();
    private static final class AdPickerSession {
        final String documentUrl;
        AdPickerSession(String url) { documentUrl = navigationDocumentUrl(url); }
    }
    private final ConcurrentHashMap<WebView, AdPickerSession> adPickerSessions = new ConcurrentHashMap<WebView, AdPickerSession>();
    private final Set<WebView> qrScannerViews = ConcurrentHashMap.newKeySet();
    private final Set<String> faviconFetchQueued = ConcurrentHashMap.newKeySet();
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
    private Runnable pendingVaultCancelAction;
    private long vaultUnlockedUntil;
    private PasswordVault.Credential stagedCredential;
    private long stagedCredentialAt;
    private String lastCredentialOfferKey = "";
    private long lastCredentialOfferAt;
    private final Runnable vaultLock = new Runnable() {
        @Override public void run() {
            vaultUnlockedUntil = 0L;
            if (stagedCredential != null) stagedCredential.clear();
            stagedCredential = null;
            stagedCredentialAt = 0L;
            if (services != null) services.lockVault();
        }
    };

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
        String scriptTabId;
        String title = "新标签页";
        String url = HOME_URL;
        Bundle state;
        WebView liveView;
        long lastActiveAt;
        boolean pinned;
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
        // Prepare the provider before any WebView API, in parallel with native UI and disk work.
        beginWebViewStartup();
        Window window = getWindow();
        // Use modern surface colors instead of harsh white/black
        int startupChrome = nightMode ? 0xFF0A0D12 : 0xFFFAFBFC;
        window.setStatusBarColor(startupChrome);
        window.setNavigationBarColor(startupChrome);
        int layoutFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        window.getDecorView().setSystemUiVisibility(layoutFlags | (nightMode ? 0 :
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            // DEFAULT letterboxes a fullscreen window around the camera cutout on some
            // devices. Showing/hiding the status bar would then still resize the page.
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }

        deviceProfile = DeviceProfile.detect(this);
        migrateLegacyLogoExample();
        homeImages = new HomeImageStore(this);
        favicons = new FaviconStore(this);
        searchEngines = new SearchEngineStore(prefs);
        adBlockEnabled = prefs.getBoolean("adblock", true);
        startupCustomRulesReady = !adBlockEnabled;
        startupReadiness.set(StartupReadiness.FILTERS, startupCustomRulesReady);
        desktopMode = prefs.getBoolean("desktop", false);
        edgeNavigationEnabled = prefs.getBoolean("edge_navigation", true);
        httpsOnly = prefs.getBoolean("https_only", true);
        restoreTabs = HomeOpenPolicy.restoresLast(prefs.getString("home_open_mode", ""),
                prefs.getBoolean("restore_tabs", false));
        acceptThirdPartyCookies = prefs.getBoolean("accept_third_party_cookies", false);
        searchEngine = prefs.getString("search_engine", "google");
        if (!searchEngines.contains(searchEngine)) searchEngine = "google";
        performanceMode = prefs.getString("performance_mode", MODE_STANDARD);
        autoPictureInPicture = prefs.getBoolean("auto_picture_in_picture", false);
        immersiveScrollChrome = prefs.getBoolean("immersive_scroll_chrome", true);
        clearDataOnExit = prefs.getBoolean("clear_data_on_exit", false);
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
        connectionHints = new NavigationConnectionHints(uiHandler, prefs, adBlock,
                new NavigationConnectionHints.Host() {
                    @Override public boolean isCurrentPageCallback(WebView source, long sequence) {
                        return !exitDataCleared && MainActivity.this.isCurrentPageCallback(source, sequence);
                    }
                    @Override public boolean isAdBlockEnabled() { return adBlockEnabled; }
                });
        adBlockRuleLoader = new AdBlockRuleLoader(adBlock, prefs, services, uiHandler,
                new AdBlockRuleLoader.TaskQueue() {
                    @Override public boolean execute(Runnable task) { return executeTask(scriptExecutor, task); }
                }, new AdBlockRuleLoader.Listener() {
                    @Override public void onPublished(boolean notifyUser) { onAdBlockRulesLoaded(notifyUser); }
                    @Override public void onFailed() { onAdBlockRulesLoadFailed(); }
                });
        // Publish the small custom-rule snapshot first, then compile cached subscriptions in
        // the same background task. Neither operation waits for onPageFinished or uses the UI.
        rebuildAdBlockRulesAsync(false);

        BrowserTab first = new BrowserTab();
        tabs.add(first);
        // Start both disk decoders before constructing the view hierarchy and WebView. Previously
        // the synchronous Chromium startup completed first, so the advertised parallel path was
        // actually serialized on every cold launch.
        beginStartupLoad(state, getIntent());
        buildUi();
        registerPredictiveBack();
    }

    /**
     * Uses the provider's supported async protocol when available. The callback is always posted
     * to the main looper, so onCreate finishes building the native surface before it is delivered.
     */
    private void beginWebViewStartup() {
        webViewStartupExecutor = BackgroundExecutor.createStartup(1, 32, "median-webview-startup");
        WebViewStartupCompat.startUpWebView(webViewStartupExecutor, new WebViewStartupCompat.Callback() {
            @Override public void onSuccess(WebViewStartupCompat.Result result) {
                finishWebViewStartupExecutor();
                if (activityDestroyed || isFinishing()) return;
                webViewStartupDiagnostic = result.usedAsyncStartup() ? "异步初始化" : "兼容初始化";
                if (result.getTotalTimeInUiThreadMillis() != null)
                    webViewStartupDiagnostic += " · UI " + result.getTotalTimeInUiThreadMillis() + " ms";
                if (result.getMaxTimePerTaskInUiThreadMillis() != null)
                    webViewStartupDiagnostic += " · 最长任务 " + result.getMaxTimePerTaskInUiThreadMillis() + " ms";
                postAfterUiTransition(new Runnable() {
                    @Override public void run() { initializeInitialWebView(); }
                });
            }

            @Override public void onFailure(Throwable error) {
                finishWebViewStartupExecutor();
                if (activityDestroyed || isFinishing()) return;
                // A real provider startup failure makes further WebView API calls unsafe.
                // Keep the native surface visible rather than retrying new WebView here.
                if (startupStatus != null) startupStatus.setText("网页引擎启动失败，请关闭后重新打开 Median");
            }
        });
    }

    private void finishWebViewStartupExecutor() {
        if (webViewStartupExecutor != null) webViewStartupExecutor.shutdown();
        webViewStartupExecutor = null;
    }

    /** Constructs the view only after provider preparation, or on the older-provider fallback. */
    private void initializeInitialWebView() {
        if (activityDestroyed || isFinishing() || webView != null || webContainer == null) return;

        webView = createConfiguredWebView();
        // Keep the startup surface until view construction has actually succeeded.
        if (startupSurface != null && startupSurface.getParent() == webContainer) {
            Motion.fadeOutStartup(startupSurface, reduceMotion(), new Runnable() {
                @Override public void run() {
                    if (startupSurface != null && startupSurface.getParent() == webContainer) {
                        webContainer.removeView(startupSurface);
                        startupSurface = null;
                    }
                }
            });
        } else {
            webContainer.removeAllViews();
        }

        final WebView initialView = webView;
        // Keep the startup surface above the newly created view until its short fade completes.
        webContainer.addView(initialView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // WebView already renders through the provider's hardware pipeline. A temporary forced
        // layer duplicates its backing surface and delays the first useful frame on cold startup,
        // so let the view draw immediately while the startup surface fades away.
        initialView.setAlpha(1f);

        // configureWebView() runs before this field is assigned, so its generic renderer policy
        // cannot yet know that this is the visible tab. Promote it immediately after binding;
        // otherwise the first homepage/network navigation may run at background priority.
        applyPerformanceMode(webView);
        BrowserTab first = tabs.get(0);
        bindWebViewToTab(first, webView);
        first.lastActiveAt = SystemClock.uptimeMillis();
        if (activityResumed) webView.onResume();
        // Do not start a placeholder navigation here. maybeStartFastStartup() owns the first
        // useful navigation and can run before the background stores have finished decoding.
        startupReadiness.set(StartupReadiness.VIEW, true);
        updateChrome();
        maybeStartFastStartup();
        maybeCompleteStartup();
    }

    /** Starts the first useful page as soon as the native view and Activity are resumed. */
    private void maybeStartFastStartup() {
        if (startupReady || startupFastStarted || startupFastPosted || webView == null || !activityResumed ||
                !startupCustomRulesReady)
            return;
        startupFastPosted = true;
        final Runnable launch = new Runnable() {
            @Override public void run() {
                if (activityDestroyed || isFinishing() || webView == null || !activityResumed) {
                    startupFastPosted = false;
                    return;
                }
                startupFastPosted = false;
                startupFastStarted = true;
                Bundle savedState = startupSavedState;
                String externalUrl = startupExternalUrl;
                startupSavedState = null;
                startupExternalUrl = "";
                completeStartup(savedState, externalUrl);
            }
        };
        // An attached WebView is ready to accept the first navigation now. A second queue hop
        // adds main-thread latency without warming Chromium or improving renderer readiness.
        // Keep the attach-aware frame fallback for OEMs that resume before the first attach.
        if (webView.isAttachedToWindow()) launch.run();
        else postAfterUiTransition(launch);
    }

    /** Decodes larger user state away from the first-frame path. */
    private void beginStartupLoad(final Bundle savedState, Intent intent) {
        final String externalUrl = isExternalHttpIntent(intent) ? intent.getData().toString() : "";
        if (externalUrl.length() > 0) WebViewNavigationWarmupCompat.enqueueForStartup(externalUrl);
        startupSavedState = savedState;
        startupExternalUrl = externalUrl;
        startupDataLoader = new StartupDataLoader(getApplicationContext(), prefs, uiHandler,
                new StartupDataLoader.TaskQueue() {
                    @Override public boolean execute(Runnable task) { return executeTask(startupExecutor, task); }
                }, new StartupDataLoader.Listener() {
                    @Override public void onConnectionHintsReady(ConnectionHintsStore hints) {
                        if (activityDestroyed || exitDataCleared || !connectionHints.publishRestored(hints)) return;
                        if (webView != null && !pageFinished && isNetworkPage(currentPageUrl)) {
                            prepareNavigationConnections(currentPageUrl);
                            installConnectionHintScript(webView, currentPageUrl, true);
                        }
                    }

                    @Override public boolean onLoaded(StartupDataLoader.Result load) {
                        if (activityDestroyed || isFinishing()) return false;
                        dataStore = load.data;
                        bookmarkFolders = load.folders;
                        siteSettingsStore = load.sites;
                        scriptStore = load.scripts;
                        connectionHints.publishRestored(load.connectionHints);
                        startupScriptToken = load.scriptToken;
                        startupScriptPayloads = load.scriptPayloads;
                        startupDataReady = true;
                        startupReadiness.set(StartupReadiness.DATA, true);
                        onStartupDataReady();
                        maybeCompleteStartup();
                        return true;
                    }

                    @Override public void onFailed() {
                        if (activityDestroyed || isFinishing()) return;
                        toast("本地浏览数据无法读取，请重新启动 Median");
                        finishAfterTransition();
                    }

                    @Override public void onWorkersFinished() {
                        if (startupExecutor != null) startupExecutor.shutdown();
                    }
                });
        startupDataLoader.start();
    }

    private void maybeCompleteStartup() {
        if (startupReady || startupFastPosted || startupFastStarted || webView == null ||
                !startupReadiness.claimPost()) return;
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
        if (startupDataReady) installPreparedDocumentStartUserScripts(webView);
        boolean restoredTabs = restoreTabMetadata(savedState);
        BrowserTab active = tabs.get(currentTabIndex);
        Bundle webViewState = savedState == null ? null :
                (savedState.containsKey(STATE_WEBVIEW) ? savedState.getBundle(STATE_WEBVIEW) :
                        savedState.containsKey(STATE_TABS) ? null : savedState);
        String directInput = StartupNavigationPolicy.preferredInput(pendingStartupInput, externalUrl);
        pendingStartupInput = null;
        if (directInput.length() > 0) {
            // Typed input is newer than an external launch intent. Loading the homepage/session
            // first and this destination immediately afterwards recreates the cold-start race
            // fixed above, so execute only the selected direct navigation.
            startupSessionPending = false;
            loadInput(directInput);
        } else if (restoreWebViewState(webView, webViewState)) {
            startupSessionPending = false;
            active.url = webView.getUrl() == null ? HOME_URL : webView.getUrl();
            if (isHomeUrl(active.url)) {
                // A restored WebView can contain the placeholder homepage rendered before the
                // bookmark stores finished loading. Always rebuild internal home pages from the
                // now-ready stores so long-idle/process-death restores cannot show stale/empty data.
                renderedHomeKeys.remove(webView);
                showHome();
            } else if (isQrScannerUrl(active.url)) {
                showQrScannerPage();
            } else if (isNetworkPage(active.url)) {
                currentPageUrl = active.url;
                currentPageHost = hostOf(active.url);
                prepareNetworkDestination(webView, active.url, currentPageHost);
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
        } else if (restoredTabs) {
            // Activity recreation is independent of the fresh-launch homepage preference.
            // Metadata also retains the selected URL if Chromium could not save its history.
            startupSessionPending = false;
            if (isHomeUrl(active.url)) showHome();
            else if (isQrScannerUrl(active.url)) showQrScannerPage();
            else loadNetworkUrl(webView, active.url);
        } else {
            startupSessionPending = restoreTabs && dataStore == null;
            if (!restoreStartupSession()) {
                active.url = configuredHomeUrl();
                if (isHomeUrl(active.url)) showHome();
                else loadNetworkUrl(webView, active.url);
            }
        }
        requestChromeUpdate();
        showCommunityNoticeOnFirstLaunch(savedState);
    }

    private boolean restoreStartupSession() {
        if (!restoreTabs || dataStore == null || webView == null) return false;
        List<BrowserDataStore.SessionTab> saved = dataStore.restoreSession();
        if (saved.isEmpty()) return false;
        tabs.clear();
        for (BrowserDataStore.SessionTab item : saved) {
            BrowserTab restored = new BrowserTab();
            restored.title = item.title;
            restored.url = item.url;
            restored.pinned = item.pinned;
            tabs.add(restored);
        }
        currentTabIndex = Math.min(dataStore.restoredSessionIndex(), tabs.size() - 1);
        BrowserTab active = tabs.get(currentTabIndex);
        bindWebViewToTab(active, webView);
        active.lastActiveAt = SystemClock.uptimeMillis();
        if (isHomeUrl(active.url)) showHome();
        else loadNetworkUrl(webView, active.url);
        return true;
    }

    /** Publishes decoded stores after first paint without replacing an active user navigation. */
    private void onStartupDataReady() {
        if (activityDestroyed || webView == null) return;
        installPreparedDocumentStartUserScripts(webView);
        if (startupSessionPending && isHomeUrl(currentPageUrl) && isHomeUrl(webView.getUrl())) {
            startupSessionPending = false;
            if (restoreStartupSession()) {
                requestChromeUpdate();
                return;
            }
        }
        // The cache key includes every persisted site setting. Publishing the stores does not
        // invalidate an identical default policy; avoid resending settings during the first load.
        // A real site override changes the key and is still applied below.
        // Only apply settings or show home if the current page matches what WebView is actually showing
        // AND the user hasn't navigated away from the initial state
        String actualUrl = webView.getUrl();
        if (!pageFinished && isNetworkPage(currentPageUrl)) prepareNavigationConnections(currentPageUrl);
        if (isNetworkPage(currentPageUrl) && isNetworkPage(actualUrl)) {
            applySiteSettings(webView, currentPageHost);
        } else if (!userHasNavigated && isHomeUrl(currentPageUrl) && isHomeUrl(actualUrl)) {
            showHome();
        }
    }

    /** Network refresh yields to page completion; local filtering is already initialized. */
    private void scheduleDeferredStartupWork() {
        if (!startupReady) return;
        if (deferredStartupComplete || deferredStartupPending) return;
        deferredStartupPending = true;
        uiHandler.postDelayed(deferredStartup, FILTER_SUBSCRIPTION_IDLE_DELAY_MS);
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

    private String homeToken() {
        if (homeToken == null) homeToken = UrlCleaner.randomToken();
        return homeToken;
    }

    private String adPickToken() {
        if (adPickToken == null) adPickToken = UrlCleaner.randomToken();
        return adPickToken;
    }

    private String qrToken() {
        if (qrToken == null) qrToken = UrlCleaner.randomToken();
        return qrToken;
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
        rootFrame.setBackgroundColor(nightMode ? DARK_WEB : WHITE);
        int chromeBackground = nightMode ? DARK_BACKGROUND : WHITE;
        int chromeSurface = nightMode ? DARK_SURFACE : SURFACE;
        int chromeText = nightMode ? DARK_TEXT : TEXT;
        int chromeHint = nightMode ? DARK_MUTED : 0xff80868b;

        browserChrome = new FrameLayout(this);
        browserChrome.setBackgroundColor(chromeBackground);
        rootFrame.addView(browserChrome, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        topBar.setBackgroundColor(chromeBackground);

        addressPill = new LinearLayout(this);
        addressPill.setOrientation(LinearLayout.HORIZONTAL);
        addressPill.setGravity(Gravity.CENTER_VERTICAL);
        addressPill.setPadding(dp(4), 0, dp(4), 0);
        addressPill.setBackground(roundRect(chromeSurface, 999));
        addressPill.setElevation(0.5f * getResources().getDisplayMetrics().density);

        shieldButton = iconButton(BrowserIconView.SHIELD, "保护与脚本");
        addressPill.addView(shieldButton, new LinearLayout.LayoutParams(dp(42), dp(42)));

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextSize(15.5f);
        addressBar.setTextColor(chromeText);
        addressBar.setHintTextColor(chromeHint);
        addressBar.setHint("搜索或输入网址");
        addressBar.setSelectAllOnFocus(true);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setBackgroundColor(Color.TRANSPARENT);
        addressBar.setPadding(dp(8), 0, dp(8), 0);
        addressPill.addView(addressBar, new LinearLayout.LayoutParams(0, dp(46), 1f));

        refreshButton = iconButton(BrowserIconView.RELOAD, "刷新");
        addressPill.addView(refreshButton, new LinearLayout.LayoutParams(dp(42), dp(42)));
        topBar.addView(addressPill, new LinearLayout.LayoutParams(0, dp(46), 1f));

        topBar.setElevation(dp(8));
        browserChrome.addView(topBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62), Gravity.TOP));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));
        progressBar.setVisibility(View.GONE);
        progressBar.setElevation(dp(9));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.TOP);
        progressParams.topMargin = dp(62);
        browserChrome.addView(progressBar, progressParams);

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(nightMode ? DARK_WEB : WHITE);
        startupSurface = new LinearLayout(this);
        ((LinearLayout) startupSurface).setOrientation(LinearLayout.VERTICAL);
        ((LinearLayout) startupSurface).setGravity(Gravity.CENTER);
        startupSurface.setPadding(dp(24), dp(24), dp(24), dp(24));
        startupSurface.setBackgroundColor(nightMode ? DARK_WEB : WHITE);
        ImageView startupMark = new ImageView(this);
        startupMark.setImageResource(R.drawable.ic_launcher_foreground);
        startupMark.setContentDescription("Median");
        startupMark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        ((LinearLayout) startupSurface).addView(startupMark, new LinearLayout.LayoutParams(dp(88), dp(88)));
        TextView startupName = new TextView(this);
        startupName.setText("median");
        startupName.setTextColor(chromeText);
        startupName.setTextSize(23f);
        startupName.setGravity(Gravity.CENTER);
        startupName.setLetterSpacing(0.08f);
        ((LinearLayout) startupSurface).addView(startupName, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        startupStatus = new TextView(this);
        startupStatus.setText("正在准备网页引擎…");
        startupStatus.setTextColor(nightMode ? DARK_MUTED : MUTED);
        startupStatus.setTextSize(12f);
        startupStatus.setGravity(Gravity.CENTER);
        startupStatus.setPadding(0, dp(8), 0, 0);
        ((LinearLayout) startupSurface).addView(startupStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        webContainer.addView(startupSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Apply smooth fade-in animation to startup screen
        Motion.fadeInStartup(startupSurface, reduceMotion(), null);

        FrameLayout.LayoutParams pageParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        pageParams.topMargin = dp(62);
        pageParams.bottomMargin = dp(62);
        browserChrome.addView(webContainer, 0, pageParams);

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(dp(10), dp(4), dp(10), dp(4));
        bottomBar.setBackgroundColor(chromeBackground);
        bottomBar.setElevation(dp(8));

        backButton = iconButton(BrowserIconView.BACK, "后退");
        forwardButton = iconButton(BrowserIconView.FORWARD, "前进");
        BrowserIconView home = iconButton(BrowserIconView.HOME, "主页");
        tabButton = iconButton(BrowserIconView.TABS, "标签页");
        BrowserIconView menu = iconButton(BrowserIconView.MENU, "菜单");
        BrowserIconView[] bottomButtons = new BrowserIconView[] { backButton, forwardButton, home, tabButton, menu };
        for (BrowserIconView button : bottomButtons) bottomBar.addView(button, new LinearLayout.LayoutParams(0, dp(54), 1f));
        browserChrome.addView(bottomBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62), Gravity.BOTTOM));

        statusBarBackground = new View(this);
        navigationBarBackground = new View(this);
        statusBarBackground.setBackgroundColor(chromeBackground);
        navigationBarBackground.setBackgroundColor(chromeBackground);
        statusBarBackground.setElevation(dp(8));
        navigationBarBackground.setElevation(dp(8));
        browserChrome.addView(statusBarBackground, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP));
        browserChrome.addView(navigationBarBackground, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM));

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
                if (hasFocus) showBrowserChrome(false);
                Motion.focusPill(addressPill, hasFocus, reduceMotion());
                if (hasFocus) {
                    String url = currentPageUrl;
                    if (isInternalBrowserUrl(url) || "about:blank".equalsIgnoreCase(url)) addressBar.setText("");
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
                } else if (isQrScannerUrl(currentPageUrl)) showQrScannerPage();
                else reloadWebView(webView);
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
                if (webView != null && webView.canGoBack()) {
                    goBackWebView(webView);
                    scheduleRestoredHomeRepair(webView);
                }
            }
        });
        forwardButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (webView != null && webView.canGoForward()) goForwardWebView(webView);
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
        if (target == null) return;
        target.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public android.view.WindowInsets onApplyWindowInsets(View view, android.view.WindowInsets insets) {
                int imeBottom;
                boolean imeVisible;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsetsIgnoringVisibility(
                            android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                    systemInsetLeft = bars.left;
                    systemInsetTop = bars.top;
                    systemInsetRight = bars.right;
                    systemInsetBottom = bars.bottom;
                    imeBottom = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
                    imeVisible = insets.isVisible(android.view.WindowInsets.Type.ime());
                } else {
                    systemInsetLeft = insets.getStableInsetLeft();
                    systemInsetTop = insets.getStableInsetTop();
                    systemInsetRight = insets.getStableInsetRight();
                    systemInsetBottom = insets.getStableInsetBottom();
                    if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                        android.view.DisplayCutout cutout = insets.getDisplayCutout();
                        systemInsetLeft = Math.max(systemInsetLeft, cutout.getSafeInsetLeft());
                        systemInsetTop = Math.max(systemInsetTop, cutout.getSafeInsetTop());
                        systemInsetRight = Math.max(systemInsetRight, cutout.getSafeInsetRight());
                        systemInsetBottom = Math.max(systemInsetBottom, cutout.getSafeInsetBottom());
                    }
                    // On these releases adjustResize already removes the keyboard area from
                    // the window. Do not subtract its height a second time as root padding.
                    imeVisible = insets.getSystemWindowInsetBottom() > systemInsetBottom + dp(100);
                    imeBottom = 0;
                }
                systemKeyboardBottom = imeBottom;
                systemKeyboardVisible = imeVisible;
                applyPageInsets();
                return Build.VERSION.SDK_INT >= 30 ? insets : insets.consumeSystemWindowInsets();
            }
        });
        if (Build.VERSION.SDK_INT < 30) {
            target.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                private final android.graphics.Rect visibleFrame = new android.graphics.Rect();
                private final int[] location = new int[2];
                @Override public void onGlobalLayout() {
                    if (activityDestroyed) return;
                    target.getWindowVisibleDisplayFrame(visibleFrame);
                    target.getLocationOnScreen(location);
                    int overlap = Math.max(0, location[1] + target.getHeight() - visibleFrame.bottom);
                    // FULLSCREEN makes adjustResize ineffective on Android 8-10. Subtract
                    // only the keyboard portion still covering this window, not one already
                    // removed by the system. Status/nav changes never cross this threshold.
                    int next = overlap > dp(100) ? overlap : 0;
                    if (next == legacyKeyboardOverlap) return;
                    legacyKeyboardOverlap = next;
                    applyPageInsets();
                }
            });
        }
        target.requestApplyInsets();
    }

    private void applyPageInsets() {
        if (rootFrame == null) return;
        boolean fullSurface = immersivePageLayout || customView != null || isInPictureInPictureMode();
        keyboardVisible = systemKeyboardVisible || legacyKeyboardOverlap > 0;
        int imeBottom = Math.max(systemKeyboardBottom, legacyKeyboardOverlap);
        int top = fullSurface ? 0 : systemInsetTop;
        int bottom = fullSurface || keyboardVisible ? imeBottom : systemInsetBottom;
        if (rootFrame.getPaddingLeft() != systemInsetLeft || rootFrame.getPaddingTop() != top ||
                rootFrame.getPaddingRight() != systemInsetRight || rootFrame.getPaddingBottom() != bottom) {
            rootFrame.setPadding(systemInsetLeft, top, systemInsetRight, bottom);
        }
        updateChromeInsets();
    }

    private void updateChromeInsets() {
        if (topBar == null || bottomBar == null) return;
        int top = immersivePageLayout ? systemInsetTop : 0;
        int bottom = immersivePageLayout && !keyboardVisible ? systemInsetBottom : 0;
        FrameLayout.LayoutParams topParams = (FrameLayout.LayoutParams) topBar.getLayoutParams();
        FrameLayout.LayoutParams bottomParams = (FrameLayout.LayoutParams) bottomBar.getLayoutParams();
        FrameLayout.LayoutParams progressParams = (FrameLayout.LayoutParams) progressBar.getLayoutParams();
        if (topParams.topMargin != top) {
            topParams.topMargin = top;
            topBar.setLayoutParams(topParams);
        }
        if (bottomParams.bottomMargin != bottom) {
            bottomParams.bottomMargin = bottom;
            bottomBar.setLayoutParams(bottomParams);
        }
        if (progressParams.topMargin != top + dp(62)) {
            progressParams.topMargin = top + dp(62);
            progressBar.setLayoutParams(progressParams);
        }
        updateSystemBarBackgrounds();
        updatePageMargins();
    }

    private void updateSystemBarBackgrounds() {
        if (statusBarBackground == null || navigationBarBackground == null) return;
        FrameLayout.LayoutParams status = (FrameLayout.LayoutParams) statusBarBackground.getLayoutParams();
        FrameLayout.LayoutParams navigation = (FrameLayout.LayoutParams) navigationBarBackground.getLayoutParams();
        if (status.height != systemInsetTop) {
            status.height = systemInsetTop;
            statusBarBackground.setLayoutParams(status);
        }
        int bottom = keyboardVisible ? 0 : systemInsetBottom;
        if (navigation.height != bottom) {
            navigation.height = bottom;
            navigationBarBackground.setLayoutParams(navigation);
        }
        int visible = immersivePageLayout && !chromeHiddenByScroll && !isInPictureInPictureMode() ?
                View.VISIBLE : View.INVISIBLE;
        statusBarBackground.setVisibility(visible);
        navigationBarBackground.setVisibility(visible);
    }

    private void updatePageMargins() {
        if (webContainer == null) return;
        // Changing the viewport while WebView is tracking a finger changes its scroll
        // geometry mid-gesture. Commit the new space after UP/CANCEL instead.
        if (immersiveGestureActive && immersivePageLayout && !keyboardVisible &&
                customView == null && !isInPictureInPictureMode()) return;
        boolean fullscreen = isInPictureInPictureMode() || (immersivePageLayout && chromeHiddenByScroll);
        int top = fullscreen ? 0 : dp(62) + (immersivePageLayout ? systemInsetTop : 0);
        int bottom = fullscreen ? 0 : dp(62) +
                (immersivePageLayout && !keyboardVisible ? systemInsetBottom : 0);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) webContainer.getLayoutParams();
        if (params.topMargin == top && params.bottomMargin == bottom) return;
        // Native space is required here: an overlay makes a page's first and last
        // controls unreachable at its scroll limits. Resize once per chrome state,
        // never on scroll callbacks or on every toolbar animation frame.
        params.topMargin = top;
        params.bottomMargin = bottom;
        webContainer.setLayoutParams(params);
    }

    private void updateImmersivePageLayout() {
        if (webContainer == null || rootFrame == null) return;
        boolean next = immersiveScrollChrome && isNetworkPage(currentPageUrl);
        if (next == immersivePageLayout) return;
        immersivePageLayout = next;
        immersiveGestureActive = false;
        updatePageMargins();
        updateChromeInsets();
        applyPageInsets();
        if (!next) showBrowserChrome(false);
        else setImmersiveSystemBars(chromeHiddenByScroll);
        rootFrame.requestApplyInsets();
    }

    /** Keeps the page edge-to-edge while the scroll chrome is hidden. */
    private void setImmersiveSystemBars(boolean hidden) {
        hidden = hidden || customView != null || isInPictureInPictureMode();
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int bars = android.view.WindowInsets.Type.statusBars() |
                        android.view.WindowInsets.Type.navigationBars();
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if (hidden) controller.hide(bars); else controller.show(bars);
            }
            return;
        }

        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        int immersiveFlags = View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        flags &= ~immersiveFlags;
        // Root padding owns safe areas on every page. Keep these flags in ordinary mode
        // too, otherwise the system and root would both subtract the bar insets.
        flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (hidden) flags |= immersiveFlags;
        else {
            if (nightMode) {
                flags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decor.setSystemUiVisibility(flags);
    }

    private BrowserIconView iconButton(int icon, String description) {
        BrowserIconView button = new BrowserIconView(this, icon);
        button.setContentDescription(description);
        // Platform selectable drawables retain the Activity's original theme. Drawing the
        // bounded pressed halo in BrowserIconView keeps theme switches deterministic and avoids
        // a drawable allocation for every toolbar icon.
        button.setBackground(null);
        return button;
    }

    private WebView createConfiguredWebView() {
        WebView view = new WebView(this);
        // The window already uses hardware acceleration. A permanent View layer adds
        // a full-viewport texture on top of Chromium's own compositor, including hot tabs.
        styleWebView(view);
        configureWebView(view);
        pageHosts.put(view, "");
        return view;
    }

    /** Observe actual finger direction; resize, fling and script scrolls are not gestures. */
    private void observeImmersiveTouch(WebView source, MotionEvent event) {
        if (source != webView || activityDestroyed || event == null) return;
        int action = event.getActionMasked();
        // A sheet, custom video view, or window transition may appear between DOWN and CANCEL.
        // Consume the terminal event for our bookkeeping before checking those transient modes.
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            boolean wasActive = immersiveGestureActive;
            boolean startHidden = immersiveGestureStartHidden;
            float offset = immersiveGestureOffset;
            immersiveGestureActive = false;
            if (wasActive) {
                boolean eligible = immersivePageLayout && activeOverlay == null && customView == null &&
                        !isInPictureInPictureMode();
                if (action == MotionEvent.ACTION_UP && eligible) settleImmersiveChrome(offset >= .5f, true);
                else resetImmersiveChromeVisual(startHidden);
            }
            if (!immersivePageLayout || activeOverlay != null || customView != null ||
                    isInPictureInPictureMode()) return;
        }
        if (!immersivePageLayout || activeOverlay != null || customView != null ||
                isInPictureInPictureMode()) return;
        if (action == MotionEvent.ACTION_DOWN) {
            immersiveGestureActive = true;
            immersiveGestureDirectionLocked = false;
            immersiveGestureVertical = false;
            immersiveGestureDownX = event.getRawX();
            immersiveGestureDownY = event.getRawY();
            immersiveGestureY = event.getRawY();
            immersiveGestureStartHidden = chromeHiddenByScroll;
            immersiveGestureOffset = chromeOffsetFromViews(chromeHiddenByScroll);
            cancelChromeAnimationsForGesture();
            applyChromeGestureOffset(immersiveGestureOffset);
        } else if (event.getPointerCount() > 1 && immersiveGestureActive) {
            immersiveGestureActive = false;
            resetImmersiveChromeVisual(immersiveGestureStartHidden);
            updatePageMargins();
        } else if (action == MotionEvent.ACTION_MOVE && immersiveGestureActive) {
            float rawX = event.getRawX();
            float rawY = event.getRawY();
            float dx = rawX - immersiveGestureDownX;
            float dy = rawY - immersiveGestureDownY;
            float deltaY = rawY - immersiveGestureY;
            immersiveGestureY = rawY;
            if (!immersiveGestureDirectionLocked) {
                float slop = Math.max(dp(8), ViewConfiguration.get(source.getContext()).getScaledTouchSlop());
                if (Math.hypot(dx, dy) <= slop) return;
                immersiveGestureDirectionLocked = true;
                immersiveGestureVertical = Math.abs(dy) > Math.abs(dx) * 1.2f;
                if (!immersiveGestureVertical) {
                    immersiveGestureActive = false;
                    resetImmersiveChromeVisual(immersiveGestureStartHidden);
                    return;
                }
                // Consume only movement beyond slop when the direction first locks.
                deltaY = Math.signum(dy) * (Math.abs(dy) - slop);
            }
            if (immersiveGestureVertical) {
                float travel = Math.max(dp(62), Math.max(topBar.getHeight(), bottomBar.getHeight()));
                // Clamp each increment: overscroll must not create a dead zone on reversal.
                immersiveGestureOffset = clamp01(immersiveGestureOffset - deltaY / travel);
                applyChromeGestureOffset(immersiveGestureOffset);
            }
        }
    }

    private void hideBrowserChrome() {
        settleImmersiveChrome(true, false);
    }

    private void showBrowserChrome(boolean animate) {
        if (topBar == null || bottomBar == null || isInPictureInPictureMode()) return;
        if (animate && immersivePageLayout) {
            settleImmersiveChrome(false, true);
            return;
        }
        immersiveGestureActive = false;
        resetImmersiveChromeVisual(false);
        setImmersiveSystemBars(false);
    }

    private void settleImmersiveChrome(boolean hidden, boolean force) {
        if (!immersivePageLayout || topBar == null || bottomBar == null ||
                isInPictureInPictureMode() || (!force && hidden == chromeHiddenByScroll)) return;
        float offset = chromeOffsetFromViews(chromeHiddenByScroll);
        chromeHiddenByScroll = hidden;
        updatePageMargins();
        updateSystemBarBackgrounds();
        setImmersiveSystemBars(hidden);
        long duration = Math.max(60L, Math.round((reduceMotion() ? 80L : 180L) *
                Math.abs((hidden ? 1f : 0f) - offset)));
        cancelViewAnimationIfRunning(topBar);
        cancelViewAnimationIfRunning(bottomBar);
        cancelViewAnimationIfRunning(progressBar);
        applyChromeGestureOffset(offset);
        topBar.animate().translationY(hidden ? -topBar.getHeight() : 0f).alpha(hidden ? 0f : 1f).setDuration(duration)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (chromeHiddenByScroll) {
                            topBar.setVisibility(View.INVISIBLE);
                            topBar.setTranslationY(0f);
                            topBar.setAlpha(1f);
                        }
                    }
                }).start();
        bottomBar.animate().translationY(hidden ? bottomBar.getHeight() : 0f).alpha(hidden ? 0f : 1f).setDuration(duration)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (chromeHiddenByScroll) {
                            bottomBar.setVisibility(View.INVISIBLE);
                            bottomBar.setTranslationY(0f);
                            bottomBar.setAlpha(1f);
                        }
                    }
                }).start();
        if (progressBar != null) {
            syncChromeProgress(offset);
            if (!hidden && renderedProgress >= 0 && renderedProgress < 100) {
                progressBar.setProgress(renderedProgress);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.animate().translationY(0f).alpha(1f).setDuration(duration).start();
            } else {
                progressBar.setVisibility(View.GONE);
            }
        }
    }

    private float chromeOffsetFromViews(boolean hidden) {
        if (topBar == null || bottomBar == null) return 0f;
        if (topBar.getVisibility() != View.VISIBLE && bottomBar.getVisibility() != View.VISIBLE)
            return hidden ? 1f : 0f;
        float topHeight = Math.max(1f, topBar.getHeight());
        float bottomHeight = Math.max(1f, bottomBar.getHeight());
        float topOffset = -topBar.getTranslationY() / topHeight;
        float bottomOffset = bottomBar.getTranslationY() / bottomHeight;
        return clamp01((topOffset + bottomOffset) * .5f);
    }

    private void cancelChromeAnimationsForGesture() {
        cancelViewAnimationIfRunning(topBar);
        cancelViewAnimationIfRunning(bottomBar);
        cancelViewAnimationIfRunning(progressBar);
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
    }

    /** Cancel only an active property animation and detach stale callbacks first. */
    private static void cancelViewAnimationIfRunning(View view) {
        if (view == null) return;
        ViewPropertyAnimator animator = view.animate();
        // ViewPropertyAnimator has no isRunning() API. Clearing callbacks before
        // cancel() makes this safe for both an active and an already-idle animator.
        animator.setListener(null).setUpdateListener(null).withEndAction(null).cancel();
    }

    private void applyChromeGestureOffset(float offset) {
        if (topBar == null || bottomBar == null) return;
        offset = clamp01(offset);
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        topBar.setTranslationY(-topBar.getHeight() * offset);
        bottomBar.setTranslationY(bottomBar.getHeight() * offset);
        topBar.setAlpha(1f - offset);
        bottomBar.setAlpha(1f - offset);
        syncChromeProgress(offset);
    }

    private void syncChromeProgress(float offset) {
        if (progressBar == null) return;
        progressBar.setTranslationY(-topBar.getHeight() * offset);
        progressBar.setAlpha(1f - offset);
        boolean loading = renderedProgress >= 0 && renderedProgress < 100 &&
                !isInPictureInPictureMode() && customView == null;
        progressBar.setVisibility(loading && offset < 1f ? View.VISIBLE : View.GONE);
    }

    private void resetImmersiveChromeVisual(boolean hidden) {
        hidden = hidden && immersivePageLayout;
        chromeHiddenByScroll = hidden;
        cancelChromeAnimationsForGesture();
        applyChromeGestureOffset(hidden ? 1f : 0f);
        if (hidden || isInPictureInPictureMode() || customView != null) {
            topBar.setVisibility(View.INVISIBLE);
            bottomBar.setVisibility(View.INVISIBLE);
            topBar.setTranslationY(0f);
            bottomBar.setTranslationY(0f);
            topBar.setAlpha(1f);
            bottomBar.setAlpha(1f);
        }
        updatePageMargins();
        updateSystemBarBackgrounds();
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void bindWebViewToTab(BrowserTab tab, WebView view) {
        if (tab == null || view == null) return;
        tab.liveView = view;
        scriptTabIds.put(view, scriptTabId(tab));
    }

    private String scriptTabId(BrowserTab tab) {
        if (tab.scriptTabId == null) tab.scriptTabId = "t" + Long.toString(++scriptTabSequence, 36);
        return tab.scriptTabId;
    }

    private void styleWebView(WebView view) {
        view.setBackgroundColor(nightMode ? DARK_WEB : WHITE);
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
        cancelViewAnimationIfRunning(view);
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

    /** Gives the handle and title strip a forgiving drag path without stealing list-row gestures. */
    private void installSheetDrag(final View target, final View panel) {
        if (target == null || panel == null) return;
        final float touchSlop = Math.max(dp(8), ViewConfiguration.get(target.getContext()).getScaledTouchSlop());
        target.setOnTouchListener(new View.OnTouchListener() {
            // The target is a child of panel. Use raw coordinates so panel translation does not
            // feed back into the next event's local coordinates while the finger is down.
            float downRawX;
            float downRawY;
            float downPanelTranslation;
            long downAt;
            boolean dragging;
            boolean cancelled;

            @Override public boolean onTouch(View view, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    cancelViewAnimationIfRunning(panel);
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downPanelTranslation = panel.getTranslationY();
                    downAt = event.getEventTime();
                    dragging = false;
                    cancelled = false;
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (!dragging && !cancelled && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        // Lock after slop. Only a clearly downward gesture owns the panel; this
                        // keeps a title tap, horizontal control swipe, or upward scroll intact.
                        dragging = dy > touchSlop && dy > Math.abs(dx) * 1.12f;
                        if (!dragging) cancelled = true;
                    }
                    if (dragging) {
                        // Keep the surface under the finger. The measured height works for short
                        // landscape panels and avoids a second hidden threshold.
                        float maximum = panel.getHeight() > 0 ? panel.getHeight() : dp(480);
                        panel.setTranslationY(Math.max(0f, Math.min(maximum,
                                downPanelTranslation + dy)));
                    }
                    return true;
                }
                if (action == MotionEvent.ACTION_UP) {
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    long elapsed = Math.max(1L, event.getEventTime() - downAt);
                    float dismissDistance = Math.max(dp(64), panel.getHeight() > 0 ? panel.getHeight() * .25f : dp(120));
                    boolean quickDismiss = dy >= dp(24) && dy * 1000f / elapsed >= dp(820);
                    boolean close = dragging && (dy >= dismissDistance || quickDismiss) &&
                            dy > Math.abs(dx) * 1.05f;
                    if (close && panel == activeOverlayPanel) dismissOverlay();
                    else if (dragging)
                        panel.animate().translationY(0f).setDuration(reduceMotion() ? 80L : 150L).start();
                    dragging = false;
                    cancelled = false;
                    return true;
                }
                if (action == MotionEvent.ACTION_CANCEL) {
                    if (dragging)
                        panel.animate().translationY(0f).setDuration(reduceMotion() ? 80L : 120L).start();
                    dragging = false;
                    cancelled = false;
                    return true;
                }
                return true;
            }
        });
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
        showActionSheet(title, subtitle, items, icons, null, null, null, null, handler);
    }

    private void showActionSheet(String title, String subtitle, String[] items, int[] icons,
                                 int[] rowKinds, String[] sectionHeaders, final Runnable backAction,
                                 final SheetHandler handler) {
        showActionSheet(title, subtitle, items, icons, rowKinds, sectionHeaders, backAction, null, handler);
    }

    private void showActionSheet(String title, String subtitle, String[] items, int[] icons,
                                 int[] rowKinds, String[] sectionHeaders, final Runnable backAction,
                                 final Runnable dismissAction, final SheetHandler handler) {
        dismissOverlayForNavigation();
        final int sheetSurface = nightMode ? DARK_SHEET : WHITE;
        final int sheetText = nightMode ? DARK_TEXT : TEXT;
        final int sheetMuted = nightMode ? DARK_MUTED : MUTED;
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(104, 0, 0, 0));
        overlay.setClickable(true);
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismissOverlay(); }
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setTag(SHEET_TAG_SURFACE);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(roundRect(sheetSurface, 20));
        panel.setPadding(dp(8), dp(7), dp(8), dp(8));
        panel.setClickable(true);
        panel.setElevation(dp(12));

        View handle = new View(this);
        handle.setTag(SHEET_TAG_HANDLE);
        handle.setBackground(sheetHandleDrawable());
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(48), dp(32));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, 0);
        panel.addView(handle, handleParams);
        handle.setContentDescription("下拉关闭");
        installSheetDrag(handle, panel);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(backAction == null ? dp(10) : 0, 0, dp(2), dp(2));
        if (backAction != null) {
            BrowserIconView back = iconButton(BrowserIconView.BACK, "返回上一级");
            back.setTintColor(sheetText);
            header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
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
        heading.setTextSize(18f);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titles.addView(heading);
        if (subtitle != null && subtitle.length() > 0) {
            TextView sub = new TextView(this);
            sub.setTag(SHEET_TAG_MUTED);
            sub.setText(subtitle);
            sub.setTextColor(sheetMuted);
            sub.setTextSize(12f);
            sub.setPadding(0, dp(2), 0, 0);
            titles.addView(sub);
        }
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        BrowserIconView close = iconButton(BrowserIconView.CLOSE, "关闭");
        close.setTintColor(sheetText);
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        panel.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // The title strip is a generous drag target. Child buttons still receive their own taps,
        // while a swipe that starts beside them behaves like the native bottom-sheet gesture.
        installSheetDrag(header, panel);

        ScrollView scroll = new ScrollView(this);
        scroll.setTag(SHEET_TAG_PLAIN_SURFACE);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(sheetSurface);
        LinearLayout list = new LinearLayout(this);
        list.setTag(SHEET_TAG_PLAIN_SURFACE);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(2), 0, dp(2));
        list.setBackgroundColor(sheetSurface);
        scroll.addView(list);
        int rowsHeight = 0;
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            String section = sectionHeaders != null && i < sectionHeaders.length ? sectionHeaders[i] : null;
            if (section != null && section.trim().length() > 0) {
                TextView sectionLabel = new TextView(this);
                sectionLabel.setTag(SHEET_TAG_ACCENT);
                sectionLabel.setText(section.trim());
                sectionLabel.setTextColor(nightMode ? DARK_ACCENT : BLUE);
                sectionLabel.setTextSize(11.5f);
                sectionLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                sectionLabel.setGravity(Gravity.BOTTOM | Gravity.START);
                sectionLabel.setPadding(dp(12), dp(5), dp(8), dp(3));
                sectionLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                list.addView(sectionLabel, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
                rowsHeight += 24;
            }
            String item = items[i] == null ? "" : items[i];
            int detailBreak = item.indexOf('\n');
            String primaryText = detailBreak < 0 ? item : item.substring(0, detailBreak);
            String detailText = detailBreak < 0 ? "" : item.substring(detailBreak + 1).trim();
            final int rowKind = rowKinds != null && i < rowKinds.length ? rowKinds[i] : SHEET_ROW_NAVIGATE;
            final boolean toggleRow = rowKind == SHEET_ROW_TOGGLE_OFF || rowKind == SHEET_ROW_TOGGLE_ON;
            int rowHeight = detailText.length() == 0 ? 48 : 60;
            rowsHeight += rowHeight;
            LinearLayout row = new LinearLayout(this);
            row.setTag(SHEET_TAG_ROW);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6), 0, dp(6), 0);
            // Never inherit a selectable drawable from the Activity's original theme. Explicitly
            // painting the row surface prevents stale dark rounded blocks after a live theme
            // switch; BrowserIconView supplies the small pressed affordance independently.
            row.setBackgroundColor(sheetSurface);
            row.setForeground(null);
            row.setStateListAnimator(null);
            row.setBackgroundTintList(null);
            row.setClickable(true);
            BrowserIconView icon = iconButton(icons != null && i < icons.length ? icons[i] : BrowserIconView.MENU, primaryText);
            icon.setClickable(false);
            icon.setTintColor(sheetText);
            row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(rowHeight)));
            TextView label = new TextView(this);
            label.setTag(SHEET_TAG_PRIMARY);
            label.setText(primaryText);
            label.setTextColor(sheetText);
            label.setTextSize(14.5f);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (detailText.length() > 0) {
                LinearLayout labels = new LinearLayout(this);
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.setGravity(Gravity.CENTER_VERTICAL);
                labels.setPadding(dp(5), 0, dp(3), 0);
                labels.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView detail = new TextView(this);
                detail.setTag(SHEET_TAG_MUTED);
                detail.setText(detailText);
                detail.setTextColor(sheetMuted);
                detail.setTextSize(12f);
                detail.setSingleLine(true);
                detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
                detail.setPadding(0, dp(2), 0, 0);
                labels.addView(detail, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                row.addView(labels, new LinearLayout.LayoutParams(0, dp(rowHeight), 1f));
            } else {
                label.setGravity(Gravity.CENTER_VERTICAL);
                label.setPadding(dp(5), 0, dp(3), 0);
                row.addView(label, new LinearLayout.LayoutParams(0, dp(rowHeight), 1f));
            }
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
                        BLUE, nightMode ? 0xffbdc1c6 : 0xff757575
                }));
                toggle.setTrackTintList(new ColorStateList(states, new int[] {
                        0x761a73e8, nightMode ? 0xff505459 : 0xffbdc1c6
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
                row.setPadding(dp(6), 0, dp(14), 0);
                }
                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        dismissOverlayForNavigation();
                        handler.onItem(index);
                    }
                });
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(rowHeight));
            list.addView(row, rowParams);
        }
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int safeTop = Math.max(0, systemInsetTop - rootFrame.getPaddingTop());
        int safeBottom = Math.max(0, systemInsetBottom - rootFrame.getPaddingBottom());
        int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * .90f) - safeTop - safeBottom;
        int desiredHeight = dp(100 + rowsHeight);
        int panelHeight = Math.min(maxHeight, desiredHeight);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, panelHeight, Gravity.BOTTOM);
        panelParams.setMargins(dp(6), safeTop, dp(6), dp(6) + safeBottom);
        overlay.addView(panel, panelParams);
        rootFrame.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activeOverlay = overlay;
        activeOverlayPanel = panel;
        activeOverlaySheet = true;
        activeOverlayBackAction = backAction;
        activeOverlayDismissAction = dismissAction;
        overlayDismissInProgress = false;
        // Repaint the fully-built hierarchy so no platform drawable from a previous theme
        // survives a live night-mode switch.
        themeSheetTree(panel);
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
        final Runnable dismissAction = activeOverlayDismissAction;
        activeOverlay = null;
        activeOverlayPanel = null;
        activeOverlayBackAction = null;
        activeOverlayDismissAction = null;
        activeOverlaySheet = false;
        overlayDismissInProgress = true;
        Motion.hideOverlay(removing, panel, sheet, reduceMotion(), new Runnable() {
            @Override public void run() {
                if (removing.getParent() instanceof ViewGroup) ((ViewGroup) removing.getParent()).removeView(removing);
                overlayDismissInProgress = false;
                if (!activityDestroyed && dismissAction != null) dismissAction.run();
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
        activeOverlayDismissAction = null;
        activeOverlaySheet = false;
        overlayDismissInProgress = false;
        cancelViewAnimationIfRunning(removing);
        cancelViewAnimationIfRunning(panel);
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
        // Store readiness and view readiness can arrive in either order. Keep the prepared
        // payload until both exist, then consume it once: a later startup callback must not
        // remove a registration just because the token/payload fields were already cleared.
        if (target == null || !startupDataReady || startupScriptPayloadsApplied) return;
        startupScriptPayloadsApplied = true;
        String token = startupScriptToken;
        List<UserScriptStore.DocumentStartPayload> payloads = startupScriptPayloads;
        startupScriptToken = "";
        startupScriptPayloads = Collections.emptyList();
        installDocumentStartUserScripts(target, token, payloads);
    }

    private void installDocumentStartUserScripts(WebView target) {
        // configureWebView() runs before the first WebView is assigned to the Activity. Reuse
        // its background-prepared payload instead of copying and registering it a second time
        // on the UI thread; subsequent tabs keep the normal per-view token/registration path.
        if (!startupReady && webView == null) {
            installPreparedDocumentStartUserScripts(target);
            return;
        }
        if (target == null || scriptStore == null || !scriptStore.hasEnabledScripts()) {
            removeDocumentStartUserScripts(target);
            return;
        }
        String token = UrlCleaner.randomToken();
        List<UserScriptStore.DocumentStartPayload> payloads = scriptStore.buildDocumentStartPayloads(token);
        installDocumentStartUserScripts(target, token, payloads);
    }

    /** UI-only registration; expensive payload assembly is performed before this call. */
    private void installDocumentStartUserScripts(WebView target, String token,
                                                 List<UserScriptStore.DocumentStartPayload> payloads) {
        removeDocumentStartUserScripts(target);
        if (target == null || token == null || token.length() < 32 || payloads == null || payloads.isEmpty() ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        ArrayList<ScriptHandler> handlers = new ArrayList<ScriptHandler>(payloads.size());
        try {
            for (UserScriptStore.DocumentStartPayload payload : payloads) {
                if (payload == null || payload.source.length() == 0 || payload.allowedOriginRules.isEmpty()) continue;
                handlers.add(WebViewCompat.addDocumentStartJavaScript(target, payload.source,
                        payload.allowedOriginRules));
            }
            if (handlers.isEmpty()) return;
            scriptBridgeTokens.put(target, token);
            scriptHandlers.put(target, Collections.unmodifiableList(handlers));
            documentStartScriptViews.add(target);
        } catch (RuntimeException error) {
            for (ScriptHandler handler : handlers) {
                try { handler.remove(); } catch (RuntimeException ignored) {}
            }
            scriptBridgeTokens.remove(target);
            scriptHandlers.remove(target);
            documentStartScriptViews.remove(target);
        }
    }

    private void removeDocumentStartUserScripts(WebView target) {
        if (target == null) return;
        List<ScriptHandler> handlers = scriptHandlers.remove(target);
        if (handlers != null) for (ScriptHandler handler : handlers)
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
                final ArrayList<List<UserScriptStore.DocumentStartPayload>> payloads =
                        new ArrayList<List<UserScriptStore.DocumentStartPayload>>(live.size());
                for (int i = 0; i < live.size(); i++) payloads.add(Collections.<UserScriptStore.DocumentStartPayload>emptyList());
                if (store != null && store.hasEnabledScripts()) for (int i = 0; i < live.size(); i++) {
                    tokens[i] = UrlCleaner.randomToken();
                    payloads.set(i, store.buildDocumentStartPayloads(tokens[i]));
                }
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (activityDestroyed || generation != scriptRegistrationGeneration) return;
                        for (int i = 0; i < live.size(); i++)
                            installDocumentStartUserScripts(live.get(i), tokens[i], payloads.get(i));
                        if (reload && webView != null && isNetworkPage(webView.getUrl())) {
                            reloadWebView(webView);
                            toast("用户脚本权限已更新，当前页面正在重新加载");
                        }
                    }
                });
            }
        };
        if (!executeTask(scriptExecutor, prepare)) {
            for (WebView view : live) installDocumentStartUserScripts(view);
            if (reload && webView != null && isNetworkPage(webView.getUrl())) reloadWebView(webView);
        }
    }

    private void cancelScriptRequests(WebView target) {
        String token = scriptBridgeTokens.get(target);
        if (userScriptNetwork != null) userScriptNetwork.cancel(token);
    }

    private long scriptDocumentGeneration(WebView target) {
        Long generation = target == null ? null : scriptDocumentGenerations.get(target);
        return generation == null ? 0L : generation;
    }

    private void advanceScriptDocumentGeneration(WebView target) {
        if (target == null) return;
        long previous = scriptDocumentGeneration(target);
        scriptDocumentGenerations.put(target, previous == Long.MAX_VALUE ? 1L : previous + 1L);
    }

    private void configureWebView(final WebView target) {
        initialNavigationGuards.put(target, new InitialNavigationGuard());
        navigationGestureGuards.put(target, new NavigationGestureGuard(
                ViewConfiguration.get(this).getScaledTouchSlop()));
        mainFrameNavigationContexts.put(target, new MainFrameNavigationContext());
        WebSettings settings = target.getSettings();
        WebViewPolicy.applySecureDefaults(settings, WebSettings.LOAD_DEFAULT);
        settings.setLoadsImagesAutomatically(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        // target=_blank and user-gesture popups are promoted to real browser tabs.
        settings.setSupportMultipleWindows(true);
        settings.setTextZoom(100);
        String initialUserAgent = settings.getUserAgentString();
        String mobileUserAgent = WebViewPolicy.mobileUserAgent(initialUserAgent);
        mobileUserAgents.put(target, mobileUserAgent);
        applyDesktopMode(target, settings, initialUserAgent);
        applyPerformanceMode(target, settings);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(target, acceptThirdPartyCookies);
        // Configure the native renderer before any URL can be loaded. No page-wide JavaScript or
        // DOM traversal is involved in dark mode.
        applyDarkMode(target, settings);
        installDocumentStartUserScripts(target);

        EdgeNavigationController.attach(target, new EdgeNavigationController.Callback() {
            @Override public void onTouchEvent(MotionEvent event) {
                observeNavigationTouch(target, event);
                observeImmersiveTouch(target, event);
            }
            @Override public boolean isEnabled() { return edgeNavigationEnabled; }
            @Override public boolean canGoBack() { return target.canGoBack(); }
            @Override public boolean canGoForward() { return target.canGoForward(); }
            @Override public void goBack() {
                goBackWebView(target);
                scheduleRestoredHomeRepair(target);
            }
            @Override public void goForward() { goForwardWebView(target); }
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
                clearMainFrameRequest(view);
                if (adPickerSessions.containsKey(view)) {
                    clearMainFrameNavigation(view);
                    return true;
                }
                // Check using the source document before destination settings replace its host.
                // Native touch state also protects providers using the legacy callback.
                if (shouldBlockPageNavigation(view, url, false, false)) return true;
                rememberAcceptedTouchNavigation(view, url, false, false);
                rememberMainFrameNavigation(view, url);
                if (view != webView) return false;
                if (isNetworkPage(url)) {
                    prepareNetworkDestination(view, url);
                    watchInitialNetworkNavigation(view, url);
                }
                return handleNavigation(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (adPickerSessions.containsKey(view)) {
                    clearMainFrameNavigation(view);
                    return true;
                }
                if (request == null) return false;
                // Subframe navigation must never change the active page host or its
                // WebSettings. Doing so breaks SPA controls while scrolling still works.
                if (!request.isForMainFrame()) return false;
                clearMainFrameRequest(view);
                String url = request.getUrl().toString();
                if (shouldBlockPageNavigation(view, url, request.hasGesture(), request.isRedirect())) return true;
                rememberAcceptedTouchNavigation(view, url, request.hasGesture(), request.isRedirect());
                rememberMainFrameNavigation(view, url);
                if (view != webView) return false;
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
                // shouldInterceptRequest() already observes media candidates off the UI
                // thread. onLoadResource is a redundant UI-thread callback for every static
                // asset; scanning it here makes image/script-heavy pages compete with Chromium
                // for the frame budget. The explicit media probe still covers DOM/blob players
                // when the media center is opened.
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // URL changes from pushState/replaceState keep this generation. A real main-frame
                // navigation advances it before cancelling requests from the outgoing document.
                advanceScriptDocumentGeneration(view);
                NavigationGestureGuard gestureGuard = navigationGestureGuards.get(view);
                String initiatingDocument = protectedNavigationSourceDocument(view);
                long startedAt = SystemClock.uptimeMillis();
                // POST may skip override, and some providers start the page before the
                // request callback. Preserve a verified drag before replacing source state.
                if (gestureGuard != null && gestureGuard.hasRecentDrag(initiatingDocument, startedAt) &&
                        navigationProtectionActive(navigationSourceHost(view, initiatingDocument))) {
                    gestureGuard.onNavigationAccepted(initiatingDocument, url, true, false, startedAt);
                    rememberMainFrameNavigation(view, url);
                }
                MainFrameNavigationContext navigationContext = mainFrameNavigationContexts.get(view);
                if (navigationContext != null) navigationContext.onPageStarted(url);
                if (gestureGuard != null) gestureGuard.onPageStarted(url);
                popupSourceDocuments.remove(view);
                rememberNavigationSourceDocument(view, url);
                if (view == webView) showBrowserChrome(false);
                // A chooser belongs to the document that opened it. If that document
                // navigates before Android returns a result, never deliver the selected
                // URI to the replacement page.
                if (fileChooserCallback != null && fileChooserView == view) finishFileChooser(null);
                String startedHost = hostOf(url);
                cacheFavicon(startedHost, favicon);
                startedPageUrls.put(view, navigationDocumentUrl(url));
                unresponsiveWebViews.remove(view);
                adPickerViews.remove(view);
                adPickerSessions.remove(view);
                if (isQrScannerUrl(url)) qrScannerViews.add(view); else qrScannerViews.remove(view);
                cancelScriptRequests(view);
                if (InternalNavigationPolicy.shouldClearHomeTrust(isHomeUrl(url), url)) {
                    trustedHomeViews.remove(view);
                    customHomeViews.remove(view);
                }
                boolean offline = isOfflineUrl(url);
                if (offline) prepareOfflineDestination(view, url);
                else if (isInternalBrowserUrl(url)) prepareHomeDestination(view);
                else {
                    applyPageAccessPolicy(view, url);
                    applySiteSettings(view, startedHost);
                    // POST and history/provider navigations can bypass shouldOverrideUrlLoading.
                    prepareNavigationConnections(url);
                    installConnectionHintScript(view, url, true);
                }
                updateTabForView(view, url, view.getTitle());
                cosmeticInjected.remove(view);
                if (url != null) {
                    pageHosts.put(view, startedHost);
                    adBlockActiveByView.put(view, Boolean.valueOf(isAdBlockActiveForHost(startedHost)));
                }
                if (view != webView) return;
                // Cancel queued work from the previous document before arranging any early
                // cosmetic task for this one. The old ordering could enqueue the task and then
                // immediately clear it in advanceNavigationSequence().
                advanceNavigationSequence();
                if (url != null) {
                    currentPageUrl = url;
                    currentPageHost = startedHost;
                    if (stagedCredential != null && !currentPageHost.equalsIgnoreCase(stagedCredential.host)) {
                        stagedCredential.clear();
                        stagedCredential = null;
                        stagedCredentialAt = 0L;
                    }
                    mediaSniffer.beginPage(url);
                }
                // Stop the previous document's terminal fade before the new page
                // starts reporting progress. Regular progress updates must not
                // cancel this animation on every callback.
                cancelViewAnimationIfRunning(progressBar);
                blockedAtPageStart = adBlock.getBlockedCount();
                if (view == webView && refreshButton != null) {
                    cancelViewAnimationIfRunning(refreshButton);
                    refreshButton.animate().rotationBy(180f).setDuration(reduceMotion() ? 100L : 220L).start();
                }
                pageCommitted = false;
                pageFinished = false;
                preparedInjection = null;
                schedulePageEnhancements(url, navigationSequence);
                if (url != null && isAdBlockActiveForHost(currentPageHost) &&
                        adBlock.requiresEarlyCosmetic(currentPageHost)) {
                    // Let document-start user scripts claim the navigation worker first; CSS
                    // construction remains off the UI thread and can follow in the same window.
                    scheduleCosmeticInjection(view, currentPageHost);
                }
                requestChromeUpdate();
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                retireMainFrameNavigation(view, url);
                String expectedDocument = startedPageUrls.get(view);
                if (expectedDocument != null && !expectedDocument.equals(navigationDocumentUrl(url))) return;
                NavigationGestureGuard gestureGuard = navigationGestureGuards.get(view);
                if (gestureGuard != null) gestureGuard.onNavigationCommitted(url);
                InitialNavigationGuard guard = initialNavigationGuards.get(view);
                if (guard != null && guard.acknowledge(url) && view == webView && startupNavigationAt != 0L)
                    recordStartupNavigation("首航已提交", url);
                if (view != webView) return;
                pageCommitted = true;
                injectPreparedStart(navigationSequence);
                // Cached rules compile independently of this callback. Only their network
                // refresh waits for the completed document's idle window.
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                retireMainFrameNavigation(view, url);
                // History can restore the cached home document without onPageStarted. Its
                // last started URL then still belongs to the page we just left; authenticate
                // the restored document before that stale-callback guard returns.
                String settledUrl = view.getUrl() == null ? url : view.getUrl();
                if (isHomeUrl(settledUrl)) verifyTrustedHome(view);
                String expectedDocument = startedPageUrls.get(view);
                if (expectedDocument != null && !expectedDocument.equals(navigationDocumentUrl(url))) {
                    if (view == webView && homeThemeRepairPending &&
                            (isHomeUrl(url) || isHomeUrl(view.getUrl()))) {
                        view.post(new Runnable() {
                            @Override public void run() {
                                if (!activityDestroyed && webView == view &&
                                        isHomeUrl(currentPageUrl) && isHomeUrl(view.getUrl()) && homeThemeRepairPending)
                                    showHome(true);
                            }
                        });
                    }
                    return;
                }
                NavigationGestureGuard gestureGuard = navigationGestureGuards.get(view);
                if (gestureGuard != null) gestureGuard.onNavigationCommitted(url);
                if (isHomeUrl(url) || isHomeUrl(settledUrl)) {
                    // A back gesture can restore a homepage document generated before the
                    // latest theme switch. Native chrome already reflects the new mode, so
                    // replace that stale document before it becomes visible as a mixed page.
                    if (view == webView && (homeThemeRepairPending || !isHomeRenderCurrent(view))) {
                        view.post(new Runnable() {
                            @Override public void run() {
                                if (!activityDestroyed && webView == view &&
                                        isHomeUrl(currentPageUrl) && isHomeUrl(view.getUrl()) &&
                                        (homeThemeRepairPending || !isHomeRenderCurrent(view)))
                                    showHome(true);
                            }
                        });
                        return;
                    }
                }
                InitialNavigationGuard guard = initialNavigationGuards.get(view);
                if (guard != null && guard.acknowledge(settledUrl) && view == webView && startupNavigationAt != 0L)
                    recordStartupNavigation("首航已完成", settledUrl);
                updateTabForView(view, settledUrl, view.getTitle());
                if (settledUrl != null) {
                    String finishedHost = hostOf(settledUrl);
                    pageHosts.put(view, finishedHost);
                    adBlockActiveByView.put(view, Boolean.valueOf(isAdBlockActiveForHost(finishedHost)));
                    if (isNetworkPage(settledUrl) && isAdBlockActiveForHost(finishedHost)) {
                        // An explicit site rule may already have been scheduled at page start.
                        // Keep that ticket for the same document so the CSS is built only once;
                        // generic subscription selectors still start at page finish.
                        boolean earlyCosmetic = adBlock.requiresEarlyCosmetic(finishedHost);
                        if (!earlyCosmetic || cosmeticInjected.get(view) == null) {
                            if (!earlyCosmetic) cosmeticInjected.remove(view);
                            scheduleCosmeticInjection(view, finishedHost);
                        }
                    }
                }
                if (view != webView) return;
                if (settledUrl != null) {
                    currentPageUrl = settledUrl;
                    currentPageHost = hostOf(settledUrl);
                }
                pageFinished = true;
                pageCommitted = true;
                // onPageFinished can precede window load. The media scheduler verifies the
                // actual load end, then an idle/frame boundary, before wrapping fetch/XHR.
                scheduleLiveMediaCapture(view, settledUrl, navigationSequence);
                if (view == webView && isNetworkPage(settledUrl)) scheduleDeferredStartupWork();
                collectConnectionHints(view, settledUrl, navigationSequence);
                if (refreshButton != null) refreshButton.animate().rotation(0f).setDuration(120L).start();
                injectPreparedStart(navigationSequence);
                injectPreparedEnd(navigationSequence);
                if (dataStore != null && !isInternalBrowserUrl(settledUrl))
                    dataStore.recordVisit(view.getTitle(), settledUrl);
                updateCurrentTab(settledUrl, view.getTitle());
                persistSession();
                requestChromeUpdate();
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                // pushState/replaceState can change the page identity without onPageStarted.
                // Keep this separate from startedPageUrls, which also guards page callbacks.
                if (url != null) rememberNavigationSourceDocument(view, url);
                updateTabForView(view, url, view.getTitle());
                if (url != null) pageHosts.put(view, hostOf(url));
                // Cached history restoration may also skip onPageFinished. A URL alone does
                // not grant command access: the probe checks the document's private token.
                if (isHomeUrl(view.getUrl())) verifyTrustedHome(view);
                if (view != webView) return;
                String previousUrl = currentPageUrl;
                if (url != null) {
                    currentPageUrl = url;
                    currentPageHost = hostOf(url);
                }
                String historyUrl = url == null ? view.getUrl() : url;
                if ((isHomeUrl(historyUrl) || isHomeUrl(view.getUrl())) &&
                        (homeThemeRepairPending || !isHomeRenderCurrent(view))) {
                    // History restoration may use the provider's cached document and skip
                    // onPageFinished. Repair the stale themed homepage at the history callback.
                    view.post(new Runnable() {
                        @Override public void run() {
                            if (!activityDestroyed && webView == view &&
                                    isHomeUrl(currentPageUrl) && isHomeUrl(view.getUrl()) &&
                                    (homeThemeRepairPending || !isHomeRenderCurrent(view))) showHome(true);
                        }
                    });
                    return;
                }
                updateCurrentTab(url, view.getTitle());
                requestChromeUpdate();
            }

            public boolean onRenderProcessGone(final WebView view, android.webkit.RenderProcessGoneDetail detail) {
                // A renderer callback can arrive after a tab was closed or frozen. Such a
                // detached view must not tear down the surviving tab set during recovery.
                if (activityDestroyed || view == null || tabForView(view) == null) return true;
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
                if (view == webView && !activityDestroyed) {
                    toast("证书验证失败，已停止加载");
                    String failed = error == null ? currentPageUrl : error.getUrl();
                    String current = view.getUrl();
                    if (failed != null && current != null &&
                            sameCredentialDocument(failed, current) &&
                            hostOf(failed).equalsIgnoreCase(currentPageHost)) {
                        maybeOfferCompatibilityMode(failed, "主页面证书或 HTTPS 加载失败");
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame() && request.getUrl() != null)
                    retireMainFrameNavigation(view, request.getUrl().toString());
                if (view == webView && request != null && request.isForMainFrame() &&
                        error != null && compatibilityRelevantError(error.getErrorCode())) {
                    String failed = request.getUrl() == null ? currentPageUrl : request.getUrl().toString();
                    String current = view.getUrl();
                    if (current != null && sameCredentialDocument(failed, current))
                        maybeOfferCompatibilityMode(failed, "主页面加载失败（" + error.getErrorCode() + "）");
                }
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, final android.webkit.HttpAuthHandler handler, String host, String realm) {
                if (view != webView || handler == null) { if (handler != null) handler.cancel(); return; }
                final String expectedUrl = view.getUrl();
                final String expectedHost = host == null ? "" : host.toLowerCase(Locale.US);
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
                            @Override public void onClick(DialogInterface dialog, int which) {
                                String current = view.getUrl();
                                if (activityDestroyed || view != webView || !sameCredentialDocument(expectedUrl, current) ||
                                        !expectedHost.equalsIgnoreCase(hostOf(current))) {
                                    handler.cancel();
                                    toast("页面已经变化，已取消身份验证");
                                    return;
                                }
                                handler.proceed(username.getText().toString(), password.getText().toString());
                            }
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
                    if (newProgress > 10 && guard != null && !guard.isAcknowledged()) {
                        String url = view.getUrl();
                        if (guard.acknowledge(url) && startupNavigationAt != 0L)
                            recordStartupNavigation("首航已响应", url);
                    }
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
                cacheFavicon(hostOf(view == null ? null : view.getUrl()), icon);
            }

            @Override
            public boolean onJsPrompt(final WebView view, String url, String message,
                                      final String defaultValue, JsPromptResult result) {
                if (handleQrScannerPrompt(view, message, defaultValue, result)) return true;
                if (handleAdPickerPrompt(view, message, defaultValue, result)) return true;
                if (message != null && (message.startsWith("__MEDIAN_CREDENTIAL__") ||
                        message.startsWith("__MEDIAN_AUTOFILL__"))) {
                    // Legacy page-controlled password markers are inert. Do not parse them or
                    // let them reach Chromium's prompt UI.
                    if (result != null) result.confirm("");
                    return true;
                }
                if (homeToken != null && homeToken.equals(message)) {
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
                if (adPickerSessions.containsKey(view)) return false;
                SiteSettingsStore.SiteSettings site = siteSettingsStore == null
                        ? new SiteSettingsStore.SiteSettings() : siteSettingsStore.forHost(pageHostFor(view));
                int popups = site.get(SiteSettingsStore.POPUPS);
                if (popups == SiteSettingsStore.BLOCK || (!isUserGesture && popups != SiteSettingsStore.ALLOW) ||
                        resultMsg == null || !(resultMsg.obj instanceof WebView.WebViewTransport)) return false;
                // A drag is not an intentional request for a new window. Explicit per-site
                // ALLOW continues to permit automatic popups as the user requested.
                NavigationGestureGuard gestureGuard = navigationGestureGuards.get(view);
                String sourceDocument = navigationSourceDocument(view);
                if (popups != SiteSettingsStore.ALLOW && isUserGesture &&
                        navigationProtectionActive(navigationSourceHost(view, sourceDocument)) &&
                        gestureGuard != null && gestureGuard.hasRecentDrag(sourceDocument, SystemClock.uptimeMillis())) return false;
                if (tabs.size() >= MAX_TABS) {
                    if (isUserGesture) toast("标签页已达到 " + MAX_TABS + " 个上限");
                    return false;
                }
                BrowserTab tab = new BrowserTab();
                WebView popup = createConfiguredWebView();
                if (sourceDocument.length() > 0) popupSourceDocuments.put(popup, sourceDocument);
                bindWebViewToTab(tab, popup);
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
                if (view != webView) {
                    try { if (callback != null) callback.onReceiveValue(null); } catch (RuntimeException ignored) {}
                    return true;
                }
                finishFileChooser(null);
                fileChooserCallback = callback;
                fileChooserView = view;
                fileChooserDocumentUrl = navigationDocumentUrl(view.getUrl());
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
                    finishFileChooser(null);
                    toast("没有可用的文件选择器");
                    return true;
                }
            }
        });

        target.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                                  String mimetype, long contentLength) {
                if (adPickerSessions.containsKey(target)) return;
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
        if ((!image && !link) || !isNetworkPage(url)) return false;
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
        String[] items = new String[] { "在新标签页打开", "在后台标签页打开", "复制地址", "分享地址",
                image ? "下载图片" : "下载链接", "发送到外部下载器" };
        int[] icons = new int[] { BrowserIconView.PLUS, BrowserIconView.TABS, BrowserIconView.PLUS,
                BrowserIconView.SHARE, BrowserIconView.STORAGE, BrowserIconView.STORAGE };
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
                } else if (which == 4) {
                    String ua = source.getSettings().getUserAgentString();
                    enqueueDownload(source, url, ua, null, image ? "image/*" : "application/octet-stream");
                } else sendToExternalDownloader(url);
            }
        });
        return true;
    }

    private void openUrlInNewTab(String url, boolean foreground) {
        if (!isNetworkPage(url)) return;
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
        Uri directory = DownloadDirectory.selectedTree(this);
        if (directory != null && !DownloadDirectory.hasWriteGrant(this, directory)) {
            toast("下载文件夹授权已失效，请在下载设置中重新选择文件夹");
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
        if (message.length() > SCRIPT_BRIDGE_MAX_MESSAGE_CHARS) {
            result.confirm(bridgeError("request too large"));
            return true;
        }
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
            } else if ("getTab".equals(action)) {
                String tabId = scriptTabIds.get(source);
                String raw = tabId == null ? "{}" : scriptTabValues.get(scriptId + "|" + tabId);
                Object value = new JSONTokener(raw == null ? "{}" : raw).nextValue();
                if (!(value instanceof JSONObject)) value = new JSONObject();
                response = new JSONObject().put("ok", true).put("v", value).toString();
            } else if ("saveTab".equals(action)) {
                String tabId = scriptTabIds.get(source);
                String raw = args.optString("v", "{}");
                Object value = raw.length() <= 1024 * 1024 ? new JSONTokener(raw).nextValue() : null;
                boolean allowed = tabId != null && value instanceof JSONObject;
                if (allowed) allowed = scriptTabValues.put(scriptId + "|" + tabId, raw);
                response = bridgeOk(allowed);
            } else if ("getTabs".equals(action)) {
                JSONObject values = new JSONObject();
                String tabKeyPrefix = scriptId + "|";
                for (Map.Entry<String, String> entry : scriptTabValues.entries()) {
                    if (!entry.getKey().startsWith(tabKeyPrefix)) continue;
                    try {
                        Object value = new JSONTokener(entry.getValue()).nextValue();
                        if (value instanceof JSONObject)
                            values.put(entry.getKey().substring(tabKeyPrefix.length()), value);
                    } catch (Exception ignored) {}
                }
                String currentTab = scriptTabIds.get(source);
                if (currentTab != null && !values.has(currentTab)) values.put(currentTab, new JSONObject());
                response = new JSONObject().put("ok", true).put("v", values).toString();
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
                toast(UiText.ellipsize(notice, 240));
                response = bridgeOk(true);
            } else if ("cookie".equals(action)) {
                response = userScriptNetwork().handleCookie(currentUrl, args);
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
                response = userScriptNetwork().request(source, token, scriptId, args, currentUrl,
                        scriptDocumentGeneration(source));
            } else if ("xhrAbort".equals(action)) {
                userScriptNetwork().abort(token, args.optString("i", ""));
                response = bridgeOk(true);
            } else response = bridgeError("unknown action");
        } catch (Exception e) {
            response = bridgeError("bad request");
        }
        result.confirm(response);
        return true;
    }

    /** Allocate privileged networking only when a userscript actually uses it. */
    private UserScriptNetwork userScriptNetwork() {
        if (userScriptNetwork == null) {
            userScriptNetwork = new UserScriptNetwork(uiHandler, new UserScriptNetwork.Host() {
                @Override public boolean executeNetworkTask(Runnable task) {
                    if (scriptNetworkExecutor == null || scriptNetworkExecutor.isShutdown())
                        throw new RejectedExecutionException("脚本网络服务不可用");
                    return executeTask(scriptNetworkExecutor, task);
                }
                @Override public boolean canConnect(String scriptId, String url, String pageUrl) {
                    return scriptStore.canConnect(scriptId, url, pageUrl);
                }
                @Override public boolean isCurrentRequestDocument(WebView source, String token,
                        String scriptId, long documentGeneration) {
                    if (activityDestroyed || source == null) return false;
                    String expected = scriptBridgeTokens.get(source);
                    String current = source.getUrl();
                    return expected != null && token.equals(expected) &&
                            documentGeneration == scriptDocumentGeneration(source) &&
                            scriptStore.matchesUrl(scriptId, current);
                }
                @Override public void scheduleCookieFlush() { MainActivity.this.scheduleCookieFlush(); }
            });
        }
        return userScriptNetwork;
    }

    private void scheduleCookieFlush() {
        cookieFlushScheduler.schedule();
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
            URL parsed = NetworkSecurity.parseHttpUrl(value);
            return !"median.invalid".equalsIgnoreCase(parsed.getHost());
        } catch (Exception ignored) { return false; }
    }

    private WebResourceResponse interceptRequest(WebView source, String requestUrl) {
        Uri uri = null;
        try { uri = Uri.parse(requestUrl); } catch (RuntimeException ignored) {}
        WebResourceResponse homeAsset = interceptHomeAsset(source, uri);
        if (homeAsset != null) return homeAsset;
        String pageHost = pageHostFor(source);
        if (cachedAdBlockActive(source, pageHost) &&
                (uri == null ? adBlock.shouldBlock(requestUrl, pageHost) : adBlock.shouldBlock(uri, pageHost))) {
            return blockedResponse();
        }
        observeMediaIfLikely(source, uri, "", pageHost);
        return null;
    }

    private WebResourceResponse interceptRequest(WebView source, Uri requestUri) {
        WebResourceResponse homeAsset = interceptHomeAsset(source, requestUri);
        if (homeAsset != null) return homeAsset;
        String pageHost = pageHostFor(source);
        if (cachedAdBlockActive(source, pageHost) && adBlock.shouldBlock(requestUri, pageHost)) {
            return blockedResponse();
        }
        observeMediaIfLikely(source, requestUri, "", pageHost);
        return null;
    }

    private WebResourceResponse interceptRequest(WebView source, WebResourceRequest request) {
        Uri requestUri = request.getUrl();
        boolean mainFrame = request.isForMainFrame();
        WebResourceResponse homeAsset = interceptHomeAsset(source, requestUri);
        if (homeAsset != null) return homeAsset;
        if (customHomeViews.contains(source) && !mainFrame && requestUri != null &&
                ("http".equalsIgnoreCase(requestUri.getScheme()) || "https".equalsIgnoreCase(requestUri.getScheme())) &&
                !"median.invalid".equalsIgnoreCase(requestUri.getHost())) return blockedResponse();
        String pageHost = pageHostFor(source);
        // The context only applies to the main-frame request that starts a navigation. Avoid
        // taking its synchronized lock for every image/script/font/subframe callback; busy pages
        // can dispatch hundreds of those while the navigation ticket remains untouched.
        MainFrameNavigationContext navigationContext = mainFrame ? mainFrameNavigationContexts.get(source) : null;
        MainFrameNavigationContext.Snapshot initiating = mainFrame &&
                navigationContext != null && requestUri != null
                ? navigationContext.take(requestUri.toString(), true) : null;
        NavigationGestureGuard gestureGuard = mainFrame ? navigationGestureGuards.get(source) : null;
        // Request callbacks run off the UI thread. Read only cached source state here;
        // WebView.getUrl() is forbidden and destination settings may already be applied.
        String sourceDocument = "";
        long requestAt = mainFrame ? SystemClock.uptimeMillis() : 0L;
        if (mainFrame) {
            sourceDocument = popupSourceDocuments.get(source);
            if (sourceDocument == null || sourceDocument.length() == 0)
                sourceDocument = navigationSourceDocuments.get(source);
            if (sourceDocument == null) sourceDocument = "";
            if (gestureGuard != null)
                sourceDocument = gestureGuard.navigationSourceDocument(sourceDocument, requestAt);
        }
        String sourceHost = mainFrame ? hostOf(sourceDocument) : "";
        String ruleHost = initiating != null ? initiating.sourceHost :
                mainFrame && sourceHost.length() > 0 ? sourceHost : pageHost;
        boolean adBlockActive = initiating != null ? initiating.protectionEnabled && adBlockEnabled :
                mainFrame && sourceHost.length() > 0 ? navigationProtectionActive(sourceHost) :
                        cachedAdBlockActive(source, pageHost);
        // Request.getRequestHeaders() crosses the WebView provider boundary. Read it for rules
        // that need resource type and for a narrow set of extensionless media endpoints. The
        // performance preset never changes blocking or navigation protection.
        boolean readAccept = shouldReadAcceptHeader(requestUri, mainFrame);
        // Only extensionless media header probes need URL classification before blocking.
        // Reuse that result after blocking; ordinary static assets are scanned just once and
        // blocked assets never pay for optional media discovery.
        boolean mediaUrlClassified = source == webView && !mainFrame && requestUri != null && readAccept;
        boolean inspectMedia = mediaUrlClassified && MediaResourceSniffer.isLikelyMediaRequest(
                requestUri.getEncodedPath(), requestUri.getEncodedQuery(), "");
        boolean mediaHeaderNeeded = mediaUrlClassified && MediaResourceSniffer.shouldProbeMediaHeaders(
                requestUri.getEncodedPath(), requestUri.getEncodedQuery(), inspectMedia);
        boolean needHeaders = mainFrame ||
                (adBlockActive && adBlock.requiresRequestHeaders() && readAccept) ||
                mediaHeaderNeeded;
        Map<String, String> requestHeaders = needHeaders ? request.getRequestHeaders() : null;
        String accept = readAccept ? requestHeader(requestHeaders, "Accept") : "";
        if (adBlockActive && adBlock.shouldBlock(requestUri, ruleHost, accept, mainFrame)) {
            boolean preserveDocument = mainFrame && gestureGuard != null && requestUri != null &&
                    gestureGuard.shouldBlockNavigation(sourceDocument, requestUri.toString(), request.hasGesture(),
                            request.isRedirect(), requestAt);
            if (preserveDocument)
                gestureGuard.onNavigationBlocked(sourceDocument, requestUri.toString(), requestAt);
            return preserveDocument ? blockedNavigationResponse() : blockedResponse();
        }
        if (mainFrame && adBlockActive && gestureGuard != null && requestUri != null) {
            String targetUrl = requestUri.toString();
            if (gestureGuard.shouldBlockNavigation(sourceDocument, targetUrl, request.hasGesture(),
                    request.isRedirect(), requestAt) &&
                    !adBlock.isExplicitlyAllowed(requestUri, ruleHost, "text/html", true)) {
                gestureGuard.onNavigationBlocked(sourceDocument, targetUrl, requestAt);
                return blockedNavigationResponse();
            }
            gestureGuard.onNavigationAccepted(sourceDocument, targetUrl, request.hasGesture(),
                    request.isRedirect(), requestAt);
        }
        if (source == webView && requestUri != null) {
            if (!mediaUrlClassified) inspectMedia = MediaResourceSniffer.isLikelyMediaRequest(
                    requestUri.getEncodedPath(), requestUri.getEncodedQuery(), "");
            String mediaHint = mediaRequestHint(requestHeaders, accept);
            if (inspectMedia || MediaResourceSniffer.isMediaMimeHint(mediaHint))
                observeMediaCandidate(source, requestUri, mediaHint, pageHost);
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
        String range = requestHeader(headers, "Range");
        if (range.regionMatches(true, 0, "bytes=", 0, 6) &&
                (accept == null || accept.length() == 0 || "*/*".equals(accept.trim())))
            return "application/x-median-range";
        return accept == null ? "" : accept;
    }

    private static boolean shouldReadAcceptHeader(Uri uri, boolean mainFrame) {
        if (mainFrame) return true;
        if (uri == null) return false;
        // Keep this request-interception filter allocation-free for the common static assets.
        // Encoded paths are sufficient for suffix classification and avoid Uri's decode step.
        String path = uri.getEncodedPath();
        if (path == null) return true;
        return !(endsWithIgnoreCase(path, ".png") || endsWithIgnoreCase(path, ".jpg") ||
                endsWithIgnoreCase(path, ".jpeg") || endsWithIgnoreCase(path, ".gif") ||
                endsWithIgnoreCase(path, ".webp") || endsWithIgnoreCase(path, ".svg") ||
                endsWithIgnoreCase(path, ".avif") || endsWithIgnoreCase(path, ".css") ||
                endsWithIgnoreCase(path, ".js") || endsWithIgnoreCase(path, ".mjs") ||
                endsWithIgnoreCase(path, ".woff") || endsWithIgnoreCase(path, ".woff2") ||
                endsWithIgnoreCase(path, ".ttf") || endsWithIgnoreCase(path, ".otf") ||
                endsWithIgnoreCase(path, ".ico") || endsWithIgnoreCase(path, ".json") ||
                endsWithIgnoreCase(path, ".xml") || endsWithIgnoreCase(path, ".rss") ||
                endsWithIgnoreCase(path, ".atom") || endsWithIgnoreCase(path, ".html") ||
                endsWithIgnoreCase(path, ".htm") || endsWithIgnoreCase(path, ".mp4") ||
                endsWithIgnoreCase(path, ".webm") || endsWithIgnoreCase(path, ".m3u8") ||
                endsWithIgnoreCase(path, ".mpd") || endsWithIgnoreCase(path, ".mp3") ||
                endsWithIgnoreCase(path, ".m4a") || endsWithIgnoreCase(path, ".aac") ||
                endsWithIgnoreCase(path, ".ogg") || endsWithIgnoreCase(path, ".opus") ||
                endsWithIgnoreCase(path, ".wav") || endsWithIgnoreCase(path, ".m4s") ||
                endsWithIgnoreCase(path, ".ts"));
    }

    private static boolean endsWithIgnoreCase(String value, String suffix) {
        int start = value.length() - suffix.length();
        return start >= 0 && value.regionMatches(true, start, suffix, 0, suffix.length());
    }

    private void observeMediaIfLikely(WebView source, Uri uri, String accept, String pageHost) {
        if (source != webView || uri == null) return;
        if (!MediaResourceSniffer.isLikelyMediaRequest(uri.getEncodedPath(), uri.getEncodedQuery(), accept)) return;
        observeMediaCandidate(source, uri, accept, pageHost);
    }

    /** Records a candidate after its URL has already passed the cheap path/query filter. */
    private void observeMediaCandidate(WebView source, Uri uri, String accept, String pageHost) {
        if (source != webView || uri == null) return;
        mediaSniffer.observe(uri.toString(), accept, pageHost, "request", 0, 0, 0d);
    }

    private boolean isCurrentLiveMediaDocument(WebView target, String documentUrl, long sequence) {
        if (!isCurrentPageCallback(target, sequence) || !isNetworkPage(documentUrl)) return false;
        String current = target.getUrl();
        String started = startedPageUrls.get(target);
        return isNetworkPage(current) && documentUrl.equals(navigationDocumentUrl(current)) &&
                started != null && documentUrl.equals(navigationDocumentUrl(started));
    }

    /** Small load-state query; no resource/DOM scans or persistent observers before load. */
    private static String liveMediaLoadGateScript(String documentUrl, long sequence, boolean install) {
        String key = JSONObject.quote(sequence + ":" + documentUrl);
        return "(function(){try{var k=" + key + ",u=" + JSONObject.quote(documentUrl) + ";" +
                "function current(){return String(location.href).split('#')[0]===u;}" +
                "function loaded(){if(document.readyState!=='complete')return false;var p=window.performance,n,e;" +
                "if(!p)return false;try{if(typeof p.getEntriesByType==='function')n=p.getEntriesByType('navigation');}catch(_){}" +
                // A modern entry with loadEventEnd=0 is still loading; never replace it with
                // the legacy value. Old providers without navigation entries use Timing 1.
                "if(n&&n.length)e=n[0].loadEventEnd;else e=p.timing&&p.timing.loadEventEnd;" +
                "return typeof e==='number'&&isFinite(e)&&e>0;}" +
                "if(!current()||!loaded())return 0;var g=window.__medianMediaLoadGate;" +
                "if(!g||g.k!==k||g.d!==document){" + (install ? "return 0;" :
                "g={k:k,d:document,ready:false};window.__medianMediaLoadGate=g;" +
                // Deferred JS only signals readiness. Native code rechecks the active view,
                // navigation and Activity before submitting the synchronous install payload.
                "var mark=function(){if(window.__medianMediaLoadGate===g&&g.d===document&&current()&&loaded())g.ready=true;};" +
                "if(typeof window.requestIdleCallback==='function')window.requestIdleCallback(mark,{timeout:1000});" +
                "else if(typeof window.requestAnimationFrame==='function')window.requestAnimationFrame(function(){window.requestAnimationFrame(mark);});" +
                "return 0;") + "}if(!g.ready)return 0;" +
                (install ? MediaProbeScript.install() : "") + "return 1;}catch(e){return 0;}})();";
    }

    private void installLiveMediaCapture(WebView target, String documentUrl, long sequence) {
        if (!isCurrentLiveMediaDocument(target, documentUrl, sequence)) return;
        try { target.evaluateJavascript(liveMediaLoadGateScript(documentUrl, sequence, true), null); }
        catch (RuntimeException ignored) {}
    }

    /** Waits for real load completion and idle/double-rAF, with a finite polling budget. */
    private void scheduleLiveMediaCapture(final WebView target, final String documentUrl,
                                          final long expectedSequence) {
        final String expectedDocument = navigationDocumentUrl(documentUrl);
        if (!isCurrentLiveMediaDocument(target, expectedDocument, expectedSequence) ||
                liveMediaCaptureSequence == expectedSequence) return;
        liveMediaCaptureSequence = expectedSequence;
        uiHandler.postDelayed(new Runnable() {
            private int checks;
            @Override public void run() {
                if (!isCurrentLiveMediaDocument(target, expectedDocument, expectedSequence)) return;
                checks++;
                final Runnable retry = this;
                try {
                    target.evaluateJavascript(liveMediaLoadGateScript(expectedDocument, expectedSequence, false),
                            new ValueCallback<String>() {
                        @Override public void onReceiveValue(String value) {
                            if (!isCurrentLiveMediaDocument(target, expectedDocument, expectedSequence)) return;
                            if ("1".equals(value)) installLiveMediaCapture(target, expectedDocument, expectedSequence);
                            else if ("0".equals(value) && checks < 24) {
                                uiHandler.postDelayed(retry, checks < 4 ? 500L : 2000L);
                            }
                        }
                    });
                } catch (RuntimeException ignored) {}
            }
        }, 250L);
    }

    /** Copies WebView-owned icon pixels before handing the bounded write to the worker queue. */
    private void cacheFavicon(final String host, android.graphics.Bitmap icon) {
        if (icon == null || host == null || host.length() == 0 || favicons == null ||
                scriptNetworkExecutor == null || scriptNetworkExecutor.isShutdown()) return;
        final android.graphics.Bitmap copy;
        try { copy = icon.copy(android.graphics.Bitmap.Config.ARGB_8888, false); }
        catch (RuntimeException error) { return; }
        if (!executeTask(scriptNetworkExecutor, new Runnable() {
            @Override public void run() {
                boolean stored = false;
                try {
                    // File metadata checks can block on cold storage; keep them off the UI
                    // callback that receives the page favicon.
                    if (!activityDestroyed && !favicons.has(host)) stored = favicons.put(host, copy);
                } finally { copy.recycle(); }
                if (stored && !activityDestroyed) uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (!activityDestroyed && webView != null && isHomeUrl(webView.getUrl()))
                            refreshHomeFavicon(webView, host);
                    }
                });
            }
        })) {
            copy.recycle();
        }
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
        // YouTube is intentionally excluded from cosmetic filtering because generic selectors
        // can interfere with its player. Avoid even scheduling an empty cleanup script there.
        if (!adBlock.cosmeticFilteringAllowed(pageHost)) return;
        final String documentUrl = startedPageUrls.get(source);
        if (documentUrl == null || !isNetworkPage(documentUrl)) return;
        // Generic subscription selectors are useful after the document has painted, but a large
        // CSS payload can compete with the first interactive frame. Explicit user selectors keep
        // their document-start path; generic selectors yield briefly to first paint.
        final long uiDelay = adBlock.requiresEarlyCosmetic(pageHost) ? 0L : 120L;
        final Object ticket = new Object();
        if (cosmeticInjected.putIfAbsent(source, ticket) != null) return;
        if (!executeTask(navigationExecutor, new Runnable() {
            @Override public void run() {
                if (cosmeticInjected.get(source) != ticket || activityDestroyed ||
                        !documentUrl.equals(startedPageUrls.get(source)) ||
                        !pageHost.equalsIgnoreCase(pageHostFor(source))) {
                    cosmeticInjected.remove(source, ticket);
                    return;
                }
                final String css = adBlock.cosmeticCssForHost(pageHost);
                // Quote and assemble the payload off the UI thread. JSONObject.quote scans the
                // whole CSS string and was visible as a main-thread hitch on large subscriptions.
                final String script = "(function(){var u=" + JSONObject.quote(documentUrl) + ",c=" + JSONObject.quote(css) +
                        ",n=0;function f(){if(location.href.split('#')[0]!==u)return;var p=document.head||document.documentElement;" +
                        "if(!p){if(++n<10)setTimeout(f,30);return;}var s=document.getElementById('__median_adblock');" +
                        "if(!c){if(s)s.remove();return;}if(!s){s=document.createElement('style');s.id='__median_adblock';p.appendChild(s);}if(s.textContent!==c)s.textContent=c;}f();})();";
                Runnable apply = new Runnable() {
                    @Override public void run() {
                        if (cosmeticInjected.get(source) != ticket) return;
                        if (activityDestroyed || tabForView(source) == null ||
                                !documentUrl.equals(startedPageUrls.get(source)) ||
                                !documentUrl.equals(navigationDocumentUrl(source.getUrl())) ||
                                !pageHost.equalsIgnoreCase(pageHostFor(source)) || !isAdBlockActiveForHost(pageHost)) {
                            cosmeticInjected.remove(source, ticket);
                            return;
                        }
                        try { source.evaluateJavascript(script, null); }
                        catch (RuntimeException ignored) { cosmeticInjected.remove(source, ticket); }
                        // Keep the ticket settled for this document so blocked subresources do not
                        // enqueue another CSS build.
                    }
                };
                if (uiDelay == 0L) uiHandler.post(apply); else uiHandler.postDelayed(apply, uiDelay);
            }
        })) cosmeticInjected.remove(source, ticket);
    }

    private void refreshCosmeticFilters() {
        if (activityDestroyed) return;
        for (BrowserTab tab : tabs) {
            WebView source = tab.liveView;
            if (source == null || !isNetworkPage(source.getUrl())) continue;
            String host = hostOf(source.getUrl());
            if (!isAdBlockActiveForHost(host)) continue;
            cosmeticInjected.remove(source);
            scheduleCosmeticInjection(source, host);
        }
    }

    private WebResourceResponse blockedResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(EMPTY_RESPONSE));
    }

    private WebResourceResponse blockedNavigationResponse() {
        // A 204 cancels main-frame navigation without committing an empty replacement
        // document. This also covers POST requests that skip the override callback.
        return new WebResourceResponse("text/plain", "UTF-8", 204, "No Content",
                Collections.<String, String>emptyMap(), new ByteArrayInputStream(EMPTY_RESPONSE));
    }

    private void observeNavigationTouch(WebView source, MotionEvent event) {
        NavigationGestureGuard guard = navigationGestureGuards.get(source);
        if (guard == null || event == null) return;
        String document = navigationSourceDocument(source);
        long now = SystemClock.uptimeMillis();
        int action = event.getActionMasked();
        int pointers = event.getPointerCount();
        if (action == MotionEvent.ACTION_DOWN) {
            guard.onDown(document, now, event.getX(), event.getY(), pointers);
        } else if (action == MotionEvent.ACTION_MOVE) {
            guard.onMove(document, now, event.getX(), event.getY(), pointers);
        } else if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            guard.reset();
        } else if (action == MotionEvent.ACTION_UP) {
            guard.onUp(document, now, pointers);
        } else if (action == MotionEvent.ACTION_CANCEL) {
            guard.onCancel(document, now);
        }
    }

    private String navigationSourceDocument(WebView source) {
        if (source == null) return "";
        String opener = popupSourceDocuments.get(source);
        if (opener != null && opener.length() > 0) return opener;
        String remembered = navigationSourceDocuments.get(source);
        if (remembered != null && remembered.length() > 0) return remembered;
        String current = source.getUrl();
        return current == null ? "" : navigationDocumentUrl(current);
    }

    private void rememberNavigationSourceDocument(WebView source, String url) {
        String document = navigationDocumentUrl(url);
        String previous = navigationSourceDocuments.put(source, document);
        if (document.equals(previous)) return;
        NavigationGestureGuard guard = navigationGestureGuards.get(source);
        if (guard != null) guard.migrateDocument(previous, document);
    }

    private String protectedNavigationSourceDocument(WebView source) {
        String document = navigationSourceDocument(source);
        NavigationGestureGuard guard = navigationGestureGuards.get(source);
        return guard == null ? document : guard.navigationSourceDocument(document, SystemClock.uptimeMillis());
    }

    private void rememberAcceptedTouchNavigation(WebView source, String targetUrl,
                                                  boolean hasGesture, boolean serverRedirect) {
        NavigationGestureGuard guard = navigationGestureGuards.get(source);
        if (guard == null) return;
        String document = protectedNavigationSourceDocument(source);
        if (navigationProtectionActive(navigationSourceHost(source, document)))
            guard.onNavigationAccepted(document, targetUrl, hasGesture, serverRedirect, SystemClock.uptimeMillis());
    }

    private String navigationSourceHost(WebView source, String document) {
        String host = hostOf(document);
        if (host.length() > 0) return host;
        return pageHostFor(source);
    }

    private boolean navigationProtectionActive(String sourceHost) {
        return sourceHost != null && sourceHost.length() > 0 && isAdBlockActiveForHost(sourceHost);
    }

    private void rememberMainFrameNavigation(WebView source, String targetUrl) {
        String sourceHost = navigationSourceHost(source, protectedNavigationSourceDocument(source));
        rememberMainFrameNavigation(source, targetUrl, sourceHost, navigationProtectionActive(sourceHost));
    }

    private void rememberMainFrameNavigation(WebView source, String targetUrl, String sourceHost,
                                              boolean protectionEnabled) {
        MainFrameNavigationContext context = mainFrameNavigationContexts.get(source);
        if (context != null) context.arm(targetUrl, sourceHost, protectionEnabled);
    }

    private void clearMainFrameRequest(WebView source) {
        MainFrameNavigationContext context = mainFrameNavigationContexts.get(source);
        if (context != null) context.clear();
    }

    private void clearMainFrameNavigation(WebView source) {
        clearMainFrameRequest(source);
        NavigationGestureGuard guard = navigationGestureGuards.get(source);
        if (guard != null) guard.reset();
    }

    private void retireMainFrameNavigation(WebView source, String url) {
        MainFrameNavigationContext context = mainFrameNavigationContexts.get(source);
        if (context != null) context.clearForUrl(url);
    }

    private void reloadWebView(WebView target) {
        clearMainFrameNavigation(target);
        prepareHistoryConnectionHints(target, 0);
        target.reload();
    }

    private void goBackWebView(WebView target) {
        clearMainFrameNavigation(target);
        prepareHistoryConnectionHints(target, -1);
        target.goBack();
    }

    private void goForwardWebView(WebView target) {
        clearMainFrameNavigation(target);
        prepareHistoryConnectionHints(target, 1);
        target.goForward();
    }

    private void prepareHistoryConnectionHints(WebView target, int offset) {
        if (target == null) return;
        String url = null;
        if (offset == 0) url = target.getUrl();
        else {
            android.webkit.WebBackForwardList history = target.copyBackForwardList();
            int index = history.getCurrentIndex() + offset;
            if (index >= 0 && index < history.getSize()) url = history.getItemAtIndex(index).getUrl();
        }
        if (isNetworkPage(url)) {
            prepareNavigationConnections(url);
            installConnectionHintScript(target, url);
        } else removeConnectionHintScript(target);
    }

    private boolean shouldBlockPageNavigation(WebView source, String targetUrl, boolean hasGesture,
                                               boolean serverRedirect) {
        if (source == null || !isNetworkPage(targetUrl)) return false;
        String sourceDocument = protectedNavigationSourceDocument(source);
        String sourceHost = navigationSourceHost(source, sourceDocument);
        if (!navigationProtectionActive(sourceHost)) return false;
        Uri target;
        try { target = Uri.parse(targetUrl); } catch (RuntimeException ignored) { return false; }
        NavigationGestureGuard guard = navigationGestureGuards.get(source);
        long navigationAt = SystemClock.uptimeMillis();
        // Main-frame document rules are evaluated against the page that initiated the request.
        if (adBlock.shouldBlock(target, sourceHost, "text/html", true)) {
            if (guard != null && guard.shouldBlockNavigation(sourceDocument, targetUrl, hasGesture,
                    serverRedirect, navigationAt))
                guard.onNavigationBlocked(sourceDocument, targetUrl, navigationAt);
            return true;
        }
        if (guard == null ||
                !guard.shouldBlockNavigation(sourceDocument, targetUrl, hasGesture, serverRedirect,
                        navigationAt)) return false;
        // A navigation heuristic must also respect scoped @@ document allow rules.
        boolean navigationAllowed = adBlock.isExplicitlyAllowed(target, sourceHost, "text/html", true);
        if (!navigationAllowed)
            guard.onNavigationBlocked(sourceDocument, targetUrl, navigationAt);
        return !navigationAllowed;
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
        if (isReservedInternalHost(url)) {
            retireMainFrameNavigation(source, url);
            // Only pages rendered by Median itself may occupy the private internal origin.
            return !isHomeUrl(url) && !isQrScannerUrl(url);
        }
        if (looksLikeUserScript(url)) {
            retireMainFrameNavigation(source, url);
            installScriptFromUrl(url);
            return true;
        }
        if (isNetworkPage(url)) {
            String cleaned = shouldCleanTracking(hostOf(url)) ? UrlCleaner.cleanTracking(url) : url;
            if (!url.equals(cleaned)) {
                loadNetworkUrlFromPage(source, cleaned);
                toast("已移除跟踪参数");
                return true;
            }
        }
        if (shouldUpgradeHttp(url)) {
            loadNetworkUrlFromPage(source, "https://" + url.substring(7));
            return true;
        }
        if (isNetworkPage(url) || "about:blank".equalsIgnoreCase(url) || isOfflineUrl(url)) return false;
        if (url.regionMatches(true, 0, "data:", 0, 5) || url.regionMatches(true, 0, "about:", 0, 6)) { toast("已阻止网页跳转到不透明内部地址"); return true; }
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
                prefs.getBoolean("restore_tabs", false));
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
        if (shouldUpgradeHttp(value)) value = "https://" + value.substring(7);
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
        removeConnectionHintScript(target);
        clearMainFrameNavigation(target);
        applyPageAccessPolicy(target, HOME_URL);
        appliedSiteSettings.remove(target);
        WebSettings settings = target.getSettings();
        target.setBackgroundColor(nightMode ? DARK_WEB : WHITE);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            try { WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false); }
            catch (RuntimeException ignored) {}
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try { settings.setForceDark(WebSettings.FORCE_DARK_OFF); }
            catch (RuntimeException ignored) {}
        }
        settings.setJavaScriptEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setTextZoom(100);
    }

    /** MHTML can read only its own provider content and cannot execute archived JavaScript. */
    private void prepareOfflineDestination(WebView target, String url) {
        if (target == null || !isOfflineUrl(url)) return;
        removeConnectionHintScript(target);
        clearMainFrameNavigation(target);
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
        prepareNetworkDestination(target, url, hostOf(url));
    }

    private void prepareNetworkDestination(WebView target, String url, String host) {
        trustedHomeViews.remove(target);
        customHomeViews.remove(target);
        applyPageAccessPolicy(target, url);
        applySiteSettings(target, host);
        installConnectionHintScript(target, url);
    }

    private void removeConnectionHintScript(WebView target) {
        connectionHints.removeConnectionHintScript(target);
    }

    private void installConnectionHintScript(WebView target, String url) {
        connectionHints.installConnectionHintScript(target, url);
    }

    private void installConnectionHintScript(WebView target, String url, boolean applyToCurrentDocument) {
        connectionHints.installConnectionHintScript(target, url, applyToCurrentDocument);
    }

    private void collectConnectionHints(WebView source, String url, long sequence) {
        connectionHints.collectConnectionHints(source, url, sequence);
    }

    private void clearConnectionHints() {
        connectionHints.clearConnectionHints();
    }

    private void prepareNavigationConnections(String url) {
        connectionHints.prepareNavigationConnections(url);
    }

    /** Prepares the destination settings before issuing an HTTP(S) navigation. */
    private void loadNetworkUrl(WebView target, String url) {
        clearMainFrameNavigation(target);
        String host = hostOf(url);
        loadNetworkUrl(target, url, host, navigationProtectionActive(host));
    }

    private void loadNetworkUrlFromPage(WebView source, String url) {
        String sourceHost = navigationSourceHost(source, protectedNavigationSourceDocument(source));
        rememberAcceptedTouchNavigation(source, url, false, false);
        loadNetworkUrl(source, url, sourceHost, navigationProtectionActive(sourceHost));
    }

    private void loadNetworkUrl(WebView target, String url, String sourceHost, boolean protectionEnabled) {
        if (target == null || url == null) return;
        String host = hostOf(url);
        if (host.length() == 0 || !(url.regionMatches(true, 0, "https://", 0, 8) ||
                url.regionMatches(true, 0, "http://", 0, 7))) return;

        // Let Chromium prepare its own reusable connection while the destination's settings and
        // navigation bookkeeping are applied. This is an origin hint for an accepted navigation.
        prepareNavigationConnections(url);

        // Mark that user has navigated when loading a network URL
        if (target == webView && !isHomeUrl(url)) {
            userHasNavigated = true;
        }

        rememberMainFrameNavigation(target, url, sourceHost, protectionEnabled);
        prepareNetworkDestination(target, url, host);
        watchInitialNetworkNavigation(target, url);
        if (target == webView) {
            currentPageUrl = url;
            currentPageHost = host;
        }
        startedPageUrls.remove(target);
        target.loadUrl(url);
    }

    private void watchInitialNetworkNavigation(final WebView target, String url) {
        if (target == null || target != webView || !startupReady) return;
        // Every caller has already accepted an HTTP(S) main-frame destination. Re-parsing it into
        // java.net.URL here only allocates on the first-navigation path and cannot make loadUrl safer.
        InitialNavigationGuard guard = initialNavigationGuards.get(target);
        if (guard == null) {
            InitialNavigationGuard created = new InitialNavigationGuard();
            InitialNavigationGuard existing = initialNavigationGuards.putIfAbsent(target, created);
            guard = existing == null ? created : existing;
        }
        long generation = guard.arm(url);
        if (generation != 0L) {
            if (startupNavigationAt == 0L) startupNavigationAt = SystemClock.elapsedRealtime();
            // A cold Chromium provider can spend a short interval before it dispatches
            // onPageStarted. Recovery remains bounded, while startedPageUrls prevents a second
            // socket once the provider has accepted the request.
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
                // onPageStarted means the provider accepted the document, but it can remain at
                // the initial 10% progress for a slow origin. Reloading an already-started page
                // here creates a second connection and is the main source of cold-start stalls.
                if (startedPageUrls.get(target) != null) return;
                String providerUrl = target.getUrl();
                if (target.getProgress() > 10 && providerUrl != null &&
                        providerUrl.equals(currentPageUrl) && isNetworkPage(providerUrl)) {
                    // Some WebView versions update getUrl() before dispatching onPageStarted.
                    // Treat that state as an acknowledgement instead of issuing a duplicate
                    // socket request during the cold provider window.
                    if (guard.acknowledge(providerUrl))
                        recordStartupNavigation("首航已接受", providerUrl);
                    return;
                }
                // Several System WebView builds publish the destination URL before their first
                // progress callback. That is already an accepted navigation; reloading it here
                // creates a second socket and can make a cold page appear to take several
                // seconds. Retire the watchdog and let the provider finish its queued request.
                if (providerUrl != null && providerUrl.equals(currentPageUrl) &&
                        isNetworkPage(providerUrl)) {
                    if (guard.acknowledge(providerUrl))
                        recordStartupNavigation("首航已接受", providerUrl);
                    return;
                }
                if (!target.isAttachedToWindow()) {
                    scheduleInitialNavigationRetry(target, generation, 220L);
                    return;
                }
                String retryUrl = guard.claimRetry(generation);
                if (retryUrl.length() == 0) return;
                recordStartupNavigation("首航重载", retryUrl);
                String retryHost = hostOf(retryUrl);
                clearMainFrameNavigation(target);
                prepareNetworkDestination(target, retryUrl, retryHost);
                currentPageUrl = retryUrl;
                currentPageHost = retryHost;
                if (retryUrl.equals(target.getUrl())) reloadWebView(target);
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
                hostOf(url) + "\nSystem WebView：" + webViewVersion + "\n引擎准备：" + webViewStartupDiagnostic +
                "\n连接准备：" + WebViewNavigationWarmupCompat.getDiagnosticReport();
        prefs.edit().putString(PREF_STARTUP_DIAGNOSTIC, report).apply();
    }

    private void loadInput(String input) {
        if (input == null) return;
        if (!startupReady) {
            pendingStartupInput = input;
            return;
        }
        startupSessionPending = false;
        String text = input.trim();
        if (text.length() == 0) return;
        boolean explicitHttp = OmniboxInput.isExplicitHttpUrl(text);
        if (explicitHttp) {
            try { text = NetworkSecurity.parseHttpUrl(text).toString(); }
            catch (Exception invalidUrl) { toast("网页地址无效"); return; }
        }
        if (shouldUpgradeHttp(text)) text = "https://" + text.substring(7);
        if (explicitHttp || "about:blank".equals(text)) {
            if (explicitHttp) {
                if (shouldCleanTracking(hostOf(text))) text = UrlCleaner.cleanTracking(text);
                loadNetworkUrl(webView, text);
            } else {
                clearMainFrameNavigation(webView);
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
        showHome(false);
    }

    /** Renders the local home page, optionally resetting stale themed history. */
    private void showHome(boolean resetHistory) {
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
                cachedHomeHtml = HomePage.html(searchEngine, bookmarks, nightMode, homeToken(), config,
                        searchEngines.customEngines(), shortcuts);
                cachedHomeKey = renderKey;
            }
            renderedHomeKeys.put(webView, renderKey);

            if (resetHistory) {
                // A theme switch rebuilds the local document. WebView otherwise keeps the
                // previous themed home entry in its back stack, so an edge-back immediately
                // after the switch resurrects the old black/white document behind the new
                // native chrome. Only that explicit transition resets history; ordinary home
                // refreshes keep the user's navigation stack intact.
                try {
                    webView.stopLoading();
                    webView.clearHistory();
                } catch (RuntimeException ignored) {
                }
            }
            // A null history URL becomes about:blank. On back, the visible home still has
            // its search form but fails the native home-origin check, so commands are lost.
            webView.loadDataWithBaseURL(HOME_URL, cachedHomeHtml, "text/html", "UTF-8", HOME_URL);
        }
        // Optional icon downloads are best-effort and never part of the render critical path.
        if (startupReady) scheduleHomeFaviconPrefetch(webView, shortcuts, renderKey);
        currentPageHost = "";
        if (webView != null) pageHosts.put(webView, "");
        homeThemeRepairPending = false;
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

    /** Favicon downloads are deliberately lazy and bounded so opening the home page never waits. */
    private void scheduleHomeFaviconPrefetch(final WebView target, List<HomePage.Shortcut> shortcuts,
                                             final String renderKey) {
        if (target == null || shortcuts == null || favicons == null || scriptNetworkExecutor == null ||
                scriptNetworkExecutor.isShutdown() || activityDestroyed) return;
        int scheduled = 0;
        for (HomePage.Shortcut item : shortcuts) {
            if (scheduled >= 12) break;
            if (item == null || item.folder) continue;
            final String host = HomePage.faviconHostForUrl(item.url);
            if (host.length() == 0 || favicons.has(host) || !faviconFetchQueued.add(host)) continue;
            scheduled++;
            if (!executeTask(scriptNetworkExecutor, new Runnable() {
                @Override public void run() {
                    boolean stored = false;
                    try { stored = favicons.fetchAndStore(host); }
                    finally { faviconFetchQueued.remove(host); }
                    if (!stored || activityDestroyed) return;
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (activityDestroyed || target != webView || !isHomeUrl(target.getUrl()) ||
                                    !trustedHomeViews.contains(target) ||
                                    !renderKey.equals(renderedHomeKeys.get(target))) return;
                            refreshHomeFavicon(target, host);
                        }
                    });
                }
            })) faviconFetchQueued.remove(host);
        }
    }

    private void refreshHomeFavicon(WebView target, String host) {
        if (target == null || host == null || host.length() == 0) return;
        String script = "(function(h){var a=document.querySelectorAll('img[data-favicon-host]'),i,x,f;" +
                "for(i=0;i<a.length;i++){x=a[i];if(x.getAttribute('data-favicon-host')===h){" +
                "x.hidden=false;x.src='/favicon?host='+encodeURIComponent(h)+'&v=1';" +
                "f=x.nextElementSibling;if(f)f.hidden=true;}}})(" + JSONObject.quote(host) + ");";
        try { target.evaluateJavascript(script, null); } catch (RuntimeException ignored) {}
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

    private boolean isHomeRenderCurrent(WebView view) {
        if (view == null) return false;
        String rendered = renderedHomeKeys.get(view);
        if (rendered == null) return false;
        List<BrowserDataStore.Bookmark> bookmarks = dataStore == null
                ? Collections.<BrowserDataStore.Bookmark>emptyList() : dataStore.bookmarks();
        return rendered.equals(homeRenderKey(homePageConfig(), homeShortcuts(bookmarks)));
    }

    private void scheduleRestoredHomeRepair(final WebView target) {
        if (target == null || target != webView || activityDestroyed) return;
        Runnable repair = new Runnable() {
            @Override public void run() {
                if (activityDestroyed || webView != target || !isHomeUrl(target.getUrl())) return;
                verifyTrustedHome(target);
                if (!homeThemeRepairPending && isHomeRenderCurrent(target)) return;
                showHome(true);
            }
        };
        target.postDelayed(repair, 180L);
        target.postDelayed(repair, 520L);
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
        if (view == null || activityDestroyed || tabForView(view) == null) return;
        String probe = "(function(){var m=document.querySelector('meta[name=median-home-token]');return !!m&&m.content===" +
                JSONObject.quote(homeToken()) + ";})();";
        view.evaluateJavascript(probe, new ValueCallback<String>() {
            @Override public void onReceiveValue(String value) {
                try {
                    // The renderer may finish the probe after this tab was switched, frozen,
                    // or closed. Ignore that result instead of resurrecting a stale trusted view.
                    if (activityDestroyed || tabForView(view) == null) {
                        trustedHomeViews.remove(view);
                        customHomeViews.remove(view);
                        return;
                    }
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
        if (!isNetworkPage(url)) return;
        final WebView target = webView;
        final boolean ensureScripts = scriptStore != null && scriptStore.hasEnabledScripts();
        final String bridgeToken = target == null || scriptBridgeTokens.get(target) == null ? "" :
                scriptBridgeTokens.get(target);
        // Cosmetic CSS has its own document-scoped path; this worker only prepares userscripts.
        if (!ensureScripts) return;
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

    private void startAdPicker() {
        if (webView == null || !isNetworkPage(currentPageUrl) || currentHost().length() == 0) {
            toast("请先打开要标记广告的网页");
            return;
        }
        if (!validAdPickerHost(currentHost())) {
            toast("当前地址无法创建仅限本站的广告规则");
            return;
        }
        if (!isAdBlockActiveForHost(currentHost())) {
            toast("请先开启当前网站的广告拦截");
            return;
        }
        dismissOverlayForNavigation();
        final WebView source = webView;
        final AdPickerSession session = new AdPickerSession(source.getUrl());
        adPickerSessions.put(source, session);
        adPickerViews.add(source);
        try {
            source.evaluateJavascript(AdPickerScript.build(adPickToken()), new ValueCallback<String>() {
                @Override public void onReceiveValue(String result) {
                    if (adPickerSessions.get(source) != session) return;
                    if (!"true".equals(result)) {
                        finishAdPickerSession(source, session);
                        if (!"false".equals(result)) toast("当前页面无法进入广告标记模式");
                    }
                }
            });
            source.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        } catch (RuntimeException error) {
            finishAdPickerSession(source, session);
            toast("当前页面无法进入广告标记模式");
        }
    }

    private boolean isAdPickerSessionCurrent(WebView source, AdPickerSession session) {
        return !activityDestroyed && source != null && source == webView && session != null &&
                adPickerSessions.get(source) == session &&
                session.documentUrl.equals(navigationDocumentUrl(source.getUrl()));
    }

    private void finishAdPickerSession(WebView source, AdPickerSession session) {
        if (source == null || session == null || !adPickerSessions.remove(source, session)) return;
        adPickerViews.remove(source);
        try { source.evaluateJavascript("if(window.__medianAdPicker)window.__medianAdPicker.cancel();delete window.__medianAdSelection;", null); }
        catch (RuntimeException ignored) {}
    }

    private boolean handleAdPickerPrompt(final WebView source, String message, String value,
                                         JsPromptResult result) {
        if (!adPickToken().equals(message)) return false;
        final AdPickerSession session = source == null ? null : adPickerSessions.get(source);
        boolean accepted = result != null && isAdPickerSessionCurrent(source, session) &&
                adPickerViews.remove(source) && isNetworkPage(source.getUrl());
        if (result != null) result.confirm("");
        if (!accepted) { finishAdPickerSession(source, session); return true; }
        try {
            JSONObject picked = new JSONObject(value == null ? "{}" : value);
            if (picked.optBoolean("cancel", false)) { finishAdPickerSession(source, session); return true; }
            final String selector = picked.optString("selector", "").trim();
            final String label = picked.optString("text", "").trim();
            final String host = hostOf(source.getUrl());
            if (!validAdPickerHost(host)) {
                toast("无法为这个元素生成稳定规则");
                finishAdPickerSession(source, session);
                return true;
            }
            if (picked.optBoolean("manual", false)) {
                showManualAdPickerDialog(source, host, session);
                return true;
            }
            if (selector.length() == 0 || selector.length() > 512 ||
                    selector.indexOf('\n') >= 0 || selector.indexOf('\r') >= 0) {
                toast("无法为这个元素生成稳定规则");
                finishAdPickerSession(source, session);
                return true;
            }
            validateAdPickerSelector(source, host, selector, label, true, session);
        } catch (Exception error) {
            finishAdPickerSession(source, session);
            toast("无法读取广告标记结果");
        }
        return true;
    }

    private void showManualAdPickerDialog(final WebView source, final String host, final AdPickerSession session) {
        if (!isAdPickerSessionCurrent(source, session)) { finishAdPickerSession(source, session); return; }
        final boolean[] advancing = { false };
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(15f);
        input.setHint("例如：.ad-banner 或 #sponsor");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setTextColor(nightMode ? DARK_TEXT : TEXT);
        input.setHintTextColor(nightMode ? DARK_MUTED : MUTED);
        input.setBackground(roundRect(nightMode ? DARK_SURFACE : SURFACE, 10));
        final AlertDialog dialog = new AlertDialog.Builder(this,
                nightMode ? R.style.MedianAlertDialogDark : R.style.MedianAlertDialogLight)
                .setTitle("手动输入 CSS 选择器")
                .setMessage("适用于选择器无法自动生成的页面。先检查匹配数量，再保存为本站规则。")
                .setView(input)
                .setPositiveButton("检查并继续", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View ignored) {
                        String selector = input.getText().toString().trim();
                        if (selector.length() == 0 || selector.length() > 512 ||
                                selector.indexOf('\n') >= 0 || selector.indexOf('\r') >= 0) {
                            input.setError("请输入 1-512 个字符的一行 CSS 选择器");
                            return;
                        }
                        advancing[0] = true;
                        dialog.dismiss();
                        validateAdPickerSelector(source, host, selector, "手动选择器", false, session);
                    }
                });
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) {
                if (!advancing[0]) finishAdPickerSession(source, session);
            }
        });
        dialog.show();
    }

    private void validateAdPickerSelector(final WebView source, final String host,
                                          final String selector, final String label,
                                          final boolean requirePickedIdentity, final AdPickerSession session) {
        if (!isAdPickerSessionCurrent(source, session)) { finishAdPickerSession(source, session); return; }
        try {
            source.evaluateJavascript(AdPickerScript.validate(selector, requirePickedIdentity), new ValueCallback<String>() {
                @Override public void onReceiveValue(String rawCount) {
                    if (!isAdPickerSessionCurrent(source, session)) { finishAdPickerSession(source, session); return; }
                    int count = parseJavascriptInt(rawCount);
                    if (count < 1) {
                        finishAdPickerSession(source, session);
                        toast(count < 0 ? "CSS 选择器无效，请重新输入" :
                                (requirePickedIdentity ? "页面元素已经变化，请重新选择" : "没有匹配元素或匹配超过 200 个，请输入更具体的规则"));
                        return;
                    }
                    // A broad manual selector is useful, but cap it so an accidental `div` or
                    // `*` cannot turn one tap into a page-wide blank screen.
                    if (count > 200) {
                        finishAdPickerSession(source, session);
                        toast("选择器匹配了 " + count + " 个元素，请输入更具体的规则");
                        return;
                    }
                    confirmAdPickerRule(source, host, selector, label, count, requirePickedIdentity, session);
                }
            });
        } catch (RuntimeException error) {
            finishAdPickerSession(source, session);
            toast("当前页面无法检查这个选择器");
        }
    }

    private static int parseJavascriptInt(String raw) {
        if (raw == null) return -1;
        try { return Integer.parseInt(raw.trim()); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private boolean validAdPickerHost(String host) {
        if (host == null || host.length() == 0 || host.length() > 253 ||
                "median.invalid".equalsIgnoreCase(host)) return false;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == ':')) return false;
        }
        return true;
    }

    private void confirmAdPickerRule(final WebView source, final String host, final String selector,
                                     String label, int matchCount, final boolean requirePickedIdentity,
                                     final AdPickerSession session) {
        String preview = label.length() == 0 ? "已选中的页面元素" : label;
        preview = UiText.ellipsize(preview, 80);
        final boolean[] saving = { false };
        AlertDialog confirmation = new AlertDialog.Builder(this,
                nightMode ? R.style.MedianAlertDialogDark : R.style.MedianAlertDialogLight)
                .setTitle("将此元素标记为广告？")
                .setMessage(preview + "\n\n规则：" + host + "##" + selector +
                        "\n\n当前匹配 " + matchCount + " 个元素。规则只作用于当前网站，可随时在过滤器中心编辑。")
                .setPositiveButton("标记并隐藏", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (!isAdPickerSessionCurrent(source, session)) {
                            toast("页面已经变化，请重新选择广告元素");
                            finishAdPickerSession(source, session);
                            return;
                        }
                        saving[0] = true;
                        try {
                            source.evaluateJavascript(AdPickerScript.hide(selector, requirePickedIdentity), new ValueCallback<String>() {
                                @Override public void onReceiveValue(String rawCount) {
                                    boolean current = isAdPickerSessionCurrent(source, session);
                                    finishAdPickerSession(source, session);
                                    if (!current) return;
                                    int hidden = parseJavascriptInt(rawCount);
                                    if (hidden < 1 || hidden > 200) {
                                        toast("页面元素已经变化，请重新选择广告元素");
                                        return;
                                    }
                                    if (!appendCustomFilterRule(host + "##" + selector)) {
                                        toast("当前已隐藏，但规则未保存，请清理过多的自定义规则后重试");
                                        return;
                                    }
                                    rebuildAdBlockRulesAsync(false);
                                    toast("已隐藏 " + hidden + " 个元素，本站规则已保存");
                                }
                            });
                        } catch (RuntimeException error) {
                            finishAdPickerSession(source, session);
                            toast("当前页面无法执行隐藏，请重新选择");
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        confirmation.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) {
                if (!saving[0]) finishAdPickerSession(source, session);
            }
        });
        confirmation.show();
    }

    private boolean appendCustomFilterRule(String rule) {
        String raw = prefs.getString("custom_filter_rules", "");
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            if (rule.equals(line.trim())) {
                return true;
            }
        }
        String next = raw.trim().length() == 0 ? rule : raw.replaceFirst("\\s+$", "") + "\n" + rule;
        if (next.getBytes(StandardCharsets.UTF_8).length > 256 * 1024) {
            toast("自定义规则已达到 256 KB 上限");
            return false;
        }
        prefs.edit().putString("custom_filter_rules", next).apply();
        return true;
    }

    private void showProtectionPanel() {
        showProtectionPanel(null);
    }

    private void showProtectionPanel(final Runnable backAction) {
        if (!requireStartupReady()) return;
        final String host = currentHost();
        final String matchedException = matchingSiteException(host);
        final boolean sitePaused = host.length() > 0 && matchedException != null;
        final boolean blockingActive = adBlockEnabled;
        int pageBlocked = Math.max(0, adBlock.getBlockedCount() - blockedAtPageStart);
        String subtitle = host.length() == 0 ? "当前为主页" : host + (blockingActive ? " · 本页拦截 " + pageBlocked + " 项" : " · 广告拦截当前未开启");
        String[] items = new String[] {
                "全局广告拦截：" + (adBlockEnabled ? "已开启" : "已关闭"),
                host.length() == 0 ? "当前页面没有站点设置" : (!blockingActive ? "此网站拦截：全局拦截未开启" : (sitePaused ? "恢复此网站拦截" : "暂停此网站拦截")),
                host.length() == 0 ? "当前页面无法标记广告\n请先打开普通网页" :
                        "标记页面广告\n点选元素并生成当前网站隐藏规则",
                "过滤器中心 · 已加载 " + services.filters().enabledCount() + " 个订阅" + (blockingActive ? "" : "（未参与拦截）"),
                "用户脚本 · 已启用 " + enabledScriptCount() + " 个",
                "浏览适用于当前网站的脚本",
                "自定义拦截规则"
        };
        int[] icons = new int[] { BrowserIconView.SHIELD, BrowserIconView.SHIELD,
                BrowserIconView.CLEAN, BrowserIconView.STORAGE, BrowserIconView.SCRIPT,
                BrowserIconView.SEARCH, BrowserIconView.STORAGE };
        int[] kinds = new int[] {
                adBlockEnabled ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                host.length() == 0 || !blockingActive ? SHEET_ROW_ACTION :
                        (sitePaused ? SHEET_ROW_TOGGLE_OFF : SHEET_ROW_TOGGLE_ON),
                SHEET_ROW_ACTION, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE,
                SHEET_ROW_ACTION, SHEET_ROW_NAVIGATE
        };
        showActionSheet("保护与脚本", subtitle, items, icons, kinds, null, backAction, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) {
                    adBlockEnabled = !adBlockEnabled;
                    prefs.edit().putBoolean("adblock", adBlockEnabled).apply();
                    adBlockActiveByView.clear();
                    dismissOverlayForNavigation();
                    if (webView != null && isNetworkPage(currentPageUrl)) reloadWebView(webView);
                    showProtectionPanel(backAction);
                } else if (which == 1 && host.length() > 0 && blockingActive) {
                    HashSet<String> updated = new HashSet<String>(siteExceptions);
                    if (matchedException != null) updated.remove(matchedException); else updated.add(host);
                    siteExceptions = updated;
                    prefs.edit().putStringSet("site_exceptions", new HashSet<String>(updated)).apply();
                    adBlockActiveByView.clear();
                    dismissOverlayForNavigation();
                    if (webView != null && isNetworkPage(currentPageUrl)) reloadWebView(webView);
                    showProtectionPanel(backAction);
                } else if (which == 1) {
                    toast(host.length() == 0 ? "打开网页后才能设置站点例外" : "请先开启全局广告拦截");
                    showProtectionPanel(backAction);
                } else if (which == 2) {
                    if (host.length() == 0) {
                        toast("请先打开普通网页再标记广告");
                        showProtectionPanel(backAction);
                    } else startAdPicker();
                } else if (which == 3) {
                    showFilterCenter(new Runnable() {
                        @Override public void run() { showProtectionPanel(backAction); }
                    });
                } else if (which == 4) {
                    showScriptCenter(new Runnable() {
                        @Override public void run() { showProtectionPanel(backAction); }
                    });
                } else if (which == 5) {
                    String target = host.length() == 0 ? "https://greasyfork.org/zh-CN/scripts" : "https://greasyfork.org/zh-CN/scripts/by-site/" + host;
                    loadNetworkUrl(webView, target);
                } else if (which == 6) {
                    showCustomFilterRules(new Runnable() {
                        @Override public void run() { showProtectionPanel(backAction); }
                    });
                }
            }
        });
    }

    private void rebuildAdBlockRulesAsync(final boolean notifyUser) {
        if (!activityDestroyed) adBlockRuleLoader.request(!startupReady, notifyUser);
    }

    /** Rule loader dispatches only current-generation results on the UI thread. */
    private void onAdBlockRulesLoaded(final boolean notifyUser) {
        if (!startupCustomRulesReady) {
            startupCustomRulesReady = true;
            startupReadiness.set(StartupReadiness.FILTERS, true);
            maybeStartFastStartup();
            maybeCompleteStartup();
        }
        refreshCosmeticFilters();
        if (notifyUser) {
            AdBlockEngine.Stats stats = adBlock.getStats();
            toast("过滤器已编译：" + stats.networkRules + " 条网络规则、" + stats.cosmeticRules + " 条外观规则");
            if (webView != null) reloadWebView(webView);
        }
    }

    private void onAdBlockRulesLoadFailed() {
        // Keep the last published rules and release the startup gate on an explicit failure.
        startupCustomRulesReady = true;
        startupReadiness.set(StartupReadiness.FILTERS, true);
        toast("本地过滤规则未能加载，已保留现有保护，请稍后重试");
        maybeStartFastStartup();
        maybeCompleteStartup();
    }

    private void updateFilterSubscriptions(final boolean automatic) {
        if (filterUpdateInProgress) {
            if (!automatic) toast("过滤订阅正在更新，无需重复操作");
            return;
        }
        filterUpdateInProgress = true;
        if (!automatic) toast("正在更新过滤订阅…");
        services.filters().updateEnabled(automatic, new FilterSubscriptionStore.Callback() {
            @Override public void onComplete(int updated, int unchanged, int failed, String message) {
                filterUpdateInProgress = false;
                if (updated > 0) rebuildAdBlockRulesAsync(false);
                if (!automatic) {
                    String result = "订阅更新完成：" + updated + " 个更新，" + unchanged + " 个未变化";
                    if (failed > 0) result += "，" + failed + " 个失败" + (message.length() == 0 ? "" : "（" + message + "）");
                    if (updated > 0) result += "；外观规则已更新";
                    toast(result);
                }
            }
        });
    }

    private void showFilterCenter() {
        showFilterCenter(filterCenterBackAction);
    }

    private void showFilterCenter(final Runnable backAction) {
        filterCenterBackAction = backAction;
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
        showActionSheet("过滤器中心", "HTTPS 订阅与本地规则",
                items, icons, null, null, backAction, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) {
                    updateFilterSubscriptions(false);
                    showFilterCenter();
                }
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
        int[] kinds = custom
                ? new int[] { item.enabled ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                        SHEET_ROW_ACTION, SHEET_ROW_ACTION }
                : new int[] { item.enabled ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                        SHEET_ROW_ACTION };
        String subtitle = item.url + (item.error.length() == 0 ? "" : "\n上次错误：" + item.error);
        showActionSheet(item.name, subtitle, actions, icons, kinds, null,
                new Runnable() { @Override public void run() { showFilterCenter(); } }, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) {
                    dismissOverlayForNavigation();
                    services.filters().setEnabled(item.id, !item.enabled);
                    rebuildAdBlockRulesAsync(true);
                    showFilterCenter();
                } else if (which == 1) {
                    updateFilterSubscriptions(false);
                    showFilterCenter();
                } else {
                    confirmDeleteFilterSubscription(item);
                }
            }
        });
    }

    private void confirmDeleteFilterSubscription(final FilterSubscriptionStore.Subscription item) {
        final SettingsDialogReturn back = new SettingsDialogReturn(new Runnable() {
            @Override public void run() { showFilterSubscriptionActions(item); }
        });
        new AlertDialog.Builder(this).setTitle("删除过滤订阅？")
                .setMessage(item.name + "\n" + item.url)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        services.filters().remove(item.id);
                        rebuildAdBlockRulesAsync(true);
                        showFilterCenter();
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
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
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("添加过滤订阅")
                .setMessage("仅接受 HTTPS 文本规则，单个订阅上限 12 MB。")
                .setView(content).setPositiveButton("添加并更新", null)
                .setNegativeButton("取消", null).create();
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface ignored) { showFilterCenter(); }
        });
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View ignored) { dialog.dismiss(); showFilterCenter(); }
                });
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View ignored) {
                        try {
                            services.filters().add(name.getText().toString(), url.getText().toString().trim());
                            dialog.dismiss();
                            showFilterCenter();
                            updateFilterSubscriptions(false);
                        } catch (Exception e) { url.setError(safeMessage(e)); }
                    }
                });
            }
        });
        dialog.show();
    }

    private void showFilterDiagnostics() {
        AdBlockEngine.Stats stats = adBlock.getStats();
        SettingsDialogReturn back = new SettingsDialogReturn(new Runnable() {
            @Override public void run() { showFilterCenter(); }
        });
        new AlertDialog.Builder(this).setTitle("过滤诊断")
                .setMessage("已编译网络规则：" + stats.networkRules +
                        "\n已编译外观规则：" + stats.cosmeticRules +
                        "\n读取源文件行数：" + stats.sourceLines +
                        "\n容量状态：" + (stats.capacityLimited ? "部分规则因安全上限未载入" : "全部位于安全上限内") +
                        "\n本次运行检查请求：" + stats.inspectedRequests +
                        "\n已拦截请求：" + stats.blockedRequests +
                        "\n例外放行请求：" + stats.allowedRequests +
                        "\n\n支持 hosts、ABP 网络规则和 ## / #@# 外观规则；不执行列表中的脚本。")
                .setPositiveButton("确定", back).setOnCancelListener(back).show();
    }

    private void showCustomFilterRules() {
        showCustomFilterRules(new Runnable() {
            @Override public void run() { showFilterCenter(); }
        });
    }

    private void showCustomFilterRules(final Runnable returnAction) {
        final EditText input = new EditText(this);
        input.setText(prefs.getString("custom_filter_rules", ""));
        input.setHint("每行一条，例如：\n||ads.example.com^\n@@||trusted.example.com^");
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(8);
        input.setMaxLines(14);
        input.setHorizontallyScrolling(false);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("自定义拦截规则")
                .setMessage("支持 hosts、ABP 网络与外观规则，上限 256 KB。")
                .setView(input).setPositiveButton("保存", null)
                .setNegativeButton("取消", null).create();
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface ignored) { continueSettingsPanel(returnAction); }
        });
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View ignored) { dialog.dismiss(); continueSettingsPanel(returnAction); }
                });
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View ignored) {
                        String raw = input.getText().toString();
                        if (raw.getBytes(StandardCharsets.UTF_8).length > 256 * 1024) {
                            input.setError("规则不能超过 256 KB");
                            return;
                        }
                        prefs.edit().putString("custom_filter_rules", raw).apply();
                        dialog.dismiss();
                        rebuildAdBlockRulesAsync(true);
                        continueSettingsPanel(returnAction);
                    }
                });
            }
        });
        dialog.show();
    }

    /** A compact icon dashboard for the browser menu and settings root. */
    private void showDashboardSheet(String title, String subtitle, String[] items, int[] icons,
                                   int[] rowKinds, int columns, int[] sectionStarts,
                                   String[] sectionTitles, final Runnable backAction,
                                   final Runnable footerAction, String footerText, int footerIcon,
                                   final SheetHandler handler) {
        dismissOverlayForNavigation();
        final int sheetSurface = nightMode ? DARK_SHEET : WHITE;
        final int sheetText = nightMode ? DARK_TEXT : TEXT;
        final int sheetMuted = nightMode ? DARK_MUTED : MUTED;
        final int tileSurface = nightMode ? DARK_CARD : SURFACE;
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(104, 0, 0, 0));
        overlay.setClickable(true);
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { dismissOverlay(); }
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setTag(SHEET_TAG_SURFACE);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(roundRect(sheetSurface, 20));
        panel.setPadding(dp(10), dp(7), dp(10), dp(10));
        panel.setClickable(true);
        panel.setElevation(dp(12));

        View handle = new View(this);
        handle.setTag(SHEET_TAG_HANDLE);
        handle.setBackground(sheetHandleDrawable());
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(48), dp(30));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(handle, handleParams);
        handle.setContentDescription("下拉关闭");
        installSheetDrag(handle, panel);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        if (backAction != null) {
            BrowserIconView back = iconButton(BrowserIconView.BACK, "返回上一级");
            back.setTintColor(sheetText);
            header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { navigateOverlayBack(); }
            });
        }
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this);
        heading.setTag(SHEET_TAG_PRIMARY);
        heading.setText(title);
        heading.setTextColor(sheetText);
        heading.setTextSize(18f);
        heading.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titles.addView(heading);
        if (subtitle != null && subtitle.length() > 0) {
            TextView sub = new TextView(this);
            sub.setTag(SHEET_TAG_MUTED);
            sub.setText(subtitle);
            sub.setTextColor(sheetMuted);
            sub.setTextSize(12f);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setPadding(0, dp(2), 0, 0);
            titles.addView(sub);
        }
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        BrowserIconView close = iconButton(BrowserIconView.CLOSE, "关闭");
        close.setTintColor(sheetText);
        header.addView(close, new LinearLayout.LayoutParams(dp(44), dp(48)));
        panel.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        installSheetDrag(header, panel);

        ScrollView scroll = new ScrollView(this);
        scroll.setTag(SHEET_TAG_PLAIN_SURFACE);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(false);
        scroll.setBackgroundColor(sheetSurface);
        LinearLayout content = new LinearLayout(this);
        content.setTag(SHEET_TAG_PLAIN_SURFACE);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(1), dp(5), dp(1), dp(2));
        content.setBackgroundColor(sheetSurface);
        scroll.addView(content);

        final float extraFontScale = Math.max(0f, getResources().getConfiguration().fontScale - 1f);
        final int tileHeightDp = Math.round((columns == 2 ? 82 : 98) + extraFontScale * 44);
        final int sectionHeightDp = Math.round(27 + extraFontScale * 16);
        int contentHeightDp = 0;
        LinearLayout row = null;
        int itemsInRow = 0;
        int sectionIndex = 0;
        for (int i = 0; i < items.length; i++) {
            boolean startsSection = sectionStarts != null && sectionIndex < sectionStarts.length &&
                    i == sectionStarts[sectionIndex];
            if (startsSection) {
                if (row != null && itemsInRow > 0) {
                    while (itemsInRow < columns) {
                        row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(tileHeightDp), 1f));
                        itemsInRow++;
                    }
                    content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(tileHeightDp)));
                    contentHeightDp += tileHeightDp;
                    row = null;
                    itemsInRow = 0;
                }
                String section = sectionTitles != null && sectionIndex < sectionTitles.length
                        ? sectionTitles[sectionIndex] : "";
                sectionIndex++;
                TextView label = new TextView(this);
                label.setTag(SHEET_TAG_ACCENT);
                label.setText(section);
                label.setTextColor(nightMode ? DARK_ACCENT : BLUE);
                label.setTextSize(12f);
                label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                label.setGravity(Gravity.CENTER_VERTICAL);
                label.setPadding(dp(5), dp(5), dp(5), dp(2));
                content.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(sectionHeightDp)));
                contentHeightDp += sectionHeightDp;
            }

            if (row == null) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
            }
            final int index = i;
            String item = items[i] == null ? "" : items[i];
            int detailBreak = item.indexOf('\n');
            String primary = detailBreak < 0 ? item : item.substring(0, detailBreak);
            String detail = detailBreak < 0 ? "" : item.substring(detailBreak + 1).trim();
            int kind = rowKinds != null && i < rowKinds.length ? rowKinds[i] : SHEET_ROW_NAVIGATE;
            boolean toggle = kind == SHEET_ROW_TOGGLE_ON || kind == SHEET_ROW_TOGGLE_OFF;
            boolean checked = kind == SHEET_ROW_TOGGLE_ON;

            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(columns == 2 ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            tile.setGravity(columns == 2 ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
            tile.setPadding(columns == 2 ? dp(10) : dp(5), dp(7), columns == 2 ? dp(8) : dp(5), dp(7));
            int tileColor = toggle && checked ? (nightMode ? 0xff203654 : 0xffe8f0fe) : tileSurface;
            tile.setBackground(roundRect(tileColor, 15));
            tile.setForeground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(nightMode ? 0x356b9bea : 0x2e1a73e8),
                    null, roundRect(Color.WHITE, 15)));
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setMinimumWidth(dp(48));
            tile.setMinimumHeight(dp(48));
            boolean detailShowsState = detail.contains("已开启") || detail.contains("已关闭");
            tile.setContentDescription(primary + (detail.length() == 0 ? "" : "，" + detail) +
                    (toggle && !detailShowsState ? (checked ? "，已开启" : "，已关闭") : ""));

            BrowserIconView icon = iconButton(icons != null && i < icons.length
                    ? icons[i] : BrowserIconView.MENU, primary);
            icon.setClickable(false);
            icon.setFocusable(false);
            if (toggle && checked) icon.setTag(SHEET_TAG_ACCENT);
            icon.setTintColor(toggle && checked ? (nightMode ? DARK_ACCENT : BLUE) : sheetText);
            LinearLayout.LayoutParams iconParams = columns == 2
                    ? new LinearLayout.LayoutParams(dp(32), dp(32))
                    : new LinearLayout.LayoutParams(dp(29), dp(29));
            if (columns == 3) iconParams.gravity = Gravity.CENTER_HORIZONTAL;
            if (columns != 2 || !toggle) tile.addView(icon, iconParams);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(columns == 2 ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
            labels.setPadding(columns == 2 && !toggle ? dp(9) : 0, 0, 0, 0);
            LinearLayout.LayoutParams labelsParams = columns == 2
                    ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            if (columns == 3) labelsParams.topMargin = dp(5);

            TextView label = new TextView(this);
            label.setTag(SHEET_TAG_PRIMARY);
            label.setText(primary);
            label.setTextColor(sheetText);
            label.setTextSize(columns == 2 ? 13.5f : 12.5f);
            label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            label.setGravity(columns == 2 ? Gravity.START : Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            if (detail.length() > 0) {
                TextView detailLabel = new TextView(this);
                detailLabel.setTag(SHEET_TAG_MUTED);
                detailLabel.setText(detail);
                detailLabel.setTextColor(sheetMuted);
                detailLabel.setTextSize(columns == 2 ? 11.5f : 10.5f);
                detailLabel.setGravity(columns == 2 ? Gravity.START : Gravity.CENTER);
                detailLabel.setSingleLine(true);
                detailLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
                detailLabel.setPadding(0, dp(2), 0, 0);
                labels.addView(detailLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            } else if (toggle) {
                TextView state = new TextView(this);
                state.setTag(SHEET_TAG_MUTED);
                state.setText(checked ? "已开启" : "已关闭");
                state.setTextColor(sheetMuted);
                state.setTextSize(10.5f);
                state.setGravity(columns == 2 ? Gravity.START : Gravity.CENTER);
                state.setPadding(0, dp(2), 0, 0);
                labels.addView(state, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            tile.addView(labels, labelsParams);
            if (toggle && columns == 2) {
                Switch stateSwitch = new Switch(this);
                stateSwitch.setChecked(checked);
                stateSwitch.setShowText(false);
                stateSwitch.setClickable(false);
                stateSwitch.setFocusable(false);
                stateSwitch.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                stateSwitch.setMinimumWidth(0);
                int[][] states = new int[][] {
                        new int[] { android.R.attr.state_checked }, new int[] {}
                };
                stateSwitch.setThumbTintList(new ColorStateList(states, new int[] {
                        nightMode ? DARK_ACCENT : BLUE,
                        nightMode ? 0xffbdc1c6 : 0xff757575
                }));
                stateSwitch.setTrackTintList(new ColorStateList(states, new int[] {
                        nightMode ? 0x796b9bea : 0x761a73e8,
                        nightMode ? 0xff505459 : 0xffbdc1c6
                }));
                tile.addView(stateSwitch, new LinearLayout.LayoutParams(dp(54), dp(48)));
            }
            tile.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    dismissOverlayForNavigation();
                    handler.onItem(index);
                }
            });

            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, dp(tileHeightDp - 6), 1f);
            int horizontalGap = dp(columns == 2 ? 5 : 4);
            tileParams.setMargins(horizontalGap, dp(3), horizontalGap, dp(3));
            row.addView(tile, tileParams);
            itemsInRow++;
            if (itemsInRow == columns || i == items.length - 1) {
                while (itemsInRow < columns) {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(tileHeightDp), 1f));
                    itemsInRow++;
                }
                content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(tileHeightDp)));
                contentHeightDp += tileHeightDp;
                row = null;
                itemsInRow = 0;
            }
        }
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (footerAction != null) {
            LinearLayout footer = new LinearLayout(this);
            footer.setOrientation(LinearLayout.HORIZONTAL);
            footer.setGravity(Gravity.CENTER_VERTICAL);
            footer.setPadding(dp(12), 0, dp(12), 0);
            footer.setBackground(roundRect(nightMode ? 0xff203654 : 0xffe8f0fe, 14));
            footer.setForeground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(nightMode ? 0x356b9bea : 0x2e1a73e8),
                    null, roundRect(Color.WHITE, 14)));
            footer.setClickable(true);
            footer.setFocusable(true);
            footer.setMinimumHeight(dp(50));
            footer.setContentDescription(footerText);
            BrowserIconView footerImage = iconButton(footerIcon, footerText);
            footerImage.setTag(SHEET_TAG_ACCENT);
            footerImage.setClickable(false);
            footerImage.setFocusable(false);
            footerImage.setTintColor(nightMode ? DARK_ACCENT : BLUE);
            footer.addView(footerImage, new LinearLayout.LayoutParams(dp(30), dp(30)));
            TextView footerLabel = new TextView(this);
            footerLabel.setTag(SHEET_TAG_PRIMARY);
            footerLabel.setText(footerText);
            footerLabel.setTextColor(sheetText);
            footerLabel.setTextSize(14f);
            footerLabel.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            footerLabel.setPadding(dp(10), 0, 0, 0);
            footer.addView(footerLabel, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView chevron = new TextView(this);
            chevron.setTag(SHEET_TAG_MUTED);
            chevron.setText("›");
            chevron.setTextColor(sheetMuted);
            chevron.setTextSize(27f);
            footer.addView(chevron);
            LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
            footerParams.setMargins(dp(4), dp(7), dp(4), 0);
            panel.addView(footer, footerParams);
            footer.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    dismissOverlayForNavigation();
                    footerAction.run();
                }
            });
        }

        int safeTop = Math.max(0, systemInsetTop - rootFrame.getPaddingTop());
        int safeBottom = Math.max(0, systemInsetBottom - rootFrame.getPaddingBottom());
        int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * .90f) - safeTop - safeBottom;
        int desiredHeight = dp(120 + contentHeightDp + (footerAction == null ? 0 : 57));
        int panelHeight = Math.min(maxHeight, desiredHeight);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, panelHeight, Gravity.BOTTOM);
        panelParams.setMargins(dp(6), safeTop, dp(6), dp(6) + safeBottom);
        overlay.addView(panel, panelParams);
        rootFrame.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activeOverlay = overlay;
        activeOverlayPanel = panel;
        activeOverlaySheet = true;
        activeOverlayBackAction = backAction;
        activeOverlayDismissAction = null;
        overlayDismissInProgress = false;
        themeSheetTree(panel);
        Motion.showSheet(overlay, panel, reduceMotion());
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { dismissOverlay(); }
        });
    }

    private void showMainMenu() {
        if (!requireStartupReady()) return;
        startupSessionPending = false;
        String subtitle = currentHost().length() == 0 ? modeLabel() : currentHost() + " · " + modeLabel();
        boolean bookmarked = dataStore != null && dataStore.isBookmarked(currentPageUrl);
        String[] items = new String[] {
                "新建标签页",
                "标签页工具",
                "书签与历史",
                "下载中心",
                bookmarked ? "管理当前收藏" : "收藏当前页面",
                "保护与脚本",
                "桌面网站",
                "深色模式",
                "广告拦截",
                "页面工具",
                "扫描二维码",
                "新建隐私窗口"
        };
        int[] icons = new int[] {
                BrowserIconView.PLUS, BrowserIconView.TABS, BrowserIconView.HISTORY,
                BrowserIconView.DOWNLOAD, BrowserIconView.BOOKMARK, BrowserIconView.SHIELD,
                BrowserIconView.DESKTOP, BrowserIconView.APPEARANCE, BrowserIconView.SHIELD,
                BrowserIconView.MENU, BrowserIconView.QR, BrowserIconView.SHIELD
        };
        int[] kinds = new int[] {
                SHEET_ROW_ACTION, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE,
                SHEET_ROW_ACTION, SHEET_ROW_NAVIGATE,
                desktopMode ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                nightMode ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                adBlockEnabled ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                SHEET_ROW_NAVIGATE, SHEET_ROW_ACTION, SHEET_ROW_ACTION
        };
        showDashboardSheet("Median", subtitle, items, icons, kinds, 3,
                new int[] { 0, 6, 9 }, new String[] { "常用", "快捷开关", "工具" },
                null, new Runnable() {
                    @Override public void run() { showBrowserSettings(); }
                }, "浏览器设置", BrowserIconView.SETTINGS, new SheetHandler() {
            @Override public void onItem(int which) {
                switch (which) {
                    case 0: newTab(); break;
                    case 1: showTabTools(); break;
                    case 2: showBrowserLibrary(); break;
                    case 3: showDownloadCenter(); break;
                    case 4: toggleCurrentBookmark(); break;
                    case 5:
                        showProtectionPanel(new Runnable() {
                            @Override public void run() { showMainMenu(); }
                        });
                        break;
                    case 6:
                        desktopMode = !desktopMode;
                        prefs.edit().putBoolean("desktop", desktopMode).apply();
                        applyDesktopMode();
                        reloadWebView(webView);
                        showMainMenu();
                        break;
                    case 7:
                        toggleNightMode();
                        showMainMenu();
                        break;
                    case 8:
                        adBlockEnabled = !adBlockEnabled;
                        prefs.edit().putBoolean("adblock", adBlockEnabled).apply();
                        adBlockActiveByView.clear();
                        if (webView != null && isNetworkPage(currentPageUrl)) reloadWebView(webView);
                        toast(adBlockEnabled ? "广告拦截已开启" : "广告拦截已关闭");
                        showMainMenu();
                        break;
                    case 9: showPageTools(); break;
                    case 10: startQrScan(); break;
                    case 11: openPrivateWindow(); break;
                    default: break;
                }
            }
        });
    }

    private void showPrivacyTools() {
        String[] items = new String[] {
                "广告与隐私过滤器\n订阅、例外与规则诊断",
                "用户脚本\n安装、更新、启停与脚本命令",
                "密码管理器\n本地加密保存与填充"
        };
        int[] icons = new int[] { BrowserIconView.SHIELD, BrowserIconView.SCRIPT, BrowserIconView.KEY };
        showActionSheet("隐私与扩展", "全部数据默认只保存在本机", items, icons,
                null, null, new Runnable() {
                    @Override public void run() { showMainMenu(); }
                }, new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showFilterCenter(new Runnable() {
                            @Override public void run() { showPrivacyTools(); }
                        });
                        else if (which == 1) showScriptCenter(new Runnable() {
                            @Override public void run() { showPrivacyTools(); }
                        });
                        else showPasswordMenu(new Runnable() {
                            @Override public void run() { showPrivacyTools(); }
                        });
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
        if (!isNetworkPage(url)) {
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
                "历史记录（" + dataStore.historyCount() + "）\n日期分组、搜索与单条管理",
                "离线页面（" + services.offlinePages().getAll().size() + "）",
                "导出书签备份",
                "导入书签备份",
                "导出加密完整备份",
                "导入加密完整备份"
        };
        int[] icons = new int[] { BrowserIconView.PLUS, BrowserIconView.HISTORY,
                BrowserIconView.STORAGE, BrowserIconView.SHARE, BrowserIconView.STORAGE,
                BrowserIconView.KEY, BrowserIconView.KEY };
        showActionSheet("书签与历史", "本地保存 · 不上传云端", items, icons, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) openBookmarkManager(new Runnable() {
                    @Override public void run() { showBrowserLibrary(); }
                });
                else if (which == 1) showHistoryList("");
                else if (which == 2) showOfflinePages();
                else if (which == 3) beginBackupExport();
                else if (which == 4) beginBackupImport();
                else if (which == 5) beginFullBackupExport();
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
                }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface dialog) { showBookmarkActions(item, returnFolder); }
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
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface ignored) { showBookmarkFolder(initialFolder); }
        });
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
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface ignored) { showBookmarkFolder(parentId); }
        });
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
                }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface dialog) {
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
        if (dataStore == null) return;
        dismissOverlayForNavigation();
        final int pageBackground = nightMode ? DARK_BACKGROUND : WHITE;
        final int cardBackground = nightMode ? DARK_CARD : WHITE;
        final int pageText = nightMode ? DARK_TEXT : TEXT;
        final int pageMuted = nightMode ? DARK_MUTED : MUTED;
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(pageBackground);
        overlay.setClickable(true);
        overlay.setFocusableInTouchMode(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(8), dp(14), dp(12));
        overlay.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        BrowserIconView back = iconButton(BrowserIconView.BACK, "返回书签与历史");
        back.setTintColor(pageText);
        header.addView(back, new LinearLayout.LayoutParams(dp(46), dp(48)));
        final TextView title = new TextView(this);
        title.setTextSize(22f);
        title.setTextColor(pageText);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(54), 1f));
        BrowserIconView clear = iconButton(BrowserIconView.DELETE, "清空历史记录");
        BrowserIconView close = iconButton(BrowserIconView.CLOSE, "返回网页");
        clear.setTintColor(pageText);
        close.setTintColor(pageText);
        header.addView(clear, new LinearLayout.LayoutParams(dp(46), dp(48)));
        header.addView(close, new LinearLayout.LayoutParams(dp(46), dp(48)));
        page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        LinearLayout searchPill = new LinearLayout(this);
        searchPill.setOrientation(LinearLayout.HORIZONTAL);
        searchPill.setGravity(Gravity.CENTER_VERTICAL);
        searchPill.setPadding(dp(12), 0, dp(4), 0);
        searchPill.setBackground(roundRect(nightMode ? 0xff303337 : SURFACE, 20));
        final EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setText(query == null ? "" : query);
        search.setHint("搜索标题或网址");
        search.setHintTextColor(pageMuted);
        search.setTextColor(pageText);
        search.setTextSize(14.5f);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setPadding(0, 0, dp(6), 0);
        searchPill.addView(search, new LinearLayout.LayoutParams(0, dp(46), 1f));
        BrowserIconView runSearch = iconButton(BrowserIconView.SEARCH, "搜索历史记录");
        runSearch.setTintColor(pageText);
        searchPill.addView(runSearch, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        searchParams.setMargins(dp(2), 0, dp(2), dp(10));
        page.addView(searchPill, searchParams);

        final ArrayList<Object> rows = new ArrayList<Object>();
        final BaseAdapter[] adapterHolder = new BaseAdapter[1];
        final String[] activeQuery = new String[] { query == null ? "" : query.trim() };
        final FrameLayout body = new FrameLayout(this);
        final TextView empty = new TextView(this);
        empty.setTextColor(pageMuted);
        empty.setTextSize(14f);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(24), dp(24), dp(24));

        final class HistoryRow {
            final LinearLayout root;
            final TextView primary;
            final TextView secondary;
            final BrowserIconView more;

            HistoryRow() {
                root = new LinearLayout(MainActivity.this);
                root.setOrientation(LinearLayout.HORIZONTAL);
                root.setGravity(Gravity.CENTER_VERTICAL);
                root.setPadding(dp(16), dp(7), dp(7), dp(7));
                root.setElevation(dp(1));
                root.setClickable(true);
                LinearLayout labels = new LinearLayout(MainActivity.this);
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.setGravity(Gravity.CENTER_VERTICAL);
                primary = new TextView(MainActivity.this);
                primary.setTextColor(pageText);
                primary.setTextSize(15.5f);
                primary.setSingleLine(true);
                primary.setEllipsize(android.text.TextUtils.TruncateAt.END);
                secondary = new TextView(MainActivity.this);
                secondary.setTextColor(pageMuted);
                secondary.setTextSize(12.5f);
                secondary.setSingleLine(true);
                secondary.setEllipsize(android.text.TextUtils.TruncateAt.END);
                labels.addView(primary, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(27)));
                labels.addView(secondary, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(23)));
                root.addView(labels, new LinearLayout.LayoutParams(0, dp(58), 1f));
                more = iconButton(BrowserIconView.MENU, "历史记录操作");
                more.setTintColor(pageText);
                more.setFocusable(false);
                root.addView(more, new LinearLayout.LayoutParams(dp(46), dp(46)));
            }

            void bind(final BrowserDataStore.HistoryItem item) {
                root.setBackground(roundRect(cardBackground, 18));
                primary.setText(item.title);
                CharSequence relative = android.text.format.DateUtils.getRelativeTimeSpanString(
                        item.visitedAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS);
                String visits = item.visits > 1 ? " · " + item.visits + " 次访问" : "";
                secondary.setText(hostOf(item.url) + " · " + relative + visits);
                root.setContentDescription(item.title + "，" + hostOf(item.url));
                root.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        dismissOverlayForNavigation();
                        loadInput(item.url);
                    }
                });
                more.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        showHistoryItemActions(item, activeQuery[0]);
                    }
                });
            }
        }

        final BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int position) { return rows.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public int getViewTypeCount() { return 2; }
            @Override public int getItemViewType(int position) { return rows.get(position) instanceof String ? 0 : 1; }
            @Override public boolean isEnabled(int position) { return !(rows.get(position) instanceof String); }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                Object value = rows.get(position);
                if (value instanceof String) {
                    TextView section = convertView instanceof TextView ? (TextView) convertView : new TextView(MainActivity.this);
                    section.setText((String) value);
                    section.setTextColor(BLUE);
                    section.setTextSize(12f);
                    section.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    section.setGravity(Gravity.BOTTOM | Gravity.START);
                    section.setPadding(dp(8), 0, dp(8), dp(7));
                    return section;
                }
                HistoryRow row;
                if (convertView == null || !(convertView.getTag() instanceof HistoryRow)) {
                    row = new HistoryRow();
                    row.root.setTag(row);
                    convertView = row.root;
                } else row = (HistoryRow) convertView.getTag();
                row.bind((BrowserDataStore.HistoryItem) value);
                return convertView;
            }
        };
        adapterHolder[0] = adapter;
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(9));
        list.setPadding(0, 0, 0, dp(20));
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        body.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final Runnable rebuild = new Runnable() {
            @Override public void run() {
                rows.clear();
                List<BrowserDataStore.HistoryItem> items = dataStore.recentHistory(200, activeQuery[0]);
                java.util.Calendar today = java.util.Calendar.getInstance();
                today.set(java.util.Calendar.HOUR_OF_DAY, 0);
                today.set(java.util.Calendar.MINUTE, 0);
                today.set(java.util.Calendar.SECOND, 0);
                today.set(java.util.Calendar.MILLISECOND, 0);
                long todayStart = today.getTimeInMillis();
                today.add(java.util.Calendar.DAY_OF_YEAR, -1);
                long yesterdayStart = today.getTimeInMillis();
                String lastSection = "";
                for (BrowserDataStore.HistoryItem item : items) {
                    String section;
                    if (item.visitedAt >= todayStart) section = "今天";
                    else if (item.visitedAt >= yesterdayStart) section = "昨天";
                    else section = android.text.format.DateFormat.getMediumDateFormat(MainActivity.this)
                            .format(new java.util.Date(item.visitedAt));
                    if (!section.equals(lastSection)) {
                        rows.add(section);
                        lastSection = section;
                    }
                    rows.add(item);
                }
                boolean noResults = items.size() == 0;
                empty.setText(activeQuery[0].length() == 0 ?
                        "还没有浏览历史\n访问过的网页会按日期显示在这里" : "没有匹配的历史记录");
                empty.setVisibility(noResults ? View.VISIBLE : View.GONE);
                title.setText(activeQuery[0].length() == 0 ?
                        "历史记录  " + dataStore.historyCount() : "搜索结果  " + items.size());
                if (adapterHolder[0] != null) adapterHolder[0].notifyDataSetChanged();
            }
        };
        rebuild.run();

        View.OnClickListener searchAction = new View.OnClickListener() {
            @Override public void onClick(View view) {
                activeQuery[0] = search.getText().toString().trim();
                rebuild.run();
                hideKeyboard();
                search.clearFocus();
            }
        };
        runSearch.setOnClickListener(searchAction);
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    activeQuery[0] = search.getText().toString().trim();
                    rebuild.run();
                    hideKeyboard();
                    search.clearFocus();
                    return true;
                }
                return false;
            }
        });
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { navigateOverlayBack(); }
        });
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { dismissOverlay(); }
        });
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (dataStore.historyCount() == 0) { toast("历史记录已经为空"); return; }
                confirmClearHistory(new Runnable() {
                    @Override public void run() { showHistoryList(""); }
                });
            }
        });

        rootFrame.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activeOverlay = overlay;
        activeOverlayPanel = page;
        activeOverlaySheet = false;
        activeOverlayBackAction = new Runnable() {
            @Override public void run() { showBrowserLibrary(); }
        };
        activeOverlayDismissAction = null;
        Motion.showPage(overlay, page, reduceMotion());
    }

    private void showHistoryItemActions(final BrowserDataStore.HistoryItem item, final String query) {
        String relative = android.text.format.DateUtils.getRelativeTimeSpanString(item.visitedAt,
                System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString();
        showActionSheet(item.title, hostOf(item.url) + " · " + relative,
                new String[] { "打开网页", "在新标签页打开", "复制网址", "删除这条记录" },
                new int[] { BrowserIconView.SEARCH, BrowserIconView.PLUS,
                        BrowserIconView.SHARE, BrowserIconView.DELETE },
                new int[] { SHEET_ROW_ACTION, SHEET_ROW_ACTION, SHEET_ROW_ACTION, SHEET_ROW_ACTION },
                null, new Runnable() {
                    @Override public void run() { showHistoryList(query); }
                }, new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) loadInput(item.url);
                        else if (which == 1) openUrlInNewTab(item.url, true);
                        else if (which == 2) {
                            ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("历史记录网址", item.url));
                            toast("网址已复制");
                            showHistoryList(query);
                        } else {
                            dataStore.removeHistory(item.url);
                            toast("这条历史记录已删除");
                            showHistoryList(query);
                        }
                    }
                });
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
                        clearConnectionHints();
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
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                    "application/json", "text/html", "application/xhtml+xml",
                    "application/x-netscape-bookmark", "text/plain"
            });
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
        if (host.length() == 0) { toast("当前页面没有网站设置"); return; }
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
                if (!host.equalsIgnoreCase(currentHost())) {
                    dismissOverlayForNavigation();
                    toast("页面已经变化，请重新打开网站设置");
                    return;
                }
                if (which == 0) {
                    toggleCompatibilityMode(host, settings);
                    postAfterUiTransition(new Runnable() {
                        @Override public void run() { showSiteSettings(); }
                    });
                }
                else if (which == 1) showSiteContentSettings(host);
                else if (which == 2) showSitePermissionSettings(host);
                else if (which == 3) showSiteDisplaySettings(host);
                else if (which == 4) {
                    siteSettingsStore.clear(host);
                    adBlockActiveByView.clear();
                    applySiteSettings(webView, host);
                    reloadWebView(webView);
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
                        if (!host.equalsIgnoreCase(currentHost())) {
                            dismissOverlayForNavigation();
                            toast("页面已经变化，请重新打开网站设置");
                            return;
                        }
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
                        if (!host.equalsIgnoreCase(currentHost())) {
                            dismissOverlayForNavigation();
                            toast("页面已经变化，请重新打开网站设置");
                            return;
                        }
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
                        if (!host.equalsIgnoreCase(currentHost())) {
                            dismissOverlayForNavigation();
                            toast("页面已经变化，请重新打开网站设置");
                            return;
                        }
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
        if (!host.equalsIgnoreCase(currentHost())) {
            toast("页面已经变化，请重新打开网站设置");
            return;
        }
        siteSettingsStore.save(host, settings);
        adBlockActiveByView.clear();
        applySiteSettings(webView, host);
        reloadWebView(webView);
        postAfterUiTransition(returnAction);
    }


    private void toggleCompatibilityMode(String host, SiteSettingsStore.SiteSettings settings) {
        if (host == null || host.length() == 0 || settings == null) return;
        if (settings.compatibilityMode()) {
            settings.compatibilityMode(false);
            siteSettingsStore.save(host, settings);
            adBlockActiveByView.clear();
            applySiteSettings(webView, host);
            reloadWebView(webView);
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
        if (reload && webView != null) reloadWebView(webView);
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
        if (failedUrl.regionMatches(true, 0, "https://", 0, 8)) {
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

    private void showTextZoomDialog(final String host, final SiteSettingsStore.SiteSettings settings,
                                    final Runnable returnAction) {
        SettingsDialogReturn back = new SettingsDialogReturn(returnAction);
        final int[] values = new int[] { 80, 90, 100, 110, 125, 150, 175, 200 };
        String[] labels = new String[values.length];
        int checked = 2;
        for (int i = 0; i < values.length; i++) { labels[i] = values[i] + "%"; if (values[i] == settings.textZoom()) checked = i; }
        new AlertDialog.Builder(this).setTitle("文字缩放").setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (!host.equalsIgnoreCase(currentHost())) {
                    dialog.dismiss();
                    toast("页面已经变化，请重新打开网站设置");
                    return;
                }
                settings.textZoom(values[which]);
                siteSettingsStore.save(host, settings);
                applySiteSettings(webView, host);
                reloadWebView(webView);
                dialog.dismiss();
                continueSettingsPanel(returnAction);
            }
        }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void confirmClearCurrentSiteData() {
        final String url = currentPageUrl;
        final String host = currentHost();
        if (url == null || host.length() == 0) return;
        SettingsDialogReturn back = new SettingsDialogReturn(new Runnable() {
            @Override public void run() { showSiteSettings(); }
        });
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
                        Uri uri = null;
                        try { uri = Uri.parse(url); } catch (RuntimeException ignored) {}
                        if (uri != null) {
                            String origin = uri.getScheme() + "://" + uri.getAuthority();
                            android.webkit.WebStorage.getInstance().deleteOrigin(origin);
                            android.webkit.GeolocationPermissions.getInstance().clear(origin);
                        }
                        if (webView != null && host.equalsIgnoreCase(currentHost())) reloadWebView(webView);
                        toast("此网站数据已清除");
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void startQrScan() {
        if (!requireStartupReady()) return;
        dismissOverlayForNavigation();
        showQrScannerPage();
    }

    private void showQrScannerPage() {
        if (webView == null) return;
        WebView source = webView;
        prepareHomeDestination(source);
        currentPageUrl = QR_URL;
        currentPageHost = "";
        pageHosts.put(source, "");
        qrScannerViews.add(source);
        trustedHomeViews.remove(source);
        customHomeViews.remove(source);
        renderedHomeKeys.remove(source);
        source.loadDataWithBaseURL(QR_URL, QrScannerPage.html(qrToken(), nightMode),
                "text/html", "UTF-8", QR_URL);
        updateCurrentTab(QR_URL, "扫描二维码");
        requestChromeUpdate();
    }

    private boolean handleQrScannerPrompt(final WebView source, String message, String value,
                                          JsPromptResult result) {
        if (!qrToken().equals(message)) return false;
        boolean accepted = result != null && source != null && source == webView &&
                isQrScannerUrl(source.getUrl()) && qrScannerViews.contains(source);
        if (result != null) result.confirm("");
        if (!accepted) return true;
        try {
            JSONObject payload = new JSONObject(value == null ? "{}" : value);
            if (payload.optBoolean("cancel", false)) {
                qrScannerViews.remove(source);
                source.post(new Runnable() {
                    @Override public void run() { returnFromQrScanner(); }
                });
            } else if (payload.optBoolean("external", false)) {
                qrScannerViews.remove(source);
                source.post(new Runnable() {
                    @Override public void run() {
                        if (!launchExternalQrScanner()) {
                            toast("没有可返回扫码结果的系统应用，已切回内置扫码");
                            showQrScannerPage();
                        }
                    }
                });
            } else {
                final String scanned = payload.optString("value", "").trim();
                if (scanned.length() == 0 || scanned.length() > 16 * 1024) {
                    toast("二维码内容为空或异常过长");
                    source.post(new Runnable() {
                        @Override public void run() { if (source == webView) showQrScannerPage(); }
                    });
                } else {
                    qrScannerViews.remove(source);
                    source.post(new Runnable() {
                        @Override public void run() { showQrResult(scanned); }
                    });
                }
            }
        } catch (Exception error) {
            toast("无法读取扫码结果");
            if (source != null) source.post(new Runnable() {
                @Override public void run() { if (source == webView) showQrScannerPage(); }
            });
        }
        return true;
    }

    private boolean launchExternalQrScanner() {
        String[] actions = new String[] {
                "com.google.zxing.client.android.SCAN",
                "com.srowen.bs.android.SCAN"
        };
        for (String action : actions) {
            Intent scan = new Intent(action);
            scan.putExtra("SCAN_MODE", "QR_CODE_MODE");
            scan.putExtra("PROMPT_MESSAGE", "扫描二维码");
            try {
                startActivityForResult(scan, QR_SCAN_REQUEST);
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private void showQrResult(final String value) {
        String preview = value.replace('\n', ' ').replace('\r', ' ');
        preview = UiText.ellipsize(preview, 180);
        final boolean link = isHttpUrl(value);
        String[] items = link
                ? new String[] { "打开链接", "在新标签页打开", "复制内容", "使用搜索引擎搜索" }
                : new String[] { "使用搜索引擎搜索", "复制内容", "重新扫描" };
        int[] icons = link
                ? new int[] { BrowserIconView.SEARCH, BrowserIconView.PLUS,
                        BrowserIconView.SHARE, BrowserIconView.SEARCH }
                : new int[] { BrowserIconView.SEARCH, BrowserIconView.SHARE, BrowserIconView.QR };
        final Runnable rescan = new Runnable() {
            @Override public void run() { showQrScannerPage(); }
        };
        showActionSheet("二维码内容", preview, items, icons, null, null, rescan, rescan, new SheetHandler() {
            @Override public void onItem(int which) {
                if (link && which == 0) loadInput(value);
                else if (link && which == 1) openUrlInNewTab(value, true);
                else if ((link && which == 2) || (!link && which == 1)) {
                    ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("二维码内容", value));
                    toast("二维码内容已复制");
                    returnFromQrScanner();
                } else if (!link && which == 2) {
                    showQrScannerPage();
                } else {
                    loadInput(value);
                }
            }
        });
    }

    private void returnFromQrScanner() {
        if (webView == null) return;
        qrScannerViews.remove(webView);
        if (isQrScannerUrl(webView.getUrl()) && webView.canGoBack()) goBackWebView(webView);
        else if (isQrScannerUrl(webView.getUrl())) showHome();
    }

    private void resumeQrScannerIfNeeded() {
        if (webView == null || !isQrScannerUrl(webView.getUrl()) || !qrScannerViews.contains(webView)) return;
        try { webView.evaluateJavascript("window.__medianRestartScanner&&window.__medianRestartScanner();", null); }
        catch (RuntimeException ignored) {}
    }

    private void showPageTools() {
        if (isInternalBrowserUrl(currentPageUrl)) { toast("当前页面没有网页工具"); return; }
        int offlineCount = services.offlinePages().getAll().size();
        PageAssistant assistant = services.assistant();
        String[] items = new String[] {
                "页面内查找", "切换沉浸阅读模式", assistant.isSpeaking() ? "停止朗读" : "朗读正文",
                "页面结构与统计", "TLS 证书与 SHA-256", "保存完整离线页面", "离线页面库 · " + offlineCount + " 页",
                "分享当前页面", "复制页面地址", "复制无跟踪参数地址", "翻译为简体中文",
                "打印或另存为 PDF", "媒体中心 · 已发现 " + mediaSniffer.size() + " 项", "当前页脚本命令",
                "使用系统浏览器打开\n可用于 Passkey 等系统凭据登录",
                "收起工具栏\n全屏浏览，向下轻滑恢复"
        };
        int[] icons = new int[] { BrowserIconView.SEARCH, BrowserIconView.INFO, BrowserIconView.SPEED, BrowserIconView.INFO,
                BrowserIconView.SHIELD, BrowserIconView.STORAGE, BrowserIconView.STORAGE, BrowserIconView.SHARE, BrowserIconView.PLUS,
                BrowserIconView.SHIELD, BrowserIconView.INFO, BrowserIconView.STORAGE,
                BrowserIconView.SPEED, BrowserIconView.SCRIPT, BrowserIconView.PLUS, BrowserIconView.APPEARANCE };
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
                else if (which == 13) showPageScriptCommands();
                else if (which == 14) openCurrentPageInSystemBrowser();
                else {
                    immersiveScrollChrome = true;
                    prefs.edit().putBoolean("immersive_scroll_chrome", true).apply();
                    updateImmersivePageLayout();
                    hideBrowserChrome();
                }
            }
        });
    }

    private void openCurrentPageInSystemBrowser() {
        if (!isNetworkPage(currentPageUrl)) { toast("当前页面没有可打开的网页"); return; }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentPageUrl));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        } catch (Exception e) { toast("没有可用的系统浏览器"); }
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
        if (url == null || !url.regionMatches(true, 0, "https://", 0, 8) ||
                scriptExecutor == null || scriptExecutor.isShutdown()) {
            toast("当前页面不是可检查的 HTTPS 页面");
            return;
        }
        final String userAgent = source == null ? null : source.getSettings().getUserAgentString();
        toast("正在独立探测服务器证书（结果不代表当前页面加载响应）…");
        executeTask(scriptExecutor, new Runnable() {
            @Override public void run() {
                TlsInspector.Result value = null;
                Exception failure = null;
                try { value = TlsInspector.inspect(url, userAgent); } catch (Exception e) { failure = e; }
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
                if (activityDestroyed) return;
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
        }).setNegativeButton("返回", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { showBrowserLibrary(); }
        }).setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface dialog) { showBrowserLibrary(); }
        }).show();
    }

    private void showOfflineActions(final OfflinePageStore.Entry entry) {
        String[] items = new String[] { "离线打开", "打开原网页", "分享归档文件", "删除离线页面" };
        showActionSheet(entry.title, entry.url, items, new int[] { BrowserIconView.PLUS, BrowserIconView.RELOAD,
                BrowserIconView.SHARE, BrowserIconView.CLOSE }, null, null,
                new Runnable() { @Override public void run() { showOfflinePages(); } }, new SheetHandler() {
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
                    confirmDeleteOfflinePage(entry);
                }
            }
        });
    }

    private void confirmDeleteOfflinePage(final OfflinePageStore.Entry entry) {
        new AlertDialog.Builder(this).setTitle("删除离线页面？").setMessage(entry.title)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        services.offlinePages().remove(entry.file);
                        toast("离线页面已删除");
                        if (services.offlinePages().getAll().isEmpty()) showBrowserLibrary();
                        else showOfflinePages();
                    }
                }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { showOfflineActions(entry); }
                }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface dialog) { showOfflineActions(entry); }
                }).show();
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
        startupSessionPending = false;
        String[] items = new String[] {
                "广告拦截\n" + (adBlockEnabled ? "已开启" : "已关闭"),
                "深色模式\n" + (nightMode ? "已开启" : "已关闭"),
                "侧滑导航\n" + (edgeNavigationEnabled ? "已开启" : "已关闭"),
                "标记页面广告\n" + (isNetworkPage(currentPageUrl) ? "点选并隐藏网页元素" : "请先打开网页"),
                "浏览与搜索\n连接、启动和地址栏",
                "主页与外观\n主页、布局和主题",
                "脚本与拦截\n过滤器、规则和脚本",
                "隐私与密码\n密码库、权限与数据",
                "下载设置\n目录与下载策略",
                "性能模式\n当前：" + modeLabel(),
                "关于 Median\n版本信息与社区"
        };
        int[] icons = new int[] {
                BrowserIconView.SHIELD, BrowserIconView.APPEARANCE, BrowserIconView.BACK,
                BrowserIconView.CLEAN, BrowserIconView.SEARCH, BrowserIconView.HOME,
                BrowserIconView.SCRIPT, BrowserIconView.KEY, BrowserIconView.DOWNLOAD,
                BrowserIconView.SPEED, BrowserIconView.INFO
        };
        int[] kinds = new int[] {
                adBlockEnabled ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                nightMode ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                edgeNavigationEnabled ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                SHEET_ROW_ACTION,
                SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE,
                SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE,
                SHEET_ROW_NAVIGATE
        };
        showDashboardSheet("设置", "常用开关与分类设置", items, icons, kinds, 2,
                new int[] { 0, 4, 10 }, new String[] { "常用操作", "设置分类", "其他" }, new Runnable() {
            @Override public void run() { showMainMenu(); }
        }, null, null, 0, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) {
                    adBlockEnabled = !adBlockEnabled;
                    prefs.edit().putBoolean("adblock", adBlockEnabled).apply();
                    adBlockActiveByView.clear();
                    if (webView != null && isNetworkPage(currentPageUrl)) reloadWebView(webView);
                    toast(adBlockEnabled ? "广告拦截已开启" : "广告拦截已关闭");
                    showBrowserSettings();
                } else if (which == 3) {
                    if (!isNetworkPage(currentPageUrl)) {
                        toast("请先打开普通网页再标记广告");
                        showBrowserSettings();
                        return;
                    }
                    startAdPicker();
                } else if (which == 1) {
                    toggleNightMode();
                    // Keep the settings dashboard in place after a live theme switch so the
                    // user can continue adjusting options without reopening the menu.
                    showBrowserSettings();
                }
                else if (which == 2) {
                    edgeNavigationEnabled = !edgeNavigationEnabled;
                    prefs.edit().putBoolean("edge_navigation", edgeNavigationEnabled).apply();
                    toast(edgeNavigationEnabled ? "网页边缘手势已开启" : "网页边缘手势已关闭");
                    showBrowserSettings();
                } else if (which == 4) showBrowsingSettings();
                else if (which == 5) showHomeCustomization(MainActivity.this);
                else if (which == 6) showExtensionSettings();
                else if (which == 7) showPrivacyDataSettings();
                else if (which == 8) showDownloadSettings();
                else if (which == 9) showPerformancePanel(MainActivity.this);
                else showAbout(MainActivity.this);
            }
        });
    }

    private void showBrowsingSettings() {
        String[] items = new String[] {
                "HTTPS 优先",
                "清理跟踪参数",
                "第三方 Cookie",
                "启动时\n" + homeOpenBehaviorLabel(),
                "默认搜索\n" + searchEngineLabel(),
                "沉浸式滚动栏\n下滑隐藏顶栏和底栏，上滑显示"
        };
        int[] icons = new int[] { BrowserIconView.SHIELD, BrowserIconView.CLEAN,
                BrowserIconView.COOKIE, BrowserIconView.STARTUP, BrowserIconView.SEARCH, BrowserIconView.APPEARANCE };
        int[] kinds = new int[] {
                httpsOnly ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                cleanTrackingParameters ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                acceptThirdPartyCookies ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE,
                immersiveScrollChrome ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF
        };
        showActionSheet("浏览与搜索", "连接、启动和地址栏", items, icons, kinds, null,
                this, new SheetHandler() {
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
                        } else if (which == 3) showHomeOpenBehaviorChoice(false);
                        else if (which == 4) showSearchSettings();
                        else {
                            immersiveScrollChrome = !immersiveScrollChrome;
                            prefs.edit().putBoolean("immersive_scroll_chrome", immersiveScrollChrome).apply();
                            updateImmersivePageLayout();
                            if (!immersiveScrollChrome) showBrowserChrome(false);
                            toast(immersiveScrollChrome ? "沉浸式滚动栏已开启" : "沉浸式滚动栏已关闭");
                        }
                    }
                });
    }

    private void showExtensionSettings() {
        String[] items = new String[] {
                "广告拦截",
                "手动屏蔽页面元素",
                "过滤器订阅与规则",
                "用户脚本"
        };
        int[] icons = new int[] { BrowserIconView.SHIELD, BrowserIconView.CLEAN,
                BrowserIconView.STORAGE, BrowserIconView.SCRIPT };
        int[] kinds = new int[] { adBlockEnabled ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF,
                SHEET_ROW_ACTION, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE };
        showActionSheet("脚本与广告拦截", "规则与脚本均在本机处理", items, icons,
                kinds, null, this, new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) {
                            adBlockEnabled = !adBlockEnabled;
                            prefs.edit().putBoolean("adblock", adBlockEnabled).apply();
                            adBlockActiveByView.clear();
                            if (webView != null && isNetworkPage(currentPageUrl)) reloadWebView(webView);
                            toast(adBlockEnabled ? "广告拦截已开启" : "广告拦截已关闭");
                        } else if (which == 1) {
                            if (!isNetworkPage(currentPageUrl)) {
                                toast("请先打开普通网页再标记广告");
                                showExtensionSettings();
                            } else startAdPicker();
                        }
                        else if (which == 2) showFilterCenter(new Runnable() {
                            @Override public void run() { showExtensionSettings(); }
                        });
                        else showScriptCenter(new Runnable() {
                            @Override public void run() { showExtensionSettings(); }
                        });
                    }
                });
    }

    private void showPrivacyDataSettings() {
        String host = currentHost();
        String[] items = new String[] {
                "当前网站权限\n" + (host.length() == 0 ? "请先打开网页" : host),
                "密码库",
                "书签与历史",
                "存储与数据",
                "新建隐私窗口",
                "退出时清理浏览数据\n不删除书签、密码、下载与脚本"
        };
        int[] icons = new int[] { BrowserIconView.SHIELD, BrowserIconView.KEY,
                BrowserIconView.HISTORY, BrowserIconView.STORAGE, BrowserIconView.SHIELD, BrowserIconView.CLOSE };
        int[] kinds = new int[] { SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE,
                SHEET_ROW_NAVIGATE, SHEET_ROW_NAVIGATE,
                clearDataOnExit ? SHEET_ROW_TOGGLE_ON : SHEET_ROW_TOGGLE_OFF };
        showActionSheet("隐私与数据", "本地优先", items, icons,
                kinds, null, this, new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showSiteSettings();
                        else if (which == 1) showPasswordMenu(new Runnable() {
                            @Override public void run() { showPrivacyDataSettings(); }
                        });
                        else if (which == 2) showBrowserLibrary();
                        else if (which == 3) showStoragePanel(MainActivity.this);
                        else if (which == 4) openPrivateWindow();
                        else {
                            clearDataOnExit = !clearDataOnExit;
                            prefs.edit().putBoolean("clear_data_on_exit", clearDataOnExit).apply();
                            toast(clearDataOnExit ? "退出时将清理浏览数据" : "退出时不自动清理浏览数据");
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
        showActionSheet("搜索设置", "地址栏与主页共用", items, icons,
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
        homeCustomization().show(backAction);
    }

    /** Appearance editing is lazy and does not participate in first-page startup. */
    private HomeCustomizationController homeCustomization() {
        if (homeCustomizationController == null) {
            homeCustomizationController = new HomeCustomizationController(this, prefs, homeImages, uiHandler,
                    new HomeCustomizationController.TaskQueue() {
                        @Override public boolean isAvailable() {
                            return scriptExecutor != null && !scriptExecutor.isShutdown();
                        }
                        @Override public boolean execute(Runnable task) { return executeTask(scriptExecutor, task); }
                    }, new HomeCustomizationController.Host() {
                        @Override public HomePageConfig homePageConfig() { return MainActivity.this.homePageConfig(); }
                        @Override public boolean isNightMode() { return nightMode; }
                        @Override public void refreshHomeIfVisible() {
                            if (isHomeUrl(currentPageUrl)) showHome();
                        }
                        @Override public void toast(String message) { MainActivity.this.toast(message); }
                        @Override public void postAfterUiTransition(Runnable action) {
                            MainActivity.this.postAfterUiTransition(action);
                        }
                        @Override public void showActionSheet(String title, String subtitle, String[] items, int[] icons,
                                Runnable backAction, final HomeCustomizationController.SheetHandler handler) {
                            MainActivity.this.showActionSheet(title, subtitle, items, icons, null, null, backAction,
                                    new SheetHandler() {
                                        @Override public void onItem(int which) { handler.onItem(which); }
                                    });
                        }
                        @Override public String searchEngineLabel() { return MainActivity.this.searchEngineLabel(); }
                        @Override public void showSearchEngineDialog(Runnable action) {
                            MainActivity.this.showSearchEngineDialog(action);
                        }
                        @Override public void openBookmarkManager(Runnable action) {
                            MainActivity.this.openBookmarkManager(action);
                        }
                        @Override public String homeOpenBehaviorLabel() { return MainActivity.this.homeOpenBehaviorLabel(); }
                        @Override public void showHomeOpenBehaviorChoice() {
                            MainActivity.this.showHomeOpenBehaviorChoice(true);
                        }
                    });
        }
        return homeCustomizationController;
    }

    private void showHomeOpenBehaviorChoice() { showHomeOpenBehaviorChoice(false); }

    private void showHomeOpenBehaviorChoice(final boolean returnToCustomization) {
        HomeOpenDialogReturn back = new HomeOpenDialogReturn(returnToCustomization);
        final String[] modes = new String[] { HomeOpenPolicy.OPEN_HOME, HomeOpenPolicy.OPEN_CUSTOM_URL,
                HomeOpenPolicy.KEEP_LAST };
        String[] labels = new String[] {
                "打开主页",
                "打开自定义网页",
                "恢复上次标签"
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
                .setMessage("用于主页键、冷启动和新标签；未写协议时使用 HTTPS。")
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


    private void continueHomeCustomization() {
        homeCustomization().continueCustomization();
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
        }).setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface dialog) { continueSettingsPanel(returnAction); }
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
                    confirmDeleteSearchEngine(engine, returnAction);
                }
            }
        }).setNegativeButton("返回", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { showCustomSearchList(returnAction); }
        }).setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface dialog) { showCustomSearchList(returnAction); }
        }).show();
    }

    private void confirmDeleteSearchEngine(final SearchEngineStore.Engine engine, final Runnable returnAction) {
        new AlertDialog.Builder(this).setTitle("删除搜索引擎？").setMessage(engine.name)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        searchEngines.delete(engine.id);
                        if (engine.id.equals(searchEngine)) setSearchEngine("google");
                        cachedHomeKey = "";
                        if (isHomeUrl(currentPageUrl)) showHome();
                        showCustomSearchList(returnAction);
                    }
                }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        showCustomSearchActions(engine, returnAction);
                    }
                }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface dialog) {
                        showCustomSearchActions(engine, returnAction);
                    }
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
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface ignored) { showCustomSearchList(returnAction); }
        });
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
        if (!isNetworkPage(url)) return;
        android.content.ClipboardManager manager = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(android.content.ClipData.newPlainText("页面地址", url));
        toast("页面地址已复制");
    }

    private void translatePage() {
        String url = webView == null ? null : webView.getUrl();
        if (!isNetworkPage(url)) return;
        try { loadInput("https://translate.google.com/translate?sl=auto&tl=zh-CN&u=" + URLEncoder.encode(url, "UTF-8")); }
        catch (Exception e) { toast("无法创建翻译地址"); }
    }

    private void printPage() {
        if (webView == null || !isNetworkPage(webView.getUrl())) return;
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

    private void showDownloadSettings() {
        String[] items = new String[] { "修改下载文件夹\n当前：" + DownloadDirectory.label(this),
                "下载策略\n" + downloadPolicySummary(), "下载中心" };
        final Runnable returnHere = new Runnable() { @Override public void run() { showDownloadSettings(); } };
        showActionSheet("下载设置", "内置下载器与文件保存", items,
                new int[] { BrowserIconView.STORAGE, BrowserIconView.SETTINGS, BrowserIconView.STORAGE },
                null, null, this, new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) DownloadDirectory.showPickerDialog(MainActivity.this, DOWNLOAD_DIRECTORY_REQUEST, returnHere);
                        else if (which == 1) showDownloadPolicy(returnHere);
                        else showDownloadCenter();
                    }
                });
    }

    private void showDownloadPolicy(final Runnable returnAction) {
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
                        continueSettingsPanel(returnAction);
                    }
                }).setNegativeButton("取消", new SettingsDialogReturn(returnAction))
                .setOnCancelListener(new SettingsDialogReturn(returnAction)).create();
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
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (activityDestroyed) return;
                            copyText("SHA-256", hex, "SHA-256 已计算并复制");
                            AlertDialog dialog = new AlertDialog.Builder(MainActivity.this).setTitle("SHA-256")
                                    .setMessage(item.filename + "\n\n" + hex).setPositiveButton("确定", null).create();
                            secureDialog(dialog); dialog.show();
                        }
                    });
                } catch (final Exception e) {
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (!activityDestroyed) toast("校验失败：" + safeMessage(e));
                        }
                    });
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
        if (webView == null || !isNetworkPage(webView.getUrl())) { showMediaCenter(); return; }
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
        String[] actions = manifest ? new String[] { "解析清单、轨道与加密信息", "使用外部播放器打开", "发送到外部下载器", "下载资源", "在新标签页打开", "复制媒体地址", "分享媒体地址" }
                : new String[] { "使用外部播放器打开", "发送到外部下载器", "下载资源", "在新标签页打开", "复制媒体地址", "分享媒体地址" };
        int[] icons = manifest ? new int[] { BrowserIconView.SPEED, BrowserIconView.SPEED, BrowserIconView.STORAGE, BrowserIconView.STORAGE, BrowserIconView.PLUS, BrowserIconView.PLUS, BrowserIconView.SHARE }
                : new int[] { BrowserIconView.SPEED, BrowserIconView.STORAGE, BrowserIconView.STORAGE, BrowserIconView.PLUS, BrowserIconView.PLUS, BrowserIconView.SHARE };
        showActionSheet(resource.kind, hostOf(resource.url) + " · " + resource.mime, actions, icons, new SheetHandler() {
            @Override public void onItem(int which) {
                if (manifest && which == 0) { inspectMediaManifest(resource); return; }
                if (manifest) which--;
                if (which == 0) openMediaExternally(resource);
                else if (which == 1) sendToExternalDownloader(resource.url);
                else if (which == 2) enqueueDownload(webView, resource.url,
                        webView == null ? null : webView.getSettings().getUserAgentString(), null, resource.mime);
                else if (which == 3) openUrlInNewTab(resource.url, true);
                else if (which == 4) {
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
        final WebView sourceView = webView;
        final long sourceSequence = navigationSequence;
        if (sourceView == null || resource == null) return;
        final HashMap<String, String> headers = new HashMap<String, String>(downloadContextHeaders(sourceView, resource.url));
        headers.put("User-Agent", sourceView.getSettings().getUserAgentString());
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
                            if (!activityDestroyed && isCurrentPageCallback(sourceView, sourceSequence))
                                showManifestVariants(resource, parsed);
                        }
                    });
                } catch (final Exception error) {
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (!activityDestroyed && isCurrentPageCallback(sourceView, sourceSequence))
                                toast("清单解析失败：" + safeMessage(error));
                        }
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

    /**
     * Gives URL-only download clients (1DM, ADM, etc.) a safe hand-off. Cookies,
     * Authorization headers and the WebView profile are intentionally not shared.
     */
    private void sendToExternalDownloader(String url) {
        try {
            final ArrayList<Intent> candidates = ExternalDownloadIntents.candidates(this, url);
            if (candidates.isEmpty()) { toast("没有可接收下载链接的外部应用"); return; }
            new AlertDialog.Builder(this).setTitle("选择外部下载器")
                    .setItems(ExternalDownloadIntents.labels(this, candidates), new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            try { startActivity(candidates.get(which)); }
                            catch (ActivityNotFoundException error) { toast("此外部应用已不可用，请重新选择下载器"); }
                            catch (RuntimeException error) { toast("无法发送下载链接：" + safeMessage(error)); }
                        }
                    }).setNegativeButton("取消", null).show();
        } catch (ActivityNotFoundException e) {
            toast("没有可用的外部下载器");
        } catch (Exception e) {
            toast("无法发送下载链接：" + safeMessage(e));
        }
    }

    private void enterPagePictureInPicture() {
        if (webView == null || !isNetworkPage(currentPageUrl)) { toast("当前页面不能进入画中画"); return; }
        try {
            PictureInPictureParams params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build();
            if (!enterPictureInPictureMode(params)) toast("系统未允许进入画中画");
        } catch (Exception e) { toast("画中画不可用：" + safeMessage(e)); }
    }

    private void showPageScriptCommands() {
        if (webView == null || !isNetworkPage(currentPageUrl)) { toast("当前页面没有脚本命令"); return; }
        final WebView source = webView;
        final long sequence = navigationSequence;
        final String sourceUrl = currentPageUrl;
        final String token = scriptBridgeTokens.get(source);
        if (token == null || token.length() < 32) { toast("当前 WebView 不支持安全脚本命令"); return; }
        StringBuilder query = new StringBuilder("(function(){var a=[];");
        for (UserScriptStore.Script script : scriptStore.getAll()) {
            if (!script.enabled || !scriptStore.matchesUrl(script.id, sourceUrl)) continue;
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
                "流畅模式",
                "标准模式",
                "低功耗模式",
                "流畅模式保持广告拦截（始终开启）",
                "立即释放后台网页内存"
        };
        int[] icons = new int[] { BrowserIconView.SPEED, BrowserIconView.HOME, BrowserIconView.SHIELD, BrowserIconView.SHIELD, BrowserIconView.STORAGE };
        int[] kinds = new int[] { SHEET_ROW_ACTION, SHEET_ROW_ACTION, SHEET_ROW_ACTION,
                SHEET_ROW_TOGGLE_ON, SHEET_ROW_ACTION };
        showActionSheet("性能调度", modeLabel(), modes, icons,
                kinds, null, backAction, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 3) {
                    toast("广告拦截与跳转保护始终开启");
                    showPerformancePanel(backAction);
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
        showScriptCenter(scriptCenterBackAction);
    }

    private void showScriptCenter(final Runnable backAction) {
        scriptCenterBackAction = backAction;
        final List<UserScriptStore.Script> scripts = scriptStore.getAll();
        final int actions = 4;
        String[] items = new String[actions + scripts.size()];
        int[] icons = new int[items.length];
        int[] kinds = new int[items.length];
        String[] sections = new String[items.length];
        items[0] = "浏览 Greasy Fork\n查找公开用户脚本";
        items[1] = "查找当前网站脚本\n按当前域名筛选 Greasy Fork";
        items[2] = "粘贴链接或脚本代码安装";
        items[3] = "检查并更新全部脚本";
        icons[0] = BrowserIconView.SEARCH;
        icons[1] = BrowserIconView.SEARCH;
        icons[2] = BrowserIconView.PLUS;
        icons[3] = BrowserIconView.RELOAD;
        for (int i = 0; i < actions; i++) kinds[i] = SHEET_ROW_ACTION;
        sections[0] = "获取与管理";
        for (int i = 0; i < scripts.size(); i++) {
            UserScriptStore.Script script = scripts.get(i);
            String version = script.version.length() == 0 ? "" : " · v" + script.version;
            items[i + actions] = script.name + version + "\n" +
                    (script.enabled ? "已启用" : "已停用") + " · " + script.matches.size() +
                    " 个匹配范围 · " + script.runAt;
            icons[i + actions] = BrowserIconView.SCRIPT;
            kinds[i + actions] = SHEET_ROW_NAVIGATE;
        }
        if (scripts.size() > 0) sections[actions] = "已安装 · " + scripts.size();
        String subtitle = scripts.size() == 0 ? "尚未安装脚本 · 仅安装你信任的来源" :
                "已启用 " + enabledScriptCount() + " 个 · 无风险评分或自动隔离";
        showActionSheet("用户脚本", subtitle, items, icons, kinds, sections, backAction,
                new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) loadNetworkUrl(webView, "https://greasyfork.org/zh-CN/scripts");
                else if (which == 1) {
                    String host = currentHost();
                    loadNetworkUrl(webView, host.length() == 0 || isHomeUrl(webView.getUrl()) ?
                            "https://greasyfork.org/zh-CN/scripts" :
                            "https://greasyfork.org/zh-CN/scripts/by-site/" + host);
                } else if (which == 2) showScriptImportDialog();
                else if (which == 3) updateAllUserScripts();
                else showScriptDetails(scripts.get(which - actions));
            }
        });
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
        final SettingsDialogReturn back = new SettingsDialogReturn(new Runnable() {
            @Override public void run() { showScriptCenter(); }
        });
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("安装用户脚本")
                .setMessage("复制脚本链接后打开这里会自动填入；也可以直接粘贴完整代码。网页中的 .user.js 安装按钮会由 Median 直接接管。")
                .setView(input)
                .setPositiveButton("读取并检查", null)
                .setNegativeButton("取消", back)
                .create();
        dialog.setOnCancelListener(back);
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
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            // A network task may finish after Android has torn down this
                            // Activity. Do not attach a dialog to the dead window.
                            if (activityDestroyed) return;
                            showScriptInstallConfirmation(script);
                        }
                    });
                } catch (final Exception e) {
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (!activityDestroyed) toast("脚本安装失败：" + safeMessage(e));
                        }
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
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (activityDestroyed) return;
                            showScriptInstallConfirmation(script);
                        }
                    });
                } catch (final Exception error) {
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (!activityDestroyed) toast("脚本安装失败：" + safeMessage(error));
                        }
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
        if (parsed.requires.size() > UserScriptStore.MAX_DEPENDENCIES || parsed.resources.size() > UserScriptStore.MAX_RESOURCES)
            throw new IllegalArgumentException("用户脚本依赖数量超过限制");
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
            long assetBytes = 0L;
            for (int i = 0; i < requireUrls.size(); i++) {
                FetchedScriptBytes dependency = awaitScriptAsset(downloads.get(i));
                downloads.set(i, null);
                assetBytes += dependency.data.length;
                if (assetBytes > SCRIPT_ASSET_TOTAL_MAX_BYTES) throw new IllegalArgumentException("用户脚本依赖总大小超过限制");
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
                assetBytes += data.data.length;
                if (assetBytes > SCRIPT_ASSET_TOTAL_MAX_BYTES) throw new IllegalArgumentException("用户脚本资源总大小超过限制");
                resource.mime = guessResourceMime(resource.url);
                resource.base64 = android.util.Base64.encodeToString(data.data, android.util.Base64.NO_WRAP);
            }
        } catch (Exception failure) {
            for (Future<FetchedScriptBytes> download : downloads) if (download != null) download.cancel(true);
            throw failure;
        }
        return parsed;
    }

    private Future<FetchedScriptBytes> submitScriptAsset(final String url, final String userAgent,
                                                          final String label) {
        if (scriptNetworkExecutor == null || scriptNetworkExecutor.isShutdown())
            throw new RejectedExecutionException("脚本后台服务不可用");
        return scriptNetworkExecutor.submit(new Callable<FetchedScriptBytes>() {
            @Override public FetchedScriptBytes call() throws Exception {
                try { Process.setThreadPriority(scriptThreadPriority()); } catch (RuntimeException ignored) {}
                return fetchUserScriptBytes(url, userAgent, SCRIPT_ASSET_MAX_BYTES, label);
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
                try {
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
                // onDestroy() closes the store while an in-flight fetch is winding down.
                // Avoid writing through that closed instance and avoid posting a dialog to a
                // destroyed Activity.
                if (activityDestroyed || scriptStore == null) return;
                scriptStore.saveBatch(pendingSaves);
                final int finalChecked = checked;
                final int finalUpdated = updated;
                final int finalFailed = failed;
                final int finalSkipped = skipped;
                scriptDownloadInProgress = false;
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        if (activityDestroyed) return;
                        String result = "脚本更新完成：检查 " + finalChecked + " 个，更新 " + finalUpdated + " 个";
                        if (finalSkipped > 0) result += "，跳过 " + finalSkipped + " 个无更新地址脚本";
                        if (finalFailed > 0) result += "，失败 " + finalFailed + " 个";
                        toast(result);
                        if (finalUpdated > 0) refreshUserScriptRegistrations(true);
                    }
                });
                } catch (RuntimeException ignored) {
                    // The store can be closed while an update is winding down during Activity
                    // destruction. The finally block below releases the in-progress gate.
                } finally {
                    scriptDownloadInProgress = false;
                }
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
                "\n\n可运行的网站：" + scope.toString() +
                "\n\n请仅安装可信脚本。原生网络严格遵守 @connect，并拒绝本机与私网地址。";
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
        boolean canUpdate = preferredUpdateUrl(script).length() > 0;
        String[] items = canUpdate
                ? new String[] { script.enabled ? "停用脚本" : "启用脚本", "检查更新",
                        "权限、来源与匹配范围", "删除脚本" }
                : new String[] { script.enabled ? "停用脚本" : "启用脚本",
                        "权限、来源与匹配范围", "删除脚本" };
        int[] icons = canUpdate
                ? new int[] { BrowserIconView.SCRIPT, BrowserIconView.RELOAD,
                        BrowserIconView.INFO, BrowserIconView.DELETE }
                : new int[] { BrowserIconView.SCRIPT, BrowserIconView.INFO, BrowserIconView.DELETE };
        int[] kinds = new int[items.length];
        for (int i = 0; i < kinds.length; i++) kinds[i] = SHEET_ROW_ACTION;
        String subtitle = (script.enabled ? "已启用" : "已停用") +
                (script.version.length() == 0 ? "" : " · v" + script.version) + " · " + script.runAt;
        showActionSheet(script.name, subtitle, items, icons, kinds, null,
                new Runnable() { @Override public void run() { showScriptCenter(); } },
                new SheetHandler() {
                    @Override public void onItem(int which) {
                        boolean hasUpdate = preferredUpdateUrl(script).length() > 0;
                        if (which == 0) {
                            scriptStore.setEnabled(script.id, !script.enabled);
                            refreshUserScriptRegistrations(true);
                            toast(script.enabled ? "脚本已启用" : "脚本已停用");
                            showScriptDetails(script);
                        } else if (hasUpdate && which == 1) updateOneUserScript(script);
                        else if (which == (hasUpdate ? 2 : 1)) showScriptMetadata(script);
                        else confirmDeleteScript(script);
                    }
                });
    }

    private void showScriptMetadata(final UserScriptStore.Script script) {
        StringBuilder matches = new StringBuilder();
        for (String item : script.matches) matches.append("\n• ").append(item);
        String message = "状态：" + (script.enabled ? "已启用" : "已禁用") +
                "\n版本：" + (script.version.length() == 0 ? "未知" : script.version) +
                "\n运行时机：" + script.runAt + (script.noFrames ? " · 仅顶层页面" : "") +
                "\n声明授权：" + (script.grants.size() == 0 ? "未声明" : script.grants.toString()) +
                "\nHTTPS 依赖：" + script.requires.size() + " 项" +
                "\nHTTPS 资源：" + script.resources.size() + " 项" +
                "\n网络范围：" + (script.connects.size() == 0 ? "未声明" : script.connects.toString()) +
                "\n来源：" + (script.sourceUrl.length() == 0 ? "本地" : script.sourceUrl) +
                "\n匹配范围：" + matches.toString();
        SettingsDialogReturn back = new SettingsDialogReturn(new Runnable() {
            @Override public void run() { showScriptDetails(script); }
        });
        new AlertDialog.Builder(this)
                .setTitle(script.name)
                .setMessage(message)
                .setPositiveButton("确定", back)
                .setNegativeButton("返回", back)
                .setOnCancelListener(back).show();
    }

    private void confirmDeleteScript(final UserScriptStore.Script script) {
        SettingsDialogReturn back = new SettingsDialogReturn(new Runnable() {
            @Override public void run() { showScriptDetails(script); }
        });
        new AlertDialog.Builder(this).setTitle("删除脚本？").setMessage(script.name)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        scriptStore.delete(script.id);
                        refreshUserScriptRegistrations(true);
                        toast("脚本已删除");
                        showScriptCenter();
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
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
        dismissOverlayForNavigation();
        final int pageBackground = nightMode ? DARK_BACKGROUND : WHITE;
        final int cardBackground = nightMode ? DARK_CARD : WHITE;
        final int activeCard = nightMode ? 0xff203652 : 0xffe8f0fe;
        final int pageText = nightMode ? DARK_TEXT : TEXT;
        final int pageMuted = nightMode ? DARK_MUTED : MUTED;
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
        activeOverlayBackAction = null;
        activeOverlayDismissAction = null;
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
        BrowserTab targetTab = tabs.get(index);
        WebView previousView = webView;
        boolean singleRendererSwap = targetTab.liveView == null && previous != null && previousView != null &&
                deviceProfile != null && deviceProfile.hotWebViewLimit(performanceMode) <= 1;
        if (previous != null && previousView != null) {
            updateTabForView(previousView, previousView.getUrl(), previousView.getTitle());
            previous.lastActiveAt = SystemClock.uptimeMillis();
            previousView.onPause();
            if (previousView.getParent() == webContainer) webContainer.removeView(previousView);
        }

        currentTabIndex = index;
        // In the one-renderer policy, capture and destroy the old surface before allocating the
        // target. The former create-first/trim-next-frame order briefly doubled Chromium memory
        // and produced exactly the tab-switch peaks users could feel as jank.
        if (singleRendererSwap) {
            previous.state = captureWebViewState(previousView);
            previous.liveView = null;
            webView = null;
            destroyWebView(previousView);
            trimColdTabStates();
        }
        boolean created = targetTab.liveView == null;
        if (created) {
            makeRoomForRenderer();
            bindWebViewToTab(targetTab, acquireWebView());
        }
        webView = targetTab.liveView;
        if (!singleRendererSwap && previousView != null && previousView != webView) applyPerformanceMode(previousView);
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
            boolean restored = restoreWebViewState(webView, targetTab.state);
            targetTab.state = null;
            if (!restored) {
                if (isHomeUrl(targetTab.url)) showHome();
                else if (isQrScannerUrl(targetTab.url)) showQrScannerPage();
                else loadNetworkUrl(webView, targetTab.url);
            } else {
                currentPageUrl = webView.getUrl() == null ? targetTab.url : webView.getUrl();
                currentPageHost = hostOf(currentPageUrl);
                if (isHomeUrl(currentPageUrl)) {
                    renderedHomeKeys.remove(webView);
                    showHome();
                } else if (isQrScannerUrl(currentPageUrl)) {
                    showQrScannerPage();
                } else if (isNetworkPage(currentPageUrl)) {
                    prepareNetworkDestination(webView, currentPageUrl, currentPageHost);
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
        resumeQrScannerIfNeeded();
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
        if (tab == null || tab.url == null || isInternalBrowserUrl(tab.url)) return;
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
                "关闭其他未固定标签",
                "冻结未固定的后台标签",
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
                    int frozen = 0;
                    for (int i = 0; i < tabs.size(); i++) {
                        BrowserTab tab = tabs.get(i);
                        if (i != currentTabIndex && !tab.pinned && tab.liveView != null) {
                            freezeTab(tab);
                            frozen++;
                        }
                    }
                    if (frozen == 0) {
                        toast("没有可冻结的未固定后台标签");
                        showTabTools();
                    } else toast("已冻结 " + frozen + " 个后台标签");
                } else closeAllTabs();
            }
        });
    }

    private void reopenClosedTab() {
        if (closedTabs.size() == 0) { toast("没有最近关闭的标签"); showTabTools(); return; }
        if (tabs.size() >= MAX_TABS) { toast("标签页已达到上限"); showTabTools(); return; }
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
        if (tabs.size() >= MAX_TABS) { toast("标签页已达到上限"); showTabTools(); return; }
        saveCurrentTab();
        BrowserTab current = tabs.get(currentTabIndex);
        BrowserTab duplicate = new BrowserTab();
        duplicate.title = current.title;
        duplicate.url = current.url;
        tabs.add(duplicate);
        activateTab(tabs.size() - 1);
    }

    private void closeOtherTabs() {
        if (tabs.size() <= 1) { toast("没有其他标签"); showTabTools(); return; }
        BrowserTab current = tabs.get(currentTabIndex);
        int closed = 0;
        for (int i = tabs.size() - 1; i >= 0; i--) {
            BrowserTab tab = tabs.get(i);
            if (tab == current || tab.pinned) continue;
            rememberClosedTab(tab);
            destroyTabView(tab, false);
            tabs.remove(i);
            closed++;
        }
        currentTabIndex = tabs.indexOf(current);
        renderedTabCount = null;
        persistSession();
        requestChromeUpdate();
        if (closed == 0) {
            toast("其他标签均已固定");
            showTabTools();
        } else toast("已关闭 " + closed + " 个标签");
    }

    private void closeAllTabs() {
        BrowserTab active = currentTabIndex >= 0 && currentTabIndex < tabs.size() ? tabs.get(currentTabIndex) : null;
        for (BrowserTab tab : new ArrayList<BrowserTab>(tabs)) {
            rememberClosedTab(tab);
            if (tab != active) destroyTabView(tab, false);
        }
        tabs.clear();
        if (active == null) active = new BrowserTab();
        else scriptTabValues.removeTab(active.scriptTabId);
        tabs.add(active);
        currentTabIndex = 0;
        resetOnlyTabToHome(false);
    }

    private void showTabSearch() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("标题或网址");
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("搜索标签页").setView(input)
                .setPositiveButton("搜索", null)
                .setNegativeButton("取消", null).create();
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface ignored) { showTabTools(); }
        });
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View ignored) { dialog.dismiss(); showTabTools(); }
                });
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View ignored) {
                        String query = input.getText().toString().trim().toLowerCase(Locale.US);
                        final ArrayList<Integer> matches = new ArrayList<Integer>();
                        for (int i = 0; i < tabs.size(); i++) {
                            BrowserTab tab = tabs.get(i);
                            if (query.length() == 0 || tab.title.toLowerCase(Locale.US).contains(query) || tab.url.toLowerCase(Locale.US).contains(query)) matches.add(Integer.valueOf(i));
                        }
                        if (matches.size() == 0) { input.setError("没有匹配的标签"); return; }
                        String[] labels = new String[matches.size()];
                        for (int i = 0; i < labels.length; i++) {
                            BrowserTab tab = tabs.get(matches.get(i).intValue());
                            labels[i] = safeTitle(tab.title, tab.url) + "\n" + tab.url;
                        }
                        dialog.dismiss();
                        new AlertDialog.Builder(MainActivity.this).setTitle("匹配标签").setItems(labels, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) { activateTab(matches.get(which).intValue()); }
                        }).setNegativeButton("返回", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) { showTabSearch(); }
                        }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override public void onCancel(DialogInterface dialog) { showTabSearch(); }
                        }).show();
                    }
                });
            }
        });
        dialog.show();
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

    /** Releases an older background renderer before allocation, never after the memory peak. */
    private void makeRoomForRenderer() {
        if (deviceProfile == null) return;
        int limit = deviceProfile.hotWebViewLimit(performanceMode);
        while (liveWebViewCount() >= limit) {
            BrowserTab oldest = null;
            for (int i = 0; i < tabs.size(); i++) {
                BrowserTab candidate = tabs.get(i);
                if (i == currentTabIndex || candidate.liveView == null || candidate.liveView == webView) continue;
                if (oldest == null || candidate.lastActiveAt < oldest.lastActiveAt) oldest = candidate;
            }
            if (oldest == null) return;
            freezeTab(oldest);
        }
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
        Parcel parcel = null;
        try {
            if (view.saveState(state) == null) return null;
            parcel = Parcel.obtain();
            state.writeToParcel(parcel, 0);
            return parcel.dataSize() <= MAX_WEBVIEW_STATE_BYTES ? state : null;
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            if (parcel != null) parcel.recycle();
        }
    }

    private static boolean restoreWebViewState(WebView view, Bundle state) {
        if (view == null || state == null) return false;
        try { return view.restoreState(state) != null; }
        catch (RuntimeException ignored) { return false; }
    }

    private static boolean isBoundedTabState(Bundle state) {
        Parcel parcel = null;
        try {
            parcel = Parcel.obtain();
            state.writeToParcel(parcel, 0);
            return parcel.dataSize() <= MAX_WEBVIEW_STATE_BYTES;
        } catch (RuntimeException ignored) { return false; }
        finally { if (parcel != null) parcel.recycle(); }
    }

    private boolean isRestorableTabUrl(String url) {
        if (url == null || url.length() == 0 || url.length() > MAX_SAVED_TAB_URL_CHARS) return false;
        if (isHomeUrl(url) || isQrScannerUrl(url) || isOfflineUrl(url)) return true;
        if (!isNetworkPage(url)) return false;
        try { NetworkSecurity.parseHttpUrl(url); return true; }
        catch (Exception ignored) { return false; }
    }

    /** Only lightweight metadata belongs in the Activity parcel; background histories stay out. */
    private Bundle captureTabMetadata() {
        int count = Math.min(tabs.size(), MAX_TABS);
        if (count == 0) return null;
        int selected = Math.max(0, Math.min(currentTabIndex, count - 1));
        byte[][] urls = new byte[count][];
        byte[][] titles = new byte[count][];
        boolean[] replaced = new boolean[count];
        byte[] home = HOME_URL.getBytes(StandardCharsets.UTF_8);
        int bytes = 16 + count * 9;
        for (int i = 0; i < count; i++) {
            BrowserTab tab = tabs.get(i);
            replaced[i] = !isRestorableTabUrl(tab.url);
            urls[i] = replaced[i] ? home : tab.url.getBytes(StandardCharsets.UTF_8);
            String title = tab.title == null ? "" : tab.title;
            if (title.length() > MAX_SAVED_TAB_TITLE_CHARS) title = title.substring(0, MAX_SAVED_TAB_TITLE_CHARS);
            titles[i] = title.getBytes(StandardCharsets.UTF_8);
            bytes += urls[i].length + titles[i].length;
        }
        // Reserve ample room for Bundle/key/byte-array parcel headers. UTF-8 retains ordinary
        // ASCII URLs efficiently. If pathological URLs exceed the total budget, retain every
        // tab slot and prefer preserving the selected URL, rather than truncating valid URLs.
        while (bytes > MAX_TAB_METADATA_BYTES - 1024) {
            int largest = -1;
            for (int i = 0; i < count; i++) {
                if (i != selected && urls[i].length > home.length &&
                        (largest < 0 || urls[i].length > urls[largest].length)) largest = i;
            }
            if (largest < 0 && urls[selected].length > home.length) largest = selected;
            if (largest < 0) return null;
            bytes -= urls[largest].length - home.length;
            urls[largest] = home;
            replaced[largest] = true;
        }
        Parcel parcel = null;
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(bytes);
            DataOutputStream output = new DataOutputStream(buffer);
            output.writeInt(1);
            output.writeInt(count);
            output.writeInt(selected);
            int replacements = 0;
            for (boolean value : replaced) if (value) replacements++;
            output.writeInt(replacements);
            for (int i = 0; i < count; i++) {
                output.writeInt(urls[i].length);
                output.write(urls[i]);
                output.writeInt(titles[i].length);
                output.write(titles[i]);
                output.writeBoolean(tabs.get(i).pinned);
            }
            Bundle metadata = new Bundle();
            metadata.putByteArray("data", buffer.toByteArray());
            parcel = Parcel.obtain();
            metadata.writeToParcel(parcel, 0);
            return parcel.dataSize() <= MAX_TAB_METADATA_BYTES ? metadata : null;
        } catch (IOException | RuntimeException ignored) { return null; }
        finally { if (parcel != null) parcel.recycle(); }
    }

    private static String readTabMetadataString(DataInputStream input, int maxChars) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > maxChars * 4 || length > input.available()) throw new IOException("Invalid tab string");
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.length() > maxChars) throw new IOException("Tab string too long");
        return value;
    }

    private boolean restoreTabMetadata(Bundle savedState) {
        if (savedState == null) return false;
        try {
            Bundle metadata = savedState.getBundle(STATE_TABS);
            byte[] bytes = metadata == null ? null : metadata.getByteArray("data");
            if (bytes == null || bytes.length > MAX_TAB_METADATA_BYTES - 1024) return false;
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            if (input.readInt() != 1) return false;
            int count = input.readInt();
            int selected = input.readInt();
            int replacements = input.readInt();
            if (count < 1 || count > MAX_TABS || selected < 0 || selected >= count ||
                    replacements < 0 || replacements > count) return false;
            ArrayList<BrowserTab> restored = new ArrayList<BrowserTab>(count);
            for (int i = 0; i < count; i++) {
                BrowserTab tab = new BrowserTab();
                tab.url = readTabMetadataString(input, MAX_SAVED_TAB_URL_CHARS);
                tab.title = readTabMetadataString(input, MAX_SAVED_TAB_TITLE_CHARS);
                tab.pinned = input.readBoolean();
                if (!isRestorableTabUrl(tab.url)) return false;
                restored.add(tab);
            }
            if (input.available() != 0) return false;
            tabs.clear();
            tabs.addAll(restored);
            currentTabIndex = selected;
            BrowserTab active = tabs.get(selected);
            bindWebViewToTab(active, webView);
            active.lastActiveAt = SystemClock.uptimeMillis();
            if (replacements > 0) toast("部分超长标签地址已恢复为主页，请从历史记录重新打开");
            return true;
        } catch (IOException | RuntimeException ignored) { return false; }
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
        if (tab == null) return;
        if (!keepState) {
            tab.state = null;
            scriptTabValues.removeTab(tab.scriptTabId);
        }
        if (tab.liveView == null) return;
        WebView view = tab.liveView;
        tab.liveView = null;
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        destroyWebView(view);
    }

    private void recoverFromRendererLoss() {
        if (activityDestroyed || isFinishing() || webContainer == null) {
            rendererRecoveryPending = false;
            return;
        }
        // Renderer loss invalidates every pending page enhancement. Advance the generation
        // before replacing views so work queued for the dead renderer cannot target its replacement.
        advanceNavigationSequence();
        preparedInjection = null;
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
        applyPerformanceMode(webView);
        if (active != null) {
            bindWebViewToTab(active, webView);
            active.lastActiveAt = SystemClock.uptimeMillis();
        }
        webContainer.removeAllViews();
        webContainer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (activityResumed) webView.onResume();
        currentPageUrl = reloadUrl;
        currentPageHost = hostOf(reloadUrl);
        pageHosts.put(webView, currentPageHost);
        adBlockActiveByView.put(webView, Boolean.valueOf(isAdBlockActiveForHost(currentPageHost)));
        rendererRecoveryPending = false;
        if (isHomeUrl(reloadUrl)) showHome();
        else if (isQrScannerUrl(reloadUrl)) showQrScannerPage();
        else loadNetworkUrl(webView, reloadUrl);
        requestChromeUpdate();
        toast("网页渲染进程已恢复");
    }

    private void destroyWebView(WebView view) {
        if (view == null) return;
        initialNavigationGuards.remove(view);
        navigationGestureGuards.remove(view);
        navigationSourceDocuments.remove(view);
        mainFrameNavigationContexts.remove(view);
        popupSourceDocuments.remove(view);
        startedPageUrls.remove(view);
        unresponsiveWebViews.remove(view);
        scriptTabIds.remove(view);
        scriptDocumentGenerations.remove(view);
        removeDocumentStartUserScripts(view);
        removeConnectionHintScript(view);
        pageHosts.remove(view);
        adBlockActiveByView.remove(view);
        mobileUserAgents.remove(view);
        appliedSiteSettings.remove(view);
        cosmeticInjected.remove(view);
        trustedHomeViews.remove(view);
        customHomeViews.remove(view);
        adPickerViews.remove(view);
        adPickerSessions.remove(view);
        qrScannerViews.remove(view);
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
        else if (isQrScannerUrl(url)) value = "扫描二维码";
        return UiText.ellipsize(value, 34);
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
        setImmersiveSystemBars(true);
        rootFrame.requestApplyInsets();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void exitFullscreen() {
        if (customView == null) return;
        rootFrame.removeView(customView);
        customView = null;
        browserChrome.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(previousSystemUi);
        setImmersiveSystemBars(chromeHiddenByScroll);
        rootFrame.requestApplyInsets();
        setRequestedOrientation(previousOrientation);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
        resetWebViewTransform(webView);
        if (webView != null) webView.requestFocus(View.FOCUS_DOWN);
    }

    private boolean isSecureCredentialUrl(String url) {
        try {
            URL parsed = NetworkSecurity.parseHttpUrl(url);
            return "https".equalsIgnoreCase(parsed.getProtocol()) &&
                    parsed.getUserInfo() == null && !"median.invalid".equalsIgnoreCase(parsed.getHost());
        } catch (Exception ignored) { return false; }
    }

    private int secureCredentialPort(String url) {
        try {
            URL parsed = NetworkSecurity.parseHttpsUrl(url);
            return NetworkSecurity.effectivePort(parsed);
        } catch (Exception ignored) { return -1; }
    }

    private static boolean sameCredentialDocument(String expected, String actual) {
        return expected != null && actual != null && UrlCleaner.sameOrigin(expected, actual) &&
                navigationDocumentUrl(expected).equals(navigationDocumentUrl(actual));
    }

    private void showPasswordMenu() {
        showPasswordMenu(passwordMenuBackAction);
    }

    private void showPasswordMenu(final Runnable backAction) {
        passwordMenuBackAction = backAction;
        String[] items = new String[] {
                "网页自动填充：已停用（请手动选择账号）",
                "网页自动保存：已停用（请手动保存）",
                "手动保存当前站点账号", "手动填充当前页面", "管理已保存密码", "密码安全说明"
        };
        SettingsDialogReturn back = new SettingsDialogReturn(backAction);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("密码管理器")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            toast("网页自动填充已停用，请使用“手动填充当前页面”");
                            postAfterUiTransition(new Runnable() {
                                @Override public void run() { showPasswordMenu(); }
                            });
                            return;
                        }
                        if (which == 1) {
                            toast("网页自动保存已停用，请使用“手动保存当前站点账号”");
                            postAfterUiTransition(new Runnable() {
                                @Override public void run() { showPasswordMenu(); }
                            });
                            return;
                        }
                        if (which == 5) {
                            showVaultInfo(new Runnable() {
                                @Override public void run() { showPasswordMenu(); }
                            });
                            return;
                        }
                        final int action = which;
                        if ((action == 2 || action == 3) && !requireSecurePage()) {
                            showPasswordMenu();
                            return;
                        }
                        withVaultUnlock(new Runnable() {
                            @Override public void run() {
                                if (action == 2) saveCredentialDialog();
                                else if (action == 3) chooseCredentialToFill();
                                else manageCredentials();
                            }
                        }, new Runnable() {
                            @Override public void run() { showPasswordMenu(); }
                        });
                    }
                })
                .setNegativeButton(backAction == null ? "关闭" : "返回", back)
                .setOnCancelListener(back)
                .create();
        secureDialog(dialog);
        dialog.show();
    }

    private void withVaultUnlock(Runnable action) {
        withVaultUnlock(action, null);
    }

    private void withVaultUnlock(Runnable action, final Runnable cancelAction) {
        if (action == null) return;
        if (SystemClock.elapsedRealtime() < vaultUnlockedUntil) { action.run(); return; }
        android.app.KeyguardManager manager = (android.app.KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (manager == null || !manager.isDeviceSecure()) {
            SettingsDialogReturn cancel = new SettingsDialogReturn(cancelAction);
            new AlertDialog.Builder(this).setTitle("需要安全锁屏")
                    .setMessage("Median 现在把 Keystore 密钥直接绑定到系统身份验证。请先设置 PIN、图案、密码或生物识别，密码库不会提供绕过入口。")
                    .setPositiveButton("打开安全设置", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            try { startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); }
                            catch (Exception e) { toast("无法打开系统安全设置"); }
                        }
                    }).setNegativeButton("取消", cancel).setOnCancelListener(cancel).show();
            return;
        }
        Intent unlock = manager.createConfirmDeviceCredentialIntent("解锁 Median 密码库", "确认设备身份后继续");
        if (unlock == null) {
            toast("系统身份验证不可用，密码库保持锁定");
            continueSettingsPanel(cancelAction);
            return;
        }
        pendingVaultAction = action;
        pendingVaultCancelAction = cancelAction;
        try { startActivityForResult(unlock, VAULT_UNLOCK_REQUEST); }
        catch (Exception e) {
            pendingVaultAction = null;
            pendingVaultCancelAction = null;
            toast("无法调用系统身份验证");
            continueSettingsPanel(cancelAction);
        }
    }

    private boolean requireSecurePage() {
        Uri uri = currentUri();
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null ||
                isInternalBrowserUrl(uri.toString())) {
            toast("仅允许在 HTTPS 网站保存或填充密码");
            return false;
        }
        return currentHost().length() > 0 && secureCredentialPort(currentPageUrl) > 0;
    }

    private void saveCredentialDialog() {
        if (!requireSecurePage()) return;
        final WebView source = webView;
        final String expectedUrl = currentPageUrl;
        final String expectedHost = currentHost();
        final int expectedPort = secureCredentialPort(expectedUrl);
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
        host.setText("站点：" + expectedHost + (expectedPort == 443 ? "" : ":" + expectedPort));
        host.setPadding(0, dp(8), 0, dp(8));
        content.addView(host);
        content.addView(username);
        content.addView(password);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("保存账号")
                .setView(content)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", new SettingsDialogReturn(new Runnable() {
                    @Override public void run() { showPasswordMenu(); }
                }))
                .create();
        dialog.setOnCancelListener(new SettingsDialogReturn(new Runnable() {
            @Override public void run() { showPasswordMenu(); }
        }));
        secureDialog(dialog);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View ignored) {
                        final String user = username.getText().toString().trim();
                        final String pass = password.getText().toString();
                        if (source != webView || !sameCredentialDocument(expectedUrl, currentPageUrl) ||
                                !expectedHost.equalsIgnoreCase(currentHost()) ||
                                expectedPort != secureCredentialPort(currentPageUrl) ||
                                !requireSecurePage()) {
                            toast("页面来源已经变化，已拒绝保存");
                            dialog.dismiss();
                            return;
                        }
                        if (user.length() == 0 || pass.length() == 0) {
                            if (user.length() == 0) username.setError("用户名不能为空");
                            if (pass.length() == 0) password.setError("密码不能为空");
                            return;
                        }
                        if (user.length() > 512 || pass.length() > 8192) {
                            toast("账号或密码长度超过安全限制");
                            return;
                        }
                        final Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        save.setEnabled(false);
                        services.passwords().saveCredential(expectedHost, expectedPort, user, pass, new PasswordVault.Callback<Void>() {
                            @Override public void onComplete(Void value, Exception error) {
                                if (activityDestroyed) return;
                                if (error == null) {
                                    dialog.dismiss();
                                    toast("已加密保存");
                                } else {
                                    save.setEnabled(true);
                                    toast("保存失败：" + safeMessage(error));
                                }
                            }
                        });
                    }
                });
            }
        });
        dialog.show();
        webView.evaluateJavascript(CredentialAutofill.captureFieldsScript(), new ValueCallback<String>() {
            @Override public void onReceiveValue(String value) {
                if (activityDestroyed || !dialog.isShowing() || source != webView ||
                        !sameCredentialDocument(expectedUrl, currentPageUrl)) return;
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
        final int requestedPort = secureCredentialPort(currentPageUrl);
        final String expectedUrl = currentPageUrl;
        final long expectedSequence = navigationSequence;
        services.passwords().forOrigin(requestedHost, requestedPort, new PasswordVault.Callback<List<PasswordVault.Credential>>() {
            @Override public void onComplete(final List<PasswordVault.Credential> credentials, Exception error) {
                if (activityDestroyed) {
                    PasswordVault.clearCredentials(credentials);
                    return;
                }
                if (error != null) {
                    PasswordVault.clearCredentials(credentials);
                    toast("读取密码库失败：" + safeMessage(error));
                    showPasswordMenu();
                    return;
                }
                if (!requestedHost.equalsIgnoreCase(currentHost()) || expectedSequence != navigationSequence ||
                        requestedPort != secureCredentialPort(currentPageUrl) ||
                        !sameCredentialDocument(expectedUrl, currentPageUrl)) {
                    PasswordVault.clearCredentials(credentials);
                    toast("页面已经变化，请重新选择账号");
                    showPasswordMenu();
                    return;
                }
                if (credentials == null || credentials.size() == 0) {
                    PasswordVault.clearCredentials(credentials);
                    toast("当前站点没有已保存账号");
                    showPasswordMenu();
                    return;
                }
                String[] names = new String[credentials.size()];
                for (int i = 0; i < credentials.size(); i++) names[i] = credentials.get(i).username;
                SettingsDialogReturn back = new SettingsDialogReturn(new Runnable() {
                    @Override public void run() { showPasswordMenu(); }
                });
                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("选择账号")
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                // The dialog's dismiss listener clears the backing list. Pass an
                                // independent copy so the async WebView callback can still validate
                                // the credential after the list is wiped.
                                fillCredentialOnPage(webView, expectedUrl, credentials.get(which).copy(), false, expectedSequence);
                            }
                        }).setNegativeButton("返回", back).setOnCancelListener(back)
                        .create();
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface ignored) {
                        PasswordVault.clearCredentials(credentials);
                    }
                });
                secureDialog(dialog);
                dialog.show();
            }
        });
    }

    private void fillCredential(PasswordVault.Credential credential) {
        if (!requireSecurePage()) return;
        fillCredentialOnPage(webView, currentPageUrl, credential, false, navigationSequence);
    }

    private void fillCredentialOnPage(final WebView target, final String expectedUrl,
                                      PasswordVault.Credential credential, final boolean automatic) {
        fillCredentialOnPage(target, expectedUrl, credential, automatic, navigationSequence);
    }

    private void fillCredentialOnPage(final WebView target, final String expectedUrl,
                                      PasswordVault.Credential credential, final boolean automatic,
                                      final long expectedSequence) {
        if (target == null || target != webView || credential == null ||
                expectedSequence != navigationSequence ||
                !isSecureCredentialUrl(target.getUrl()) ||
                !credential.host.equalsIgnoreCase(currentHost()) ||
                credential.port != secureCredentialPort(currentPageUrl) ||
                credential.port != secureCredentialPort(target.getUrl()) ||
                !credential.host.equalsIgnoreCase(hostOf(target.getUrl())) ||
                !sameCredentialDocument(expectedUrl, target.getUrl())) {
            if (!automatic) toast("站点或页面已经变化，已拒绝填充");
            return;
        }
        try {
            target.evaluateJavascript(CredentialAutofill.fillScript(
                            credential.username, credential.password, !automatic),
                    new ValueCallback<String>() {
                @Override public void onReceiveValue(String value) {
                    if (target != webView || expectedSequence != navigationSequence ||
                            !isSecureCredentialUrl(currentPageUrl) ||
                            !credential.host.equalsIgnoreCase(currentHost()) ||
                            credential.port != secureCredentialPort(currentPageUrl) ||
                            credential.port != secureCredentialPort(target.getUrl()) ||
                            !credential.host.equalsIgnoreCase(hostOf(target.getUrl())) ||
                            !isSecureCredentialUrl(target.getUrl()) ||
                            !sameCredentialDocument(expectedUrl, currentPageUrl) ||
                            !sameCredentialDocument(expectedUrl, target.getUrl())) return;
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
                if (activityDestroyed) {
                    PasswordVault.clearCredentials(all);
                    return;
                }
                if (error != null) {
                    PasswordVault.clearCredentials(all);
                    toast("读取密码库失败：" + safeMessage(error));
                    showPasswordMenu();
                    return;
                }
                if (all == null || all.size() == 0) {
                    PasswordVault.clearCredentials(all);
                    toast("密码库为空");
                    showPasswordMenu();
                    return;
                }
                String[] names = new String[all.size()];
                for (int i = 0; i < all.size(); i++) names[i] = all.get(i).host + "\n" + all.get(i).username;
                SettingsDialogReturn back = new SettingsDialogReturn(new Runnable() {
                    @Override public void run() { showPasswordMenu(); }
                });
                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("已保存账号")
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                showCredentialActions(all.get(which).copy());
                            }
                        })
                        .setNegativeButton("返回", back)
                        .setOnCancelListener(back)
                        .create();
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface ignored) {
                        PasswordVault.clearCredentials(all);
                    }
                });
                secureDialog(dialog);
                dialog.show();
            }
        });
    }

    private void showCredentialActions(final PasswordVault.Credential credential) {
        if (credential == null) {
            manageCredentials();
            return;
        }
        final boolean canFill = isSecureCredentialUrl(currentPageUrl) &&
                credential.port == secureCredentialPort(currentPageUrl) &&
                credential.host.equalsIgnoreCase(currentHost());
        String[] actions = new String[] {
                canFill ? "填充当前页面" : "打开此网站",
                "复制用户名",
                "删除账号"
        };
        SettingsDialogReturn back = new SettingsDialogReturn(new Runnable() {
            @Override public void run() { manageCredentials(); }
        });
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("账号操作")
                .setMessage(credential.host + (credential.port == 443 ? "" : ":" + credential.port) + "\n" + credential.username)
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) {
                        if (which == 0) {
                            if (canFill) fillCredential(credential);
                            else loadInput("https://" + credential.host +
                                    (credential.port == 443 ? "" : ":" + credential.port));
                        } else if (which == 1) {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("Median 用户名", credential.username));
                                toast("用户名已复制");
                            }
                            postAfterUiTransition(new Runnable() {
                                @Override public void run() { manageCredentials(); }
                            });
                        } else {
                            confirmDeleteCredential(credential.copy());
                        }
                    }
                })
                .setNegativeButton("返回", back)
                .setOnCancelListener(back)
                .create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) { credential.clear(); }
        });
        secureDialog(dialog);
        dialog.show();
    }

    private void confirmDeleteCredential(final PasswordVault.Credential credential) {
        SettingsDialogReturn back = new SettingsDialogReturn(new Runnable() {
            @Override public void run() { manageCredentials(); }
        });
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("删除账号？")
                .setMessage(credential.host + (credential.port == 443 ? "" : ":" + credential.port) + "\n" + credential.username)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        services.passwords().delete(credential.id, new PasswordVault.Callback<Void>() {
                            @Override public void onComplete(Void value, Exception error) {
                                if (activityDestroyed) return;
                                if (error == null) toast("已删除");
                                else toast("删除失败：" + safeMessage(error));
                                manageCredentials();
                            }
                        });
                    }
                })
                .setNegativeButton("取消", back)
                .setOnCancelListener(back)
                .create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) {
                if (credential != null) credential.clear();
            }
        });
        secureDialog(dialog);
        dialog.show();
    }

    private void showVaultInfo(final Runnable returnAction) {
        SettingsDialogReturn back = new SettingsDialogReturn(returnAction);
        new AlertDialog.Builder(this)
                .setTitle("密码安全说明")
                .setMessage("密码使用 AES-GCM 加密，密钥由 Android Keystore 保护，解锁需系统身份验证；应用禁止系统备份。\n\n填充必须由浏览器密码菜单手动发起，仅匹配 HTTPS 主机，不填新密码、确认密码或验证码，也不会自动提交。\n\n密码库尚未经独立安全审计，请勿保存金融账户或主要邮箱密码。")
                .setPositiveButton("知道了", back)
                .setOnCancelListener(back)
                .show();
    }

    private void secureDialog(final AlertDialog dialog) {
        if (dialog != null && dialog.getWindow() != null)
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    private void showFindDialog() {
        final WebView target = webView;
        if (target == null) return;
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("输入要查找的内容");
        input.setContentDescription("页面内查找内容");
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(18), dp(8), dp(18), dp(8));
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("页面内查找")
                .setView(input)
                .setPositiveButton("查找", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View ignored) {
                        String query = input.getText().toString().trim();
                        if (query.length() == 0) {
                            input.setError("请输入要查找的内容");
                            return;
                        }
                        if (target != webView) {
                            toast("页面已经变化，请重新查找");
                            dialog.dismiss();
                            return;
                        }
                        dialog.dismiss();
                        target.clearMatches();
                        target.findAllAsync(query);
                        target.showFindDialog(query, false);
                    }
                });
            }
        });
        dialog.show();
    }

    private void sharePage() {
        String url = webView.getUrl();
        if (!isNetworkPage(url)) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, (webView.getTitle() == null ? "" : webView.getTitle() + "\n") + url);
        startActivity(Intent.createChooser(share, "分享页面"));
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
                    clearConnectionHints();
                    toast("临时缓存已清理");
                    showStoragePanel(backAction);
                } else if (which == 1) {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    android.webkit.WebStorage.getInstance().deleteAllData();
                    toast("Cookie 与站点数据已清除");
                    showStoragePanel(backAction);
                } else if (which == 2) {
                    confirmClearData(new Runnable() {
                        @Override public void run() { showStoragePanel(backAction); }
                    });
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

    private void confirmClearData(final Runnable returnAction) {
        SettingsDialogReturn back = new SettingsDialogReturn(returnAction);
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
                        clearConnectionHints();
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
                        continueSettingsPanel(returnAction);
                    }
                })
                .setNegativeButton("取消", back)
                .setOnCancelListener(back)
                .show();
    }

    private void clearDataForExit() {
        if (exitDataCleared) return;
        exitDataCleared = true;
        clearConnectionHints();
        try {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } catch (RuntimeException ignored) {}
        try { WebStorage.getInstance().deleteAllData(); } catch (RuntimeException ignored) {}
        for (BrowserTab tab : tabs) if (tab.liveView != null) {
            try { tab.liveView.clearCache(true); } catch (RuntimeException ignored) {}
            try { tab.liveView.clearFormData(); } catch (RuntimeException ignored) {}
            try { tab.liveView.clearHistory(); } catch (RuntimeException ignored) {}
        }
        if (dataStore != null) dataStore.clearHistory();
        if (dataStore != null) dataStore.clearSession();
        if (siteSettingsStore != null) siteSettingsStore.clearAll();
        try { GeolocationPermissions.getInstance().clearAll(); } catch (RuntimeException ignored) {}
        try { WebViewDatabase.getInstance(this).clearHttpAuthUsernamePassword(); } catch (RuntimeException ignored) {}
    }

    private void showCommunityNoticeOnFirstLaunch(Bundle state) {
        if (state != null || prefs.getBoolean(PREF_COMMUNITY_NOTICE_SHOWN, false)) return;
        prefs.edit().putBoolean(PREF_COMMUNITY_NOTICE_SHOWN, true).apply();
        // Never put a large dialog allocation into the launch/first-interaction window.
        uiHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                showCommunityNotice();
            }
        }, 2400L);
    }

    private void showCommunityNotice() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("欢迎使用 Median Browser")
                .setMessage("轻量、本地优先、无广告与遥测。\n\n" + COMMUNITY_INFO)
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
                .setMessage("轻量、无广告、无分析与遥测 SDK。\n\n" + profile
                        + "\n\n网页能力取决于 Android System WebView。\n\n" + COMMUNITY_INFO)
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
                + "\n\n不支持 document-start 时会安全降级；无可信桥接时禁用跨域、下载与剪贴板能力。\n\n最近首导航：\n"
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
        if (activityDestroyed || rootFrame == null) return;
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
                        if (!activityDestroyed)
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
                    if (!activityDestroyed) toast("document-start 自测超时，已保持安全降级");
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
                .setMessage("书签、历史、标签、设置、脚本与过滤规则保存在本机；Median 不含广告、分析、遥测或同步服务。\n\n网站、搜索、翻译和订阅服务会收到正常网络请求。摄像头、麦克风与位置仅在 HTTPS 页面请求且你授权后提供。\n\n密码库使用 Keystore 与 AES-GCM。用户脚本是第三方代码，请只安装可信脚本。导出文件请自行保管。")
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
        applyPerformanceMode(target, target.getSettings());
    }

    private void applyPerformanceMode(WebView target, WebSettings settings) {
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        // Chromium rasterizes visible content on demand. Hidden hot tabs do not need
        // pre-rasterized tiles competing with the active page for memory and GPU work.
        settings.setOffscreenPreRaster(false);
        settings.setLoadsImagesAutomatically(true);

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
        applyDesktopMode(target, settings, settings.getUserAgentString());
    }

    private void applyDesktopMode(WebView target, WebSettings settings) {
        applyDesktopMode(target, settings, settings.getUserAgentString());
    }

    private void applyDesktopMode(WebView target, WebSettings settings, String currentUserAgent) {
        SiteSettingsStore.SiteSettings site = siteSettingsStore == null ? new SiteSettingsStore.SiteSettings() : siteSettingsStore.forHost(pageHostFor(target));
        int desktop = site.get(SiteSettingsStore.DESKTOP);
        boolean enabled = desktop == SiteSettingsStore.ALLOW || (desktop == SiteSettingsStore.INHERIT && desktopMode);
        String mobile = mobileUserAgents.get(target);
        if (mobile == null || mobile.length() == 0) {
            mobile = WebViewPolicy.mobileUserAgent(currentUserAgent);
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
        if (!desiredUserAgent.equals(currentUserAgent)) settings.setUserAgentString(desiredUserAgent);
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
        applyDesktopMode(target, settings);
        applyDarkMode(target, settings);
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

    private void toggleNightMode() {
        nightMode = !nightMode;
        prefs.edit().putBoolean("night_mode", nightMode).apply();

        // Immediately update all backgrounds before re-rendering content
        applyDarkMode();

        // Update WebView background color immediately to prevent flash
        if (webView != null) {
            webView.setBackgroundColor(nightMode ? DARK_WEB : WHITE);
        }

        // Update all tab backgrounds
        for (BrowserTab tab : tabs) {
            if (tab.liveView != null) {
                tab.liveView.setBackgroundColor(nightMode ? DARK_WEB : WHITE);
            }
        }

        // Clear cached home render key to force regeneration with new theme
        cachedHomeKey = "";
        renderedHomeKeys.clear();

        // Re-render content after backgrounds are updated
        String activeUrl = webView == null ? currentPageUrl : webView.getUrl();
        if (activeUrl == null || activeUrl.length() == 0) activeUrl = currentPageUrl;
        homeThemeRepairPending = true;
        if (isHomeUrl(activeUrl)) {
            // Rewrite the current home document so the previous theme cannot remain in the
            // WebView back stack as a stale page.
            showHome(true);
        } else if (isQrScannerUrl(activeUrl)) {
            showQrScannerPage();
        } else if (webView != null && isInternalBrowserUrl(activeUrl)) {
            // Reload internal pages (settings, etc.) to apply new theme
            reloadWebView(webView);
        } else if (webView != null && isNetworkPage(activeUrl)) {
            // Re-apply the complete site policy so WebView providers that cache the darkening
            // flag do not leave a light surface behind after a live theme switch.
            appliedSiteSettings.remove(webView);
            applySiteSettings(webView, hostOf(activeUrl));
            webView.postInvalidateOnAnimation();
        }
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
        int background = nightMode ? DARK_BACKGROUND : WHITE;
        int surface = nightMode ? DARK_SURFACE : SURFACE;
        int foreground = nightMode ? DARK_TEXT : TEXT;
        int hint = nightMode ? DARK_MUTED : 0xff80868b;
        if (rootFrame != null) rootFrame.setBackgroundColor(background);
        browserChrome.setBackgroundColor(background);
        topBar.setBackgroundColor(background);
        bottomBar.setBackgroundColor(background);
        if (statusBarBackground != null) statusBarBackground.setBackgroundColor(background);
        if (navigationBarBackground != null) navigationBarBackground.setBackgroundColor(background);
        if (webContainer != null) {
            int webBackground = nightMode ? DARK_WEB : WHITE;
            webContainer.setBackgroundColor(webBackground);
            // During the short WebView construction window the container still owns the
            // startup surface. Repaint that child as well so a live toggle cannot leave a
            // bright rectangle behind the dark chrome.
            for (int i = 0; i < webContainer.getChildCount(); i++) {
                View child = webContainer.getChildAt(i);
                if (child != null && child != webView) child.setBackgroundColor(webBackground);
            }
        }
        addressPill.setBackground(roundRect(surface, 999));
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
        setImmersiveSystemBars(chromeHiddenByScroll);
    }

    private void refreshActiveSheetTheme() {
        if (activeOverlayPanel != null && activeOverlaySheet) themeSheetTree(activeOverlayPanel);
    }

    private void themeSheetTree(View view) {
        if (view == null) return;
        int surface = nightMode ? DARK_SHEET : WHITE;
        int primary = nightMode ? DARK_TEXT : TEXT;
        int muted = nightMode ? DARK_MUTED : MUTED;
        int accent = nightMode ? DARK_ACCENT : BLUE;
        Object marker = view.getTag();
        int tag = marker instanceof Integer ? (Integer) marker : 0;
        if (tag == SHEET_TAG_SURFACE) view.setBackground(roundRect(surface, 20));
        else if (tag == SHEET_TAG_PLAIN_SURFACE) {
            // Scroll/list surfaces are separate views; repainting only the rounded panel leaves
            // the old theme visible in the gaps after an in-place mode switch.
            view.setBackgroundColor(surface);
            view.setForeground(null);
            view.setStateListAnimator(null);
            view.setBackgroundTintList(null);
        }
        else if (tag == SHEET_TAG_HANDLE)
            view.setBackground(sheetHandleDrawable());
        else if (tag == SHEET_TAG_ROW) {
            // Rows must be repainted as well as their labels. A platform foreground or animator
            // can otherwise keep the light-theme drawable alive after a live dark-mode switch.
            view.setBackgroundColor(surface);
            view.setForeground(null);
            view.setStateListAnimator(null);
            view.setBackgroundTintList(null);
        }
        if (view instanceof BrowserIconView) ((BrowserIconView) view).setTintColor(tag == SHEET_TAG_ACCENT ? accent : primary);
        if (view instanceof TextView) {
            if (tag == SHEET_TAG_PRIMARY) ((TextView) view).setTextColor(primary);
            else if (tag == SHEET_TAG_MUTED) ((TextView) view).setTextColor(muted);
            else if (tag == SHEET_TAG_ACCENT) ((TextView) view).setTextColor(accent);
        }
        if (view instanceof Switch) {
            Switch toggle = (Switch) view;
            int[][] states = new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} };
            toggle.setThumbTintList(new ColorStateList(states, new int[] {
                    accent, nightMode ? 0xffbdc1c6 : 0xff757575
            }));
            toggle.setTrackTintList(new ColorStateList(states, new int[] {
                    0x761a73e8, nightMode ? 0xff505459 : 0xffbdc1c6
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
        applyDarkMode(target, target.getSettings());
    }

    private void applyDarkMode(WebView target, WebSettings settings) {
        boolean enabled = darkModeEnabled(target);
        target.setBackgroundColor(enabled ? DARK_WEB : WHITE);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            try {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, enabled);
            } catch (RuntimeException ignored) {
            }
        }
        // Android 10-12 WebView builds commonly expose the support-library feature flag but
        // still implement darkening through the platform Force Dark API. Explicitly clear the
        // flag on every transition too; otherwise a site-level light override can remain dark.
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                settings.setForceDark(enabled ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
            } catch (RuntimeException ignored) {
            }
        }
        target.postInvalidateOnAnimation();
    }

    private boolean darkModeEnabled(WebView target) {
        String host = pageHostFor(target);
        SiteSettingsStore.SiteSettings site = siteSettingsStore == null
                ? new SiteSettingsStore.SiteSettings() : siteSettingsStore.forHost(host);
        int dark = site.get(SiteSettingsStore.DARK);
        boolean enabled = dark == SiteSettingsStore.ALLOW || (dark == SiteSettingsStore.INHERIT && nightMode);
        return enabled && !isInternalBrowserUrl(target == null ? null : target.getUrl());
    }

    private boolean isMediaCompatibilityHost(String host) {
        if (host == null) return false;
        String value = host.toLowerCase(Locale.US);
        return value.equals("youtube.com") || value.endsWith(".youtube.com") || value.equals("youtu.be") ||
                value.equals("youtube-nocookie.com") || value.endsWith(".youtube-nocookie.com");
    }

    private boolean isNetworkPage(String url) {
        return url != null && !isReservedInternalHost(url) &&
                (url.regionMatches(true, 0, "https://", 0, 8) ||
                        url.regionMatches(true, 0, "http://", 0, 7));
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
        if (!httpsOnly || url == null || !url.regionMatches(true, 0, "http://", 0, 7)) return false;
        String host = hostOf(url);
        SiteSettingsStore.SiteSettings site = siteSettingsStore == null ? null : siteSettingsStore.forHost(host);
        if (site != null && site.compatibilityMode()) return false;
        return host.length() > 0 && !host.endsWith(".onion") && !NetworkSecurity.isObviouslyLocalHost(host);
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
            if (url.regionMatches(true, 0, "intent:", 0, 7)) {
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
        if (pendingPermissionRequest != null && pendingPermissionRequest != request) clearPendingWebPermission(true);
        if (request == null || source == null || source != webView || request.getOrigin() == null ||
                !sameSecureOrigin(request.getOrigin().toString(), source.getUrl())) {
            if (request != null) request.deny();
            return;
        }
        if (isQrScannerUrl(source.getUrl()) && qrScannerViews.contains(source)) {
            boolean wantsCamera = false;
            for (String resource : request.getResources()) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) wantsCamera = true;
            }
            if (!wantsCamera) { request.deny(); return; }
            ArrayList<String> webResources = new ArrayList<String>();
            webResources.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
            ArrayList<String> androidPermissions = new ArrayList<String>();
            androidPermissions.add(Manifest.permission.CAMERA);
            grantWebPermission(source, request.getOrigin().toString(), request,
                    webResources, androidPermissions);
            return;
        }
        final ArrayList<String> webResources = new ArrayList<String>();
        final ArrayList<String> androidPermissions = new ArrayList<String>();
        StringBuilder names = new StringBuilder();
        boolean autoGrant = true;
        String originHost = request.getOrigin().getHost();
        if (originHost == null || originHost.length() == 0) { request.deny(); return; }
        final SiteSettingsStore.SiteSettings site = siteSettingsStore == null
                ? new SiteSettingsStore.SiteSettings() : siteSettingsStore.forHost(originHost);
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
            if (pendingPermissionRequest != null && pendingPermissionRequest != request) clearPendingWebPermission(true);
            pendingPermissionRequest = request;
            pendingPermissionView = source;
            pendingPermissionOrigin = origin;
            pendingWebPermissionResources = webResources.toArray(new String[webResources.size()]);
            requestPermissions(missing.toArray(new String[missing.size()]), WEB_PERMISSION_REQUEST);
        }
    }

    private void clearPendingWebPermission(boolean deny) {
        PermissionRequest pending = pendingPermissionRequest;
        pendingPermissionRequest = null;
        pendingWebPermissionResources = null;
        pendingPermissionView = null;
        pendingPermissionOrigin = null;
        if (deny && pending != null) pending.deny();
    }

    private void handleGeolocationRequest(final WebView source, final String origin, final GeolocationPermissions.Callback callback) {
        if (callback == null) return;
        if (pendingGeolocationCallback != null && pendingGeolocationCallback != callback) {
            pendingGeolocationCallback.invoke(pendingGeolocationOrigin, false, false);
            pendingGeolocationCallback = null;
            pendingGeolocationOrigin = null;
            pendingGeolocationView = null;
        }
        final String host = hostOf(origin);
        if (source == null || source != webView || !sameSecureOrigin(origin, source.getUrl())) {
            callback.invoke(origin, false, false);
            return;
        }
        SiteSettingsStore.SiteSettings site = siteSettingsStore == null
                ? new SiteSettingsStore.SiteSettings() : siteSettingsStore.forHost(host);
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
            clearPendingWebPermission(false);
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
        updateImmersivePageLayout();
        updateAddressBar();
        boolean canBack = webView.canGoBack();
        boolean canForward = webView.canGoForward();
        EdgeNavigationController.updateSystemGestureExclusion(webView, edgeNavigationEnabled,
                canBack, canForward);
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
        boolean shieldActive = isAdBlockActiveForHost(currentPageHost);
        if (renderedShieldActive == null || renderedShieldActive.booleanValue() != shieldActive) {
            renderedShieldActive = Boolean.valueOf(shieldActive);
            shieldButton.setActive(shieldActive);
        }
    }

    private void updateAddressBar() {
        if (addressBar == null || addressBar.hasFocus()) return;
        String url = currentPageUrl;
        String display = "";
        if (isQrScannerUrl(url)) {
            display = "扫描二维码";
        } else if (url != null && !isHomeUrl(url) && !"about:blank".equals(url)) {
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
                    // A navigation can begin while the previous 100% fade is still running.
                    // Cancel that end action before starting the new terminal fade.
                    cancelViewAnimationIfRunning(progressBar);
                    if (renderedProgress != 100) Motion.animateProgress(progressBar, renderedProgress, 100, reduceMotion());
                    renderedProgress = 100;
                    if (progressBar.getVisibility() != View.GONE) {
                        cancelViewAnimationIfRunning(progressBar);
                        progressBar.animate().alpha(0f).setDuration(reduceMotion() ? 70L : 130L).withEndAction(new Runnable() {
                            @Override public void run() { progressBar.setVisibility(View.GONE); progressBar.setAlpha(1f); }
                        }).start();
                    }
                    return;
                }
                if (renderedProgress >= 0 && renderedProgress < 100 && Math.abs(value - renderedProgress) < progressStep()) return;
                int previous = renderedProgress;
                renderedProgress = value;
                if (immersiveGestureActive) {
                    cancelViewAnimationIfRunning(progressBar);
                    syncChromeProgress(immersiveGestureOffset);
                    Motion.animateProgress(progressBar, previous, value, reduceMotion());
                    return;
                }
                if (chromeHiddenByScroll || isInPictureInPictureMode()) {
                    cancelViewAnimationIfRunning(progressBar);
                    progressBar.setVisibility(View.GONE);
                    return;
                }
                // Retire the old completion callback before exposing progress for a new load.
                if (previous == 100) cancelViewAnimationIfRunning(progressBar);
                progressBar.setAlpha(1f);
                if (progressBar.getVisibility() != View.VISIBLE) {
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

    private boolean isInternalBrowserUrl(String url) {
        if (!isReservedInternalHost(url)) return false;
        try { return "https".equalsIgnoreCase(Uri.parse(url).getScheme()); }
        catch (RuntimeException ignored) { return false; }
    }

    private boolean isReservedInternalHost(String url) {
        if (url == null) return false;
        try { return "median.invalid".equalsIgnoreCase(Uri.parse(url).getHost()); }
        catch (RuntimeException ignored) { return false; }
    }

    private static String navigationDocumentUrl(String url) {
        if (url == null) return "";
        int fragment = url.indexOf('#');
        return fragment < 0 ? url : url.substring(0, fragment);
    }

    private boolean isQrScannerUrl(String url) {
        if (!UrlCleaner.isHttpsOrigin(url, "median.invalid")) return false;
        try {
            Uri uri = Uri.parse(url);
            return "/scan".equals(uri.getPath()) && uri.getQuery() == null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private android.graphics.drawable.Drawable sheetHandleDrawable() {
        return new android.graphics.drawable.InsetDrawable(
                roundRect(nightMode ? 0x505f6368 : 0x365f6368, 2), 0, dp(14), 0, dp(14));
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
    }

    private void finishFileChooser(Uri[] result) {
        ValueCallback<Uri[]> callback = fileChooserCallback;
        fileChooserCallback = null;
        fileChooserView = null;
        fileChooserDocumentUrl = "";
        if (callback != null) try { callback.onReceiveValue(result); } catch (RuntimeException ignored) {}
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String safeMessage(Exception e) {
        return ErrorText.message(e, 180);
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
                    String trimmed = raw.trim();
                    if (trimmed.startsWith("<")) {
                        imported = importBookmarkHtml(raw);
                    } else {
                        imported = dataStore.importJson(raw);
                        JSONObject root = new JSONObject(raw);
                        JSONObject folders = root.optJSONObject("bookmarkFolders");
                        if (folders != null) bookmarkFolders.mergeJson(folders.toString());
                    }
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

    private int importBookmarkHtml(String raw) {
        List<BookmarkHtmlParser.Item> items = BookmarkHtmlParser.parse(raw);
        if (items.isEmpty()) throw new IllegalArgumentException("HTML 中没有可导入的网页书签");
        HashMap<String, String> folderIds = new HashMap<String, String>();
        int imported = 0;
        for (BookmarkHtmlParser.Item item : items) {
            BrowserDataStore.Bookmark added = dataStore.addBookmark(item.title, item.url);
            if (added == null) continue;
            imported++;
            String parent = BookmarkFolderStore.ROOT;
            StringBuilder path = new StringBuilder();
            for (String name : item.folders) {
                if (path.length() > 0) path.append('/');
                path.append(name);
                String key = path.toString();
                String folderId = folderIds.get(key);
                if (folderId == null) {
                    BookmarkFolderStore.Folder found = null;
                    for (BookmarkFolderStore.Folder candidate : bookmarkFolders.foldersIn(parent))
                        if (candidate.name.equals(name)) { found = candidate; break; }
                    if (found == null) found = bookmarkFolders.create(name, parent, false);
                    if (found == null) break;
                    folderId = found.id;
                    folderIds.put(key, folderId);
                }
                parent = folderId;
            }
            bookmarkFolders.setParentForUrl(added.url, parent);
        }
        if (imported == 0) throw new IllegalArgumentException("没有新的网页书签可导入");
        return imported;
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
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(exporting ? "加密完整备份" : "解密完整备份")
                .setMessage(exporting ? "包含书签、历史、标签、设置、脚本、过滤订阅和密码。离线页面及下载文件不包含在内。忘记口令将无法恢复。" :
                        "恢复会替换当前浏览数据、脚本、设置和密码。AES-GCM 校验失败时不会应用备份。")
                .setView(fields)
                .setPositiveButton(exporting ? "加密导出" : "解密恢复", null)
                .setNegativeButton("取消", null).create();
        secureDialog(dialog);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View ignored) {
                        String first = password.getText().toString();
                        if (first.length() < 10 || first.length() > 256) {
                            password.setError("备份口令需为 10–256 个字符");
                            return;
                        }
                        if (exporting && !first.equals(confirmation.getText().toString())) {
                            confirmation.setError("两次输入的口令不一致");
                            return;
                        }
                        dialog.dismiss();
                        if (exporting) createFullBackup(uri, first.toCharArray());
                        else restoreFullBackup(uri, first.toCharArray());
                    }
                });
            }
        });
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
        // Retain the legacy field for older backup readers; the unsafe direct-network mode was
        // removed and is never restored.
        value.put("networkDirect", false);
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
        boolean legacyRestoreTabs = value.optBoolean("restoreTabs", false);
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
        scriptTabValues.clear();
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
        bindWebViewToTab(active, webView);
        applyPerformanceMode(webView);
        active.lastActiveAt = SystemClock.uptimeMillis();
        webContainer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        currentPageUrl = active.url;
        currentPageHost = hostOf(active.url);
        pageHosts.put(webView, currentPageHost);
        if (isHomeUrl(active.url)) showHome();
        else if (isQrScannerUrl(active.url)) showQrScannerPage();
        else loadNetworkUrl(webView, active.url);
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
        else if (intent != null && Intent.ACTION_MAIN.equals(intent.getAction()) &&
                intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
                !HomeOpenPolicy.KEEP_LAST.equals(homeOpenMode())) {
            // A fresh tap on the launcher follows the selected startup destination even
            // when Android reuses this task. Returning from another app keeps the page.
            dismissOverlayForNavigation();
            startupSessionPending = false;
            if (startupReady) openConfiguredHome();
            else {
                startupSavedState = null;
                startupExternalUrl = "";
                pendingStartupInput = null;
            }
        }
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
        saveCurrentTab();
        Bundle webViewState = captureWebViewState(webView);
        Bundle tabMetadata = captureTabMetadata();
        Bundle browserState = new Bundle();
        if (webViewState != null) browserState.putBundle(STATE_WEBVIEW, webViewState);
        if (tabMetadata != null) browserState.putBundle(STATE_TABS, tabMetadata);
        // Keep the combined browser contribution within the original history-only budget.
        // Tab identity/selection and a reloadable URL take precedence over optional history.
        if (!isBoundedTabState(browserState)) webViewState = null;
        if (webViewState != null) outState.putBundle(STATE_WEBVIEW, webViewState);
        if (tabMetadata != null) outState.putBundle(STATE_TABS, tabMetadata);
        persistSession();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DOWNLOAD_DIRECTORY_REQUEST) {
            DownloadDirectory.handlePickerResult(this, resultCode, data, new Runnable() {
                @Override public void run() { showDownloadSettings(); }
            });
        } else if (requestCode == QR_SCAN_REQUEST) {
            String scanned = "";
            if (resultCode == RESULT_OK && data != null) {
                scanned = data.getStringExtra("SCAN_RESULT");
                if (scanned == null || scanned.trim().length() == 0) scanned = data.getDataString();
                if (scanned == null || scanned.trim().length() == 0) scanned = data.getStringExtra("result");
            }
            String value = scanned == null ? "" : scanned.trim();
            if (value.length() > 16 * 1024) {
                toast("二维码内容异常过长");
                if (webView != null && isQrScannerUrl(webView.getUrl())) showQrScannerPage();
            } else if (value.length() > 0) showQrResult(value);
            else if (webView != null && isQrScannerUrl(webView.getUrl())) showQrScannerPage();
        } else if (requestCode == FILE_CHOOSER_REQUEST && fileChooserCallback != null) {
            Uri[] result = null;
            try { result = WebChromeClient.FileChooserParams.parseResult(resultCode, data); }
            catch (RuntimeException ignored) {}
            WebView chooserView = fileChooserView;
            String current = chooserView == null ? null : chooserView.getUrl();
            if (chooserView == null || chooserView != webView || activityDestroyed ||
                    !fileChooserDocumentUrl.equals(navigationDocumentUrl(current))) result = null;
            finishFileChooser(result);
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
            homeCustomization().onImagePickerResult(HomeImageStore.Kind.WALLPAPER, resultCode, data);
        } else if (requestCode == HOME_LOGO_REQUEST) {
            homeCustomization().onImagePickerResult(HomeImageStore.Kind.LOGO, resultCode, data);
        } else if (requestCode == VAULT_UNLOCK_REQUEST) {
            Runnable action = pendingVaultAction;
            Runnable cancelAction = pendingVaultCancelAction;
            pendingVaultAction = null;
            pendingVaultCancelAction = null;
            if (resultCode == RESULT_OK) {
                uiHandler.removeCallbacks(vaultLock);
                vaultUnlockedUntil = SystemClock.elapsedRealtime() + VAULT_UNLOCK_WINDOW_MS;
                uiHandler.postDelayed(vaultLock, VAULT_UNLOCK_WINDOW_MS);
                if (action != null) action.run();
            } else {
                toast("密码库保持锁定");
                continueSettingsPanel(cancelAction);
            }
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
        } else if (webView != null && adPickerSessions.containsKey(webView)) {
            finishAdPickerSession(webView, adPickerSessions.get(webView));
        } else if (webView != null && webView.canGoBack()) {
            goBackWebView(webView);
            scheduleRestoredHomeRepair(webView);
        } else if (tabs.size() > 1) {
            closeCurrentTab();
        } else {
            if (clearDataOnExit) clearDataForExit();
            finishAfterTransition();
        }
    }

    private void trimRuntimeCaches(boolean severe) {
        preparedInjection = null;
        adBlock.trimMemory();
        if (scriptStore != null) scriptStore.trimMemory();
        if (services != null) services.trimMemory();
        // The active WebView owns the rendered document; these Java strings are rebuildable.
        cachedHomeKey = "";
        cachedHomeHtml = "";
        if (severe) scriptTabValues.trimMemory();
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
        uiHandler.removeCallbacks(vaultLock);
        vaultLock.run();
        // Preserve hot-tab resume behavior. Android's memory-pressure callbacks below own
        // renderer freezing; merely opening another Activity must not turn a hot tab cold.
        if (services != null) services.trimMemory();
        maybeTrimTransientCache();
        super.onStop();
    }

    @Override
    protected void onPause() {
        immersiveGestureActive = false;
        updatePageMargins();
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
            resumeQrScannerIfNeeded();
        }
        startupReadiness.set(StartupReadiness.RESUMED, true);
        maybeStartFastStartup();
        maybeCompleteStartup();
        if (pageFinished && isNetworkPage(currentPageUrl)) scheduleDeferredStartupWork();
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
        updatePageMargins();
        if (rootFrame != null) rootFrame.requestApplyInsets();
        if (inPictureInPictureMode) {
            if (topBar != null) topBar.setVisibility(View.INVISIBLE);
            if (bottomBar != null) bottomBar.setVisibility(View.INVISIBLE);
            if (progressBar != null) progressBar.setVisibility(View.GONE);
        } else if (chromeHiddenByScroll) {
            if (topBar != null) topBar.setVisibility(View.INVISIBLE);
            if (bottomBar != null) bottomBar.setVisibility(View.INVISIBLE);
            setImmersiveSystemBars(true);
        } else {
            showBrowserChrome(false);
        }
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
            boolean severe = level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                    level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE;
            trimRuntimeCaches(severe);
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
        trimRuntimeCaches(true);
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
        if (adBlockRuleLoader != null) adBlockRuleLoader.close();
        if (startupDataLoader != null) startupDataLoader.close();
        if (connectionHints != null) connectionHints.close();
        if (userScriptNetwork != null) userScriptNetwork.close();
        if (homeCustomizationController != null) homeCustomizationController.close();
        pendingVaultAction = null;
        pendingVaultCancelAction = null;
        uiHandler.removeCallbacks(vaultLock);
        vaultLock.run();
        unregisterPredictiveBack();
        if (!(clearDataOnExit && exitDataCleared)) persistSession();
        clearPendingWebPermission(true);
        if (pendingGeolocationCallback != null) pendingGeolocationCallback.invoke(pendingGeolocationOrigin, false, false);
        pendingGeolocationCallback = null;
        pendingGeolocationOrigin = null;
        pendingGeolocationView = null;
        finishFileChooser(null);
        dismissOverlay();
        uiHandler.removeCallbacksAndMessages(null);
        if (startupExecutor != null) startupExecutor.shutdownNow();
        // Provider startup is process-wide. Do not interrupt its queued work on Activity teardown;
        // its terminal callback retires this executor even if this Activity has been destroyed.
        if (navigationExecutor != null) navigationExecutor.shutdownNow();
        if (scriptExecutor != null) scriptExecutor.shutdownNow();
        if (scriptNetworkExecutor != null) scriptNetworkExecutor.shutdownNow();
        faviconFetchQueued.clear();
        scriptTabValues.clear();
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
