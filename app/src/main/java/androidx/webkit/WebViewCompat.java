/*
 * Copyright 2018 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0.
 */
package androidx.webkit;

import android.net.Uri;
import android.webkit.WebView;

import androidx.webkit.internal.WebViewGlueCommunicator;

import java.util.LinkedHashSet;
import java.util.Set;

/** Focused AndroidX-compatible facade for the document-start API used by Median. */
public final class WebViewCompat {
    private WebViewCompat() {}

    public static ScriptHandler addDocumentStartJavaScript(
            WebView webView, String script, Set<String> allowedOriginRules) {
        if (allowedOriginRules == null || allowedOriginRules.isEmpty())
            throw new IllegalArgumentException("allowedOriginRules must not be empty");
        LinkedHashSet<String> normalized = new LinkedHashSet<String>();
        for (String rule : allowedOriginRules) normalized.add(normalizeOriginRule(rule));
        return addDocumentStartJavaScript(webView, script, normalized.toArray(new String[0]));
    }

    /** Allocation-light path for the single origin rule used by browser registrations. */
    public static ScriptHandler addDocumentStartJavaScript(
            WebView webView, String script, String allowedOriginRule) {
        return addDocumentStartJavaScript(webView, script,
                new String[] { normalizeOriginRule(allowedOriginRule) });
    }

    private static ScriptHandler addDocumentStartJavaScript(
            WebView webView, String script, String[] rules) {
        if (webView == null) throw new NullPointerException("webView");
        if (script == null) throw new NullPointerException("script");
        if (script.length() == 0) throw new IllegalArgumentException("script must not be empty");
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            throw new UnsupportedOperationException(
                    "DOCUMENT_START_SCRIPT is not supported by this WebView provider");
        }
        return WebViewGlueCommunicator.addDocumentStartJavaScript(webView, script, rules);
    }

    public static String getDocumentStartDiagnosticReport() {
        return WebViewGlueCommunicator.diagnosticReport();
    }

    public static void refreshWebViewProvider() {
        WebViewGlueCommunicator.invalidate();
    }

    private static String normalizeOriginRule(String rule) {
        if (rule == null) throw new IllegalArgumentException("origin rule must not be null");
        String value = rule.trim();
        if (value.length() == 0) throw new IllegalArgumentException("origin rule must not be empty");
        if ("*".equals(value)) return value;
        if (value.indexOf('/') >= 0 && !value.contains("://"))
            throw new IllegalArgumentException("origin rule requires a scheme: " + value);
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || host.length() == 0
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && uri.getPath().length() > 0))
            throw new IllegalArgumentException("invalid origin rule: " + value);
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)))
            throw new IllegalArgumentException("unsupported origin scheme: " + scheme);
        if (host.startsWith("*.") && host.length() <= 2)
            throw new IllegalArgumentException("invalid wildcard host: " + value);
        return value;
    }
}
