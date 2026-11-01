'use strict';

// Compile the MainActivity provider-start methods verbatim and drive their lifecycle callbacks.
// The harness intentionally leaves the real WebView provider facade to its own protocol test.
const fs = require('fs');
const path = require('path');
const os = require('os');
const cp = require('child_process');
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const repo = path.resolve(__dirname, '../..');
const persistentOut = args.includes('--out');
const out = path.resolve(persistentOut ? option('--out') : fs.mkdtempSync(path.join(os.tmpdir(), 'median-webview-startup-')));
const jdk = option('--jdk', process.env.JAVA_HOME || '');
const executable = name => jdk ? path.join(jdk, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
const activity = fs.readFileSync(path.join(repo, 'app/src/main/java/com/xinyv/median/MainActivity.java'), 'utf8');
const extract = signature => {
  const begin = activity.indexOf(signature);
  if (begin < 0) throw new Error('Production method was not found: ' + signature);
  const brace = activity.indexOf('{', begin);
  let depth = 0;
  for (let i = brace; i < activity.length; i++) {
    if (activity[i] === '{') depth++;
    else if (activity[i] === '}' && --depth === 0) return activity.slice(begin, i + 1);
  }
  throw new Error('Unterminated production method: ' + signature);
};
const beginMethod = extract('    private void beginWebViewStartup()');
const finishMethod = extract('    private void finishWebViewStartupExecutor()');
const initialMethod = extract('    private void initializeInitialWebView()');
const construction = initialMethod.indexOf('webView = createConfiguredWebView();');
if (construction < 0) throw new Error('Initial WebView construction boundary was not found');
const initialGuard = initialMethod.slice(initialMethod.indexOf('{') + 1, construction);
if (!initialGuard.includes('activityDestroyed') || !initialGuard.includes('isFinishing()') ||
    !initialGuard.includes('webView != null') || !initialGuard.includes('webContainer == null'))
  throw new Error('Initial WebView lifecycle guard is incomplete');
const onCreate = extract('    protected void onCreate(Bundle state)');
if (onCreate.indexOf('super.onCreate(state);') >= onCreate.indexOf('beginWebViewStartup();') ||
    onCreate.indexOf('beginWebViewStartup();') >= onCreate.indexOf('buildUi();') ||
    onCreate.includes('initializeInitialWebView();'))
  throw new Error('Provider startup must precede native UI work and own initial WebView creation');
const onDestroy = extract('    protected void onDestroy()');
if (onDestroy.includes('webViewStartupExecutor') || onDestroy.includes('finishWebViewStartupExecutor('))
  throw new Error('Activity destruction must leave process-wide provider work for terminal callback cleanup');
const fields = ['webViewStartupExecutor', 'webViewStartupDiagnostic', 'activityDestroyed', 'startupStatus'];
const declarations = fields.map(name => {
  const line = activity.split(/\r?\n/).find(line => new RegExp('^    private (?:final |volatile )?[^;=]+\\b' + name + '(?: =|;)').test(line));
  if (!line) throw new Error('Production field was not found: ' + name);
  return line;
}).join('\n');
fs.mkdirSync(out, { recursive: true });
const harness = path.join(out, 'MainActivityWebViewStartupHarness.java');
fs.writeFileSync(harness, `package com.xinyv.median;
import java.util.concurrent.*;

final class MainActivityWebViewStartupHarness {
${declarations}
    private boolean finishing;
    private Object webView;
    private Object webContainer = new Object();
    private RecordingExecutor ownedExecutor;
    private WebViewStartupCompat.Callback ownedCallback;
    int posted;
    int initialized;

    private boolean isFinishing() { return finishing; }
    private void postAfterUiTransition(Runnable action) { posted++; pending = action; }
    private Runnable pending;
    private void initializeInitialWebView() {
${initialGuard}
        initialized++;
        webView = new Object();
    }

${beginMethod}

${finishMethod}

    void start() {
        beginWebViewStartup();
        ownedExecutor = (RecordingExecutor) webViewStartupExecutor;
        ownedCallback = WebViewStartupCompat.callback;
    }
    void buildNativeSurface() { startupStatus = new TextView(); }
    void runPosted() { if (pending != null) { Runnable action = pending; pending = null; action.run(); } }
    void destroyBeforeCallback() { activityDestroyed = true; }
    void finishBeforeCallback() { finishing = true; }
    void detachContainer() { webContainer = null; }
    void initializeAgain() { initializeInitialWebView(); }
    String diagnostic() { return webViewStartupDiagnostic; }
    String status() { return startupStatus == null ? null : startupStatus.getText(); }
    boolean executorCleared() { return webViewStartupExecutor == null; }
    RecordingExecutor executor() { return ownedExecutor; }
    WebViewStartupCompat.Callback callback() { return ownedCallback; }
    void finishExecutorAgain() { finishWebViewStartupExecutor(); }
}

final class TextView {
    private String text = "正在准备网页引擎…";
    void setText(String value) { text = value; }
    String getText() { return text; }
}

final class RecordingExecutor extends AbstractExecutorService {
    int shutdownCalls;
    int shutdownNowCalls;
    private boolean shutdown;
    public void shutdown() { shutdownCalls++; shutdown = true; }
    public java.util.List<Runnable> shutdownNow() { shutdownNowCalls++; shutdown = true; return java.util.Collections.emptyList(); }
    public boolean isShutdown() { return shutdown; }
    public boolean isTerminated() { return shutdown; }
    public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
    public void execute(Runnable task) { task.run(); }
}

final class BackgroundExecutor {
    static RecordingExecutor last;
    static ExecutorService createStartup(int core, int queue, String name) {
        return create(core, queue, name, false);
    }
    static ExecutorService create(int core, int queue, String name, boolean daemon) {
        last = new RecordingExecutor();
        return last;
    }
}

final class WebViewStartupCompat {
    interface Callback {
        void onSuccess(Result result);
        void onFailure(Throwable error);
    }
    static Callback callback;
    static Executor executor;
    static void startUpWebView(Executor value, Callback listener) { executor = value; callback = listener; }
    static final class Result {
        private final boolean async;
        private final Long total;
        private final Long max;
        Result(boolean async, Long total, Long max) { this.async = async; this.total = total; this.max = max; }
        boolean usedAsyncStartup() { return async; }
        Long getTotalTimeInUiThreadMillis() { return total; }
        Long getMaxTimePerTaskInUiThreadMillis() { return max; }
    }
}
`);
const test = path.join(__dirname, 'WebViewStartupBehaviorSelfTest.java');
function run(command, arguments_) {
    const result = cp.spawnSync(executable(command), arguments_, { encoding: 'utf8' });
    if (result.error) throw result.error;
    if (result.status !== 0) throw new Error(result.stdout + result.stderr);
    if (result.stdout) process.stdout.write(result.stdout);
}
try {
    run('javac', ['-encoding', 'UTF-8', '--release', '17', '-d', out, harness, test]);
    run('java', ['-cp', out, 'com.xinyv.median.WebViewStartupBehaviorSelfTest']);
} finally {
    if (!persistentOut) {
        if (path.dirname(out) !== path.resolve(os.tmpdir()) || !path.basename(out).startsWith('median-webview-startup-'))
            throw new Error('Refusing cleanup outside the generated temporary harness directory');
        fs.rmSync(out, { recursive: true, force: true });
    }
}
