package com.xinyv.median;

import android.webkit.WebSettings;

/** One security/compatibility baseline shared by every browser WebView profile. */
final class WebViewPolicy {
    private WebViewPolicy() {}

    static void applySecureDefaults(WebSettings settings, int cacheMode) {
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setCacheMode(cacheMode);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setSaveFormData(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
    }

    static String mobileUserAgent(String userAgent) {
        if (userAgent == null) return "";
        return userAgent.replace("; wv", "").replace(" Version/4.0", "").replace("  ", " ").trim();
    }
}
