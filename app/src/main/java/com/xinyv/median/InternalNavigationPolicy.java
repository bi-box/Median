package com.xinyv.median;

/** Stable policy for commands emitted by Median's built-in homepage. */
final class InternalNavigationPolicy {
    private InternalNavigationPolicy() {}

    static boolean canHandleCommand(boolean trustedBuiltInHome, boolean customHome) {
        // Do not consult WebView.getUrl() here. During shouldOverrideUrlLoading() some WebView
        // providers already expose the pending median:// URL instead of the committed document.
        return trustedBuiltInHome && !customHome;
    }

    static boolean shouldClearHomeTrust(boolean homeUrl, String startedUrl) {
        if (homeUrl || startedUrl == null) return false;
        String value = startedUrl.trim().toLowerCase(java.util.Locale.US);
        // These may be transient callbacks while loadDataWithBaseURL() or an intercepted command
        // is being resolved. Neither commits an untrusted network document.
        return !value.equals("about:blank") && !value.startsWith("median://");
    }
}
