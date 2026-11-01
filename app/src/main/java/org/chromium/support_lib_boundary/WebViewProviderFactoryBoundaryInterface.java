/* Copyright 2018 The Chromium Authors. BSD-style license. */
package org.chromium.support_lib_boundary;

import android.webkit.WebView;
import java.lang.reflect.InvocationHandler;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface WebViewProviderFactoryBoundaryInterface {
    InvocationHandler createWebView(WebView webView);
    InvocationHandler getWebkitToCompatConverter();
    String[] getSupportedFeatures();
    InvocationHandler getStatics();
    InvocationHandler getProfileStore();

    /** Chromium ASYNC_WEBVIEW_STARTUP_V2 protocol; keys follow the official boundary API. */
    void startUpWebView(Consumer<BiConsumer<Integer, Object>> config,
            Consumer<Consumer<BiConsumer<Integer, Object>>> onSuccess,
            Consumer<Consumer<BiConsumer<Integer, Object>>> onFailure);
}
