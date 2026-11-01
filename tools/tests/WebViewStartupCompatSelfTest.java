import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import androidx.webkit.WebViewStartupCompat;
import androidx.webkit.internal.WebViewGlueCommunicator;
import org.chromium.support_lib_glue.SupportLibReflectionUtil;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class WebViewStartupCompatSelfTest {
    private static int cases;
    private static final Executor WORKER = task -> {
        Thread thread = new Thread(task, "startup-test-worker");
        thread.start();
        try { thread.join(3000); } catch (InterruptedException e) { throw new AssertionError(e); }
        check(!thread.isAlive(), "worker completed");
    };
    private static final class Callback implements WebViewStartupCompat.Callback {
        int count;
        WebViewStartupCompat.Result result;
        Throwable error;
        public void onSuccess(WebViewStartupCompat.Result value) {
            check(Looper.myLooper() == Looper.getMainLooper(), "success main thread");
            count++; result = value;
        }
        public void onFailure(Throwable value) {
            check(Looper.myLooper() == Looper.getMainLooper(), "failure main thread");
            count++; error = value;
        }
    }
    private static void reset() {
        drain();
        WebViewGlueCommunicator.invalidate();
        Build.VERSION.SDK_INT = 36;
        WebView.loads = 0;
        WebView.loadFailure = null;
        WebView.loader = WebView.class.getClassLoader();
        SupportLibReflectionUtil.features = new String[] { "ASYNC_WEBVIEW_STARTUP_V2", "DOCUMENT_START_SCRIPT:1" };
        SupportLibReflectionUtil.starts = 0;
        SupportLibReflectionUtil.throwOnStart = false;
        SupportLibReflectionUtil.featureEntered = null;
        SupportLibReflectionUtil.featureRelease = null;
        SupportLibReflectionUtil.success = null;
        SupportLibReflectionUtil.failure = null;
        SupportLibReflectionUtil.config = null;
    }
    private static void drain() {
        Runnable task;
        while ((task = Handler.QUEUE.poll()) != null) task.run();
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void done(String name) { cases++; System.out.println("PASS " + name); }
    private static Callback start() {
        Callback callback = new Callback();
        WebViewStartupCompat.startUpWebView(WORKER, callback);
        check(callback.count == 0, "callback always posted");
        return callback;
    }
    public static void main(String[] args) throws Exception {
        Looper.getMainLooper();
        reset();
        Callback success = start();
        check(SupportLibReflectionUtil.starts == 1 && success.count == 0, "wait for provider");
        check(SupportLibReflectionUtil.config.get(0) == WORKER, "executor protocol");
        check(Boolean.TRUE.equals(SupportLibReflectionUtil.config.get(1)), "UI tasks requested");
        check(!SupportLibReflectionUtil.config.containsKey(2), "default profiles untouched");
        SupportLibReflectionUtil.success.accept(fields -> { fields.accept(0, Long.valueOf(31)); fields.accept(1, Long.valueOf(17)); });
        check(success.count == 0, "provider callback never runs application inline");
        drain();
        check(success.count == 1 && success.error == null && success.result.usedAsyncStartup(), "V2 ready");
        check(success.result.getTotalTimeInUiThreadMillis() == 31 && success.result.getMaxTimePerTaskInUiThreadMillis() == 17, "timings");
        done("V2 protocol, deferred completion and diagnostics");

        reset();
        SupportLibReflectionUtil.features = new String[] { "ASYNC_WEBVIEW_STARTUP" };
        Callback unsupported = start(); drain();
        check(unsupported.count == 1 && !unsupported.result.usedAsyncStartup() && SupportLibReflectionUtil.starts == 0, "V1 fallback");
        done("unsupported V2 uses synchronous constructor path");

        reset(); Build.VERSION.SDK_INT = 27;
        Callback old = start(); drain();
        check(!old.result.usedAsyncStartup() && WebView.loads == 0, "API27 no hidden API");
        done("API26/27 skip provider reflection");

        reset();
        Callback direct = new Callback(); WebViewStartupCompat.startUpWebView(Runnable::run, direct);
        check(direct.count == 0 && WebView.loads == 0, "direct executor safe"); drain();
        check(!direct.result.usedAsyncStartup(), "direct fallback");
        done("direct executor does not initialize provider on UI thread");

        reset(); Callback rejected = new Callback();
        WebViewStartupCompat.startUpWebView(task -> { throw new RejectedExecutionException(); }, rejected);
        check(rejected.count == 0, "rejection posts"); drain();
        check(!rejected.result.usedAsyncStartup() && WebView.loads == 0, "rejection safe fallback");
        done("rejected worker safely releases synchronous path");

        reset(); Callback failure = start();
        SupportLibReflectionUtil.failure.accept(fields -> { fields.accept(0, Integer.valueOf(5)); fields.accept(1, "native initialization failed"); });
        SupportLibReflectionUtil.success.accept(fields -> {}); drain();
        check(failure.count == 1 && failure.result == null && failure.error.getMessage().contains("native initialization failed (5)"), "failure never fallback");
        done("provider failure terminates without retry or duplicate completion");

        reset(); SupportLibReflectionUtil.throwOnStart = true;
        Callback thrown = start(); drain();
        check(thrown.error != null && thrown.result == null, "accepted startup throw is failure");
        done("startup exception does not construct WebView");

        reset(); Callback badDiagnostic = start();
        SupportLibReflectionUtil.success.accept(fields -> { throw new IllegalArgumentException("bad diagnostic"); }); drain();
        check(badDiagnostic.count == 1 && badDiagnostic.result.usedAsyncStartup(), "optional timing cannot block readiness");
        done("malformed optional diagnostics preserve successful startup");

        reset(); WebView.loadFailure = new IllegalStateException("provider unavailable");
        Callback noProvider = start(); drain();
        check(noProvider.error != null && noProvider.result == null, "framework load failure not fallback");
        done("framework provider initialization failure is terminal");

        reset();
        WebView.loader = new ClassLoader(WebView.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("org.chromium.support_lib_glue.SupportLibReflectionUtil")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        Callback noGlue = start(); drain();
        check(noGlue.error == null && !noGlue.result.usedAsyncStartup(), "optional glue failure fallback");
        done("missing optional support glue keeps framework fallback");

        reset(); Callback racing = start();
        CountDownLatch go = new CountDownLatch(1);
        Thread good = new Thread(() -> { await(go); SupportLibReflectionUtil.success.accept(fields -> {}); });
        Thread bad = new Thread(() -> { await(go); SupportLibReflectionUtil.failure.accept(fields -> {}); });
        good.start(); bad.start(); go.countDown(); good.join(); bad.join(); drain();
        check(racing.count == 1 && (racing.result != null ^ racing.error != null), "competing callbacks exactly once");
        done("concurrent provider completions deliver once");

        reset();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        SupportLibReflectionUtil.featureEntered = entered;
        SupportLibReflectionUtil.featureRelease = release;
        Thread loading = new Thread(() -> WebViewGlueCommunicator.getFactoryForStartup());
        loading.start(); check(entered.await(3, TimeUnit.SECONDS), "factory loading reached feature gate");
        AtomicBoolean queryReturned = new AtomicBoolean(), featureSupported = new AtomicBoolean();
        Thread query = new Thread(() -> { featureSupported.set(WebViewGlueCommunicator.isFeatureSupported("DOCUMENT_START_SCRIPT:1")); queryReturned.set(true); });
        query.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (query.getState() != Thread.State.BLOCKED && !queryReturned.get() && System.nanoTime() < deadline) Thread.yield();
        check(!queryReturned.get() && query.getState() == Thread.State.BLOCKED, "concurrent query waits for complete publication");
        release.countDown(); loading.join(3000); query.join(3000);
        check(queryReturned.get() && featureSupported.get(), "query observes final features");
        done("concurrent feature query never observes half-loaded factory");
        System.out.println("All " + cases + " behavior cases passed against production Java sources.");
    }
    private static void await(CountDownLatch gate) {
        try { if (!gate.await(3, TimeUnit.SECONDS)) throw new AssertionError("gate timeout"); }
        catch (InterruptedException e) { throw new AssertionError(e); }
    }
}
