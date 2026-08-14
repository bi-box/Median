package com.xinyv.median;

import android.annotation.TargetApi;
import android.webkit.WebView;
import android.webkit.WebViewRenderProcess;
import android.webkit.WebViewRenderProcessClient;

import java.util.concurrent.Executor;

/** Keeps API 29 renderer-process symbols out of the Android 8/9 activity verifier path. */
@TargetApi(29)
final class RendererWatchdog {
    interface Terminator { boolean terminate(); }

    interface Callback {
        void onUnresponsive(WebView view, Terminator terminateRenderer);
        void onResponsive(WebView view);
    }

    private RendererWatchdog() {}

    static void attach(WebView view, Executor executor, final Callback callback) {
        view.setWebViewRenderProcessClient(executor, new WebViewRenderProcessClient() {
            @Override public void onRenderProcessUnresponsive(WebView view, final WebViewRenderProcess renderer) {
                callback.onUnresponsive(view, new Terminator() {
                    @Override public boolean terminate() {
                        try { return renderer.terminate(); } catch (RuntimeException ignored) { return false; }
                    }
                });
            }

            @Override public void onRenderProcessResponsive(WebView view, WebViewRenderProcess renderer) {
                callback.onResponsive(view);
            }
        });
    }

    static void detach(WebView view) {
        try { view.setWebViewRenderProcessClient((WebViewRenderProcessClient) null); }
        catch (RuntimeException ignored) {}
    }
}
