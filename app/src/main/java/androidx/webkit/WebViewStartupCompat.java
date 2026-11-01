/* Copyright 2026. Uses Chromium's public AndroidX support-library boundary protocol. */
package androidx.webkit;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.webkit.internal.WebViewGlueCommunicator;

import org.chromium.support_lib_boundary.WebViewProviderFactoryBoundaryInterface;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Focused optional WebView startup facade. Call before any other WebView APIs and construct the
 * first WebView only from onSuccess. The V2 protocol moves supported provider initialization
 * off the UI thread; UI-only Chromium tasks still run on the UI thread.
 *
 * Older providers (and API 26/27) return usedAsyncStartup=false, leaving initialization to the
 * caller's normal main-thread WebView constructor. No hidden API or background WebView is used.
 */
public final class WebViewStartupCompat {
    private static final String ASYNC_STARTUP_V2 = "ASYNC_WEBVIEW_STARTUP_V2";

    private WebViewStartupCompat() {}

    public interface Callback {
        /** Always posted to the main looper, including the synchronous fallback case. */
        void onSuccess(Result result);

        /**
         * Always posted to the main looper. Do not call WebView APIs after provider startup fails;
         * AndroidX documents that doing so can throw or crash the process.
         */
        void onFailure(Throwable error);
    }

    public static final class Result {
        private final boolean async;
        private final Long totalUiMillis;
        private final Long maxUiTaskMillis;

        private Result(boolean async, Long totalUiMillis, Long maxUiTaskMillis) {
            this.async = async;
            this.totalUiMillis = totalUiMillis;
            this.maxUiTaskMillis = maxUiTaskMillis;
        }

        /** False means that the caller should use its existing synchronous WebView creation. */
        public boolean usedAsyncStartup() { return async; }
        /** Optional provider diagnostics; unavailable on the synchronous fallback path. */
        public Long getTotalTimeInUiThreadMillis() { return totalUiMillis; }
        public Long getMaxTimePerTaskInUiThreadMillis() { return maxUiTaskMillis; }
    }

    /** Starts provider preparation on the supplied executor without holding an Activity context. */
    public static void startUpWebView(final Executor backgroundExecutor, final Callback callback) {
        if (backgroundExecutor == null) throw new NullPointerException("backgroundExecutor");
        if (callback == null) throw new NullPointerException("callback");
        final Completion completion = new Completion(callback);
        try {
            backgroundExecutor.execute(new Runnable() {
                @Override public void run() {
                    // Avoid running provider initialization on the UI thread with a direct or
                    // caller-runs executor. Let the proven framework constructor handle it.
                    if (Build.VERSION.SDK_INT < 28 || Looper.myLooper() == Looper.getMainLooper()) {
                        completion.success(new Result(false, null, null));
                        return;
                    }
                    try {
                        WebViewProviderFactoryBoundaryInterface factory =
                                WebViewGlueCommunicator.getFactoryForStartup();
                        WebViewNavigationWarmupCompat.onStartupBoundaryReady();
                        if (factory == null || !WebViewGlueCommunicator.isFeatureSupported(ASYNC_STARTUP_V2)) {
                            completion.success(new Result(false, null, null));
                            return;
                        }
                        Consumer<BiConsumer<Integer, Object>> config = new Consumer<BiConsumer<Integer, Object>>() {
                            @Override public void accept(BiConsumer<Integer, Object> fields) {
                                fields.accept(0, backgroundExecutor);
                                fields.accept(1, Boolean.TRUE);
                                // Omit profile names: preserve Chromium's default-profile behavior.
                            }
                        };
                        factory.startUpWebView(config,
                                new Consumer<Consumer<BiConsumer<Integer, Object>>>() {
                                    @Override public void accept(Consumer<BiConsumer<Integer, Object>> stream) {
                                        completion.success(readResult(stream));
                                    }
                                },
                                new Consumer<Consumer<BiConsumer<Integer, Object>>>() {
                                    @Override public void accept(Consumer<BiConsumer<Integer, Object>> stream) {
                                        completion.failure(readFailure(stream));
                                    }
                                });
                    } catch (RuntimeException error) {
                        completion.failure(error);
                    } catch (LinkageError error) {
                        completion.failure(error);
                    }
                }
            });
        } catch (RejectedExecutionException rejected) {
            // No provider task was accepted, so normal main-thread initialization is safe.
            completion.success(new Result(false, null, null));
        }
    }

    private static Result readResult(Consumer<BiConsumer<Integer, Object>> stream) {
        final Long[] timing = new Long[2];
        try {
            if (stream != null) stream.accept(new BiConsumer<Integer, Object>() {
                @Override public void accept(Integer key, Object value) {
                    if (key != null && key >= 0 && key < timing.length && value instanceof Long)
                        timing[key] = (Long) value;
                }
            });
        } catch (RuntimeException ignored) {
            // Startup succeeded; optional diagnostic serialization must not prevent navigation.
        } catch (LinkageError ignored) {
            // A diagnostic adapter incompatibility must not invalidate completed startup.
        }
        return new Result(true, timing[0], timing[1]);
    }

    private static Throwable readFailure(Consumer<BiConsumer<Integer, Object>> stream) {
        final String[] message = { "WebView startup failed" };
        final Integer[] code = new Integer[1];
        try {
            if (stream != null) stream.accept(new BiConsumer<Integer, Object>() {
                @Override public void accept(Integer key, Object value) {
                    if (Integer.valueOf(0).equals(key) && value instanceof Integer) code[0] = (Integer) value;
                    if (Integer.valueOf(1).equals(key) && value instanceof String) message[0] = (String) value;
                }
            });
        } catch (RuntimeException error) {
            return new IllegalStateException("WebView startup failed", error);
        } catch (LinkageError error) {
            return new IllegalStateException("WebView startup failed", error);
        }
        return new IllegalStateException(message[0] + (code[0] == null ? "" : " (" + code[0] + ")"));
    }

    private static final class Completion {
        private final AtomicBoolean delivered = new AtomicBoolean();
        private final Handler main = new Handler(Looper.getMainLooper());
        private final Callback callback;

        Completion(Callback callback) { this.callback = callback; }

        void success(final Result result) {
            if (delivered.compareAndSet(false, true))
                main.post(new Runnable() {
                    @Override public void run() { callback.onSuccess(result); }
                });
        }

        void failure(final Throwable error) {
            if (delivered.compareAndSet(false, true))
                main.post(new Runnable() {
                    @Override public void run() { callback.onFailure(error); }
                });
        }
    }
}
