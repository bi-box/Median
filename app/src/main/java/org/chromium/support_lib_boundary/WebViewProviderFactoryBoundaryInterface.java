/* Copyright 2018 The Chromium Authors. BSD-style license. */
package org.chromium.support_lib_boundary;

import android.webkit.WebView;
import java.lang.reflect.InvocationHandler;

public interface WebViewProviderFactoryBoundaryInterface {
    InvocationHandler createWebView(WebView webView);
    InvocationHandler getWebkitToCompatConverter();
    String[] getSupportedFeatures();
}
