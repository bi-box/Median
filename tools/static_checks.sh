#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

python3 tools/java_syntax_sanity.py
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
for p in Path('app/src/main').rglob('*.xml'):
    ET.parse(p)
print('Android XML parse passed')
PY

if rg -n 'addJavascriptInterface|MIXED_CONTENT_ALWAYS_ALLOW|setAllowFileAccess\(true\)|setAllowUniversalAccessFromFileURLs\(true\)|setInstanceFollowRedirects\(true\)' app/src/main; then
  echo 'Unsafe WebView or redirect surface found.' >&2
  exit 1
fi
if rg -n 'median-debug|STOREPASS:-android|versionName=.1\.3\.|Median Browser 1\.2' --glob '!CHANGELOG.md' --glob '!UPGRADE_NOTES.md' --glob '!tools/static_checks.sh' .; then
  echo 'Stale release/debug metadata found.' >&2
  exit 1
fi
rg -q "medianVersionCode = 92" app/build.gradle
rg -q "medianVersionName = '2.3.0'" app/build.gradle
rg -Fq 'VERSION_CODE="${VERSION_CODE:-92}"' tools/build_500kb_apk.sh
rg -Fq 'export VERSION_CODE="${VERSION_CODE:-92}"' tools/build_signed_update.sh
rg -q "applicationId 'com.xinyv.median.compat'" app/build.gradle
rg -q 'targetSdk 36' app/build.gradle
if rg -n '^[[:space:]]*implementation[[:space:]]' app/build.gradle; then
  echo 'Unexpected production runtime dependency found; the focused WebKit slice must remain self-contained.' >&2
  exit 1
fi
rg -Fq 'WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'WebViewCompat.addDocumentStartJavaScript' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'applyDarkMode(target);' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'WebSettingsCompat.setAlgorithmicDarkeningAllowed(target.getSettings(), enabled)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'public static final String ALGORITHMIC_DARKENING = "ALGORITHMIC_DARKENING"' app/src/main/java/androidx/webkit/WebViewFeature.java
rg -Fq 'InvocationHandler getWebkitToCompatConverter()' app/src/main/java/org/chromium/support_lib_boundary/WebViewProviderFactoryBoundaryInterface.java
rg -Fq 'InvocationHandler convertSettings(WebSettings webSettings)' app/src/main/java/org/chromium/support_lib_boundary/WebkitToCompatConverterBoundaryInterface.java
rg -Fq 'void setAlgorithmicDarkeningAllowed(boolean allow)' app/src/main/java/org/chromium/support_lib_boundary/WebSettingsBoundaryInterface.java
rg -Fq '<style name="AppThemeDark"' app/src/main/res/values/styles.xml
rg -Fq '<item name="android:isLightTheme">true</item>' app/src/main/res/values/styles.xml
rg -Fq '<item name="android:isLightTheme">false</item>' app/src/main/res/values/styles.xml
rg -Fq 'refreshActiveSheetTheme();' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq '.search input{border:0;outline:0;font-size:17px;flex:1;min-width:0;background:transparent' app/src/main/java/com/xinyv/median/HomePage.java
rg -Fq 'InternalNavigationPolicy.canHandleCommand' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'InternalNavigationPolicy.shouldClearHomeTrust' app/src/main/java/com/xinyv/median/MainActivity.java
if rg -n '网页无权调用浏览器内部功能|MutationObserver|getComputedStyle|proceduralScriptForHost|AggressivePerformanceController|PerformanceHintManager|preferredRefreshRate|THREAD_PRIORITY_DISPLAY' app/src/main/java/com/xinyv/median; then
  echo 'Intrusive internal-command errors or continuous/aggressive page work returned.' >&2
  exit 1
fi
if [ -e app/src/main/java/com/xinyv/median/PageDarkening.java ] ||
   rg -n 'PageDarkening|darkScriptHandlers|installDocumentStartDarkMode|applyPageDarkFallback|getComputedStyle' \
      app/src/main/java/com/xinyv/median/MainActivity.java; then
  echo 'DOM-scanning dark mode must remain completely removed.' >&2
  exit 1
fi
python3 - <<'PY'
from pathlib import Path
text = Path('app/src/main/java/com/xinyv/median/MainActivity.java').read_text(encoding='utf-8')
on_create = text.index('protected void onCreate(Bundle state)')
on_create_end = text.index('private void initializeInitialWebView()', on_create)
startup = text[on_create:on_create_end]
assert startup.index('setTheme(') < startup.index('super.onCreate(state)'), 'Dark theme must be selected before Activity creation'
assert 'WebViewCompat.startUpWebView' not in startup, 'First navigation must not wait for an optional OEM startup callback'
initial_start = text.index('private void initializeInitialWebView()')
initial_end = text.index('private void beginStartupLoad(', initial_start)
initial = text[initial_start:initial_end]
assert 'showHome()' not in initial and '.loadUrl(' not in initial and 'loadDataWithBaseURL' not in initial, 'Initial WebView construction must not race a placeholder navigation'
complete_start = text.index('private void completeStartup(')
complete_end = text.index('private void scheduleDeferredStartupWork()', complete_start)
complete = text[complete_start:complete_end]
assert complete.count('loadInput(') == 1 and 'webView.loadUrl(' not in complete, 'Startup completion must issue at most one direct navigation'
gate_start = text.index('private void maybeCompleteStartup()')
gate_end = text.index('private void completeStartup(', gate_start)
gate = text[gate_start:gate_end]
assert 'startupReadiness.claimPost()' in gate and 'postAfterUiTransition' in gate and 'startupReadiness.begin()' in gate, 'Cold start must wait for resumed first-frame dispatch'
transition_start = text.index('private void postAfterUiTransition(')
transition_end = text.index('private static String settingPreview(', transition_start)
transition = text[transition_start:transition_end]
assert 'addOnAttachStateChangeListener' in transition and 'postOnAnimation' in transition, 'Cold start must cross view attachment and a real frame'
prepare_start = text.index('private void prepareNetworkDestination(')
prepare_end = text.index('private void loadNetworkUrl(', prepare_start)
prepare = text[prepare_start:prepare_end]
assert prepare.index('applyPageAccessPolicy') < prepare.index('applySiteSettings'), 'Destination network access must be restored before site policy'
assert 'watchInitialNetworkNavigation(target, url);' in text and '.acknowledge(url)' in text, 'First network navigation must have progress-qualified recovery'
page_start = text[text.index('public void onPageStarted(WebView view, String url'):text.index('public void onPageCommitVisible(WebView view, String url')]
assert 'acknowledgeInitialNavigation' not in page_start, 'onPageStarted may fire at the stuck 10-percent state'
assert 'newProgress > 10' in text and 'onPageCommitVisible' in text, 'Initial navigation may retire only after real progress or commit'
assert 'HOME_TOKEN.equals(message)' in text, 'Homepage commands must use a token-authenticated prompt channel'
prompt_start = text.index('public boolean onJsPrompt(')
prompt_end = text.index('public void onShowCustomView(', prompt_start)
prompt = text[prompt_start:prompt_end]
assert 'isHomeUrl(view.getUrl())' in prompt and 'view.post(new Runnable()' in prompt, 'Homepage command must be validated and deferred until the prompt callback returns'
assert prompt.index('result.confirm("")') < prompt.index('view.post(new Runnable()'), 'Prompt must be resolved before command dispatch'
refresh_start = text.index('refreshButton.setOnClickListener(')
refresh_end = text.index('refreshButton.setOnLongClickListener(', refresh_start)
refresh = text[refresh_start:refresh_end]
assert refresh.index('renderedHomeKeys.remove(webView)') < refresh.index('showHome()'), 'Homepage refresh must bypass the rendered-page cache'
offline_start = text.index('private void prepareOfflineDestination(')
offline_end = text.index('private void prepareNetworkDestination(', offline_start)
offline = text[offline_start:offline_end]
assert 'appliedSiteSettings.remove(target)' in offline and 'setJavaScriptEnabled(false)' in offline, 'Offline state must invalidate the cached online site policy'
home_start = text.index('private void prepareHomeDestination(')
home_end = offline_start
home = text[home_start:home_end]
assert 'setJavaScriptEnabled(true)' in home and 'setBlockNetworkImage(false)' in home, 'Homepage state must recover from restrictive page settings'
performance_start = text.index('private void applyPerformanceMode(WebView target)')
performance_end = text.index('private void applyDesktopMode()', performance_start)
assert 'setBlockNetworkLoads' not in text[performance_start:performance_end], 'Performance tuning must not mutate page access policy'
native_start = text.index('private void applyDarkMode(WebView target)')
native_end = text.index('private boolean darkModeEnabled(WebView target)', native_start)
native = text[native_start:native_end]
assert 'WebSettingsCompat.setAlgorithmicDarkeningAllowed' in native, 'Native WebView darkening missing'
assert 'evaluateJavascript' not in native and 'loadUrl(' not in native, 'Dark toggles must not inject or reload page scripts'
policy_start = native_end
policy_end = text.index('private boolean isMediaCompatibilityHost', policy_start)
assert 'isMediaCompatibilityHost' not in text[policy_start:policy_end], 'Dark mode must not exempt YouTube or media hosts'
PY
rg -q 'DOCUMENT_START_SCRIPT:1' app/src/main/java/androidx/webkit/WebViewFeature.java
rg -q 'org.chromium.support_lib_glue.SupportLibReflectionUtil' app/src/main/java/androidx/webkit/internal/WebViewGlueCommunicator.java
rg -q 'class WebViewCompat' app/src/main/java/androidx/webkit/WebViewCompat.java
rg -q 'class WebViewFeature' app/src/main/java/androidx/webkit/WebViewFeature.java
rg -q 'interface WebViewProviderFactoryBoundaryInterface' app/src/main/java/org/chromium/support_lib_boundary/WebViewProviderFactoryBoundaryInterface.java
rg -q 'android:allowBackup="false"' app/src/main/AndroidManifest.xml
rg -q 'android:exported="false"' app/src/main/AndroidManifest.xml
rg -q 'android.permission.REQUEST_INSTALL_PACKAGES' app/src/main/AndroidManifest.xml
rg -Fq 'android:authorities="${applicationId}.downloads"' app/src/main/AndroidManifest.xml
rg -q 'EXPECTED_SHA256=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78' gradlew
rg -Fq 'BuildConfig.APPLICATION_ID + ".offline"' app/src/main/java/com/xinyv/median/OfflineContentProvider.java
rg -Fq 'android:taskAffinity="${applicationId}.private"' app/src/main/AndroidManifest.xml
rg -Fq 'NetworkSecurity.isCredentialHeader(name)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'NetworkSecurity.isCredentialHeader(name)' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'parseContentRange(connection.getHeaderField("Content-Range"))' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'sameSecureOrigin(pendingPermissionOrigin, webView.getUrl())' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq "String(location.hostname||'').toLowerCase()==='median.invalid'" app/src/main/java/com/xinyv/median/UserScriptStore.java
rg -Fq 'coldTabStateLimit(performanceMode)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'WebViewPolicy.applySecureDefaults(settings, WebSettings.LOAD_DEFAULT)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'WebViewPolicy.applySecureDefaults(settings, WebSettings.LOAD_NO_CACHE)' app/src/main/java/com/xinyv/median/PrivateActivity.java
rg -Fq 'settings.setBlockNetworkLoads(false)' app/src/main/java/com/xinyv/median/WebViewPolicy.java
rg -Fq 'final BaseAdapter tabAdapter' app/src/main/java/com/xinyv/median/MainActivity.java
if rg -n 'spareWebView|scheduleWebViewPrewarm|allowPrewarmedWebView' app/src/main/java/com/xinyv/median; then
  echo 'Disabled speculative WebView prewarming must stay removed.' >&2
  exit 1
fi
rg -Fq 'if (!changed) return' app/src/main/java/com/xinyv/median/BrowserDataStore.java
rg -Fq 'if (!dirty) return' app/src/main/java/com/xinyv/median/BrowserDataStore.java
rg -Fq 'bookmarkSnapshot = new ArrayList<Bookmark>(bookmarks)' app/src/main/java/com/xinyv/median/BrowserDataStore.java
rg -Fq 'sameNormalizedUrl(item.url, normalized)' app/src/main/java/com/xinyv/median/BrowserDataStore.java
rg -Fq 'io.postDelayed(writer, WRITE_RETRY_MS)' app/src/main/java/com/xinyv/median/BrowserDataStore.java
rg -Fq 'matchCache.put(url, stable)' app/src/main/java/com/xinyv/median/UserScriptStore.java
rg -Fq 'HashMap<String, JSONObject> cache' app/src/main/java/com/xinyv/median/ScriptValueStore.java
rg -Fq 'OmniboxInput.looksLikeWebAddress(text)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'OmniboxInput.looksLikeWebAddress(value)' app/src/main/java/com/xinyv/median/PrivateActivity.java
rg -Fq 'row.setBackgroundResource(selectableBounded())' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'resolveAttribute(android.R.attr.selectableItemBackground, out, true)' app/src/main/java/com/xinyv/median/MainActivity.java
if rg -n 'row\.setBackgroundResource\(selectableBorderless\(\)\)' app/src/main/java/com/xinyv/median; then
  echo 'Full-width rows must use a bounded ripple.' >&2
  exit 1
fi
rg -Fq 'showHomeCustomization()' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'sections[0] = "隐私与安全"' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq '返回键只关闭设置' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'private void navigateOverlayBack()' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'private void dismissOverlayForNavigation()' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'SHEET_ROW_TOGGLE_ON' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'new ArrayBlockingQueue<Runnable>' app/src/main/java/com/xinyv/median/BackgroundExecutor.java
if rg -n 'CallerRunsPolicy|new LinkedBlockingQueue<Runnable>()' app/src/main/java/com/xinyv/median/MainActivity.java; then
  echo 'Background queues must stay bounded and never fall back to the UI caller.' >&2
  exit 1
fi
rg -Fq 'new ThreadPoolExecutor.DiscardOldestPolicy()' app/src/main/java/com/xinyv/median/BackgroundExecutor.java
rg -Fq 'private boolean executeTask(ExecutorService executor, Runnable task)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'private void beginStartupLoad' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'startupExecutor = BackgroundExecutor.create(2, 8, "median-startup", false)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'final AtomicInteger remaining = new AtomicInteger(2)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'startupScriptSource = load.scriptSource' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'initializeInitialWebView();' app/src/main/java/com/xinyv/median/MainActivity.java
if rg -n 'webViewStartupFallback|webViewStartupComplete|startUpWebView\(scriptExecutor' app/src/main/java/com/xinyv/median/MainActivity.java; then
  echo 'Main browsing startup must not wait on optional asynchronous WebView startup.' >&2
  exit 1
fi
rg -Fq 'services.warmLocalIndexes()' app/src/main/java/com/xinyv/median/MainActivity.java
if rg -n 'webViewStartupFallback|webViewStartupComplete|WebViewCompat.startUpWebView' app/src/main/java/com/xinyv/median/PrivateActivity.java; then
  echo 'Private browsing startup must not wait on optional asynchronous WebView startup.' >&2
  exit 1
fi
rg -Fq 'startupExecutor = BackgroundExecutor.create(2, 4, "median-private-startup", false)' app/src/main/java/com/xinyv/median/PrivateActivity.java
rg -Fq '!cookieResetComplete || !filterRulesReady' app/src/main/java/com/xinyv/median/PrivateActivity.java
rg -Fq 'startupReadiness.claimPost()' app/src/main/java/com/xinyv/median/PrivateActivity.java
rg -Fq 'postAfterAttachedFrame(complete)' app/src/main/java/com/xinyv/median/PrivateActivity.java
rg -Fq 'addOnAttachStateChangeListener' app/src/main/java/com/xinyv/median/PrivateActivity.java
rg -Fq 'initialNavigationGuard.acknowledge(url)' app/src/main/java/com/xinyv/median/PrivateActivity.java
rg -Fq 'scheduleInitialNavigationRetry(webView, pendingNavigation, 250L)' app/src/main/java/com/xinyv/median/PrivateActivity.java
if rg -n 'filterCompileThread|new Thread\(' app/src/main/java/com/xinyv/median/PrivateActivity.java ||
   [ "$(rg -c 'new Thread\(' app/src/main/java/com/xinyv/median/BackgroundExecutor.java)" -ne 1 ]; then
  echo 'Private startup work must stay behind its single bounded-executor ThreadFactory.' >&2
  exit 1
fi
if rg -n 'ASYNC_WEBVIEW_STARTUP|startUpWebView' \
    app/src/main/java/androidx/webkit app/src/main/java/org/chromium/support_lib_boundary; then
  echo 'Unused asynchronous WebView startup glue must stay out of the release.' >&2
  exit 1
fi
rg -Fq 'renderedHomeKeys.put(webView, renderKey)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'registerPredictiveBack()' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'private boolean isCurrentPageCallback(WebView source, long sequence)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'requestGeneration != speechGeneration' app/src/main/java/com/xinyv/median/PageAssistant.java
rg -Fq 'io.postDelayed(writer, WRITE_DELAY_MS)' app/src/main/java/com/xinyv/median/UserScriptStore.java
test "$(rg -l 'LocalDataIo.acquire\(\)' app/src/main/java/com/xinyv/median/{BrowserDataStore,SiteSettingsStore,UserScriptStore}.java | wc -l)" -eq 3
! rg -q 'HandlerThread' app/src/main/java/com/xinyv/median/{BrowserDataStore,SiteSettingsStore,UserScriptStore}.java
rg -Fq 'new ArrayBlockingQueue<Runnable>(1)' app/src/main/java/com/xinyv/median/FilterSubscriptionStore.java
rg -Fq 'new ArrayBlockingQueue<Runnable>(32)' app/src/main/java/com/xinyv/median/PasswordVault.java
if rg -n 'Executors\.newFixedThreadPool|Executors\.newCachedThreadPool' \
    app/src/main/java/com/xinyv/median/FilterSubscriptionStore.java \
    app/src/main/java/com/xinyv/median/PasswordVault.java; then
  echo 'Optional service queues must remain bounded.' >&2
  exit 1
fi
if rg -n 'WifiManager|WifiLock|ACCESS_WIFI_STATE' app/src/main/java/com/xinyv/median/MainActivity.java app/src/main/AndroidManifest.xml; then
  echo 'Foreground browsing must not hold a Wi-Fi performance lock.' >&2
  exit 1
fi
rg -Fq 'io.postDelayed(writer, WRITE_DELAY_MS)' app/src/main/java/com/xinyv/median/SiteSettingsStore.java
rg -Fq 'bar.setProgress(to, true)' app/src/main/java/com/xinyv/median/Motion.java
rg -Fq 'showHomeCustomizationPanel()' app/src/main/java/com/xinyv/median/MainActivity.java
if rg -Fq 'if (!isHomeUrl(currentPageUrl)) showHome();' app/src/main/java/com/xinyv/median/MainActivity.java; then
  echo 'Opening homepage settings must not replace the current browsing page.' >&2
  exit 1
fi
rg -Fq 'SETTINGS = 19' app/src/main/java/com/xinyv/median/BrowserIconView.java
rg -Fq 'android:strokeLineCap="round"' app/src/main/res/drawable/ic_launcher_foreground.xml
rg -Fq '<color name="launcher_background">#181A1F</color>' app/src/main/res/values/launcher_colors.xml
rg -Fq 'android:strokeColor="#E7E9ED"' app/src/main/res/drawable/ic_launcher_foreground.xml
rg -Fq 'android:strokeColor="#7D8590"' app/src/main/res/drawable/ic_launcher_foreground.xml
rg -Fq 'android:pathData="M31,28L84,43L41,81Z"' app/src/main/res/drawable/ic_launcher_foreground.xml
rg -Fq 'android:pathData="M31,28L62.5,62"' app/src/main/res/drawable/ic_launcher_foreground.xml
rg -Fq '<monochrome android:drawable="@drawable/ic_launcher_foreground" />' app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml
rg -Fq 'String source = scriptStore.buildDocumentStartScript(token);' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'for compression_level in range(6, 10)' tools/deflate_raw.py
rg -Fq 'for memory_level in range(5, 10)' tools/deflate_raw.py
rg -Fq 'for compression_level in range(10, 13)' tools/deflate_raw.py
if rg -n 'buildDocumentStartScripts|recordExecutionResult|lastCostMs|__mreport' app/src/main/java/com/xinyv/median; then
  echo 'Per-script document registration or execution telemetry returned.' >&2
  exit 1
fi
if rg -n 'pathData="M35,69V40L54,61L73,40V69"|strokeColor="#2DD4FF"|M27,73V47C27,38' app/src/main/res/drawable/ic_launcher*.xml; then
  echo 'Legacy letter or terminal launcher artwork found.' >&2
  exit 1
fi
rg -Fq 'interceptHomeAsset(source, requestUri)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'HomeOpenPolicy.restoresLast' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'tab.url = configuredHomeUrl()' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq '"/home-custom".equals(uri.getPath())' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq "sandbox='allow-scripts allow-forms allow-popups allow-popups-to-escape-sandbox allow-top-navigation-by-user-activation'" app/src/main/java/com/xinyv/median/HomePage.java
rg -Fq "connect-src 'none'" app/src/main/java/com/xinyv/median/CustomHomeHtml.java
rg -Fq 'customHomeViews.contains(source) && !request.isForMainFrame()' app/src/main/java/com/xinyv/median/MainActivity.java
if rg -n 'allow-same-origin' app/src/main/java/com/xinyv/median/HomePage.java; then
  echo 'Custom homepage scripts must remain isolated from the trusted home origin.' >&2
  exit 1
fi
rg -Fq 'WALLPAPER_MAX_DIMENSION = 2048' app/src/main/java/com/xinyv/median/HomeImageStore.java
rg -Fq 'LogoMarkup.renderPreset(options.logoStyle' app/src/main/java/com/xinyv/median/HomePage.java
rg -Fq 'MAX_VISIBLE_CODE_POINTS = 48' app/src/main/java/com/xinyv/median/LogoMarkup.java
rg -Fq 'options.logoLetterSpacing' app/src/main/java/com/xinyv/median/HomePage.java
rg -Fq 'home_logo_gradient_angle' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src 'self' data:" app/src/main/java/com/xinyv/median/HomePage.java
rg -Fq 'CustomHomeCss.error(raw)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq "class='logo-space'" app/src/main/java/com/xinyv/median/LogoMarkup.java
if rg -n 'data:image/|Base64.*home.wallpaper|home.wallpaper.*Base64' app/src/main/java/com/xinyv/median; then
  echo 'Home images must be streamed locally instead of embedded into every HTML page.' >&2
  exit 1
fi
rg -Fq 'if (rule.thirdParty || rule.firstParty)' app/src/main/java/com/xinyv/median/AdBlockEngine.java
rg -Fq 'RULE_MATCH_SCRATCH' app/src/main/java/com/xinyv/median/AdBlockEngine.java
rg -Fq 'seen.add(selector)' app/src/main/java/com/xinyv/median/AdBlockEngine.java
if rg -n 'target\.contains\(selector\)' app/src/main/java/com/xinyv/median/AdBlockEngine.java; then
  echo 'Cosmetic selector de-duplication must remain hash-based.' >&2
  exit 1
fi
rg -Fq 'updateInFlight.compareAndSet(false, true)' app/src/main/java/com/xinyv/median/FilterSubscriptionStore.java
rg -Fq 'manualUpdatePending.set(true)' app/src/main/java/com/xinyv/median/FilterSubscriptionStore.java
rg -Fq 'collectUpdateTargets(runAutomatic)' app/src/main/java/com/xinyv/median/FilterSubscriptionStore.java
rg -Fq 'cosmeticInjected.putIfAbsent(source, Boolean.TRUE)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'services = new BrowserServices(this)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'class BookmarkFolderStore' app/src/main/java/com/xinyv/median/BookmarkFolderStore.java
rg -Fq 'bookmarkFolders.path(folderId)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'showBookmarkEditor(null, "", "", folderId)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq "median://folder?id=" app/src/main/java/com/xinyv/median/HomePage.java
rg -Fq "median://folders" app/src/main/java/com/xinyv/median/HomePage.java
rg -Fq 'root.put("bookmarkFolders"' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'DownloadFileTypes.correctCompletedApk' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'DownloadFileTypes.mimeForOpen' app/src/main/java/com/xinyv/median/DownloadCenterActivity.java
rg -Fq 'class DownloadRetryPolicy' app/src/main/java/com/xinyv/median/DownloadRetryPolicy.java
rg -Fq 'findBlockingDuplicate(url, 15000L)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'CookieManager.getInstance().getCookie(current.toString())' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'storeResponseCookies(current, connection)' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'control.attach(connection)' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'TASK_EXECUTOR.allowCoreThreadTimeOut(true)' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'new LinkedBlockingQueue<Runnable>(48)' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'locks.renewIfDue(now)' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
if rg -n 'wakeLock\.acquire\(\)' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java; then
  echo 'Download wake locks must always have a bounded timeout.' >&2
  exit 1
fi
rg -Fq 'return START_REDELIVER_INTENT' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'private static final ArrayList<Item> items' app/src/main/java/com/xinyv/median/DownloadStore.java
rg -Fq 'TELEMETRY_WRITE_INTERVAL_MS = 5000L' app/src/main/java/com/xinyv/median/DownloadStore.java
rg -Fq 'ensurePrivateDataDirectory()' app/src/main/java/com/xinyv/median/PrivateActivity.java
if [ "$(rg -c 'loadLocked\(' app/src/main/java/com/xinyv/median/DownloadStore.java)" -ne 2 ]; then
  echo 'DownloadStore must not reparse the full JSON index on every operation.' >&2
  exit 1
fi
rg -Fq 'AdaptiveDownloadService.isTaskScheduled(item.id)' app/src/main/java/com/xinyv/median/DownloadCenterActivity.java
rg -Fq 'extends BaseAdapter' app/src/main/java/com/xinyv/median/DownloadCenterActivity.java
rg -Fq 'store.removeAll(ids)' app/src/main/java/com/xinyv/median/DownloadCenterActivity.java
rg -Fq 'DownloadCenterPolicy.canResume(status)' app/src/main/java/com/xinyv/median/DownloadCenterActivity.java
rg -Fq 'enqueueDownload(target, url, userAgent, contentDisposition, mimetype, contentLength)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'EXTRA_TOTAL_BYTES' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'DownloadCenterPolicy.resolvedTotal(item.totalBytes, totalBytes)' app/src/main/java/com/xinyv/median/DownloadStore.java
rg -Fq 'DownloadCenterPolicy.progressPermille(current, total)' app/src/main/java/com/xinyv/median/DownloadCenterActivity.java
rg -Fq 'responseTotal(connection, cursor, contentRange)' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'services.downloads().addAdaptive' app/src/main/java/com/xinyv/median/MainActivity.java
if [ -e app/src/main/java/com/xinyv/median/DownloadMemoryPolicy.java ]; then
  echo 'Unused download diagnostics and mode planner returned.' >&2
  exit 1
fi
rg -Fq 'DownloadState.create(task)' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'connection = open(task, task.url, range' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
if rg -n 'private Probe probe|bytes=0-0' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java; then
  echo 'Downloads must not spend a separate one-byte probe request before the real transfer.' >&2
  exit 1
fi
rg -Fq 'private static final int TASK_THREADS = Math.max(1, Math.min(2' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'publishToAppDownloads' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
rg -Fq 'DownloadContentProvider.uriFor' app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java
if rg -n 'new DownloadManager\.Request|enqueueSystemDownload|addSystem\(' app/src/main/java/com/xinyv/median; then
  echo 'New downloads must not use Android DownloadManager.' >&2
  exit 1
fi
if rg -n 'videoplayback|getPlayerResponse|streamingData|isGoogleVideoUrl|youtubeQueryRange|networkObserved|YouTube 完整视频|YouTube 媒体轨|SABR|UMP' app/src/main/java/com/xinyv/median; then
  echo 'YouTube-specific sniffing/downloading code must remain removed in the classic build.' >&2
  exit 1
fi
if rg -n 'new (FilterSubscriptionStore|DownloadStore|OfflinePageStore|PageAssistant|PasswordVault|PerformanceMonitor)\(' app/src/main/java/com/xinyv/median/MainActivity.java; then
  echo 'Optional browser services must remain off the startup path.' >&2
  exit 1
fi
if rg -n 'new Thread\(' app/src/main/java/com/xinyv/median/MainActivity.java; then
  echo 'MainActivity may create threads only through the shared bounded-executor ThreadFactory.' >&2
  exit 1
fi
rg -Fq 'loadInput(query);' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'loadInput(uri.getQueryParameter("url"));' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'prompt(t,u)' app/src/main/java/com/xinyv/median/HomePage.java
rg -Fq 'HOME_TOKEN = UrlCleaner.randomToken()' app/src/main/java/com/xinyv/median/PrivateActivity.java
python3 - <<'PY'
from pathlib import Path
text = Path('app/src/main/java/com/xinyv/median/PrivateActivity.java').read_text(encoding='utf-8')
start = text.index('onJsPrompt(final WebView view')
end = text.index('onProgressChanged(WebView view', start)
prompt = text[start:end]
assert 'isHome(view.getUrl())' in prompt and 'view.post(new Runnable()' in prompt
assert prompt.index('result.confirm("")') < prompt.index('view.post(new Runnable()')
PY
rg -Fq 'private static final long INITIAL_NAVIGATION_ACK_MS = 450L' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'private static final long INITIAL_NAVIGATION_ACK_MS = 450L' app/src/main/java/com/xinyv/median/PrivateActivity.java
rg -Fq 'applyPerformanceMode(webView);' app/src/main/java/com/xinyv/median/MainActivity.java
if rg -n 'acknowledgeProgress' app/src/main/java/com/xinyv/median tools/tests; then
  echo 'A queued progress value must not acknowledge the initial HTTP(S) main-frame load.' >&2
  exit 1
fi
rg -Fq 'ThreadFactory factory = new ThreadFactory()' app/src/main/java/com/xinyv/median/BackgroundExecutor.java
rg -Fq '删除当前文件夹' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'confirmDeleteBookmarkFolder(current, false)' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq '粘贴链接或脚本代码安装' app/src/main/java/com/xinyv/median/MainActivity.java
rg -Fq 'UserScriptInstallPolicy.MAX_FETCH_ATTEMPTS' app/src/main/java/com/xinyv/median/MainActivity.java
if rg -n 'STOREPASS.*android|KEYPASS.*android|median-debug\.p12|keytool.*-storepass android' --glob '!CHANGELOG.md' --glob '!UPGRADE_NOTES.md' --glob '!tools/static_checks.sh' .; then
  echo 'Weak or auto-generated release signing material found.' >&2
  exit 1
fi
bash -n build.sh tools/build_500kb_apk.sh tools/verify.sh tools/verify_release_tag.sh tools/tests/userscript_js_syntax_test.sh
node --check tools/tests/homepage_behavior_test.js
sh -n gradlew

TMP="$(mktemp -d)"
trap 'find "$TMP" -depth -delete' EXIT
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/InternalNavigationPolicy.java \
  tools/tests/InternalNavigationPolicySelfTest.java
java -cp "$TMP" com.xinyv.median.InternalNavigationPolicySelfTest
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/StartupNavigationPolicy.java \
  tools/tests/StartupNavigationPolicySelfTest.java
java -cp "$TMP" com.xinyv.median.StartupNavigationPolicySelfTest
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/StartupReadiness.java \
  tools/tests/StartupReadinessSelfTest.java
java -cp "$TMP" com.xinyv.median.StartupReadinessSelfTest
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/InitialNavigationGuard.java \
  tools/tests/InitialNavigationGuardSelfTest.java
java -cp "$TMP" com.xinyv.median.InitialNavigationGuardSelfTest
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/MediaResourceSniffer.java \
  tools/tests/MediaResourceSnifferSelfTest.java
java -cp "$TMP" com.xinyv.median.MediaResourceSnifferSelfTest
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/MediaProbeScript.java \
  tools/tests/MediaProbeScriptSelfTest.java
java -cp "$TMP" com.xinyv.median.MediaProbeScriptSelfTest
java -cp "$TMP" com.xinyv.median.MediaProbeScriptSelfTest install > "$TMP/media-install.js"
java -cp "$TMP" com.xinyv.median.MediaProbeScriptSelfTest build > "$TMP/media-probe.js"
node --check "$TMP/media-install.js" >/dev/null
node --check "$TMP/media-probe.js" >/dev/null
node tools/tests/media_probe_behavior_test.js "$TMP/media-install.js" "$TMP/media-probe.js"
echo 'Generated media probe JavaScript syntax passed'
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/CredentialAutofill.java \
  tools/tests/CredentialAutofillSelfTest.java
java -cp "$TMP" com.xinyv.median.CredentialAutofillSelfTest
java -cp "$TMP" com.xinyv.median.CredentialAutofillSelfTest detect > "$TMP/credential-detect.js"
java -cp "$TMP" com.xinyv.median.CredentialAutofillSelfTest fill > "$TMP/credential-fill.js"
java -cp "$TMP" com.xinyv.median.CredentialAutofillSelfTest capture > "$TMP/credential-capture.js"
node --check "$TMP/credential-detect.js" >/dev/null
node --check "$TMP/credential-fill.js" >/dev/null
node --check "$TMP/credential-capture.js" >/dev/null
node tools/tests/credential_autofill_behavior_test.js \
  "$TMP/credential-detect.js" "$TMP/credential-fill.js" "$TMP/credential-capture.js"
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/NetworkSecurity.java \
  app/src/main/java/com/xinyv/median/MediaManifestParser.java \
  tools/tests/MediaManifestParserSelfTest.java
java -cp "$TMP" com.xinyv.median.MediaManifestParserSelfTest
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/UserScriptInstallPolicy.java \
  tools/tests/UserScriptInstallPolicySelfTest.java
java -cp "$TMP" com.xinyv.median.UserScriptInstallPolicySelfTest
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/NetworkSecurity.java \
  tools/tests/NetworkSecuritySelfTest.java
java -cp "$TMP" com.xinyv.median.NetworkSecuritySelfTest
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/DownloadRetryPolicy.java \
  tools/tests/DownloadRetryPolicySelfTest.java
java -cp "$TMP" com.xinyv.median.DownloadRetryPolicySelfTest
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/OmniboxInput.java \
  tools/tests/OmniboxInputSelfTest.java
java -cp "$TMP" com.xinyv.median.OmniboxInputSelfTest
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/HomeOpenPolicy.java \
  tools/tests/HomeOpenPolicySelfTest.java
java -cp "$TMP" com.xinyv.median.HomeOpenPolicySelfTest
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/CustomHomeCss.java \
  tools/tests/CustomHomeCssSelfTest.java
java -cp "$TMP" com.xinyv.median.CustomHomeCssSelfTest
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/CustomHomeHtml.java \
  tools/tests/CustomHomeHtmlSelfTest.java
java -cp "$TMP" com.xinyv.median.CustomHomeHtmlSelfTest
javac --release 17 -d "$TMP" \
  app/src/main/java/com/xinyv/median/LogoMarkup.java \
  app/src/main/java/com/xinyv/median/CustomHomeCss.java \
  app/src/main/java/com/xinyv/median/HomePageConfig.java \
  tools/tests/HomePageConfigSelfTest.java \
  tools/tests/LogoMarkupSelfTest.java
java -cp "$TMP" com.xinyv.median.HomePageConfigSelfTest
java -cp "$TMP" com.xinyv.median.LogoMarkupSelfTest
tools/tests/userscript_js_syntax_test.sh

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git diff --check
fi
echo 'Static checks passed.'
